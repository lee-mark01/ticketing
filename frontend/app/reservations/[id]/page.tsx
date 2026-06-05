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
      const res = await confirmReservation(reservationId, {
        paymentMethod: "CARD",
      });
      setResult(res);
    } catch (err: unknown) {
      const e = err as { code?: string; message?: string };
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
      <div className="max-w-md mx-auto mt-20 bg-white rounded-lg border p-8 text-center">
        <div className="text-4xl mb-4">✅</div>
        <h1 className="text-xl font-bold mb-2">예매 확정!</h1>
        <p className="text-gray-600 mb-4">
          예매번호: {result.reservationId}
        </p>
        <p className="text-lg font-semibold mb-1">
          {result.payment.amount.toLocaleString()}원
        </p>
        <p className="text-sm text-gray-500 mb-6">
          결제 상태: {result.payment.status}
        </p>
        <button
          onClick={() => router.push("/me/reservations")}
          className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700"
        >
          내 예매 목록 보기
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto mt-20 bg-white rounded-lg border p-8">
      <h1 className="text-xl font-bold mb-4">예매 확인</h1>
      <p className="text-gray-600 mb-2">예매번호: {reservationId}</p>
      <p className="text-sm text-gray-500 mb-6">
        좌석이 선점되었습니다. 결제를 진행하시겠습니까?
      </p>

      {error && <p className="text-red-600 text-sm mb-4">{error}</p>}

      <div className="flex gap-3">
        <button
          onClick={handleConfirm}
          disabled={loading}
          className="flex-1 bg-blue-600 text-white py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
        >
          {loading ? "처리 중..." : "결제 확정"}
        </button>
        <button
          onClick={handleCancel}
          disabled={loading}
          className="flex-1 border border-gray-300 py-2 rounded-lg hover:bg-gray-50 disabled:opacity-50"
        >
          취소
        </button>
      </div>
    </div>
  );
}
