package com.ticketing.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HoldResponse(
        Long reservationId,
        String status,
        List<SeatDto> heldSeats,
        int totalAmount,
        LocalDateTime expiresAt
) {
    public record SeatDto(Long eventSeatId, int price) {
    }
}
