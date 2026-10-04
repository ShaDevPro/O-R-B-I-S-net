# Building OrbisNet from Source

This guide explains how to build the public (bridled) version of OrbisNet.

## Requirements

| Tool | Minimum version |
|---|---|
| Android Studio | Meerkat 2024.3+ |
| JDK | 17+ |
| Android SDK | API 37 (compile), API 24 (min) |
| Gradle | 8.10+ (via wrapper) |

## Steps

```bash
# 1. Clone the repo
git clone https://github.com/ShaDevPro/O-R-B-I-S-net.git
cd O-R-B-I-S-net

# 2. Open in Android Studio → File → Open → select the folder
#    OR build from command line:

# 3. Build debug APK
./gradlew assembleDebug

# 4. Output
# app/build/outputs/apk/debug/app-debug.apk
```

## What compiles, what doesn't

| Component | Status in public build |
|---|---|
| Open UI screens & models | ✅ Fully compilable |
| Crypto primitives (Secp256k1, Bech32, BIP-340) | ✅ Fully compilable |
| Zero-Knowledge Edge Backend (`/backend`) | ✅ Fully compilable & auditable |
| Core Orchestrators (Sync, E2EE, Ratchet) | ⚠️ Compilable stubs (Preserved API surface) |
| Proprietary Screens (Conversation, Admin) | ⚠️ Compilable stubs (Preserved API surface) |

## About the proprietary components

OrbisNet protects its competitive differentiators (Double Ratchet engine, sovereign peer sync, call management, hardware attestation) through compilable stubs following the selective open-source model.

See [README.md](README.md#-open-source-status--proprietary-components) for the complete inventory of protected components and their public guarantees.

## google-services.json

The included `google-services.json` is configured for the `com.sha.orbisnet`
package. If you fork and change the `applicationId`, you will need to generate
your own Firebase project and replace this file.

## Signing (release builds)

The keystore used for production releases is private and not included.
For your own release build, generate a keystore:

```bash
keytool -genkeypair -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias orbis
```

Then configure it in `app/build.gradle.kts` under `signingConfigs`.
