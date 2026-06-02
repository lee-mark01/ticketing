package com.ticketing.booking.controller;

import com.ticketing.booking.dto.ConfirmRequest;
import com.ticketing.booking.dto.ConfirmResponse;
import com.ticketing.booking.dto.MyReservationResponse;
import com.ticketing.booking.service.ReservationService;
import com.ticketing.common.dto.PageResponse;
import com.ticketing.config.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @DeleteMapping("/api/reservations/{reservationId}/hold")
    public ResponseEntity<Void> cancelHold(@PathVariable Long reservationId) {
        reservationService.cancelHold(reservationId, AuthUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/reservations/{reservationId}/confirm")
    public ResponseEntity<ConfirmResponse> confirm(
            @PathVariable Long reservationId,
            @RequestBody ConfirmRequest request) {
        ConfirmResponse response = reservationService.confirmReservation(
                reservationId, AuthUtil.getCurrentUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/me/reservations")
    public ResponseEntity<PageResponse<MyReservationResponse>> getMyReservations(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                reservationService.getMyReservations(AuthUtil.getCurrentUserId(), pageable));
    }
}
