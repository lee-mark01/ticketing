package com.ticketing.event.repository;

public interface SeatMapProjection {
    Long getEventSeatId();
    String getSection();
    String getRow();
    String getNumber();
    String getStatus();
    Integer getPrice();
}
