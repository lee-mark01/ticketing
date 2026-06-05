import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import Link from "next/link";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "공연 티켓 예매",
  description: "공연 티켓 예매 시스템 — DB 과목 프로젝트",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={`${geistSans.variable} h-full`}>
      <body className="min-h-full flex flex-col bg-gray-50 text-gray-900 antialiased">
        <header className="bg-white border-b px-6 py-3 flex items-center justify-between">
          <Link href="/" className="text-lg font-bold text-blue-600">
            TicketBook
          </Link>
          <nav className="flex gap-4 text-sm">
            <Link href="/" className="hover:text-blue-600">
              공연 목록
            </Link>
            <Link href="/me/reservations" className="hover:text-blue-600">
              내 예매
            </Link>
            <Link href="/login" className="hover:text-blue-600">
              로그인
            </Link>
          </nav>
        </header>
        <div className="bg-yellow-50 border-b border-yellow-200 px-4 py-1.5 text-center text-xs text-yellow-700">
          무료 서버 특성상 첫 접속 시 백엔드 응답이 30~60초 지연될 수 있습니다
        </div>
        <main className="flex-1 max-w-5xl mx-auto w-full px-4 py-6">
          {children}
        </main>
      </body>
    </html>
  );
}
