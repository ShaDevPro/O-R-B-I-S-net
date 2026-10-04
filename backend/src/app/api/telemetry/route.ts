import { NextRequest, NextResponse } from "next/server";
import { recordTelemetry, getAppConfig, TelemetryPayload } from "@/lib/redis";

export const runtime = "edge";

export async function POST(req: NextRequest) {
  try {
    const payload: TelemetryPayload = await req.json();

    if (!payload.installIdHash) {
      return NextResponse.json({ error: "Missing installIdHash" }, { status: 400 });
    }

    // Vercel Edge automatically provides the user's country in header 'x-vercel-ip-country'
    const edgeCountry = req.headers.get("x-vercel-ip-country") ||
      req.headers.get("cf-ipcountry") ||
      (payload.locale ? payload.locale.split("_")[1] : "XX");

    const countryCode = (edgeCountry || "XX").toUpperCase();

    // Record anonymously in Redis / Memory store
    await recordTelemetry(payload, countryCode);

    // Return status along with latest app config in the same round-trip
    const currentConfig = await getAppConfig();
    const effectiveConfig = {
      ...currentConfig,
      minRequiredVersionCode: currentConfig.forceUpdate ? currentConfig.minRequiredVersionCode : 100,
      minRequiredVersionName: currentConfig.forceUpdate ? currentConfig.minRequiredVersionName : "1.0.0"
    };

    return NextResponse.json({
      ok: true,
      detectedCountry: countryCode,
      config: effectiveConfig
    }, { status: 200 });
  } catch (error) {
    console.error("Telemetry API error:", error);
    return NextResponse.json({ error: "Failed to process telemetry" }, { status: 500 });
  }
}
