# JARVIS 0.5.1 Phone Control Integration Review

Date: 2026-11-07

## Source compatibility
Reviewed AnaaySampat/jarvis Android phone plugin sources and README. The upstream application is a Tauri/React/Rust application with Kotlin Android plugin code, so its app shell and JS/Rust bridge are not drop-in compatible with this Kotlin/Compose project. This integration retains the existing Compose app, native llama.cpp/JNI inference, GGUF import and encrypted local memory. It does not import the upstream Tauri runtime or cloud provider integrations.

## Phone control implementation in this repository
- Declares a user-enabled Android AccessibilityService with the required BIND_ACCESSIBILITY_SERVICE protection and service XML metadata.
- Reads visible, non-password accessibility labels on demand.
- Supports visible-label tapping, focused non-password text entry, scrolling, Home and Back navigation, and launching installed apps by visible app label.
- Adds emergency STOP and explicit resume commands. STOP guards screen observation, tap, text entry, scroll and navigation operations.
- Sensitive UI labels including send, pay, purchase, delete, transfer, call, submit, password, PIN and OTP are blocked from automated tapping. User completes those actions manually in the target app.
- Accessibility permission is not silently enabled. The user must enable it in Android Settings.

## Strict offline and device constraints
- No INTERNET permission is present in AndroidManifest.xml.
- No upstream cloud model providers, remote APIs, remote telemetry or cloud fallback are included.
- Existing llama.cpp inference and private encrypted local memory are retained.
- ABI remains arm64-v8a. A 4 GB RAM device remains constrained by model size, context length, thermal throttling and native memory pressure; no model is bundled.

## Android limitations
- Accessibility cannot bypass lock screens, password/PIN fields, protected windows, app security boundaries or Android permission dialogs.
- Some system toggles are restricted to system/privileged apps or require the user to complete Android Settings UI.
- Screen observation depends on what the foreground app exposes to Accessibility. Secure/protected content may be unavailable.
- This implementation is an allowlisted accessibility operator, not unrestricted device/root control.

## Automated verification
- Added unit coverage for the conservative phone action policy.
- CI workflow is configured to verify the absence of INTERNET permission, fetch llama.cpp, assemble the debug APK and run JVM unit tests.
- The repository's latest GitHub Actions run must complete successfully before an APK is treated as verified. Physical-device testing is still required for Accessibility enablement, OEM behavior, target-device RAM, model latency and thermal stability.

## Release decision
Do not distribute as a verified release until the latest CI build and unit tests succeed and the APK artifact is present. This report does not claim physical-device validation.
