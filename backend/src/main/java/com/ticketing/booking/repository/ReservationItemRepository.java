package com.ticketing.booking.repository;

import com.ticketing.booking.entity.ReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationItemRepository extends JpaRepository<ReservationItem, Long> {

    List<ReservationItem> findByReservationId(Long reservationId);
}
