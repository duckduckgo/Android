# YouTube Ad Blocking

**Status: Hack Phase — Ad blocking confirmed working ✅**

## Overview

This module injects ad-blocking scriptlets into YouTube pages before any page JavaScript executes, blocking ads before YouTube's ad infrastructure initialises.

## Results

### Hack phase questions answered

**a) Can we reliably inject scriptlets before YouTube's JS init?**
✅ **Yes.** Both injection mechanisms successfully block YouTube ads.

**b) Can we see ad blocking actually working?**
✅ **Yes.** Pre-roll and mid-roll ads are blocked. Videos start playing immediately without ad interruptions.

## Configuration

### Remote config example

```json
{
  "features": {
    "youTubeAdBlocking": {
      "state": "enabled",
      "settings": {
        "injectMethod": "intercept",
        "timingIntercept": "enabled",
        "timingEvaluate": "enabled",
        "timingAdsjs": "disabled"
      }
    }
  }
}
```

### Settings reference

#### `injectMethod` — which mechanism injects the full ad-blocking scriptlet bundle

| Value | Mechanism | Description |
|-------|-----------|-------------|
| `"none"` | — | Disabled. No scriptlet injection (useful for A/B testing while feature is "enabled"). |
| `"evaluate"` | C: `evaluateJavascript` | Injects scriptlets via `evaluateJavascript` in `onPageStarted`. No HTML modification, no CSP stripping, no OkHttp. Simplest approach, but timing may be slightly later. |
| `"intercept"` | B: `shouldInterceptRequest` | Intercepts YouTube HTML, fetches via OkHttp, strips CSP, injects `<script>` into `<head>`. Guaranteed pre-init timing, but more complex (cookie bridging, redirect handling). **Default.** |
| `"adsjs"` | A: `addDocumentStartJavaScript` | Automatic iframe + SPA coverage, no CSP issues. Reserved for future use — may crash on some WebView versions. |

#### `timingIntercept` / `timingEvaluate` / `timingAdsjs` — timing probe controls

Each independently controls whether that mechanism's timing probe fires. Values: `"enabled"` / `"disabled"`. Enable one at a time for clean, isolated measurements. All default to `"enabled"`.

| Setting | Controls | Logcat tag |
|---------|----------|-----------|
| `timingIntercept` | Probe injected via `shouldInterceptRequest` HTML modification | `[DDG-YT-ADBLOCK]` |
| `timingEvaluate` | Probe injected via `evaluateJavascript` in `onPageStarted` | `[DDG-YT-ADBLOCK-EVALUATE]` |
| `timingAdsjs` | Probe injected via `addDocumentStartJavaScript` (not yet wired) | `[DDG-YT-ADBLOCK-ADSJS]` |

### How to test

1. Open DuckDuckGo browser internal settings
2. Enable the `youTubeAdBlocking` feature flag
3. Navigate to `youtube.com` and verify ads are blocked (default: `intercept` mode)
4. To switch injection mechanism: change the `injectMethod` setting in the config
5. **Important:** after changing config, do a full page reload (not SPA navigation) — swipe down to refresh or navigate away and back

### Quick config examples

**Enable with HTML interception (default), all timing probes active:**
```json
{ "state": "enabled", "settings": { "injectMethod": "intercept" } }
```

**Enable with evaluateJavascript, only evaluate timing probe:**
```json
{ "state": "enabled", "settings": { "injectMethod": "evaluate", "timingIntercept": "disabled", "timingEvaluate": "enabled", "timingAdsjs": "disabled" } }
```

**Intercept mode, only intercept timing (no evaluate noise):**
```json
{ "state": "enabled", "settings": { "injectMethod": "intercept", "timingIntercept": "enabled", "timingEvaluate": "disabled", "timingAdsjs": "disabled" } }
```

**Compare evaluate vs intercept timing (no scriptlet injection, probes only):**
```json
{ "state": "enabled", "settings": { "injectMethod": "none", "timingIntercept": "enabled", "timingEvaluate": "enabled", "timingAdsjs": "disabled" } }
```

**Feature enabled but all probes disabled (clean, no timing noise):**
```json
{ "state": "enabled", "settings": { "injectMethod": "intercept", "timingIntercept": "disabled", "timingEvaluate": "disabled", "timingAdsjs": "disabled" } }
```

**Feature fully disabled:**
```json
{ "state": "disabled" }
```

### What to look for in logcat

Filter by `YouTubeAdBlocking` or `DDG-YT-ADBLOCK`:

```
# Mechanism B (shouldInterceptRequest HTML mod) — when useEvaluateJs is OFF:
YouTubeAdBlocking: Injected probe script into www.youtube.com/...
[DDG-YT-ADBLOCK] Injected at 0.42 ms | ytInitialData: false | ...

# Mechanism C (evaluateJavascript) — when useEvaluateJs is ON:
YouTubeAdBlocking: [evaluateJs mode] Injecting full scriptlet bundle for ...
[DDG-YT-ADBLOCK] Injected at X ms | ytInitialData: false/true | ...

# Timing comparison probe (always fires regardless of mode):
[DDG-YT-ADBLOCK-EVALUATE] Injected at X ms | ytInitialData: false/true | ...
```

