"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { getEvent, getSeats, holdSeats } from "@/lib/api";
import type { EventDetailResponse, SeatDto } from "@/lib/types";

const STATUS_COLOR: Record<string, string> = {
  AVAILABLE: "bg-green-500 hover:bg-green-600 cursor-pointer",
  HELD: "bg-yellow-400",
  SOLD: "bg-red-400",
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

  // 구역별 그룹핑
  const sections = seats.reduce<Record<string, SeatDto[]>>((acc, seat) => {
    if (!acc[seat.section]) acc[seat.section] = [];
    acc[seat.section].push(seat);
    return acc;
  }, {});

  // 구역 내 열별 그룹핑
  function groupByRow(sectionSeats: SeatDto[]) {
    const rows: Record<string, SeatDto[]> = {};
    for (const s of sectionSeats) {
      if (!rows[s.row]) rows[s.row] = [];
      rows[s.row].push(s);
    }
    return Object.entries(rows).sort(([a], [b]) => a.localeCompare(b));
  }

  const totalPrice = seats
    .filter((s) => selected.has(s.eventSeatId))
    .reduce((sum, s) => sum + s.price, 0);

  if (error && !event) return <p className="text-red-600">{error}</p>;
  if (!event) return <p className="text-gray-500">로딩 중...</p>;

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold">{event.title}</h1>
        <p className="text-gray-600">
          {event.venue.name} · {new Date(event.startsAt).toLocaleDateString("ko-KR", {
            year: "numeric", month: "long", day: "numeric",
            hour: "2-digit", minute: "2-digit",
          })}
        </p>
        <p className="text-sm text-gray-500 mt-1">잔여 {event.availableSeatCount}석</p>
      </div>

      {/* 범례 */}
      <div className="flex gap-4 mb-4 text-xs">
        <span className="flex items-center gap-1"><span className="w-4 h-4 rounded bg-green-500 inline-block" /> AVAILABLE</span>
        <span className="flex items-center gap-1"><span className="w-4 h-4 rounded bg-yellow-400 inline-block" /> HELD</span>
        <span className="flex items-center gap-1"><span className="w-4 h-4 rounded bg-red-400 inline-block" /> SOLD</span>
        <span className="flex items-center gap-1"><span className="w-4 h-4 rounded bg-blue-500 inline-block" /> 내 선택</span>
      </div>

      {/* 좌석맵 */}
      <div className="space-y-6 mb-6">
        {Object.entries(sections)
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([section, sectionSeats]) => (
            <div key={section} className="bg-white border rounded-lg p-4">
              <h3 className="font-semibold mb-2 text-sm text-gray-700">
                {section}구역 — {sectionSeats[0]?.price.toLocaleString()}원
              </h3>
              <div className="space-y-1">
                {groupByRow(sectionSeats).map(([row, rowSeats]) => (
                  <div key={row} className="flex items-center gap-1">
                    <span className="w-6 text-xs text-gray-400 text-right mr-1">{row}</span>
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
                            className={`w-7 h-7 rounded text-[10px] text-white font-medium transition-colors ${
                              isSelected
                                ? "bg-blue-500 ring-2 ring-blue-300"
                                : STATUS_COLOR[seat.status] || "bg-gray-300"
                            } disabled:cursor-not-allowed`}
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

      {/* 선택 패널 */}
      {selected.size > 0 && (
        <div className="sticky bottom-0 bg-white border-t p-4 shadow-lg rounded-t-lg">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">
                {selected.size}석 선택 ·{" "}
                <span className="font-semibold">{totalPrice.toLocaleString()}원</span>
              </p>
              <p className="text-xs text-gray-400">
                {seats
                  .filter((s) => selected.has(s.eventSeatId))
                  .map((s) => `${s.section}-${s.row}-${s.number}`)
                  .join(", ")}
              </p>
            </div>
            <button
              onClick={handleHold}
              disabled={holding}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              {holding ? "선점 중..." : "좌석 선점"}
            </button>
          </div>
          {error && <p className="text-red-600 text-sm mt-2">{error}</p>}
        </div>
      )}
    </>
  );
}
