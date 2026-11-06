# Unified URL Model — Migration Guide

## Goal

Replace the scattered, String/`Uri`/`HttpUrl`-based URL handling across the app with the semantic
`DaxUrl` / `DaxDomain` model, so URL logic lives in one place and consumers stop re-deriving state
from raw Strings (and stop repeating the parse-and-null-check "dance").

The guiding rule: **never expose a naked `String` where a type with an invariant exists** — hosts
are `DaxDomain`, schemes are `DaxScheme`, host-kind is `DaxDomainType`. Raw Strings only appear at
the construction boundary (`getUrl`/`getDomain`).

## What exists today (phase-1 de-risk slice)

Shipped behind the `unifiedUrlModel` feature flag (`UnifiedUrlModelFeature`):

- **`:unified-url-model-api`** (pure-JVM): `DaxUrl`, `DaxDomain`, `DaxScheme`, `DaxDomainType`,
  `DaxUrlProvider`.
- **`:unified-url-model-impl`**: `RealDaxUrl`, `RealDaxDomain` (each a single class taking a
  `memoize` flag — see below), the `held` property helper, `RealDaxUrlProvider`.
- **End-to-end proof**: `Toggle.getExceptionDomains()` returns the remote-config `exceptions` as
  resolved `DaxDomain`s. Resolution happens in `ToggleImpl` via a `daxDomainResolver` lambda passed
  through `FeatureToggles.Builder`, which the codegen'd `*_ProxyModule` supplies from an injected
  `Lazy<DaxUrlProvider>` — `Lazy` because `RealDaxUrlProvider` reads its own feature flag and eager
  injection would close a Dagger cycle back through that module.

  **`FeatureException` stays an untouched DTO.** It is the Moshi/Room shape for remote config and is
  what content-scope-scripts serializes back out to the JS layer; it carries no `DaxDomain`. Keeping
  the DTO and the domain model separate is what removes the need for a process-wide service-locator
  holder: resolution moved to `ToggleImpl`, which is constructed from the Dagger graph, so there is
  no install point, no startup-ordering window, and no null-until-installed state.

