package com.ticketing.event.service;

import com.ticketing.common.dto.PageResponse;
import com.ticketing.common.exception.BusinessException;
import com.ticketing.event.dto.EventDetailResponse;
import com.ticketing.event.dto.EventListResponse;
import com.ticketing.event.dto.SeatResponse;
import com.ticketing.event.entity.Event;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.EventSeatRepository;
import com.ticketing.event.repository.SeatMapProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;

    public PageResponse<EventListResponse> getEvents(Pageable pageable) {
        Page<Event> page = eventRepository.findAllWithVenue(pageable);
        List<EventListResponse> content = page.getContent().stream()
                .map(EventListResponse::from)
                .toList();
        return PageResponse.from(page, content);
    }

    public EventDetailResponse getEvent(Long eventId) {
        Event event = eventRepository.findByIdWithVenue(eventId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EVENT_NOT_FOUND", "공연을 찾을 수 없습니다."));
        return EventDetailResponse.from(event);
    }

    public SeatResponse getSeats(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "EVENT_NOT_FOUND", "공연을 찾을 수 없습니다.");
        }

        List<SeatMapProjection> projections = eventSeatRepository.findSeatMapByEventId(eventId);
        List<SeatResponse.SeatDto> seats = projections.stream()
                .map(p -> new SeatResponse.SeatDto(
                        p.getEventSeatId(), p.getSection(), p.getRow(),
                        p.getNumber(), p.getStatus(), p.getPrice()))
                .toList();
        return new SeatResponse(eventId, seats);
    }
}
