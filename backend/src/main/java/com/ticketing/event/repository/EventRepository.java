package com.ticketing.event.repository;

import com.ticketing.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e JOIN FETCH e.venue ORDER BY e.startsAt DESC")
    Page<Event> findAllWithVenue(Pageable pageable);

    @Query("SELECT e FROM Event e JOIN FETCH e.venue WHERE e.id = :eventId")
    java.util.Optional<Event> findByIdWithVenue(Long eventId);

    @Modifying
    @Query(value = """
            UPDATE events
            SET available_seat_count = available_seat_count - :count
            WHERE id = :eventId
              AND available_seat_count >= :count
            """, nativeQuery = true)
    int decreaseAvailableSeatCount(@Param("eventId") Long eventId, @Param("count") int count);

    @Modifying
    @Query(value = """
            UPDATE events
            SET available_seat_count = available_seat_count + :count
            WHERE id = :eventId
            """, nativeQuery = true)
    int increaseAvailableSeatCount(@Param("eventId") Long eventId, @Param("count") int count);
}
