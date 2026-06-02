package com.ticketing.event.entity;

import com.ticketing.venue.entity.Seat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventSeatStatus status;

    @Column(nullable = false)
    private int price;

    @Version
    private int version;

    public void hold() {
        if (this.status != EventSeatStatus.AVAILABLE) {
            throw new IllegalStateException("AVAILABLE 상태에서만 HOLD할 수 있습니다. 현재: " + this.status);
        }
        this.status = EventSeatStatus.HELD;
    }

    public void release() {
        if (this.status != EventSeatStatus.HELD) {
            throw new IllegalStateException("HELD 상태에서만 해제할 수 있습니다. 현재: " + this.status);
        }
        this.status = EventSeatStatus.AVAILABLE;
    }

    public void sell() {
        if (this.status != EventSeatStatus.HELD) {
            throw new IllegalStateException("HELD 상태에서만 판매할 수 있습니다. 현재: " + this.status);
        }
        this.status = EventSeatStatus.SOLD;
    }
}
