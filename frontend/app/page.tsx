"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { getEvents } from "@/lib/api";
import type { EventListResponse, PageResponse } from "@/lib/types";

export default function HomePage() {
  const [data, setData] = useState<PageResponse<EventListResponse> | null>(
    null
  );
  const [error, setError] = useState("");

  useEffect(() => {
    getEvents()
      .then(setData)
      .catch(() => setError("공연 목록을 불러올 수 없습니다."));
  }, []);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!data) return <p className="text-gray-500">로딩 중...</p>;

  return (
    <>
      <h1 className="text-2xl font-bold mb-6">공연 목록</h1>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {data.content.map((event) => (
          <Link
            key={event.id}
            href={`/events/${event.id}`}
            className="block bg-white rounded-lg border p-5 hover:shadow-md transition-shadow"
          >
            <h2 className="font-semibold text-lg mb-2">{event.title}</h2>
            <p className="text-sm text-gray-600 mb-1">{event.venueName}</p>
            <p className="text-sm text-gray-600 mb-3">
              {new Date(event.startsAt).toLocaleDateString("ko-KR", {
                year: "numeric",
                month: "long",
                day: "numeric",
                hour: "2-digit",
                minute: "2-digit",
              })}
            </p>
            <span
              className={`text-sm font-medium ${
                event.availableSeatCount > 0
                  ? "text-green-600"
                  : "text-red-600"
              }`}
            >
              잔여 {event.availableSeatCount}석
            </span>
          </Link>
        ))}
      </div>
    </>
  );
}
