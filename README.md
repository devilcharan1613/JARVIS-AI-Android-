# JARVIS Offline for Android

A local-first Android assistant prototype using Kotlin, Jetpack Compose and llama.cpp.

## Status
Initial source scaffold only. This is **not a release-ready or tested APK**. GitHub Actions is configured to attempt a debug build and unit tests on pushes.

## Requirements
Android Studio, JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1. ARM64 device recommended. Four GB RAM is restrictive. Supply a compatible GGUF model yourself.

## Build
```bash
git clone https://github.com/devilcharan1613/JARVIS-AI-Android-.git
cd JARVIS-AI-Android-
bash scripts/fetch-llama.sh
gradle assembleDebug
gradle testDebugUnitTest
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Implemented in this scaffold
- llama.cpp native JNI bridge, CPU inference path
- GGUF model import with Android document picker
- encrypted local notes using AndroidX Security Crypto
- no INTERNET permission and no cloud fallback

## Known gaps
- Voice capture and playback are not wired into the UI. Vosk is included as a dependency but language model assets and integration remain to be added.
- Wake-word foreground service is not implemented; Android background microphone and battery restrictions apply.
- Biometric/device-authentication gate for sensitive actions is not implemented.
- Conversation history/context injection, robust streaming/cancellation, model compatibility validation and device profiling remain.
- The native API integration must be verified against the checked-out llama.cpp revision by CI; compatibility fixes may be required.

## Privacy
No INTERNET permission is declared. Models and notes remain on-device. The model file is not bundled. Encrypted preferences are excluded from Android backup.

## Test
Run `gradle testDebugUnitTest`. Real-device tests are still required for inference, memory pressure, thermal behavior, language quality and airplane-mode operation.
