export default function Home() {
  return (
    <main style={{
      minHeight: "100vh",
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      justifyContent: "center",
      padding: "2rem",
      textAlign: "center"
    }}>
      <div style={{
        background: "radial-gradient(circle at center, rgba(2, 132, 199, 0.08), transparent 70%)",
        position: "absolute",
        width: "600px",
        height: "600px",
        pointerEvents: "none"
      }} />

      <div style={{
        display: "inline-flex",
        alignItems: "center",
        gap: "0.5rem",
        background: "rgba(2, 132, 199, 0.08)",
        border: "1px solid rgba(2, 132, 199, 0.2)",
        borderRadius: "9999px",
        padding: "0.4rem 1rem",
        marginBottom: "1.5rem",
        fontSize: "0.875rem",
        color: "#0284C7",
        fontWeight: "600"
      }}>
        <span>🛡️</span>
        <span>OrbisNet Sovereign Cloud</span>
      </div>

      <h1 style={{
        fontSize: "2.75rem",
        fontWeight: "800",
        letterSpacing: "-0.02em",
        margin: "0 0 1rem 0",
        background: "linear-gradient(to right, #0F172A, #334155)",
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent"
      }}>
        Backend Télémétrie & Mises à Jour
      </h1>

      <p style={{
        maxWidth: "580px",
        fontSize: "1.1rem",
        lineHeight: "1.6",
        color: "#475569",
        margin: "0 0 2rem 0"
      }}>
        Infrastructure sans serveur hébergée sur Vercel. Mesure d'usage anonyme (Zero-Knowledge),
        statistiques par pays et forçage instantané des versions critiques pour OrbisNet.
      </p>

      <div style={{
        display: "flex",
        gap: "1rem",
        flexWrap: "wrap",
        justifyContent: "center"
      }}>
        <a
          href="/admin"
          style={{
            background: "#0284C7",
            color: "#FFFFFF",
            fontWeight: "600",
            padding: "0.75rem 1.5rem",
            borderRadius: "0.75rem",
            textDecoration: "none",
            boxShadow: "0 4px 14px 0 rgba(2, 132, 199, 0.3)"
          }}
        >
          Accéder à la Console Admin →
        </a>
        <a
          href="/api/config"
          target="_blank"
          rel="noreferrer"
          style={{
            background: "#FFFFFF",
            border: "1px solid #E2E8F0",
            color: "#334155",
            fontWeight: "500",
            padding: "0.75rem 1.5rem",
            borderRadius: "0.75rem",
            textDecoration: "none",
            boxShadow: "0 1px 3px rgba(0, 0, 0, 0.05)"
          }}
        >
          Vérifier API Config (JSON)
        </a>
      </div>
    </main>
  );
}
