"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { getEvent, getSeats, holdSeats } from "@/lib/api";
import type { EventDetailResponse, SeatDto } from "@/lib/types";

const STATUS_STYLE: Record<string, string> = {
  AVAILABLE: "bg-emerald-500/80 hover:bg-emerald-400 cursor-pointer",
  HELD: "bg-amber-500/60 cursor-not-allowed",
  SOLD: "bg-slate-600/60 cursor-not-allowed",
};

export default function EventDetailPage() {
  const params = useParams();
  const router = useRouter();
  const eventId = Number(params.id);

  const [event, setEvent] = useState<EventDetailResponse | null>(null);
  const [seats, setSeats] = useState<SeatDto[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [error, setError] = useState("");
  const [holding, setHolding] = useState(false);

  useEffect(() => {
    getEvent(eventId).then(setEvent).catch(() => setError("공연을 찾을 수 없습니다."));
    getSeats(eventId).then((r) => setSeats(r.seats)).catch(() => {});
  }, [eventId]);

  function toggleSeat(seat: SeatDto) {
    if (seat.status !== "AVAILABLE") return;
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(seat.eventSeatId)) next.delete(seat.eventSeatId);
      else next.add(seat.eventSeatId);
      return next;
    });
  }

  async function handleHold() {
    if (selected.size === 0) return;
    setHolding(true);
    setError("");
    try {
      const res = await holdSeats(eventId, { seatIds: Array.from(selected) });
      router.push(`/reservations/${res.reservationId}`);
    } catch (err: unknown) {
      const e = err as { code?: string; message?: string };
      if (e.code === "SEAT_NOT_AVAILABLE") {
        setError("이미 선점된 좌석입니다. 좌석맵을 새로고침합니다.");
        getSeats(eventId).then((r) => setSeats(r.seats));
        setSelected(new Set());
      } else {
        setError(e.message || "좌석 선점에 실패했습니다.");
      }
    } finally {
      setHolding(false);
    }
  }

  const sections = seats.reduce<Record<string, SeatDto[]>>((acc, seat) => {
    if (!acc[seat.section]) acc[seat.section] = [];
    acc[seat.section].push(seat);
    return acc;
  }, {});

  function groupByRow(sectionSeats: SeatDto[]) {
    const rows: Record<string, SeatDto[]> = {};
    for (const s of sectionSeats) {
      if (!rows[s.row]) rows[s.row] = [];
      rows[s.row].push(s);
    }
    return Object.entries(rows).sort(([a], [b]) => a.localeCompare(b));
  }

  const selectedSeats = seats.filter((s) => selected.has(s.eventSeatId));
  const totalPrice = selectedSeats.reduce((sum, s) => sum + s.price, 0);

  if (error && !event)
    return (
      <div className="flex items-center justify-center min-h-[40vh]">
        <p className="text-red-400">{error}</p>
      </div>
    );
  if (!event)
    return (
      <div className="flex items-center justify-center min-h-[40vh]">
        <div className="flex items-center gap-3" style={{ color: "var(--text-muted)" }}>
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          로딩 중...
        </div>
      </div>
    );

  return (
    <div className="flex flex-col lg:flex-row gap-6">
      {/* Left: Seat map */}
      <div className="flex-1 min-w-0">
        {/* Event header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight mb-1">{event.title}</h1>
          <p className="text-sm" style={{ color: "var(--text-secondary)" }}>
            {event.venue.name} &middot;{" "}
            {new Date(event.startsAt).toLocaleDateString("ko-KR", {
              year: "numeric", month: "long", day: "numeric",
              hour: "2-digit", minute: "2-digit",
            })}
          </p>
        </div>

        {/* Stage indicator */}
        <div className="mb-6">
          <div className="mx-auto w-48 py-2 rounded-t-2xl text-center text-xs font-semibold tracking-widest uppercase"
               style={{ background: "var(--border)", color: "var(--text-muted)" }}>
            STAGE
          </div>
        </div>

        {/* Legend */}
        <div className="flex flex-wrap items-center gap-4 mb-5 text-xs" style={{ color: "var(--text-secondary)" }}>
          <span className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded bg-emerald-500/80" /> 선택 가능
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded bg-amber-500/60" /> 선점됨
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded bg-slate-600/60" /> 판매됨
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded bg-blue-500 ring-2 ring-blue-400/50" /> 내 선택
          </span>
        </div>

        {/* Sections */}
        <div className="space-y-4">
          {Object.entries(sections)
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([section, sectionSeats]) => (
              <div key={section} className="rounded-xl p-4"
                   style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}>
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-semibold" style={{ color: "var(--text-primary)" }}>
                    {section}구역
                  </h3>
                  <span className="text-xs px-2 py-0.5 rounded-full"
                        style={{ background: "rgba(59,130,246,0.12)", color: "var(--accent)" }}>
                    {sectionSeats[0]?.price.toLocaleString()}원
                  </span>
                </div>
                <div className="space-y-1">
                  {groupByRow(sectionSeats).map(([row, rowSeats]) => (
                    <div key={row} className="flex items-center gap-[3px]">
                      <span className="w-5 text-[10px] text-right shrink-0" style={{ color: "var(--text-muted)" }}>
                        {row}
                      </span>
                      {rowSeats
                        .sort((a, b) => Number(a.number) - Number(b.number))
                        .map((seat) => {
                          const isSelected = selected.has(seat.eventSeatId);
                          return (
                            <button
                              key={seat.eventSeatId}
                              onClick={() => toggleSeat(seat)}
                              disabled={seat.status !== "AVAILABLE"}
                              title={`${section}-${row}-${seat.number} (${seat.status})`}
                              className={`w-6 h-6 rounded text-[9px] font-medium transition-all duration-150 ${
                                isSelected
                                  ? "bg-blue-500 ring-2 ring-blue-400/50 text-white scale-110"
                                  : STATUS_STYLE[seat.status] || "bg-slate-700"
                              } disabled:cursor-not-allowed`}
                              style={!isSelected && seat.status === "AVAILABLE" ? { color: "rgba(255,255,255,0.8)" } : { color: "rgba(255,255,255,0.4)" }}
                            >
                              {seat.number}
                            </button>
                          );
                        })}
                    </div>
                  ))}
                </div>
              </div>
            ))}
        </div>
      </div>

      {/* Right: Selection panel */}
      <div className="lg:w-80 shrink-0">
        <div className="lg:sticky lg:top-20 rounded-xl p-5"
             style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}>
          <h3 className="text-sm font-semibold mb-4" style={{ color: "var(--text-primary)" }}>
            선택한 좌석
          </h3>

          {selectedSeats.length === 0 ? (
            <p className="text-sm py-8 text-center" style={{ color: "var(--text-muted)" }}>
              좌석을 선택해주세요
            </p>
          ) : (
            <>
              <div className="space-y-2 mb-4">
                {selectedSeats.map((s) => (
                  <div key={s.eventSeatId}
                       className="flex items-center justify-between text-sm py-2 px-3 rounded-lg"
                       style={{ background: "rgba(59,130,246,0.08)" }}>
                    <span style={{ color: "var(--text-primary)" }}>
                      {s.section}-{s.row}-{s.number}
                    </span>
                    <span style={{ color: "var(--text-secondary)" }}>
                      {s.price.toLocaleString()}원
                    </span>
                  </div>
                ))}
              </div>

              <div className="flex items-center justify-between py-3 mb-4"
                   style={{ borderTop: "1px solid var(--border)" }}>
                <span className="text-sm" style={{ color: "var(--text-secondary)" }}>합계</span>
                <span className="text-lg font-bold" style={{ color: "var(--text-primary)" }}>
                  {totalPrice.toLocaleString()}원
                </span>
              </div>
            </>
          )}

          {error && <p className="text-red-400 text-xs mb-3">{error}</p>}

          <button
            onClick={handleHold}
            disabled={selected.size === 0 || holding}
            className="w-full py-3 rounded-xl text-sm font-semibold transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed hover:shadow-lg hover:shadow-blue-500/25"
            style={{
              background: selected.size > 0 ? "var(--accent)" : "var(--border)",
              color: "#fff",
            }}
          >
            {holding ? "선점 중..." : selected.size > 0 ? `${selected.size}석 선점하기` : "좌석을 선택하세요"}
          </button>
        </div>
      </div>
    </div>
  );
}