Key properties already established:
- **Memoization A/B** gated by `useUnifiedUrl()`: a single `RealDaxUrl`/`RealDaxDomain` takes a
  `memoize` flag. `memoize = false` recomputes on every access (mirrors today's re-parse-per-call);
  `memoize = true` parses once and caches `domain`/`scheme`/`isWebUrl` and the eTLD+1. The provider
  passes `memoize = useUnifiedUrl`. Both modes return identical results — only the repeated work
  differs — so the flag is a pure performance A/B, and the eventual collapse to "always memoize" is
  deleting the flag/param, not a class. Per-property behavior is chosen by the `held(memoize) { … }`
  helper, so each member is written once (no Legacy/New parallel hierarchy to keep in sync).
- **Construction is locked down**: all impl constructors are `internal`; a `DaxUrl`/`DaxDomain` can
  only be built via `DaxUrlProvider`. `raw`/`value` are module-internal, never on the public API.
- **`getDomain` is lenient** (allows `localhost`/IPs) so the URL-matching hot path keeps working;
  strict needs are served by `DaxDomain.type` rather than a validity gate.

## Operation → model mapping

| Today | Model equivalent | Status |
|---|---|---|
| `uri.host` / `baseHost` / `UriString.host()` | `daxUrl.domain` (`DaxDomain?`) | ✅ done |
| `UriString.sameOrSubdomain(url, exc.domain)` (hot path, ~54 sites) | `daxUrl.matches(exceptionDomain)` / `parentDomain.sameOrSubdomain(child)` | ✅ done |
| `uri.isHttps` / `uri.scheme` (~69 sites) | `daxUrl.isHttps` / `daxUrl.scheme` (`DaxScheme`) | ✅ done |
| `toTldPlusOne()` / `toTldPlusOneOrSelf()` | `DaxDomain.toTldPlusOne()` / `toTldPlusOneOrSelf()` | ✅ done |
| `UriString.isWebUrl(x)` (default) | `daxUrl.isWebUrl` | ✅ done (ported) |
| `UriString.isValidDomain(x)` (allowlist etc.) | `daxDomain.type == REGISTRABLE_DOMAIN` | ⚠️ decide (see below) |
| `uri.path` / `pathSegments` / `lastPathSegment` (~72 sites) | `DaxUrl.path` / `pathSegments` / `lastPathSegment` | ✅ done |
| `uri.getQueryParameter(k)` (~30 sites) | `DaxUrl.queryParameter(k)` / `queryParameterNames` | ✅ done |
| `uri.port` / `query` / `fragment` (few sites) | `DaxUrl.port` (`Int?`, no `-1`) / `query` / `fragment` | ✅ done |
| `uri.authority` (~4 sites) | prefer `domain` + `port` — not added (composite of existing) | ⚠️ skip unless needed |
| `uri.buildUpon()` / raw `Uri` / `toString()` | `DaxUrlProvider` factory ops only — **deliberately not exposed** | 🚫 by design |
| `uri.isLocalUrl` / `isMobileSite` | `DaxUrl.isLocalUrl` / `isMobileSite` (+ `DaxDomainType.IP`) | ✅ done |
| `uri.hasIpHost` | `DaxDomainType.IP` (no dedicated member — zero non-test callers) | ✅ subsumed |
| `uri.toHttps` / `withScheme` | `DaxUrlProvider.withScheme(url, scheme)` | ✅ done |
| `QueryUrlConverter` / `UriString.isWebUrl(x, extractUrlQuery = true)` | `DaxUrlProvider.parseQuery(...)` | ❌ to add (see below) |
| `Domain` value class (`browser-api`) | `DaxDomain` | ❌ replace/delete |

## Raw host (`www`-preserving) — do-not-migrate + review list

`DaxDomain` normalizes and **strips `www`** at construction (matches `baseHost` / `UriString.host()`).
That is correct and load-bearing for matching (`sameOrSubdomain` stays symmetric regardless of which
operand carries `www`). But a handful of sites read the raw `Uri.host` where `www.x` and `x` must
stay distinct. They do **not** map to `DaxDomain`. Triaged:

- **DO NOT MIGRATE — `asLocationPermissionOrigin` (`BrowserTabViewModel:2253`).** This rebuilds a
  **WebView origin string** (`"https://" + host + "/"`) to match the origin WebView hands back in
  `onGeolocationPermissionsShowPrompt` (`BrowserChromeClient:161`). WebView keys geolocation grants
  by exact origin, so stripping `www` would fail to match its key and lose/misapply permissions.
  This wants an **Origin**, not a host — leave it on raw `Uri`. If it ever moves onto the model, add
  a purpose-built `DaxUrlProvider.origin(url): String?` (or a small `DaxOrigin`) that encodes the
  `scheme://host/` contract; do **not** route it through `DaxDomain` and do **not** add a bare
  `fullHost` accessor for it.

- **REVIEW ON MIGRATION (likely oversights — model is *more* correct).**
  - `MaliciousSiteBlockerWebViewIntegration:156` — `documentUri.host == referer.host` is a *same-page*
    heuristic written as naive raw equality; it misses `www` normalization **and** subdomains
    (`example.com` doc vs `cdn.example.com` referer read as foreign). Migrate to `matches` /
    `sameOrSubdomain`, which fixes both. Verify the same-page decision doesn't rely on the stricter
    raw behavior first.
  - `SurveyActivity:208` — `request.url.host == "duckduckgo.com"` targets a known endpoint; not
    *required* to be raw (`baseHost` of `duckduckgo.com` and `www.duckduckgo.com` both collapse to
    `duckduckgo.com`), so `domain` would be strictly more robust. Safe to migrate; just confirm the
    completion URL host.

- **REVIEW ON MIGRATION (incidental `www` handling — low stakes, either works).**
  - `WebNavigationState:74` — host-change → `NewPage` vs `UrlUpdated`. On raw host, `www.x → x`
    classifies as a new page load; on `baseHost` it wouldn't. No evidence this was deliberate.
  - `BrowserTabViewModel:1729` (`iconReceived`) — favicon guard `currentUrl.host != visitedUrl.host`;
    raw host rejects a favicon served for the non-`www` variant of the current host.
  Both would arguably behave better on `baseHost`/`domain`; migrate with a quick behavioral check,
  don't assume raw was intended.

## Required additions before broad migration

1. **Query → URL conversion (`parseQuery`).** The omnibar/share-sheet path
   (`BrowserActivity` → `OmnibarEntryConverter.convertQueryToUrl(..., extractUrlFromQuery = true)`
   → `QueryUrlConverter` → `UriString.isWebUrl(x, extractUrlQuery = true)`) turns free-form text
   into a navigable URL. This is a layer *above* `DaxUrl` (extraction happens before you have a URL
   to wrap), so it must **not** be a flag on `DaxUrl.isWebUrl`. Add a provider entry point:
   ```kotlin
   // DaxUrlProvider
   fun parseQuery(query: String, extractUrlFromQuery: Boolean = false): DaxUrl?
   ```
   internally doing the `extractUrl` (clean whitespace/quotes → regex-scan → single/first-http) +
   validate dance, returning `null` for "treat as search". This is what `QueryUrlConverter` migrates onto.

2. **Phase-2 `DaxUrl` members** (path, query, local/IP/mobile classification). Add to the interface
   and implement once in `RealDaxUrl` — cheap accessors as plain getters off `uri`; anything worth
   caching as `by held(memoize) { … }` so it follows the A/B mode automatically.

3. **Strict domain validation for user input.** `AllowListViewModel`, `FireproofWebsiteRepositoryImpl`,
   `AuthCookiesAllowedDomainsRepository` validate a user/stored domain String. Two viable shapes:
   - **Preferred:** consumers use `getDomain(candidate)?.takeIf { it.type == REGISTRABLE_DOMAIN }`.
   - Or add an explicit `DaxUrlProvider.getValidDomain(candidate): DaxDomain?` strict factory.
   Do **not** tighten `getDomain` itself — it must stay lenient for URL matching.

4. **IPv6 classification — at parity, no work needed now.** `DaxDomainType` classifies IPv4
   (`isIpv4Literal`) → `IP`; IPv6 literals fall into `OTHER`. This matches the current URL layer:
   `Uri.hasIpHost` is IPv4-only too, and no browser URL code classifies IPv6 hosts. The one
   IPv6-aware URL util is `UriExtension.isLocalUrl` (via `InetAddress`), which phase-2
   `DaxUrl.isLocalUrl` should **delegate to** rather than reimplement. (The VPN/network-protection
   modules handle IPv6, but that's tunnel config, unrelated to URL classification.)

5. **`UriString` convergence, then removal.** Long-term, `UriString.isWebUrl`/`isValidDomain`/
   `sameOrSubdomain`/`host` should delegate to the model (single source of truth), then `UriString`
   and the `Domain` value class are deleted. The model owning `isWebUrl` (already ported) is step one.

## Migration mechanics

- **Flag-gated, per-feature rollout.** Migrate one feature at a time; `useUnifiedUrl()` selects
  recompute mode (`memoize = false`, parity) vs memoize mode (`memoize = true`). Both modes must
  produce identical results — only performance differs.
- **DTO and domain model stay separate.** `FeatureException` (and every `*Repository` exception
  entity) is the Moshi/Room persistence shape and keeps its `domain: String`. It gains nothing — no
  `DaxDomain` field, computed or stored. Content-scope-scripts serializes `List<FeatureException>`
  straight back out to the JS layer, so the DTO must stay exactly the wire shape.
- **Resolution happens in the producer, which is always injectable.** Every place that builds
  exceptions is either a Dagger `@Provides` or an `@Inject` class, so it can take `DaxUrlProvider`
  directly. For remote-config toggles that producer is `ToggleImpl`, fed by a `daxDomainResolver`
  lambda on `FeatureToggles.Builder` that the codegen'd `*_ProxyModule` supplies. For the legacy
  per-feature Room repositories it is the repository's own `@Provides`. There is no service-locator
  holder, no install point, and therefore no window in which a domain resolves to null because
  startup hadn't got far enough.
- **Consumers read `DaxDomain`, never a String.** `Toggle.getExceptionDomains(): List<DaxDomain>`
  replaces `getExceptions()` for matching; `getExceptions()` is `@Deprecated` and goes away once the
  last matcher is migrated. Legacy repositories expose their exceptions as `List<DaxDomain>` when
  their feature migrates. Entries whose `domain` has no resolvable host are dropped at the boundary
  (`mapNotNull`), so consumers never null-check a domain.
- **Caching a resolved `DaxDomain` is fine and expected.** Because resolution is eager at the
  producer, a resolved list is a stable snapshot of a config version — repositories should hold
  `List<DaxDomain>` in memory and re-resolve only when config changes. `ToggleImpl` memoizes its
  resolved list against the `CachedToggleStore` `State` instance, so a config write invalidates it
  automatically.
- **Dependency direction.** The url-model is low-level. Higher-level code (`browser-api`'s
  `UriString`, `app`'s `QueryUrlConverter`) migrates to depend on / delegate to it — never the
  reverse. Do not add a `browser-api` dependency to `:unified-url-model-impl`.

## Risks / watch-items

- **Hot-path performance.** The `matches(DaxDomain)` overload is load-bearing: exception domains
  must be pre-parsed `DaxDomain`s (resolved once at the producer) so `exceptions.any { … }` doesn't
  re-parse per iteration. Keep the memoizing mode (`memoize = true`) on the hot path.
- **Migration boundary: String-based sites vs `Uri`-based sites.** The model is a *String*
  abstraction (pure-JVM api → `getUrl(String)`), so "recompute mode == today" only holds where the
  current code was String-based:
  - **String-holding sites** (most of the hot path — `sameOrSubdomain(url, domain)`, `(host, domain)`,
    `(domain, tracker.domain)`): today re-parses per call, so recompute mode faithfully reproduces
    today, memoize mode improves it, and flag-off is a clean rollback. Migrate freely.
  - **`Uri`-holding sites** (fewer — `UriString`'s `Uri` overloads / `uri.host` / `sameOrSubdomainPair(Uri, Uri)`):
    today parses zero times; the model forces a `uri.toString()` round-trip re-parse. Here **recompute
    mode is already a regression vs today** (not a faithful baseline), and memoize mode only breaks even
    after the first cached parse. Flag-off is *not* a clean rollback for these.

  Caching the `Uri` in recompute mode does **not** resolve this — it would soften the `Uri`-site
  regression but break String-site fidelity (recompute mode would become faster than today there). The
  two site types want opposite things from recompute mode, so the answer is to split by site type, not
  to change the mode. Recompute mode stays re-parsing (it's the honest "today" arm for String sites).

  **Recommendation:** migrate String-holding sites first (the bulk of the value; recompute = today). For
  `Uri`-holding sites, either leave them on the `Uri` path, or — only if enough of them justify it —
  add a `Uri` entry point: a second, Android-level provider interface that accepts `Uri` (the
  String-based `DaxUrlProvider` stays in the pure-JVM api for `feature-toggles-api`).
  That's the only thing that truly removes the round-trip parse.
- **`RealDaxUrlProvider` no longer caches.** The bounded LRU was removed: the provider builds a fresh
  model per `getUrl`/`getDomain` call, and reuse is the caller's job. The hot-path caching layer is now
  **the producer-side resolved list** (exceptions resolved once per config version and held) plus
  resolving the incoming (child) URL once at the match boundary rather than per exception. If profiling
  later shows churn on true String-entry points you don't own (e.g. resource URLs handed over by the
  WebView), reintroduce a small dedup cache *there*, not as a blanket provider LRU.
- **Early-load repositories are now safe by construction.** `RealMaliciousSiteProtectionRCRepository`
  loads exceptions in an `init{}` IO coroutine and caches into a `@Volatile` field refreshed only on
  `onPrivacyConfigDownloaded()`. Under the previous locator design that was a live hazard (it could
  read before install and pin null forever). With resolution injected into the producer, the provider
  is a constructor dependency — it cannot be absent when the load runs — so an `init{}`-block load
  resolves correctly on the first pass.
- **`UriString`'s LRU keys on `(child+parent).hashCode()`** — a hash used as the key, collision-prone
  (pre-existing latent bug). Fix when consolidating caching into the model.
- **Behavior parity.** `getDomain` intentionally allows `localhost`/IPs; strict `isValidDomain`
  (rejects them) is opt-in via `type`. Don't silently tighten during migration.
- **Dagger cycle.** `RealDaxUrlProvider` injects `UnifiedUrlModelFeature` to read its own flag, and
  every codegen'd `*_ProxyModule` now injects `DaxUrlProvider`. That is a cycle unless the generated
  module takes `Lazy<DaxUrlProvider>` — it does. Anything else that injects `DaxUrlProvider` into a
  feature-toggle provision point must do the same.

## Suggested order

1. Add phase-2 `DaxUrl` members (path/query/type helpers). (The provider LRU has been removed;
   caching is now producer-side: exceptions resolved once per config version — see risks.)
2. Migrate the hot-path exception matchers (privacy-config: https, contentblocking, trackingparameters,
   amplinks, gpc; request-filterer; TDS; cookies; autofill) to `daxUrl.matches(daxDomain)`.
3. Migrate user-input validation (allowlist, fireproofing, auth-cookies) to `type`/`getValidDomain`.
4. Add `parseQuery` and migrate `QueryUrlConverter` / omnibar / share-sheet.
5. Point `UriString` + `Domain` at the model; delete once no direct consumers remain.
