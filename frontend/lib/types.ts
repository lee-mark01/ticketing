// 백엔드 DTO 기준 TypeScript 타입 정의

// === Auth ===
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

// === Events ===
export interface EventListResponse {
  id: number;
  title: string;
  venueName: string;
  startsAt: string;
  bookingOpensAt: string;
  availableSeatCount: number;
}

export interface EventDetailResponse {
  id: number;
  title: string;
  startsAt: string;
  bookingOpensAt: string;
  availableSeatCount: number;
  venue: {
    id: number;
    name: string;
    address: string;
  };
}

// === Seats ===
export interface SeatDto {
  eventSeatId: number;
  section: string;
  row: string;
  number: string;
  status: string; // AVAILABLE | HELD | SOLD
  price: number;
}

export interface SeatResponse {
  eventId: number;
  seats: SeatDto[];
}

// === Booking ===
export interface HoldRequest {
  seatIds: number[];
}

export interface HoldResponse {
  reservationId: number;
  status: string;
  heldSeats: { eventSeatId: number; price: number }[];
  totalAmount: number;
  expiresAt: string;
}

export interface ConfirmRequest {
  paymentMethod: string;
}

export interface ConfirmResponse {
  reservationId: number;
  status: string;
  payment: {
    id: number;
    amount: number;
    status: string;
    paidAt: string;
  };
}

export interface MyReservationResponse {
  reservationId: number;
  eventTitle: string;
  status: string;
  seats: string[];
  totalAmount: number;
  createdAt: string;
}

// === Common ===
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
}
