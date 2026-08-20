# Privacy

Vita is a local-first Android application. It has no Vita-operated account, proxy, analytics, advertising, telemetry, or crash-reporting service.

## Data sent to the configured model provider

When the user explicitly analyzes a meal or sends a chat message, Vita sends data directly to the configured cloud model provider:

- meal analysis: sanitized meal images, the chosen meal time, and optional notes;
- meal correction: the current unconfirmed analysis and the correction text;
- chat: the current session, confirmed nutrition targets, and bounded recent meal summaries.

Before upload, images are oriented, resized to at most 3200 pixels on the longest edge, flattened, and encoded as JPEG quality 90. The uploaded bytes do not preserve EXIF or GPS metadata. Original image bytes are not persisted by Vita.

Target eligibility and numeric nutrition targets are calculated locally. Birth date, height, weight, and eligibility screening inputs are not sent to a model and are not retained as a health questionnaire.

The selected provider's privacy policy and retention rules apply to data it receives. Vita cannot delete data already accepted by that provider.

## Data stored on the device

- SQLCipher database: confirmed meals, nutrition goals, drafts, and only the current chat session;
- encrypted files: reduced meal thumbnails;
- Android Keystore-protected encrypted preferences: provider configuration, API Key, database key, and current task state.
- bounded diagnostic logs: operation name, stage, duration, error category, app version, and basic Android runtime information.

Diagnostic logs are stored only in the app sandbox and are never uploaded automatically. They intentionally exclude API Keys, meal and chat content, image locations, and full provider endpoints. The user can explicitly export a redacted text copy from Settings when reporting a problem.

Starting a new chat deletes the previous session and messages in one database transaction. The settings screen can delete all local app data, including diagnostic logs, after two confirmations. Android uninstallation also removes the app sandbox, subject to operating-system behavior.

## Network policy

Only credential-free HTTPS URLs that resolve exclusively to globally routable addresses are allowed. HTTP, loopback, LAN, CGNAT, link-local, reserved, multicast, and redirect responses are rejected. WebView content cannot make direct network requests because `connect-src` is disabled by Content Security Policy.
