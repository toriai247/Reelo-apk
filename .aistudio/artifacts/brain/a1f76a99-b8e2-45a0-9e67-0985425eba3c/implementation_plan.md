# Reelo Watch Screen WebView Enhancement Plan

Reelo-r Watch screen (WebView)-e existing rail layout (left: navigation & server controls, right: episode list) ebong 9:16 portrait vertical layout thik rekhe ad-redirect auto-return, ad overlay cleaner, browser sheet, guided Secure DNS, background popup gate, ebong real HTML5 `<video>` resume playback feature-gulo implement korar bistarit plan.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Schema & Database Integrity**: Existing Supabase schema (`REELO_SERIES_SCHEMA_d5a32c21.md`) onujayi shudhu `watch_progress` table (`user_id, episode_id, series_id, position_sec, duration_sec, completed`) ebong local DataStore use kora hobe. Kono notun unsupported table/column add kora hobe na.
> - **No Custom Video Player**: WebView-e stream embed thakbe; custom ExoPlayer ba media player replace kora hobe na.
> - **Default In-app DoH Proxy**: ERR_TIMED_OUT issue prevent korar jonno ager in-app DoH proxy default **OFF** kora hobe, ebong Feature 3-r guided Android system Private DNS (`dns.google`) default recommended way hobe.
> - **Preserve Rail Layout**: Left rail-e notun **Open in Browser** (Globe icon) ebong **Shield** (🛡️ Ad & Redirect Controller) icon jog hobe; right episode rail intact thakbe. Portrait mode-eo Top Bar ebong Action Bar-e accessibility deya hobe.

---

## Assumptions & Clarifications

1. **Cross-Origin Iframe Video Access**: Third-party embeds (e.g. streamtape, dood, superstream) often place the `<video>` inside an iframe. Android WebKit's `WebViewCompat.addDocumentStartJavaScript(allowedOriginRules = setOf("*"))` runs inside both the main frame and child subframes. In the rare event that an iframe contains restrictive `sandbox` attributes preventing bridge execution, the app seamlessly falls back to foreground elapsed time and logs `"ReeloResume: no video element found"`.
2. **Dual Portrait & Landscape Support**: The rail layout is active in 16:9 landscape mode, but users also watch in 9:16 portrait reel mode. The Globe (Open in browser) and Shield (Ad & Redirect status) affordances will be present in both layouts (Landscape rail + Portrait top header/action column) for complete usability.
3. **Local & Cloud Progress Sync**: For guests, episode progress is persistently cached in Jetpack DataStore (`watch_pos_<episodeId>`). For authenticated users, progress is stored locally every ~5s and synced to Supabase `watch_progress` every ~30s and on screen exit.
4. **Ad Domain Blocklist**: A compact bundled asset file (`ad_blocklist.txt`) containing high-confidence ad/tracker hostnames will be read at runtime to intercept sub-resource requests in `shouldInterceptRequest`, requiring zero external network calls.

---

## 1. Overview & Core Concept

Reelo app-e third-party streaming server embed gulo aggressive ad-redirects, click-jacking overlays, ebong unwanted popups toiri kore. Ei 7-ti features user-ke smooth, uninterrupted stream dekhar sujog kore dibe:
1. **Open in Browser**: Original source URL diye Brave, Chrome, Firefox ba installed browser-e seamlessly switch kora.
2. **Recommended Browser (Brave)**: Fast & ad-free streaming-er jonno Brave recommendation card & popup with auto-launch / Play Store install.
3. **Secure DNS Guide**: System Private DNS (`dns.google`) set korar guided wizard with real-time detection on Android 9+ (API 28+).
4. **Stay on Original Site**: Trusted hosts whitelist monitoring, main-frame ad redirect block with instant auto-return, 5-second "Allow once" snackbar, ebong rail counter.
5. **Ad Overlay Cleaner**: `WebViewCompat.addDocumentStartJavaScript` diye safe DOM cleaner jeta aggressive transparent click-traps & fake popups close korbe without touching actual video elements.
6. **Window / Popup Gate**: `onCreateWindow` default deny; optional background popup worker.
7. **Real Video Resume Playback**: `ReeloBridge` WebMessageListener diye iframe & main-frame `<video>` element-er actual `currentTime` track kore DataStore + Supabase sync, reload/crash recovery, ebong Series Details-e "Continue from Ex • mm:ss" display.

