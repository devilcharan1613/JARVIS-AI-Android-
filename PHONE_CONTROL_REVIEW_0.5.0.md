# JARVIS 0.5.0 - Android Phone Control Developer Review

Date: 2026-11-07
Scope: integration of user-enabled Android AccessibilityService into the native Kotlin + llama.cpp offline app, inspired by the phone operator architecture in AnaaySampat/jarvis.

## Upstream assessment

The reference project is a Tauri 2 + Rust + React + TypeScript application with a substantial Kotlin Android plugin. Its phone operator uses Android AccessibilityService to observe app UI and perform actions. It also includes a native task loop, STOP overlay, action risk classification, voice services and permission handling.

The reference app's documented default AI route uses hosted providers. That is incompatible with this project's strict offline policy. This implementation adapts the Android platform boundary and basic phone operator pattern only; it does not copy the Tauri/React application, provider routes, PC pairing, TV control or network features.

Upstream project: https://github.com/AnaaySampat/jarvis
License declared by upstream repository: MIT. No upstream source files were copied into this implementation.

## Implemented in 0.5.0

- Registered `JarvisPhoneAccessibilityService` in the Android manifest with BIND_ACCESSIBILITY_SERVICE protection.
- Added an accessibility-service XML configuration that requests visible window content and view identifiers.
- Added an explicit Settings action to open Android Accessibility settings. The user must manually enable the service.
- Added on-demand screen text observation.
- Added visible-label tap with enabled/visible checks and parent-click fallback.
- Added focused non-password text entry, scroll up/down and Back/Home global navigation.
- Added launch-by-installed-app-label.
- Added local chat commands: `enable phone control`, `open <app>`, `read screen`, `tap <label>`, `type <text>`, `scroll up/down`, `go back`, `go home`.
- Blocked tap labels associated with sending, payment, deletion, transfers, calls, installation, password/PIN/OTP and other high-risk actions.
- Preserved strict offline: no INTERNET permission, network stack or cloud fallback added.

## Important platform and security boundaries

Accessibility access is powerful. The service can inspect visible accessibility labels and interact with supported UI nodes after the user explicitly enables it. It is not unrestricted device-owner/root control.

Android and individual apps may prevent access to secure surfaces, password fields, protected content, custom canvas/game UI, or certain system settings. Wi-Fi, mobile data, airplane mode, permission grants, lock-screen security, factory reset and payment authorization are not exposed as direct commands.

Sensitive actions are intentionally not automated. The user must personally review and complete calls, messages, payments, purchases, deletion, installation and authentication steps. A biometric approval flow is not yet implemented.

The service is event-driven but currently reads the active window only on demand. It does not continuously record screen contents, screenshots or keystrokes. No wake-word or background microphone has been added.

## Known gaps

1. No full agent observe-plan-act-verify loop yet. Commands are deterministic phrase handlers, not model-generated action plans.
2. No persistent task journal, bounded step budget, cancellation token or cross-app STOP overlay.
3. No screenshot/vision fallback for apps without accessibility nodes.
4. No explicit per-action confirmation sheet or biometric gate for medium-risk operations.
5. No real-device validation across OEMs and Android versions.
6. Phone-control permission onboarding and state display need testing.
7. Some app launch resolution may be affected by package visibility and OEM launchers.

## Verification

- Source and manifest review: completed.
- Android service registration and disclosure: committed.
- Build and unit tests: pending GitHub Actions.
- Accessibility enablement and app interaction: not yet tested on physical device.
- Strict offline manifest check: existing CI workflow includes a no-INTERNET assertion.
- Security testing: pending.

## Release status

Developer preview only. Do not claim complete control of every phone function. Before release, verify the service on the actual device, test accessibility permission revocation, validate STOP/cancellation, and review the app's security/privacy disclosures.
