import { NextResponse } from "next/server";

export const runtime = "edge";

/**
 * Remote App Configuration Endpoint — Public Stub.
 * Full update push and Redis synchronization is proprietary to ShaDevPro.
 */
export async function POST() {
  return NextResponse.json(
    { error: "Forbidden: Supervisor management endpoint is proprietary" },
    { status: 403 }
  );
}
