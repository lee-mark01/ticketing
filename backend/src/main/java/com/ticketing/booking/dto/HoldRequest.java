package com.ticketing.booking.dto;

import java.util.List;

public record HoldRequest(List<Long> seatIds) {
}
