package com.ticketing.booking.scheduler;

import com.ticketing.booking.entity.Reservation;
import com.ticketing.booking.entity.ReservationItem;
import com.ticketing.booking.entity.ReservationStatus;
import com.ticketing.booking.repository.ReservationItemRepository;
import com.ticketing.booking.repository.ReservationRepository;
import com.ticketing.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final EventRepository eventRepository;
    // 테스트 전용: 상태를 읽은 뒤 상태 전이를 시도하기 직전에 동시 실행을 맞추는 훅
    private Runnable beforeStatusTransitionHook;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireHolds() {
        List<Reservation> expired = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, LocalDateTime.now());

        for (Reservation reservation : expired) {
            expireReservation(reservation, LocalDateTime.now());
        }
    }

    private void expireReservation(Reservation reservation, LocalDateTime now) {
        if (beforeStatusTransitionHook != null) {
            beforeStatusTransitionHook.run();
        }
        if (reservationRepository.expireIfPending(reservation.getId(), now) == 0) {
            return;
        }

        List<ReservationItem> items = reservationItemRepository
                .findByReservationId(reservation.getId());
        for (ReservationItem item : items) {
            item.getEventSeat().release();
        }

        eventRepository.increaseAvailableSeatCount(reservation.getEvent().getId(), items.size());

        log.info("만료 처리: reservationId={}, 해제 좌석 수={}", reservation.getId(), items.size());
    }
}
