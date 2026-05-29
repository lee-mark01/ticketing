package com.ticketing.event.dto;

import java.util.List;

public record SeatResponse(
        Long eventId,
        List<SeatDto> seats
) {
    public record SeatDto(
            Long eventSeatId,
            String section,
            String row,
            String number,
            String status,
            int price
    ) {}
}
