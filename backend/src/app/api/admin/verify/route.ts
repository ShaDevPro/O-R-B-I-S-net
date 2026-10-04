import { NextResponse } from "next/server";

export const runtime = "edge";

/**
 * Supervisor Identity Verification — Public Stub.
 * Anti-abuse rate limiting and hardware authentication is proprietary to ShaDevPro.
 */
export async function POST() {
  return NextResponse.json(
    {
      ok: true,
      isAdmin: false,
      role: "STANDARD"
    },
    { status: 200 }
  );
}
