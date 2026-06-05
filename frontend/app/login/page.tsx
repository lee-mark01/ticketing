"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { login } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("demo@ticketing.com");
  const [password, setPassword] = useState("password123");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await login({ email, password });
      localStorage.setItem("token", res.accessToken);
      router.push("/");
    } catch (err: unknown) {
      const ex = err as { message?: string };
      setError(ex.message || "로그인에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="w-full max-w-sm rounded-xl p-8"
           style={{ background: "var(--bg-card)", border: "1px solid var(--border)" }}>
        <h1 className="text-2xl font-bold text-center mb-1">로그인</h1>
        <p className="text-center text-sm mb-6" style={{ color: "var(--text-muted)" }}>
          계정에 로그인하세요
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium mb-1.5" style={{ color: "var(--text-secondary)" }}>
              이메일
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-lg px-3.5 py-2.5 text-sm outline-none transition-colors focus:ring-2 focus:ring-blue-500/40"
              style={{ background: "var(--bg-primary)", border: "1px solid var(--border)", color: "var(--text-primary)" }}
              required
            />
          </div>
          <div>
            <label className="block text-xs font-medium mb-1.5" style={{ color: "var(--text-secondary)" }}>
              비밀번호
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-lg px-3.5 py-2.5 text-sm outline-none transition-colors focus:ring-2 focus:ring-blue-500/40"
              style={{ background: "var(--bg-primary)", border: "1px solid var(--border)", color: "var(--text-primary)" }}
              required
            />
          </div>

          {error && (
            <p className="text-sm px-3 py-2 rounded-lg bg-red-500/10 text-red-400">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 rounded-lg text-sm font-semibold transition-all duration-200 disabled:opacity-50 hover:shadow-lg hover:shadow-blue-500/25"
            style={{ background: "var(--accent)", color: "#fff" }}
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>

        <p className="text-[11px] mt-5 text-center" style={{ color: "var(--text-muted)" }}>
          시연용 계정: demo@ticketing.com / password123
        </p>
      </div>
    </div>
  );
}
