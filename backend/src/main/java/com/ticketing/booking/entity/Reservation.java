package com.ticketing.booking.entity;

import com.ticketing.event.entity.Event;
import com.ticketing.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Reservation create(User user, Event event, LocalDateTime expiresAt) {
        Reservation r = new Reservation();
        r.user = user;
        r.event = event;
        r.status = ReservationStatus.PENDING;
        r.expiresAt = expiresAt;
        return r;
    }

    public boolean isExpired() {
        return this.expiresAt != null && LocalDateTime.now().isAfter(this.expiresAt);
    }

    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 확정할 수 있습니다. 현재: " + this.status);
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status == ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("이미 확정된 예매는 이 방법으로 취소할 수 없습니다.");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 만료할 수 있습니다. 현재: " + this.status);
        }
        this.status = ReservationStatus.EXPIRED;
    }
}
