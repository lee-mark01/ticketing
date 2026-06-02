package com.ticketing.booking.repository;

import java.time.LocalDateTime;

public interface MyReservationProjection {
    Long getReservationId();
    String getEventTitle();
    String getStatus();
    String getSeats();
    Integer getTotalAmount();
    LocalDateTime getCreatedAt();
}
