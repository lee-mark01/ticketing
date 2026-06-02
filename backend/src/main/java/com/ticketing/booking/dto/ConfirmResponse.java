package com.ticketing.booking.dto;

import com.ticketing.booking.entity.Payment;

import java.time.LocalDateTime;

public record ConfirmResponse(
        Long reservationId,
        String status,
        PaymentDto payment
) {
    public record PaymentDto(Long id, int amount, String status, LocalDateTime paidAt) {
    }

    public static ConfirmResponse from(Long reservationId, String status, Payment payment) {
        return new ConfirmResponse(
                reservationId,
                status,
                new PaymentDto(payment.getId(), payment.getAmount(),
                        payment.getStatus().name(), payment.getPaidAt())
        );
    }
}
