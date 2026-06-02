package com.ticketing.event.controller;

import com.ticketing.booking.dto.HoldRequest;
import com.ticketing.booking.dto.HoldResponse;
import com.ticketing.booking.service.ReservationService;
import com.ticketing.common.dto.PageResponse;
import com.ticketing.config.AuthUtil;
import com.ticketing.event.dto.EventDetailResponse;
import com.ticketing.event.dto.EventListResponse;
import com.ticketing.event.dto.SeatResponse;
import com.ticketing.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final ReservationService reservationService;

    @GetMapping
    public ResponseEntity<PageResponse<EventListResponse>> getEvents(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventService.getEvents(pageable));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventDetailResponse> getEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEvent(eventId));
    }

    @GetMapping("/{eventId}/seats")
    public ResponseEntity<SeatResponse> getSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getSeats(eventId));
    }

    @PostMapping("/{eventId}/holds")
    public ResponseEntity<HoldResponse> holdSeats(
            @PathVariable Long eventId,
            @RequestBody HoldRequest request) {
        HoldResponse response = reservationService.holdSeats(
                eventId, request.seatIds(), AuthUtil.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
