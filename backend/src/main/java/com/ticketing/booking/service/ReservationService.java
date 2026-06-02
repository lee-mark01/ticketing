package com.ticketing.booking.service;

import com.ticketing.booking.dto.*;
import com.ticketing.booking.entity.Payment;
import com.ticketing.booking.entity.Reservation;
import com.ticketing.booking.entity.ReservationItem;
import com.ticketing.booking.entity.ReservationStatus;
import com.ticketing.booking.repository.MyReservationProjection;
import com.ticketing.booking.repository.PaymentRepository;
import com.ticketing.booking.repository.ReservationItemRepository;
import com.ticketing.booking.repository.ReservationRepository;
import com.ticketing.common.dto.PageResponse;
import com.ticketing.common.exception.BusinessException;
import com.ticketing.event.entity.Event;
import com.ticketing.event.entity.EventSeat;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.EventSeatRepository;
import com.ticketing.user.entity.User;
import com.ticketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int HOLD_MINUTES = 7;

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final PaymentRepository paymentRepository;

    // 테스트 전용: seat.sell() 이후, Payment 생성 직전에 실행되는 훅
    private Runnable beforePaymentHook;

    void setBeforePaymentHook(Runnable hook) {
        this.beforePaymentHook = hook;
    }

    @Transactional
    public HoldResponse holdSeats(Long eventId, List<Long> seatIds, Long userId) {
        // 1. 중복 검사
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "INVALID_SEATS", "중복된 좌석 ID가 포함되어 있습니다.");
        }

        // 2. Event 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EVENT_NOT_FOUND", "공연을 찾을 수 없습니다."));

        // 3. User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        // 4. FOR UPDATE + eventId 조건으로 좌석 조회
        List<EventSeat> seats = eventSeatRepository.findAllByEventIdAndIdInWithLock(eventId, seatIds);

        // 5. 조회 결과 수 != 요청 수 → 존재하지 않거나 다른 공연 좌석
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "INVALID_SEATS", "유효하지 않은 좌석이 포함되어 있습니다.");
        }

        // 6. 각 좌석 HOLD
        try {
            for (EventSeat seat : seats) {
                seat.hold();
            }
        } catch (IllegalStateException e) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "SEAT_NOT_AVAILABLE", "이미 선점된 좌석입니다.");
        }

        // 7. availableSeatCount 감소
        event.decreaseAvailableSeatCount(seatIds.size());

        // 8. Reservation 생성
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(HOLD_MINUTES);
        Reservation reservation = Reservation.create(user, event, expiresAt);
        reservationRepository.save(reservation);

        // 9. ReservationItem 생성
        int totalAmount = 0;
        List<HoldResponse.SeatDto> heldSeats = new java.util.ArrayList<>();
        for (EventSeat seat : seats) {
            reservationItemRepository.save(ReservationItem.create(reservation, seat));
            heldSeats.add(new HoldResponse.SeatDto(seat.getId(), seat.getPrice()));
            totalAmount += seat.getPrice();
        }

        return new HoldResponse(
                reservation.getId(),
                reservation.getStatus().name(),
                heldSeats,
                totalAmount,
                expiresAt
        );
    }

    @Transactional
    public void cancelHold(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RESERVATION_NOT_FOUND", "예매를 찾을 수 없습니다."));

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ALREADY_CONFIRMED", "이미 확정된 예매는 이 방법으로 취소할 수 없습니다.");
        }

        reservation.cancel();

        List<ReservationItem> items = reservationItemRepository.findByReservationId(reservationId);
        for (ReservationItem item : items) {
            item.getEventSeat().release();
        }

        Event event = reservation.getEvent();
        event.increaseAvailableSeatCount(items.size());
    }

    @Transactional
    public ConfirmResponse confirmReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RESERVATION_NOT_FOUND", "예매를 찾을 수 없습니다."));

        if (reservation.isExpired()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "RESERVATION_EXPIRED", "HOLD가 만료되었습니다.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ALREADY_CONFIRMED", "이미 처리된 예매입니다.");
        }

        // 좌석 HELD→SOLD
        List<ReservationItem> items = reservationItemRepository.findByReservationId(reservationId);
        int totalAmount = 0;
        for (ReservationItem item : items) {
            item.getEventSeat().sell();
            totalAmount += item.getEventSeat().getPrice();
        }

        // availableSeatCount 변경 없음 (이미 HOLD에서 감소됨)

        // 테스트 전용 훅: seat.sell() 이후, Payment 생성 직전
        if (beforePaymentHook != null) {
            beforePaymentHook.run();
        }

        // Payment 생성
        Payment payment = Payment.create(reservation, totalAmount);
        paymentRepository.save(payment);

        // Reservation 확정
        reservation.confirm();

        return ConfirmResponse.from(
                reservation.getId(),
                reservation.getStatus().name(),
                payment
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<MyReservationResponse> getMyReservations(Long userId, Pageable pageable) {
        Page<MyReservationProjection> page = reservationRepository.findMyReservations(userId, pageable);

        List<MyReservationResponse> content = page.getContent().stream()
                .map(p -> new MyReservationResponse(
                        p.getReservationId(),
                        p.getEventTitle(),
                        p.getStatus(),
                        p.getSeats() != null ? Arrays.asList(p.getSeats().split(", ")) : List.of(),
                        p.getTotalAmount(),
                        p.getCreatedAt()))
                .toList();

        return PageResponse.from(page, content);
    }
}