---

## 2. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                    REELO WATCH SCREEN                                  │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│  ┌───────────────┐     ┌────────────────────────────────────────────────────────────┐  │
│  │   LEFT RAIL   │     │                    WEBVIEW CONTAINER                       │  │
│  │ ───────────── │     │                                                            │  │
│  │ • Back        │     │  ┌──────────────────────────────────────────────────────┐  │  │
│  │ • Forward     │     │  │  Main Frame / Stream Iframe (<video>)               │  │  │
│  │ • Reload      │     │  │  • ReeloBridge JS Listener (timeupdate ~5s)          │  │  │
│  │ • Exit        │     │  │  • Ad Cleaner Script (MutationObserver)              │  │  │
│  │ • Servers     │     │  │  • Trusted Host Verifier (shouldOverrideUrlLoading) │  │  │
│  │ • Globe 🌐 ───┼──┐  │  └──────────────────────────────────────────────────────┘  │  │
│  │ • Shield 🛡️ ──┼─┐│  │                                                            │  │
│  └───────────────┘ ││  │  Floating: [ Resumed from 12:34 · Start over ] (5s)        │  │
│                    ││  │  Snackbar: [ Ad redirect blocked · Allow once (5s) ]       │  │
│                    ││  └────────────────────────────────────────────────────────────┘  │
│                    ││                                                                  │
│                    │└─► [ Shield & Ad Protection Bottom Sheet ]                        │
│                    │    • Stay on original site (ON/OFF)                               │
│                    │    • Ad cleaner active (ON/OFF)                                   │
│                    │    • Popup-gated players (ON/OFF)                                 │
│                    │    • Blocked redirect counters & Reset site rules                 │
│                    │                                                                   │
│                    └──► [ Open In Browser Bottom Sheet ]                               │
│                         • Original source URL (truncated)                              │
│                         • Recommended Browser Card (Brave open / install)              │
│                         • Installed browser list (Chrome, Firefox, Edge, Opera, etc.)  │
│                         • Copy original URL / Copy current page URL / Share            │
│                         • Guided Secure DNS launcher                                   │
└────────────────────────────────────────────────────────────────────────────────────────┘
                                    │                           │
                                    ▼                           ▼
                        ┌───────────────────────┐   ┌───────────────────────┐
                        │    LOCAL DATASTORE    │   │   SUPABASE BACKEND    │
                        ├───────────────────────┤   ├───────────────────────┤
                        │ • Episode progress    │   │ • watch_progress      │
                        │ • Shield settings     │   │   (upsert ~30s / exit)│
                        │ • Brave prompt expiry │   │ • episode_sources     │
                        │ • Trusted host prefs  │   │ • record_view RPC     │
                        └───────────────────────┘   └───────────────────────┘
```

---

## 3. Detailed Feature Specifications

### Feature 1: "Open in browser" Sheet
- **Left Rail & Portrait Entry**: Globe icon (`Icons.Default.Language`) on the left navigation rail and portrait top bar.
- **Manifest Queries (`AndroidManifest.xml`)**:
  - Add `<queries>` for `ACTION_VIEW`, `CATEGORY_BROWSABLE`, `https`.
  - Specific packages: `com.brave.browser`, `com.android.chrome`, `org.mozilla.firefox`, `com.microsoft.emmx`, `com.opera.browser`, `com.duckduckgo.mobile.android`, `com.sec.android.app.sbrowser`.
- **Browser Discovery**:
  - Detect installed browsers via `packageManager.queryIntentActivities(...)`.
  - Display app icon, name, and package. Tapping opens the **original source URL** (`episode_sources.url`) using `Intent.setPackage(packageName)`.
- **Sheet Layout**:
  1. Truncated original URL with copy indicator.
  2. Recommended Brave card (Feature 2).
  3. Installed browser cards/row.
  4. Actions: "Copy Original URL", "Copy Current Page URL", "Share Link".
  5. Action button: "Watch here with Secure DNS" (opens Feature 3 guide).

### Feature 2: Recommended Browser Popup (Brave)
- **Objective Recommendation**: "Use Brave browser for fast & ad-free viewing" (`com.brave.browser`). No false claims or affiliate tags.
- **Action Buttons**:
  - Installed: "Open in Brave" (`setPackage("com.brave.browser")` + original URL).
  - Not installed: "Get Brave" (`market://details?id=com.brave.browser` with fallback to `https://play.google.com/store/apps/details?id=com.brave.browser`).
