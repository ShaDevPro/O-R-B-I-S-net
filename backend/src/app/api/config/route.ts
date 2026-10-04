import { NextResponse } from "next/server";
import { getAppConfig } from "@/lib/redis";

export const runtime = "edge";

export async function GET() {
  try {
    const config = await getAppConfig();
    const effectiveConfig = {
      ...config,
      minRequiredVersionCode: config.forceUpdate ? config.minRequiredVersionCode : 100,
      minRequiredVersionName: config.forceUpdate ? config.minRequiredVersionName : "1.0.0"
    };
    return NextResponse.json(effectiveConfig, {
      status: 200,
      headers: {
        "Cache-Control": "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0"
      }
    });
  } catch (error) {
    console.error("Config API error:", error);
    return NextResponse.json({ error: "Failed to fetch config" }, { status: 500 });
  }
}
