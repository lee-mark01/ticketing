package com.ticketing.booking.service;

import com.ticketing.booking.entity.Reservation;
import com.ticketing.booking.entity.ReservationItem;
import com.ticketing.booking.repository.ReservationItemRepository;
import com.ticketing.booking.repository.ReservationRepository;
import com.ticketing.common.exception.BusinessException;
import com.ticketing.event.entity.Event;
import com.ticketing.event.entity.EventSeat;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.EventSeatRepository;
import com.ticketing.user.entity.User;
import com.ticketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * ⚠️ 동시성 비교 실험 전용 서비스 — 운영 사용 금지.
 *
 * 락 전략별(NAIVE/PESSIMISTIC/OPTIMISTIC) 동작 차이를 증명하기 위한 실험용 코드.
 * 운영 예매 로직은 반드시 ReservationService.holdSeats()를 사용할 것.
 */
@Service
@RequiredArgsConstructor
public class ConcurrencyExperimentService {

    private static final int HOLD_MINUTES = 7;

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final JdbcTemplate jdbcTemplate;

    // 테스트 전용: 조회 완료 후 모든 스레드가 동시에 UPDATE로 진입하기 위한 barrier
    private CyclicBarrier readBarrier;

    void setReadBarrier(CyclicBarrier barrier) {
        this.readBarrier = barrier;
    }

    /**
     * ⚠️ NAIVE — 의도적 unsafe 구현. 운영 사용 금지.
     *
     * JPA dirty checking(@Version)을 우회하여 version 조건 없이 직접 UPDATE.
     * 동시성 보호가 없으므로 중복 예매(oversell)가 발생할 수 있음.
     */
    @Transactional
    public void holdSeatsNaive(Long eventId, Long seatId, Long userId) {
        // 1. native SQL로 좌석 상태 조회 (락 없음, JPA 영속성 컨텍스트 우회)
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM event_seats WHERE event_id = ? AND id = ?",
                String.class, eventId, seatId);

        if (!"AVAILABLE".equals(status)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "SEAT_NOT_AVAILABLE", "이미 선점된 좌석입니다.");
        }

        // 2. readBarrier — 모든 스레드가 AVAILABLE 확인 후 대기
        awaitBarrier();

        // 3. ⚠️ unsafe UPDATE: version 조건 없이 직접 상태 변경
        jdbcTemplate.update(
                "UPDATE event_seats SET status = 'HELD' WHERE id = ?", seatId);

        // 4. ⚠️ unsafe: available_seat_count 감소 (version 조건 없음)
        jdbcTemplate.update(
                "UPDATE events SET available_seat_count = available_seat_count - 1 WHERE id = ?",
                eventId);

        // 5. reservation, reservation_items 생성
        User user = userRepository.findById(userId).orElseThrow();
        Event event = eventRepository.findById(eventId).orElseThrow();
        Reservation reservation = Reservation.create(user, event,
                LocalDateTime.now().plusMinutes(HOLD_MINUTES));
        reservationRepository.save(reservation);

        EventSeat seat = eventSeatRepository.findById(seatId).orElseThrow();
        reservationItemRepository.save(ReservationItem.create(reservation, seat));
    }

    /**
     * PESSIMISTIC — SELECT ... FOR UPDATE 사용.
     *
     * readBarrier를 사용하지 않음. FOR UPDATE가 자연스럽게 직렬화.
     */
    @Transactional
    public void holdSeatsPessimistic(Long eventId, Long seatId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EVENT_NOT_FOUND", "공연을 찾을 수 없습니다."));
        User user = userRepository.findById(userId).orElseThrow();

        // FOR UPDATE로 row lock 획득. 다른 스레드는 여기서 대기.
        List<EventSeat> seats = eventSeatRepository
                .findAllByEventIdAndIdInWithLock(eventId, List.of(seatId));

        if (seats.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "INVALID_SEATS", "유효하지 않은 좌석입니다.");
        }

        EventSeat seat = seats.get(0);
        try {
            seat.hold();
        } catch (IllegalStateException e) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "SEAT_NOT_AVAILABLE", "이미 선점된 좌석입니다.");
        }

        event.decreaseAvailableSeatCount(1);

        Reservation reservation = Reservation.create(user, event,
                LocalDateTime.now().plusMinutes(HOLD_MINUTES));
        reservationRepository.save(reservation);
        reservationItemRepository.save(ReservationItem.create(reservation, seat));
    }

    /**
     * OPTIMISTIC — 락 없이 조회 후 JPA @Version으로 충돌 감지.
     *
     * readBarrier로 모든 스레드가 version=0을 읽은 뒤 동시에 flush.
     * 먼저 커밋한 쪽만 성공, 나머지는 ObjectOptimisticLockingFailureException.
     */
    @Transactional
    public void holdSeatsOptimistic(Long eventId, Long seatId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EVENT_NOT_FOUND", "공연을 찾을 수 없습니다."));
        User user = userRepository.findById(userId).orElseThrow();

        // 락 없이 조회 — 모든 스레드가 AVAILABLE, version=0 읽음
        List<EventSeat> seats = eventSeatRepository
                .findAllByEventIdAndIdIn(eventId, List.of(seatId));

        if (seats.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "INVALID_SEATS", "유효하지 않은 좌석입니다.");
        }

        EventSeat seat = seats.get(0);
        try {
            seat.hold();
        } catch (IllegalStateException e) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "SEAT_NOT_AVAILABLE", "이미 선점된 좌석입니다.");
        }

        // readBarrier — 모든 스레드가 조회+hold() 완료 후 대기
        // 이후 트랜잭션 커밋 시 @Version이 WHERE version = 0 조건 추가
        awaitBarrier();

        event.decreaseAvailableSeatCount(1);

        Reservation reservation = Reservation.create(user, event,
                LocalDateTime.now().plusMinutes(HOLD_MINUTES));
        reservationRepository.save(reservation);
        reservationItemRepository.save(ReservationItem.create(reservation, seat));
    }

    /**
     * PESSIMISTIC — 다중 좌석 묶음 HOLD.
     */
    @Transactional
    public void holdSeatsPessimisticMulti(Long eventId, List<Long> seatIds, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EVENT_NOT_FOUND", "공연을 찾을 수 없습니다."));
        User user = userRepository.findById(userId).orElseThrow();

        List<EventSeat> seats = eventSeatRepository
                .findAllByEventIdAndIdInWithLock(eventId, seatIds);

        if (seats.size() != seatIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "INVALID_SEATS", "유효하지 않은 좌석이 포함되어 있습니다.");
        }

        try {
            for (EventSeat seat : seats) {
                seat.hold();
            }
        } catch (IllegalStateException e) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "SEAT_NOT_AVAILABLE", "이미 선점된 좌석입니다.");
        }

        event.decreaseAvailableSeatCount(seatIds.size());

        Reservation reservation = Reservation.create(user, event,
                LocalDateTime.now().plusMinutes(HOLD_MINUTES));
        reservationRepository.save(reservation);
        for (EventSeat seat : seats) {
            reservationItemRepository.save(ReservationItem.create(reservation, seat));
        }
    }

    private void awaitBarrier() {
        if (readBarrier != null) {
            try {
                readBarrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Barrier 대기 실패", e);
            }
        }
    }
}
