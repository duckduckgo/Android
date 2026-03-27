# YouTube Ad Blocking

**Status: Hack Phase**

## Overview

Test harness for evaluating YouTube ad blocking approaches on Android. Supports three injection mechanisms for delivering ad-blocking scriptlets into YouTube pages, all switchable via remote config. Includes per-mechanism timing probes and per-scriptlet controls to isolate and compare approaches.

## Configuration

All settings default to inactive until remote config delivers them. Nothing fires on cold start before settings arrive.

### Remote config example

```json
{
  "features": {
    "youTubeAdBlocking": {
      "state": "enabled",
      "settings": {
        "injectMethod": "intercept",
        "injectMain": "enabled",
        "injectIsolated": "enabled",
        "timingIntercept": "enabled",
        "timingEvaluate": "disabled",
        "timingAdsjs": "disabled"
      }
    }
  }
}
```

### Settings reference

#### `injectMethod` — which mechanism injects the scriptlet bundle

| Value | Mechanism | Description |
|-------|-----------|-------------|
| `"none"` | — | No scriptlet injection. Timing probes still fire if enabled. |
| `"evaluate"` | `evaluateJavascript` | Injects via `evaluateJavascript` in `onPageStarted`. No HTML modification, no CSP stripping, no OkHttp. Simplest approach. |
| `"intercept"` | `shouldInterceptRequest` | Intercepts YouTube HTML, fetches via OkHttp, strips CSP, injects `<script>` into `<head>`. Guaranteed pre-init timing, but complex (cookie bridging, redirect handling). |
| `"adsjs"` | `addDocumentStartJavaScript` | Automatic iframe + SPA coverage, no CSP issues. May crash on some WebView versions. |

Default: `"none"` (until settings are delivered).

#### `injectMain` / `injectIsolated` — which scriptlets to include

| Setting | What it controls | Default |
|---------|-----------------|---------|
| `injectMain` | MAIN world scriptlet — patches YouTube JS APIs to prevent ad requests (113KB) | `"enabled"` (when settings delivered) |
| `injectIsolated` | ISOLATED world scriptlet — DOM-level element hiding and cleanup (34KB) | `"enabled"` (when settings delivered) |

Values: `"enabled"` / `"disabled"`.

**Note:** The main scriptlet is required for blocking pre-roll and mid-roll ads (it intercepts at the API level). The isolated scriptlet handles DOM cleanup (hiding ad UI elements) but cannot prevent ads from loading on its own.

#### `timingIntercept` / `timingEvaluate` / `timingAdsjs` — timing probe controls

Each independently controls whether that mechanism fires its timing probe. Use to get isolated measurements without interference.

| Setting | Logcat tag | Default |
|---------|-----------|---------|
| `timingIntercept` | `[DDG-YT-ADBLOCK-INTERCEPT]` | `"disabled"` |
| `timingEvaluate` | `[DDG-YT-ADBLOCK-EVALUATE]` | `"disabled"` |
| `timingAdsjs` | `[DDG-YT-ADBLOCK-ADSJS]` | `"disabled"` |

Values: `"enabled"` / `"disabled"`. All default to `"disabled"` until settings are delivered.

### Quick config examples

**Intercept mode, both scriptlets, intercept timing only:**
```json
{ "state": "enabled", "settings": { "injectMethod": "intercept", "injectMain": "enabled", "injectIsolated": "enabled", "timingIntercept": "enabled" } }
```

**Evaluate mode, main scriptlet only:**
```json
{ "state": "enabled", "settings": { "injectMethod": "evaluate", "injectMain": "enabled", "injectIsolated": "disabled", "timingEvaluate": "enabled" } }
```

**addDocumentStartJavaScript mode, all scriptlets:**
```json
{ "state": "enabled", "settings": { "injectMethod": "adsjs", "injectMain": "enabled", "injectIsolated": "enabled", "timingAdsjs": "enabled" } }
```

**No injection, compare all three timing probes:**
```json
{ "state": "enabled", "settings": { "injectMethod": "none", "timingIntercept": "enabled", "timingEvaluate": "enabled", "timingAdsjs": "enabled" } }
```

**Feature fully disabled:**
```json
{ "state": "disabled" }
```

### How to test

1. Set the config in remote config / internal settings
2. **Close and re-open the app** — settings must be delivered before they take effect
3. **Use the fire button** — this clears any cached YouTube pages and cookies, giving you a clean state. Important when switching between injection methods to avoid stale injected content from a previous approach.
4. Navigate to `youtube.com`
5. Check logcat (filter `YouTubeAdBlocking` or `DDG-YT-ADBLOCK`)

**When switching approaches:** close app → re-open → fire button → navigate to YouTube. This ensures you're testing the new config from a clean slate.

### What to look for in logcat

**Settings dump** — every plugin logs the full settings state on each YouTube page:
```
YouTubeAdBlocking [evaluate plugin] onPageStarted https://... | injectMethod=INTERCEPT injectMain=true injectIsolated=true timingIntercept=true timingEvaluate=false timingAdsjs=false
```

**Injection decisions:**
```
YouTubeAdBlocking [intercept plugin] INJECTING SCRIPTLETS via shouldInterceptRequest HTML mod into www.youtube.com/watch | ...
YouTubeAdBlocking [evaluate plugin] SKIPPED — not active method and timing disabled
YouTubeAdBlocking [adsjs plugin] SKIPPED — not active method and timing disabled
```

