package com.ticketing.booking.scheduler;

import com.ticketing.booking.entity.Reservation;
import com.ticketing.booking.entity.ReservationItem;
import com.ticketing.booking.entity.ReservationStatus;
import com.ticketing.booking.repository.ReservationItemRepository;
import com.ticketing.booking.repository.ReservationRepository;
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

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireHolds() {
        List<Reservation> expired = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, LocalDateTime.now());

        for (Reservation reservation : expired) {
            reservation.expire();

            List<ReservationItem> items = reservationItemRepository
                    .findByReservationId(reservation.getId());
            for (ReservationItem item : items) {
                item.getEventSeat().release();
            }

            reservation.getEvent().increaseAvailableSeatCount(items.size());

            log.info("만료 처리: reservationId={}, 해제 좌석 수={}", reservation.getId(), items.size());
        }
    }
}
