import type {
  LoginRequest,
  LoginResponse,
  EventListResponse,
  EventDetailResponse,
  SeatResponse,
  HoldRequest,
  HoldResponse,
  ConfirmRequest,
  ConfirmResponse,
  MyReservationResponse,
  PageResponse,
} from "./types";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("token");
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }));
    throw { status: res.status, ...error };
  }

  if (res.status === 204) return undefined as T;
  return res.json();
}

// Auth
export async function login(data: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

// Events
export async function getEvents(
  page = 0,
  size = 20
): Promise<PageResponse<EventListResponse>> {
  return request(`/api/events?page=${page}&size=${size}`);
}

export async function getEvent(eventId: number): Promise<EventDetailResponse> {
  return request(`/api/events/${eventId}`);
}

export async function getSeats(eventId: number): Promise<SeatResponse> {
  return request(`/api/events/${eventId}/seats`);
}

// Booking
export async function holdSeats(
  eventId: number,
  data: HoldRequest
): Promise<HoldResponse> {
  return request(`/api/events/${eventId}/holds`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function confirmReservation(
  reservationId: number,
  data: ConfirmRequest
): Promise<ConfirmResponse> {
  return request(`/api/reservations/${reservationId}/confirm`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function cancelHold(reservationId: number): Promise<void> {
  return request(`/api/reservations/${reservationId}/hold`, {
    method: "DELETE",
  });
}

export async function getMyReservations(
  page = 0,
  size = 20
): Promise<PageResponse<MyReservationResponse>> {
  return request(`/api/me/reservations?page=${page}&size=${size}`);
}
