import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "OrbisNet Sovereign Cloud & Telemetry",
  description: "Secure privacy-first telemetry and over-the-air update manager for OrbisNet.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr">
      <body style={{
        margin: 0,
        padding: 0,
        backgroundColor: "#F8FAFC",
        color: "#0F172A",
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
      }}>
        {children}
      </body>
    </html>
  );
}
