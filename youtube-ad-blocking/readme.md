# YouTube Ad Blocking

**Status: Hack Phase / Proof of Concept**

## Overview

This module intercepts YouTube HTML document requests and injects scriptlets before any page JavaScript executes, blocking ads before YouTube's ad infrastructure initialises.

## Architecture

### Injection Mechanism — `shouldInterceptRequest` HTML Modification (Mechanism B)

Uses the WebView's `shouldInterceptRequest` callback to intercept YouTube page loads:

1. **Intercepts** YouTube main-frame and iframe HTML document requests
2. **Fetches** the response via OkHttp (forwarding original request headers)
3. **Strips** `Content-Security-Policy` headers (YouTube's CSP blocks inline scripts)
4. **Injects** `<script>{scriptlet}</script>` immediately after `<head>` in the HTML
5. **Returns** the modified `WebResourceResponse` to the WebView

Because the HTML is modified before the WebView parser sees it, the injected script executes before any page JavaScript — equivalent to `document_start` timing.

### Why not `addDocumentStartJavaScript`?

The `addDocumentStartJavaScript` API (Mechanism A) is simpler and provides automatic iframe/SPA coverage, but has been observed to cause crashes on some WebView versions. The `shouldInterceptRequest` approach uses only stable, long-standing WebView APIs.

### Trade-offs

| Concern | `shouldInterceptRequest` (this impl) | `addDocumentStartJavaScript` |
|---------|--------------------------------------|------------------------------|
| Crashes | ✅ Stable APIs only | ⚠️ Can crash |
| Timing | ✅ Before any page JS | ✅ Before any page JS |
| Iframes | ⚠️ Must detect iframe doc requests | ✅ Automatic |
| SPA nav | ⚠️ Scriptlets must patch pushState | ✅ Automatic |
| CSP | ⚠️ Must strip CSP headers | ✅ Not subject to CSP |
| Latency | ⚠️ Buffers full response | ✅ No buffering |

### Module Structure

```
youtube-ad-blocking/
├── youtube-ad-blocking-api/    # Public API interface
└── youtube-ad-blocking-impl/   # Implementation + probe script
```

### Feature Flag

Controlled by remote config feature `youTubeAdBlocking` (defaults to OFF).
Enable via internal settings or local override for testing.

## Hack Phase: What This Validates

1. **Injection timing** — Does the injected script run before YouTube's ad init?
2. **CSP stripping** — Does removing CSP headers allow the inline script to execute?
3. **Iframe coverage** — Are iframe document requests detected and injected?
4. **SPA navigation** — Do the pushState/replaceState patches in the probe maintain coverage?
5. **Latency** — Does response buffering add perceptible delay?

### Testing the Probe

1. Enable the `youTubeAdBlocking` feature flag
2. Navigate to `youtube.com` in the DDG browser
3. Check logcat for `[DDG-YT-ADBLOCK]` messages:
   - `Injected at X ms` — timing (should be <5ms)
   - `ytInitialData: false` — proves injection beats YouTube init
   - `frame: main/iframe` — frame type detection
   - SPA navigation logs on video clicks
4. Also check logcat for `YouTubeAdBlocking: Injected probe script into ...` — confirms the interception path

## What's NOT Included (follow-on work)

- Real scriptlets from `content-blocker-extension`
- Remote scriptlet loading from CDN
- Ad sub-resource blocking via request filtering
- Auto-update / version checking
- Production settings UI
- DuckPlayer interaction handling