**Scriptlet BEFORE/AFTER timing** — shows YouTube init state at each stage:
```
[DDG-YT-ADBLOCK-INTERCEPT] BEFORE MAIN scriptlet (112981 bytes) at 0.42ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false
[DDG-YT-ADBLOCK-INTERCEPT] AFTER MAIN scriptlet at 12.3ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false
[DDG-YT-ADBLOCK-INTERCEPT] BEFORE ISOLATED scriptlet (33992 bytes) at 12.5ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false
[DDG-YT-ADBLOCK-INTERCEPT] AFTER ISOLATED scriptlet at 15.1ms | ytInitialData: false | ytcfg: true | ytPlayerResponse: false
```

If BEFORE shows `false` but AFTER shows `true`, YouTube's init ran during that scriptlet's execution.

### Understanding the probe values

| Field | What it is | `false` means | `true` means |
|-------|-----------|---------------|-------------|
| `Injected at X ms` | `performance.now()` — time since document context created | — | — |
| `ytInitialData` | YouTube's server-rendered page data blob, including ad config. Set by an inline `<script>` in `<head>`. | Injection beat YouTube's init. | YouTube's init already ran. |
| `ytcfg` | YouTube's configuration object (feature flags, experiments). | Before YouTube configured itself. | Config loaded (ad blocking may still work). |
| `ytPlayerResponse` | Initial video + ad payload (`adPlacements`, `playerAds`). | Can strip ad fields before player reads them. | Player payload already set. |
| `frame` | `main` or `iframe`. YouTube's ad player sometimes runs in an iframe. | — | — |

## Architecture

### Three injection mechanisms

| Mechanism | Plugin class | Scope | How it works |
|-----------|-------------|-------|-------------|
| **intercept** | `YouTubeAdBlockingRequestInterceptor` | `AppScope` | Intercepts YouTube HTML in `shouldInterceptRequest`, fetches via OkHttp + `WebViewCookieJar`, strips CSP, injects `<script>` into `<head>` |
| **evaluate** | `YouTubeAdBlockingEvaluateJsPlugin` | `AppScope` | `JsInjectorPlugin` — calls `evaluateJavascript` in `onPageStarted`. Not subject to CSP. |
| **adsjs** | `YouTubeAdBlockingAdsJsPlugin` | `FragmentScope` | `AddDocumentStartJavaScriptPlugin` — registers via `addDocumentStartJavaScript`. Auto covers iframes + SPA. |

### Scriptlets

| Script | Size | World | Purpose |
|--------|------|-------|---------|
| `youtube_ad_blocking_main.js` | 113KB | MAIN | Patches YouTube APIs to intercept and suppress ad requests. Required for blocking pre-roll/mid-roll. |
| `youtube_ad_blocking_isolated.js` | 34KB | ISOLATED | DOM-level element hiding and cleanup. Cosmetic — hides ad UI but cannot prevent ads from loading. |
| `youtube_ad_blocking_probe.js` | 2KB | — | Timing diagnostics + SPA navigation monitoring (`pushState`/`replaceState` patches). |

Scriptlets sourced from `duckduckgo/content-blocker-extension` (v2026.3.24), derived from uBlock Origin Lite filters.

### Module structure

```
youtube-ad-blocking/
├── youtube-ad-blocking-api/    # Public API: YouTubeAdBlocking interface
└── youtube-ad-blocking-impl/   # Implementation + scriptlet resources
    ├── YouTubeAdBlockingFeature.kt              # Remote feature toggle
    ├── YouTubeAdBlockingSettingsStore.kt         # Settings provider (reads Toggle.getSettings())
    ├── ScriptletBundleBuilder.kt                 # Shared: builds scriptlet bundle with timing markers
    ├── RealYouTubeAdBlocking.kt                 # API impl, routes to intercept mechanism
    ├── YouTubeAdBlockingRequestInterceptor.kt   # Mechanism: intercept (shouldInterceptRequest)
    ├── YouTubeAdBlockingTimingComparisonPlugin.kt  # Mechanism: evaluate (evaluateJavascript)
    └── YouTubeAdBlockingAdsJsPlugin.kt          # Mechanism: adsjs (addDocumentStartJavaScript)
```

## Known limitations

- **Settings delivery timing** — all defaults are inactive until remote config is delivered. Restart the app after changing config.
- **Fire button** — wipes the toggle store. Settings cached in-memory for current session but may need app restart.
- **Response buffering** (intercept only) — full HTML response buffered in memory before injection.
- **Cookie bridging** (intercept only) — `WebViewCookieJar` bridges OkHttp ↔ CookieManager; may have edge cases with consent flows.
- **SPA navigation** (intercept only) — `shouldInterceptRequest` doesn't fire on pushState navigations; scriptlets patch pushState/replaceState.
- **Iframe coverage** (evaluate only) — `evaluateJavascript` only runs in the main frame, not iframes.
- **Crash risk** (adsjs only) — `addDocumentStartJavaScript` may crash on some WebView versions.
- **Scriptlets are bundled locally** — no remote loading, CDN, or auto-update.
- **No DuckPlayer interaction** — not yet handled.
