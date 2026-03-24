# YouTube Ad Blocking

**Status: Hack Phase / Proof of Concept**

## Overview

This module injects scriptlets into YouTube pages at document start to block ads before YouTube's JavaScript initialises. It uses the existing `addDocumentStartJavaScript` infrastructure (AndroidX WebKit) which is already proven in the DDG Android browser for content scope scripts, clipboard, and blob downloads.

## Architecture

### Injection Mechanism

Uses `WebViewCompat.addDocumentStartJavaScript()` which provides:
- **Pre-page-JS execution** — scripts run before any page JavaScript (`document_start` equivalent)
- **Automatic iframe coverage** — applies to all frames including ad player iframes
- **SPA navigation persistence** — persists across `pushState`/`replaceState` navigations
- **CSP bypass** — not subject to the page's Content Security Policy
- **Origin scoping** — restricted to `youtube.com` and `m.youtube.com` only

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

1. **Injection timing** — Does `addDocumentStartJavaScript` fire before YouTube's ad init?
2. **Iframe coverage** — Are iframes (ad player frames) covered?
3. **SPA navigation** — Does injection persist across video-to-video navigation?

### Testing the Probe

1. Enable the `youTubeAdBlocking` feature flag
2. Navigate to `youtube.com` in the DDG browser
3. Check logcat for `[DDG-YT-ADBLOCK]` messages:
   - `Injected at X ms` — timing (should be <5ms)
   - `ytInitialData: false` — proves injection beats YouTube init
   - `frame: main/iframe` — frame type detection
   - SPA navigation logs on video clicks

## What's NOT Included (follow-on work)

- Real scriptlets from `content-blocker-extension`
- Remote scriptlet loading from CDN
- `shouldInterceptRequest` ad sub-resource blocking
- Auto-update / version checking
- Production settings UI
- DuckPlayer interaction handling
