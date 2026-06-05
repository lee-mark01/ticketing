"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getMyReservations } from "@/lib/api";
import type { MyReservationResponse, PageResponse } from "@/lib/types";

const STATUS_STYLE: Record<string, string> = {
  PENDING: "bg-amber-500/15 text-amber-400",
  CONFIRMED: "bg-emerald-500/15 text-emerald-400",
  EXPIRED: "bg-slate-500/15 text-slate-400",
  CANCELLED: "bg-red-500/15 text-red-400",
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
          로딩 중...
        </div>
      </div>
    );

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight mb-1">내 예매</h1>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>
          예매 내역을 확인하세요
        </p>
      </div>

      {data.content.length === 0 ? (
        <div className="text-center py-16 rounded-xl"
             style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}>
          <p style={{ color: "var(--text-muted)" }}>예매 내역이 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {data.content.map((r) => (
            <div key={r.reservationId}
                 className="rounded-xl p-5 flex items-center justify-between"
                 style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}>
              <div className="min-w-0">
                <p className="font-semibold text-sm mb-1 truncate">{r.eventTitle}</p>
                <p className="text-xs mb-1" style={{ color: "var(--text-secondary)" }}>
                  좌석: {r.seats.join(", ")}
                </p>
                <p className="text-xs" style={{ color: "var(--text-muted)" }}>
                  {new Date(r.createdAt).toLocaleDateString("ko-KR", {
                    year: "numeric",
                    month: "long",
                    day: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </p>
              </div>
              <div className="text-right shrink-0 ml-4">
                <span className={`inline-block text-[11px] font-semibold px-2.5 py-1 rounded-full ${STATUS_STYLE[r.status] || "bg-slate-500/15 text-slate-400"}`}>
                  {r.status}
                </span>
                <p className="text-sm font-semibold mt-1.5">{r.totalAmount.toLocaleString()}원</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
