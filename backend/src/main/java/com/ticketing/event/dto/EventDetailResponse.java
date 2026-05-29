package com.ticketing.event.dto;

import com.ticketing.event.entity.Event;

import java.time.LocalDateTime;

public record EventDetailResponse(
        Long id,
        String title,
        LocalDateTime startsAt,
        LocalDateTime bookingOpensAt,
        int availableSeatCount,
        VenueInfo venue
) {
    public record VenueInfo(Long id, String name, String address) {}

    public static EventDetailResponse from(Event event) {
        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getStartsAt(),
                event.getBookingOpensAt(),
                event.getAvailableSeatCount(),
                new VenueInfo(
                        event.getVenue().getId(),
                        event.getVenue().getName(),
                        event.getVenue().getAddress()));
    }
}
