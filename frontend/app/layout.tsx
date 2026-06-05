import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import Link from "next/link";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "TicketBook",
  description: "공연 티켓 예매 시스템",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={`${geistSans.variable} h-full`}>
      <body className="min-h-full flex flex-col antialiased"
            style={{ background: "var(--bg-primary)", color: "var(--text-primary)" }}>
        {/* Notice banner */}
        <div className="text-center text-[11px] py-1 px-4"
             style={{ background: "rgba(59,130,246,0.08)", color: "var(--text-muted)", borderBottom: "1px solid var(--border)" }}>
          무료 서버 특성상 첫 접속 시 응답이 지연될 수 있습니다
        </div>

        {/* Header */}
        <header className="sticky top-0 z-50 backdrop-blur-md"
                style={{ background: "rgba(15,23,42,0.85)", borderBottom: "1px solid var(--border)" }}>
          <div className="max-w-6xl mx-auto px-6 h-14 flex items-center justify-between">
            <Link href="/" className="flex items-center gap-2">
              <span className="text-xl font-bold" style={{ color: "var(--accent)" }}>
                TicketBook
              </span>
            </Link>
            <nav className="flex items-center gap-1">
              <Link href="/"
                    className="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors hover:bg-white/5"
                    style={{ color: "var(--text-secondary)" }}>
                공연 목록
              </Link>
              <Link href="/me/reservations"
                    className="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors hover:bg-white/5"
                    style={{ color: "var(--text-secondary)" }}>
                내 예매
              </Link>
              <Link href="/login"
                    className="ml-2 px-4 py-1.5 rounded-lg text-sm font-medium transition-colors"
                    style={{ background: "var(--accent)", color: "#fff" }}>
                로그인
              </Link>
            </nav>
          </div>
        </header>

        <main className="flex-1 max-w-6xl mx-auto w-full px-6 py-8">
          {children}
        </main>

        <footer className="text-center py-6 text-xs" style={{ color: "var(--text-muted)", borderTop: "1px solid var(--border)" }}>
          TicketBook — DB 과목 프로젝트
        </footer>
      </body>
    </html>
  );
}
