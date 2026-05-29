package com.ticketing.event.dto;

import com.ticketing.event.entity.Event;

import java.time.LocalDateTime;

public record EventListResponse(
        Long id,
        String title,
        String venueName,
        LocalDateTime startsAt,
        LocalDateTime bookingOpensAt,
        int availableSeatCount
) {
    public static EventListResponse from(Event event) {
        return new EventListResponse(
                event.getId(),
                event.getTitle(),
                event.getVenue().getName(),
                event.getStartsAt(),
                event.getBookingOpensAt(),
                event.getAvailableSeatCount());
    }
}
