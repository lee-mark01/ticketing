package com.ticketing.event.controller;

import com.ticketing.common.dto.PageResponse;
import com.ticketing.event.dto.EventDetailResponse;
import com.ticketing.event.dto.EventListResponse;
import com.ticketing.event.dto.SeatResponse;
import com.ticketing.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

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
}
