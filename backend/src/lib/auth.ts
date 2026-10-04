import { NextRequest } from "next/server";

/**
 * Admin Authentication — Public Stub.
 * Real cryptographic verification logic is proprietary to ShaDevPro.
 */
export async function verifyAdminKey(req: NextRequest): Promise<boolean> {
  const headerKey = req.headers.get("x-admin-key") || req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  const queryKey = req.nextUrl.searchParams.get("key");
  const providedKey = (headerKey || queryKey || "").trim();

  const configured = process.env.ORBIS_ADMIN_KEY?.trim();
  if (!configured || !providedKey) return false;

  return providedKey === configured;
}

export async function isValidAdminKey(key: string): Promise<boolean> {
  const configured = process.env.ORBIS_ADMIN_KEY?.trim();
  if (!configured || !key) return false;
  return key.trim() === configured;
}
