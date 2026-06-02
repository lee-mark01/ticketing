package com.ticketing.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MyReservationResponse(
        Long reservationId,
        String eventTitle,
        String status,
        List<String> seats,
        int totalAmount,
        LocalDateTime createdAt
) {
}
