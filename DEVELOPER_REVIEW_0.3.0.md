# JARVIS Offline 0.3.0 - Developer Review

Review date: 2026-11-07  
Scope: source-level review of the GitHub repository, Android UI, model import, JNI inference path, app manifest, and CI configuration.

## Executive summary

The project is a functional prototype scaffold, not a production-ready personal assistant. This review found several correctness and usability risks. The fixes listed below have been committed to the `main` branch and a new CI build has been triggered. CI results and real-device behavior must be checked before release.

## Fixes committed in 0.3.0

| Area | Finding | Change |
|---|---|---|
| Screen layout | Main content used `fillMaxSize()` inside a vertical column with a bottom navigation bar. This could push or clip navigation on smaller screens. | Constrained the selected screen to the remaining weighted area; bottom navigation now has its own measured area. |
| Model import | A null input stream, empty file, copy failure, or model-load failure could escape the coroutine and leave a poor error state. | Added guarded file opening, non-empty-file validation, exception handling and visible failure messages. |
| Native tokenization | The JNI layer treated a too-small token buffer as a prompt error instead of retrying with the capacity reported by llama.cpp. | Added a retry path for the required token count. |
| Context bounds | A prompt plus generation could exceed the configured context window. | Added context-window validation and clamps output tokens to remaining context capacity. |
| Chat context | Only the latest question was sent to the model. | Adds up to six recent turns and up to 3,000 characters of saved local notes to the prompt. All processing remains local. |
| App identity | The launcher had no custom JARVIS icon. | Added an Arc Reactor-inspired adaptive launcher icon and round icon, plus a dark launch theme. |
| Versioning | Previous UI build version was 0.2.0. | Bumped version to 0.3.0 / versionCode 3. |

## Remaining issues and risks

### High priority
1. **No real-device runtime test yet.** CI compilation cannot prove that a selected GGUF loads, generates correctly, or stays within the phone's memory/thermal limits.
2. **Model is not bundled.** Users must import a compatible GGUF file. A model suitable for a 4 GB RAM phone needs careful size and quantization selection.
3. **Model selection is session-only.** The app does not yet persist a model selection and automatically reload it after process restart.
4. **Native engine lifecycle/thread safety.** The C++ model/context are process-global. Concurrent load, generation, and unload operations are not serialized; activity destruction during inference could race with native cleanup.
5. **No cancellation or progress streaming.** Long generation can appear frozen and cannot currently be cancelled from the UI.

### Medium priority
6. **Voice is not implemented.** Vosk is present as a dependency, but microphone capture, bundled language models, permission UX, offline TTS and voice UI are not wired.
7. **Wake-word service is not implemented.** Always-on microphone behavior needs an explicit foreground-service design, user controls, battery testing and Android-version-specific permission handling.
8. **Owner authentication is not implemented.** Sensitive actions should use Android BiometricPrompt/device credential before those actions are added.
9. **Memory is basic.** Notes are encrypted locally, but there is no edit history, search UI, structured facts, delete-by-item, or automatic memory management. This build adds bounded note context to local prompts.
10. **Language quality depends on the chosen model.** English, Telugu and Hindi are not guaranteed by the app itself.

### Low priority / cleanup
11. The Vosk dependency currently adds dependency weight without an active voice feature. Remove it until the voice module is integrated or wire it in with tested local models.
12. Add instrumentation tests for import, navigation, activity recreation, model failure, memory persistence and airplane-mode behavior.
13. Add release signing, shrink/size analysis, crash reporting that does not upload private data, and a reproducible release pipeline before public distribution.

## Privacy and permissions review

- The manifest currently does not declare `android.permission.INTERNET`; this preserves the offline-only architecture.
- The model and notes are stored in app-private storage.
- The Android document picker grants access to the user-selected file; the app copies it into its private model directory.
- Do not add network permission, analytics SDKs, remote crash upload, cloud backup or online model downloads without an explicit product decision and clear consent.

## Test status

| Test | Status |
|---|---|
| Source review | Completed |
| UI layout and import error handling fixes | Committed |
| Native tokenization/context safeguards | Committed |
| Launcher icon resources | Committed |
| Gradle debug build | Awaiting latest GitHub Actions run |
| Unit tests | Awaiting latest GitHub Actions run |
| Emulator navigation and rotation | Not run |
| Physical-device GGUF inference | Not run |
| 4 GB RAM / thermal stress test | Not run |
| Offline/airplane-mode verification on device | Not run |
| Voice / wake-word / biometric tests | Not applicable; features not implemented |

## Release recommendation

Treat 0.3.0 as a developer-preview build until the latest CI run passes and a physical Android device test is completed. Do not describe voice, wake-word, biometric security or full JARVIS device automation as implemented.
