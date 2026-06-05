"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { getEvents } from "@/lib/api";
import type { EventListResponse, PageResponse } from "@/lib/types";

const POSTER_COLORS = [
  "from-blue-600 to-indigo-800",
  "from-purple-600 to-fuchsia-800",
  "from-emerald-600 to-teal-800",
  "from-orange-500 to-red-700",
  "from-cyan-500 to-blue-700",
];

export default function HomePage() {
  const [data, setData] = useState<PageResponse<EventListResponse> | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    getEvents()
      .then(setData)
      .catch(() => setError("공연 목록을 불러올 수 없습니다."));
  }, []);

  if (error)
    return (
      <div className="flex items-center justify-center min-h-[40vh]">
        <p className="text-red-400">{error}</p>
      </div>
    );

  if (!data)
    return (
      <div className="flex items-center justify-center min-h-[40vh]">
        <div className="flex items-center gap-3" style={{ color: "var(--text-muted)" }}>
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          공연 목록을 불러오는 중...
        </div>
      </div>
    );

  return (
    <>
      <div className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight mb-2">공연 목록</h1>
        <p style={{ color: "var(--text-muted)" }} className="text-sm">
          원하는 공연을 선택하고 좌석을 예매하세요
        </p>
      </div>

      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {data.content.map((event, i) => (
          <Link
            key={event.id}
            href={`/events/${event.id}`}
            className="group block rounded-xl overflow-hidden transition-all duration-200 hover:scale-[1.02] hover:shadow-2xl hover:shadow-blue-500/10"
            style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}
          >
            {/* Poster area */}
            <div className={`h-36 bg-gradient-to-br ${POSTER_COLORS[i % POSTER_COLORS.length]} flex items-end p-5`}>
              <div>
                <p className="text-white/60 text-xs font-medium uppercase tracking-wider mb-1">
                  {event.venueName}
                </p>
                <h2 className="text-white text-lg font-bold leading-snug">
                  {event.title}
                </h2>
              </div>
            </div>

            {/* Info */}
            <div className="p-5 space-y-3">
              <div className="flex items-center gap-2 text-sm" style={{ color: "var(--text-secondary)" }}>
                <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5" />
                </svg>
                {new Date(event.startsAt).toLocaleDateString("ko-KR", {
                  year: "numeric",
                  month: "long",
                  day: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </div>

              <div className="flex items-center justify-between">
                <span
                  className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-full ${
                    event.availableSeatCount > 0
                      ? "bg-emerald-500/15 text-emerald-400"
                      : "bg-red-500/15 text-red-400"
                  }`}
                >
                  <span className={`w-1.5 h-1.5 rounded-full ${
                    event.availableSeatCount > 0 ? "bg-emerald-400" : "bg-red-400"
                  }`} />
                  잔여 {event.availableSeatCount}석
                </span>

                <span
                  className="text-xs font-medium px-3 py-1 rounded-lg transition-colors group-hover:bg-blue-500 group-hover:text-white"
                  style={{ background: "rgba(59,130,246,0.15)", color: "var(--accent)" }}
                >
                  예매하기
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </>
  );
}
