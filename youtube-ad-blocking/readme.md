# YouTube Ad Blocking

**Status: Hack Phase — Ad blocking confirmed working ✅**

## Overview

This module injects ad-blocking scriptlets into YouTube pages before any page JavaScript executes, blocking ads before YouTube's ad infrastructure initialises. Three injection mechanisms are supported and switchable via remote config.

## Results

**a) Can we reliably inject scriptlets before YouTube's JS init?** ✅ Yes.
**b) Can we see ad blocking actually working?** ✅ Yes — pre-roll and mid-roll ads blocked.

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
| `"evaluate"` | C: `evaluateJavascript` | Injects via `evaluateJavascript` in `onPageStarted`. No HTML modification, no CSP stripping, no OkHttp. Simplest approach. |
| `"intercept"` | B: `shouldInterceptRequest` | Intercepts YouTube HTML, fetches via OkHttp, strips CSP, injects `<script>` into `<head>`. Guaranteed pre-init timing, but complex (cookie bridging, redirect handling). |
| `"adsjs"` | A: `addDocumentStartJavaScript` | Automatic iframe + SPA coverage, no CSP issues. May crash on some WebView versions. |

Default: `"none"` (until settings are delivered).

#### `injectMain` / `injectIsolated` — which scriptlets to include

| Setting | Controls | Default |
|---------|----------|---------|
| `injectMain` | MAIN world scriptlet — patches YouTube APIs, intercepts ad calls (113KB) | `"enabled"` (when settings delivered) |
| `injectIsolated` | ISOLATED world scriptlet — DOM-level element hiding and cleanup (34KB) | `"enabled"` (when settings delivered) |

Values: `"enabled"` / `"disabled"`.

#### `timingIntercept` / `timingEvaluate` / `timingAdsjs` — timing probe controls

Each independently controls whether that mechanism fires its timing probe. Use these to get clean, isolated timing measurements without interference.

| Setting | Logcat tag | Default |
|---------|-----------|---------|
| `timingIntercept` | `[DDG-YT-ADBLOCK]` | `"disabled"` |
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
2. **Kill and restart the app** (or wait for settings delivery) — settings must be delivered via `store()` before they take effect
3. Navigate to `youtube.com`
4. Check logcat (filter `YouTubeAdBlocking` or `DDG-YT-ADBLOCK`)

### What to look for in logcat

**Settings dump** — every plugin logs the full settings state when it runs on a YouTube page:
```
YouTubeAdBlocking [evaluate plugin] onPageStarted https://... | injectMethod=INTERCEPT injectMain=true injectIsolated=true timingIntercept=true timingEvaluate=false timingAdsjs=false
```

**Injection decisions** — each plugin logs what it's doing:
```
YouTubeAdBlocking [intercept plugin] INJECTING SCRIPTLETS via shouldInterceptRequest HTML mod into www.youtube.com/watch (timing=true)
YouTubeAdBlocking [evaluate plugin] SKIPPED — not active method and timing disabled
YouTubeAdBlocking [adsjs plugin] SKIPPED — not active method and timing disabled
```

**Scriptlet markers** — which scriptlets actually ran in the page:
```
[DDG-YT-ADBLOCK] Running MAIN scriptlet (112981 bytes)
[DDG-YT-ADBLOCK] Running ISOLATED scriptlet (33992 bytes)
```

**Timing probe** — injection timing relative to YouTube's init:
```
[DDG-YT-ADBLOCK] Injected at 0.42 ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false | frame: main
```

### Understanding the probe values

| Field | What it is | `false` means | `true` means |
|-------|-----------|---------------|-------------|
| `Injected at X ms` | `performance.now()` — time since document context created | — | — |
| `ytInitialData` | YouTube's server-rendered page data blob, including ad config. Set by an inline `<script>` in `<head>`. | ✅ We beat YouTube's init. | ❌ YouTube's init already ran. |
| `ytcfg` | YouTube's configuration object (feature flags, experiments). | ✅ Before YouTube configured itself. | ⚠️ Config loaded, but ad blocking may still work. |
| `ytPlayerResponse` | Initial video + ad payload (`adPlacements`, `playerAds`). | ✅ Can strip ad fields before player reads them. | ❌ Player payload already set. |
| `frame` | `main` or `iframe`. YouTube's ad player sometimes runs in an iframe. | — | — |

