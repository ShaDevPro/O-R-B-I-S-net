import { NextResponse } from "next/server";

export const runtime = "edge";

/**
 * Supervisor Metrics Aggregator — Public Stub.
 * Full metrics consolidation and HyperLogLog pipeline is proprietary to ShaDevPro.
 */
export async function GET() {
  return NextResponse.json(
    { error: "Forbidden: Telemetry consolidation endpoint is proprietary" },
    { status: 403 }
  );
}
