package com.ticketing.event.repository;

import com.ticketing.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e JOIN FETCH e.venue ORDER BY e.startsAt DESC")
    Page<Event> findAllWithVenue(Pageable pageable);

    @Query("SELECT e FROM Event e JOIN FETCH e.venue WHERE e.id = :eventId")
    java.util.Optional<Event> findByIdWithVenue(Long eventId);
}
