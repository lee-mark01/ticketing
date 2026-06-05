"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { confirmReservation, cancelHold } from "@/lib/api";
import type { ConfirmResponse } from "@/lib/types";

export default function ReservationPage() {
  const params = useParams();
  const router = useRouter();
  const reservationId = Number(params.id);

  const [result, setResult] = useState<ConfirmResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleConfirm() {
    setLoading(true);
    setError("");
    try {
      const res = await confirmReservation(reservationId, { paymentMethod: "CARD" });
      setResult(res);
    } catch (err: unknown) {
      const e = err as { message?: string };
      setError(e.message || "결제에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleCancel() {
    setLoading(true);
    setError("");
    try {
      await cancelHold(reservationId);
      router.push("/");
    } catch (err: unknown) {
      const e = err as { message?: string };
      setError(e.message || "취소에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  if (result) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-full max-w-md rounded-xl p-8 text-center"
             style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}>
          <div className="w-16 h-16 mx-auto mb-4 rounded-full flex items-center justify-center bg-emerald-500/15">
            <svg className="w-8 h-8 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h1 className="text-xl font-bold mb-1">예매 확정!</h1>
          <p className="text-sm mb-5" style={{ color: "var(--text-muted)" }}>
            예매번호 #{result.reservationId}
          </p>
          <div className="rounded-lg p-4 mb-5" style={{ background: "var(--bg-primary)" }}>
            <p className="text-2xl font-bold mb-1">{result.payment.amount.toLocaleString()}원</p>
            <span className="inline-block text-xs px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 font-medium">
              {result.payment.status}
            </span>
          </div>
          <button
            onClick={() => router.push("/me/reservations")}
            className="w-full py-2.5 rounded-xl text-sm font-semibold transition-all hover:shadow-lg hover:shadow-blue-500/25"
            style={{ background: "var(--accent)", color: "#fff" }}
          >
            내 예매 목록 보기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="w-full max-w-md rounded-xl p-8"
           style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}>
        <div className="w-16 h-16 mx-auto mb-4 rounded-full flex items-center justify-center"
             style={{ background: "rgba(59,130,246,0.12)" }}>
          <svg className="w-8 h-8" style={{ color: "var(--accent)" }} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 6v.75m0 3v.75m0 3v.75m0 3V18m-9-5.25h5.25M7.5 15h3M3.375 5.25c-.621 0-1.125.504-1.125 1.125v3.026a2.999 2.999 0 010 5.198v3.026c0 .621.504 1.125 1.125 1.125h17.25c.621 0 1.125-.504 1.125-1.125v-3.026a2.999 2.999 0 010-5.198V6.375c0-.621-.504-1.125-1.125-1.125H3.375z" />
          </svg>
        </div>
        <h1 className="text-xl font-bold text-center mb-1">예매 확인</h1>
        <p className="text-center text-sm mb-6" style={{ color: "var(--text-muted)" }}>
          예매번호 #{reservationId} &middot; 좌석이 선점되었습니다
        </p>

        {error && (
          <p className="text-sm px-3 py-2 rounded-lg bg-red-500/10 text-red-400 mb-4">{error}</p>
        )}

        <div className="flex gap-3">
          <button
            onClick={handleConfirm}
            disabled={loading}
            className="flex-1 py-2.5 rounded-xl text-sm font-semibold transition-all disabled:opacity-50 hover:shadow-lg hover:shadow-blue-500/25"
            style={{ background: "var(--accent)", color: "#fff" }}
          >
            {loading ? "처리 중..." : "결제 확정"}
          </button>
          <button
            onClick={handleCancel}
            disabled={loading}
            className="flex-1 py-2.5 rounded-xl text-sm font-medium transition-colors disabled:opacity-50"
            style={{ background: "transparent", border: "1px solid var(--border)", color: "var(--text-secondary)" }}
          >
            취소
          </button>
        </div>
      </div>
    </div>
  );
}