- **Trigger Logic**:
  - First-time on Watch screen.
  - Frequent ad redirects blocked (>= 10 blocks in 60s).
  - Stream load error.
- **Frequency Capping**: Dismissible, "Don't show again" toggle, maximum once every 24 hours auto-display. Always pinned inside the "Open in browser" sheet.

### Feature 3: Guided Secure DNS (dns.google)
- **Guided UI Bottom Sheet**:
  - "Copy dns.google" button: Copies to clipboard with confirmation toast.
  - "Open network settings" button: Launches `Settings.ACTION_WIRELESS_SETTINGS` with fallback to `Settings.ACTION_SETTINGS`.
  - Step-by-step instructions with vendor notes:
    - Stock Android / Pixel: *Settings > Network & internet > Private DNS*
    - Samsung: *Settings > Connections > More connection settings > Private DNS*
    - Xiaomi / MIUI: *Settings > Connection & sharing > Private DNS*
    - Paste `dns.google` and tap *Save*.
  - **Live Detection (API 28+)**:
    - Uses `ConnectivityManager` to inspect active network's `LinkProperties.isPrivateDnsActive` and `privateDnsServerName`.
    - Auto-refreshes on `Lifecycle.Event.ON_RESUME`. If detected, shows *"Secure DNS ON (dns.google) ✓"* and a "Reload" button.
- **In-App DoH Proxy**:
  - Set default preference to `false` in `PreferencesManager` to prevent `ERR_TIMED_OUT`.
  - All proxy classes (`DohResolver`, `LocalDohProxyServer`) remain preserved.

### Feature 4: Stay on Original Site (Auto-Return)
- **Trusted Host Tracking**:
  - `originUrl` = selected `episode_sources.url`.
  - `trustedHosts` = host of `originUrl` plus initial server-side HTTP 3xx redirect chain resolved before first user touch. Subframe / iframe requests are not blocked; only main-frame navigations are checked.
- **Main-Frame Interception (`shouldOverrideUrlLoading`)**:
  - Non-http(s) scheme (e.g. `intent://`, `market://`): return `true` (BLOCK).
  - Main-frame navigation outside `trustedHosts`: return `true` (BLOCK) unless temporarily allowed via "Allow once".
- **Safety Net**:
  - In `onPageStarted` and `doUpdateVisitedHistory`, if main-frame URL host is untrusted:
    - Call `stopLoading()`.
    - Inspect `copyBackForwardList()` for the last trusted history index and call `goBackOrForward(steps)`.
    - If no history available, call `loadUrl(originUrl)`.
- **UI & Shield**:
  - Transient snackbar: *"Ad redirect blocked"* + *"Allow once"* (5-second window).
  - Left rail Shield icon with real-time blocked counter badge (e.g., 🛡️ 3).
  - Tapping Shield opens quick sheet with:
    - "Stay on original site" toggle (default ON).
    - Blocked count and reset button.
    - Turn off protection for this site.
  - If >= 10 redirects blocked in 60s, trigger the Brave recommendation popup.
- **Logcat Tag**: `ReeloNav` for allowed and blocked navigations.

### Feature 5: Ad Overlay Cleaner (Auto Close Popups / X)
- **Script Injection**:
  - Use `WebViewCompat.addDocumentStartJavaScript(allowedOriginRules = setOf("*"))` if supported, with fallback to `evaluateJavascript` on `onPageFinished`.
