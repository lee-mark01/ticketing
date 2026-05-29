package com.ticketing.event.repository;

import com.ticketing.event.entity.EventSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    @Query(value = """
            SELECT es.id        AS eventSeatId,
                   s.section     AS section,
                   s.seat_row    AS row,
                   s.seat_number AS number,
                   es.status     AS status,
                   es.price      AS price
            FROM event_seats es
            JOIN seats s ON s.id = es.seat_id
            WHERE es.event_id = :eventId
            ORDER BY s.section, s.seat_row, s.seat_number
            """, nativeQuery = true)
    List<SeatMapProjection> findSeatMapByEventId(@Param("eventId") Long eventId);
}