**Success** = all three `false` + low `ms` value.

## Architecture

### Three injection mechanisms

| Mechanism | Plugin | Scope | How it works |
|-----------|--------|-------|-------------|
| **intercept** | `YouTubeAdBlockingRequestInterceptor` | `AppScope` | Intercepts YouTube HTML in `shouldInterceptRequest`, fetches via OkHttp + `WebViewCookieJar`, strips CSP, injects `<script>` into `<head>` |
| **evaluate** | `YouTubeAdBlockingEvaluateJsPlugin` | `AppScope` | `JsInjectorPlugin` — calls `evaluateJavascript` in `onPageStarted`. Not subject to CSP. |
| **adsjs** | `YouTubeAdBlockingAdsJsPlugin` | `FragmentScope` | `AddDocumentStartJavaScriptPlugin` — registers via `WebViewCompatWrapper.addDocumentStartJavaScript`. Auto covers iframes + SPA. |

### Scriptlet bundle

| Script | Size | Purpose |
|--------|------|---------|
| `youtube_ad_blocking_main.js` | 113KB | MAIN world: patches YouTube APIs, intercepts ad-related calls |
| `youtube_ad_blocking_isolated.js` | 34KB | ISOLATED world: DOM-level element hiding and cleanup |
| `youtube_ad_blocking_probe.js` | 2KB | Timing diagnostics (logs injection timing to console/logcat) |

Scriptlets sourced from `duckduckgo/content-blocker-extension` (v2026.3.24), derived from uBlock Origin Lite filters.

### Module structure

```
youtube-ad-blocking/
├── youtube-ad-blocking-api/    # Public API: YouTubeAdBlocking interface
└── youtube-ad-blocking-impl/   # Implementation + scriptlet resources
    ├── YouTubeAdBlockingFeature.kt              # Remote feature toggle
    ├── YouTubeAdBlockingSettingsStore.kt         # Settings: injectMethod, injectMain, injectIsolated, timing*
    ├── RealYouTubeAdBlocking.kt                 # API impl, routes to intercept mechanism
    ├── YouTubeAdBlockingRequestInterceptor.kt   # Mechanism B (shouldInterceptRequest)
    ├── YouTubeAdBlockingTimingComparisonPlugin.kt  # Mechanism C (evaluateJavascript)
    └── YouTubeAdBlockingAdsJsPlugin.kt          # Mechanism A (addDocumentStartJavaScript)
```

## Known limitations (hack phase)

- **Settings delivery timing** — all defaults are `NONE`/`false` until `store()` is called. The first page load after cold start won't have ad blocking until settings arrive. Restart the app after changing config.
- **Response buffering latency** (intercept only) — full HTML response buffered in memory before injection
- **SPA navigation** (intercept only) — `shouldInterceptRequest` doesn't fire on pushState navigations; scriptlets patch pushState/replaceState
- **Iframe coverage** (evaluate only) — `evaluateJavascript` only runs in the main frame, not iframes
- **Cookie bridging** (intercept only) — `WebViewCookieJar` bridges OkHttp ↔ CookieManager; may have edge cases
- **Scriptlets are bundled locally** — no remote loading, CDN, or auto-update
- **No DuckPlayer interaction** — not yet handled

## What's next (follow-on work)

- Determine preferred injection mechanism based on timing data
- Remote scriptlet loading from CDN with auto-update
- Ad sub-resource blocking via `shouldInterceptRequest`
- DuckPlayer interaction handling
- Production settings UI / onboarding
- Performance measurement
- Broader device/WebView version testing
