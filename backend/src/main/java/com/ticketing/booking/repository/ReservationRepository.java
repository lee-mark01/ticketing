package com.ticketing.booking.repository;

import com.ticketing.booking.entity.Reservation;
import com.ticketing.booking.entity.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByIdAndUserId(Long id, Long userId);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);

    @Query(value = """
            SELECT r.id            AS reservationId,
                   e.title          AS eventTitle,
                   r.status         AS status,
                   STRING_AGG(s.section || '-' || s.seat_row || '-' || s.seat_number, ', '
                              ORDER BY s.section, s.seat_row, s.seat_number) AS seats,
                   COALESCE(SUM(es.price), 0) AS totalAmount,
                   r.created_at     AS createdAt
            FROM reservations r
            JOIN events e ON e.id = r.event_id
            LEFT JOIN reservation_items ri ON ri.reservation_id = r.id
            LEFT JOIN event_seats es ON es.id = ri.event_seat_id
            LEFT JOIN seats s ON s.id = es.seat_id
            WHERE r.user_id = :userId
            GROUP BY r.id, e.title, r.status, r.created_at
            ORDER BY r.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM reservations WHERE user_id = :userId
            """,
            nativeQuery = true)
    Page<MyReservationProjection> findMyReservations(@Param("userId") Long userId, Pageable pageable);
}
