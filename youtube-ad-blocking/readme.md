# YouTube Ad Blocking

**Status: Hack Phase — Ad blocking confirmed working ✅**

## Overview

This module intercepts YouTube HTML document requests via `shouldInterceptRequest`, injects ad-blocking scriptlets from `content-blocker-extension` before any page JavaScript executes, and strips Content-Security-Policy headers so the injected scripts run unblocked.

## Results

### Hack phase questions answered

**a) Can we reliably inject scriptlets before YouTube's JS init?**
✅ **Yes.** The `shouldInterceptRequest` HTML modification approach successfully injects scriptlets before YouTube's ad infrastructure initialises. The probe script confirms `ytInitialData: false` at injection time.

**b) Can we see ad blocking actually working?**
✅ **Yes.** Pre-roll and mid-roll ads are blocked. Videos start playing immediately without ad interruptions.

### Mechanism chosen: `shouldInterceptRequest` HTML modification (Mechanism B)

`addDocumentStartJavaScript` (Mechanism A) was initially implemented but rejected due to crash risk. Mechanism B uses only stable, long-standing WebView APIs.

## Architecture

### How it works

1. `WebViewRequestInterceptor.shouldIntercept()` calls `YouTubeAdBlocking.intercept()` for every request
2. For YouTube HTML document requests (main frame + iframes), the interceptor:
   - Fetches the response via OkHttp with a `WebViewCookieJar` bridging to `CookieManager`
   - Strips `Content-Security-Policy` headers
   - Injects `<script>{scriptlet bundle}</script>` immediately after `<head>`
   - Returns the modified `WebResourceResponse`
3. The scriptlet bundle runs before any YouTube JS, patching APIs and blocking ad logic

### Scriptlet bundle

Three scripts injected in order:
| Script | Size | Purpose |
|--------|------|---------|
| `youtube_ad_blocking_main.js` | 113KB | MAIN world: patches YouTube APIs, intercepts ad-related calls |
| `youtube_ad_blocking_isolated.js` | 34KB | ISOLATED world: DOM-level element hiding and cleanup |
| `youtube_ad_blocking_probe.js` | 2KB | Timing diagnostics (logs injection timing to logcat) |

Scriptlets sourced from `duckduckgo/content-blocker-extension` (v2026.3.24), derived from uBlock Origin Lite filters.

### Cookie handling

OkHttp uses a `WebViewCookieJar` that bridges to Android's `CookieManager`:
- Reads WebView cookies for outgoing requests (so YouTube sees session/consent cookies)
- Writes YouTube's `Set-Cookie` headers back to `CookieManager` (so consent, auth persist)

### Redirect handling

OkHttp redirect following is disabled. On 3xx responses, `null` is returned so the WebView handles redirects natively — `shouldInterceptRequest` fires again for the target URL.

### Module structure

```
youtube-ad-blocking/
├── youtube-ad-blocking-api/    # Public API: YouTubeAdBlocking interface
└── youtube-ad-blocking-impl/   # Implementation + scriptlet resources
```

### Feature flag

Controlled by remote config feature `youTubeAdBlocking` (defaults to OFF).

## Known limitations (hack phase)

- **Response buffering latency** — The full HTML response is buffered in memory before being returned to the WebView. Not yet measured on slow connections.
- **SPA navigation** — `shouldInterceptRequest` only fires on real HTTP requests. Scriptlets patch `pushState`/`replaceState` to maintain coverage across video-to-video navigation, but this depends on the scriptlets handling it correctly.
- **Iframe coverage** — Iframe HTML document requests are detected via `Accept: text/html` header check. Not all iframe formats may be caught.
- **Scriptlets are bundled locally** — No remote loading, CDN, or auto-update mechanism yet.
- **No DuckPlayer interaction** — When DuckPlayer is active, both features may conflict. Not yet handled.

## What's next (follow-on work)

- Remote scriptlet loading from CDN with auto-update
- Ad sub-resource blocking via `shouldInterceptRequest` (complementary to scriptlet injection)
- DuckPlayer interaction handling
- Production settings UI / onboarding
- Performance measurement (buffering latency, memory)
- Broader device/WebView version testing
