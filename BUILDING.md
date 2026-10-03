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
| UI (Jetpack Compose) | ✅ Fully compilable |
| Calls (WebRTC) | ✅ Fully compilable |
| AI Guard / Smart Reply | ✅ Fully compilable |
| Crypto (Secp256k1, Bech32) | ✅ Fully compilable |
| Relay pool client | ✅ Fully compilable |
| **NostrSyncManager** | ⚠️ Stub — compiles, no production logic |
| **NostrProtocolEngine** | ⚠️ Stub — compiles, no production logic |

## About the proprietary Nostr layer

`NostrSyncManager` and `NostrProtocolEngine` are the synchronization and
protocol engines that power OrbisNet's decentralized messaging, feed, and calls.

The files in `app/src/main/java/com/sha/orbis/nostr/service/NostrSyncManager.kt`
and `app/src/main/java/com/sha/orbis/nostr/protocol/NostrProtocolEngine.kt`
are **structural stubs**: they compile correctly and expose the full public API,
but the production network logic is distributed as a pre-compiled binary
(`orbis-core-release.aar`) under the [BUSL-1.1 license](LICENSE).

Reference stubs with documentation are also available in [`docs/stubs/`](docs/stubs/).

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
