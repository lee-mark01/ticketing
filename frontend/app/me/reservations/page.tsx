"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getMyReservations } from "@/lib/api";
import type { MyReservationResponse, PageResponse } from "@/lib/types";

const STATUS_BADGE: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-800",
  CONFIRMED: "bg-green-100 text-green-800",
  EXPIRED: "bg-gray-100 text-gray-600",
  CANCELLED: "bg-red-100 text-red-800",
};

export default function MyReservationsPage() {
  const router = useRouter();
  const [data, setData] = useState<PageResponse<MyReservationResponse> | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    getMyReservations()
      .then(setData)
      .catch((err: { status?: number }) => {
        if (err.status === 401) {
          router.push("/login");
        } else {
          setError("예매 목록을 불러올 수 없습니다.");
        }
      });
  }, [router]);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!data) return <p className="text-gray-500">로딩 중...</p>;

  if (data.content.length === 0) {
    return (
      <>
        <h1 className="text-2xl font-bold mb-6">내 예매</h1>
        <p className="text-gray-500">예매 내역이 없습니다.</p>
      </>
    );
  }

  return (
    <>
      <h1 className="text-2xl font-bold mb-6">내 예매</h1>
      <div className="space-y-3">
        {data.content.map((r) => (
          <div
            key={r.reservationId}
            className="bg-white border rounded-lg p-4 flex items-center justify-between"
          >
            <div>
              <p className="font-semibold">{r.eventTitle}</p>
              <p className="text-sm text-gray-600">
                좌석: {r.seats.join(", ")}
              </p>
              <p className="text-sm text-gray-500">
                {new Date(r.createdAt).toLocaleDateString("ko-KR", {
                  year: "numeric",
                  month: "long",
                  day: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </p>
            </div>
            <div className="text-right">
              <span
                className={`inline-block px-2 py-1 rounded text-xs font-medium ${
                  STATUS_BADGE[r.status] || "bg-gray-100"
                }`}
              >
                {r.status}
              </span>
              <p className="text-sm font-medium mt-1">
                {r.totalAmount.toLocaleString()}원
              </p>
            </div>
          </div>
        ))}
      </div>
    </>
  );
}