### Understanding the probe values

| Field | What it is | `false` means | `true` means |
|-------|-----------|---------------|-------------|
| `Injected at X ms` | `performance.now()` — time since the document context was created | — | — |
| `ytInitialData` | `window.ytInitialData` — YouTube's server-rendered page data blob. Set by an inline `<script>` in `<head>` that bootstraps the page, including ad configuration. | ✅ We beat YouTube's init. Scriptlets can intercept ad setup. | ❌ YouTube's init already ran. Scriptlets may be too late to block ads. |
| `ytcfg` | `window.ytcfg` — YouTube's configuration object. Set early in page init, contains feature flags, experiment IDs, and client config. | ✅ Injected before YouTube configured itself. | ⚠️ YouTube config already loaded, but ad blocking may still work depending on which APIs are patched. |
| `ytPlayerResponse` | `window.ytInitialPlayerResponse` — the initial video + ad payload. Contains `adPlacements`, `playerAds`, and other ad metadata that the player reads on init. | ✅ Injected before the player payload was set. Scriptlets can strip ad fields. | ❌ Player payload already set. Pre-roll ad data is already available to the player. |
| `frame` | Whether we're in the main frame or an iframe. YouTube's ad player sometimes runs in an iframe with its own JS context. | — | — |

**What "success" looks like:**
```
[DDG-YT-ADBLOCK] Injected at 0.42 ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false | frame: main
```

All three `false` = we beat YouTube's init completely. The scriptlets have full control over the ad APIs before YouTube touches them.

### Comparing the two mechanisms

1. Enable `youTubeAdBlocking`, disable `useEvaluateJs`
2. Navigate to YouTube, check logcat for both `[DDG-YT-ADBLOCK]` and `[DDG-YT-ADBLOCK-EVALUATE]` tags
3. Compare timing values and `ytInitialData` state for each
4. Enable `useEvaluateJs`, reload YouTube
5. Check if ads are still blocked

If `evaluateJavascript` shows `ytInitialData: false` and ads are blocked, it's the preferred approach (simpler, no CSP stripping).

## Architecture

### Mechanism B: `shouldInterceptRequest` HTML modification (default)

1. `WebViewRequestInterceptor` calls `YouTubeAdBlocking.intercept()` for every request
2. For YouTube HTML documents, the interceptor:
   - Fetches the response via OkHttp with `WebViewCookieJar` bridging to `CookieManager`
   - Strips `Content-Security-Policy` headers (YouTube's CSP blocks inline scripts)
   - Injects `<script>{scriptlet bundle}</script>` immediately after `<head>`
   - Returns the modified `WebResourceResponse`
3. OkHttp redirect following is disabled — redirects handled natively by WebView

### Mechanism C: `evaluateJavascript` (when `useEvaluateJs` is ON)

1. `YouTubeAdBlockingEvaluateJsPlugin` (a `JsInjectorPlugin`) runs in `onPageStarted`
2. For YouTube URLs, calls `webView.evaluateJavascript(scriptletBundle, null)`
3. No HTML modification, no CSP stripping, no OkHttp fetch needed
4. `evaluateJavascript` is not subject to CSP (injected by the host app)

### Scriptlet bundle

Three scripts injected in order:
| Script | Size | Purpose |
|--------|------|---------|
| `youtube_ad_blocking_main.js` | 113KB | MAIN world: patches YouTube APIs, intercepts ad-related calls |
| `youtube_ad_blocking_isolated.js` | 34KB | ISOLATED world: DOM-level element hiding and cleanup |
| `youtube_ad_blocking_probe.js` | 2KB | Timing diagnostics (logs injection timing to logcat) |

Scriptlets sourced from `duckduckgo/content-blocker-extension` (v2026.3.24), derived from uBlock Origin Lite filters.

### Module structure

```
youtube-ad-blocking/
├── youtube-ad-blocking-api/    # Public API: YouTubeAdBlocking interface
└── youtube-ad-blocking-impl/   # Implementation + scriptlet resources
    ├── YouTubeAdBlockingFeature.kt              # Feature flags (self + useEvaluateJs)
    ├── RealYouTubeAdBlocking.kt                 # API impl, routes to correct mechanism
    ├── YouTubeAdBlockingRequestInterceptor.kt   # Mechanism B (shouldInterceptRequest)
    └── YouTubeAdBlockingTimingComparisonPlugin.kt  # Mechanism C (evaluateJavascript)
```

## Known limitations (hack phase)

- **Response buffering latency** (Mechanism B only) — full HTML buffered in memory
- **SPA navigation** — `shouldInterceptRequest` only fires on real HTTP requests; scriptlets patch `pushState`/`replaceState`
- **Iframe coverage** (Mechanism C) — `evaluateJavascript` only runs in the main frame, not iframes
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