- **Script Rules**:
  - Throttled (`~500ms`) `MutationObserver` checking for overlays:
    - `position: fixed | absolute`, `z-index >= 1000`, covering >= 30% viewport or transparent click-catcher layer.
  - Safe Close Detection:
    - Search for genuine close buttons inside overlay (`aria-label="close"`, "×", "✕", "X", `class*="close"`).
    - If found, attempt click; if overlay remains or redirects, apply `display: none !important; pointer-events: none !important;`.
  - **Zero False-Positive Protection**:
    - Exclude `<video>`, video ancestors, and media player controls (`.jw-*`, `.plyr*`, `.vjs-*`, `video-js`, `[class*=player]`, `.controls`).
- **Resource Blocklist**:
  - Bundled asset list (`ad_blocklist.txt`) of notorious ad/tracker domains intercepted via `shouldInterceptRequest` returning an empty `WebResourceResponse`.
- **Settings**: "Ad cleaner" toggle in Shield sheet (default ON).
- **Logcat Tag**: `ReeloAds` for cleaned overlays and blocked ad requests.

### Feature 6: Window / Popup Handling (Background)
- **`onCreateWindow`**: Return `false` by default to block popups from draining data and generating ad impressions.
- **Popup-Gated Players Setting** (default OFF):
  - Accessible via Shield sheet.
  - When enabled, popup URL is loaded into an invisible, unattached `WebView` instance without JavaScript interfaces or DOM storage access.
  - Destroyed automatically after `onPageFinished` or after a 2-second timeout, keeping the user on the video page.

### Feature 7: Real Playback Position Resume
- **Bridge Listener**:
  - `WebViewCompat.addWebMessageListener(webView, "ReeloBridge", setOf("*"), listener)` (with fallback for unsupported devices).
  - Injected script observes all frames for `<video duration > 120s>` (ignoring short preroll ads).
  - Sends `{currentTime, duration, ended}` via `ReeloBridge` throttled every ~5s.
- **Persistence**:
  - **DataStore**: Saved every ~5s keyed by `episodeId` for both guests and authenticated users.
  - **Supabase**: For authenticated users, upserts `watch_progress` (`position_sec`, `duration_sec`, `completed`) every ~30s and on pause / screen exit. `completed = true` if ended or `position >= 90% duration`.
- **Resume Flow**:
  - JS sends `'ready'` to app on video metadata load.
  - App replies with saved seconds if `savedSec > 10` and `savedSec < duration - 30`.
  - Video sets `video.currentTime = savedSec`.
  - App displays animated chip: *"Resumed from mm:ss · Start over"* for 5 seconds. Tapping *"Start over"* seeks to 0.
- **Fallback**: If no video element is accessible due to sandbox restrictions, fallback to foreground elapsed time and log `"ReeloResume: no video element found"`.
- **Series Details**: Displays *"Continue from E{num} · mm:ss"* on the primary play button.
- **Logcat Tag**: `ReeloResume` for position updates, resume handshakes, and fallbacks.

---

## 4. Verification & Testing Plan

1. **Ad Redirect Test**:
   - Tap ad triggers in WebView: verify WebView stays on original domain or auto-returns without user manual back navigation.
   - Verify snackbar "Ad redirect blocked · Allow once (5s)" and shield badge count increment.
2. **Ad Cleaner Test**:
   - Verify fake close traps and floating ad overlays are hidden while HTML5 player controls remain fully interactive.
3. **Resume Playback Test**:
   - Play video past 2 minutes, reload or re-enter episode: verify video resumes from saved position and "Resumed from mm:ss · Start over" chip appears.
   - Verify "Continue from Ex • mm:ss" appears on Series Details screen.
4. **Open in Browser Sheet Test**:
   - Tap globe icon: verify sheet displays original URL, Brave card with correct state (Open or Get Brave), installed browsers list, and copy/share buttons.
5. **Secure DNS Guide Test**:
   - Open Secure DNS sheet: verify clipboard copy of `dns.google`, settings launch, API 28+ active status detection, and reload button.
6. **Hardware & Layout Test**:
   - Test back navigation, fullscreen toggle, and episode switching; verify Logcat output tags (`ReeloNav`, `ReeloAds`, `ReeloResume`).
