# Spike — Resolving feature-flag exceptions onto the unified URL model

**Status:** draft · **Owner:** @cradoiu · **Reviewer:** Cris B
**Related:** [`migration.md`](migration.md) · Unified URL Model Sync (Asana, bi-weekly)

> Purpose of this document: capture *why* the current shape of `Toggle.getExceptionDomains()` looks
> the way it does, and what we rejected getting there — so the tech design can reference it instead
> of re-deriving the constraints. Sections marked **TODO** are still to be filled in.

---

## 1. Problem statement

The unified URL model (`DaxUrl` / `DaxDomain`) exists to remove the repeated
`String → Uri → host → String` round-tripping on the page-load hot path. The single biggest
consumer of that hot path is **feature-flag exception matching**: `~54` call sites doing
`UriString.sameOrSubdomain(url, exception.domain)`, once per exception, per navigation.

To make those matchers cheap, the exception domains have to be **pre-parsed `DaxDomain`s**, resolved
once per config version — not re-parsed inside `exceptions.any { … }`.

That is where the difficulty starts, and it comes from three constraints that pull against each
other:

| # | Constraint | Consequence |
|---|---|---|
| C1 | `FeatureException` lives in `:feature-toggles-api` — a pure-JVM module, no Dagger, no Android | Anything it references must also be pure-JVM and non-injectable |
| C2 | `FeatureException` is the **Moshi/Room wire shape**, and content-scope-scripts serializes `List<FeatureException>` straight back out to the JS layer | The DTO cannot change shape, and cannot gain polymorphic fields |
| C3 | `RealDaxUrlProvider` reads its own remote feature flag (`UnifiedUrlModelFeature`) to pick recompute vs memoize mode | Provider → toggle → provider is a **dependency cycle** |

C3 is the circular dependency we hit first: the URL model is behind a feature flag, and the feature
flag machinery is exactly what wants to consume the URL model.

---

## 2. Options considered

### Option A — polymorphic URL type on the DTO (interface + legacy/new impls + provider)

**Shape.** `DaxUrl` as an interface with two implementations (`LegacyDaxUrl` recomputing per access,
`NewDaxUrl` memoizing), selected by a provider that reads the feature flag. `FeatureException` gains
a field of the interface type so exceptions carry a resolved URL.

**Why we tried it.** It puts the resolved domain exactly where consumers need it (on the exception
itself), and the flag choice is a single decision at construction time.

**Why we rejected it.**

- **Moshi cannot deserialize an interface field.** With a polymorphic type on the DTO, Moshi has no
  way to know which concrete class to instantiate, so **every** place that parses or serializes
  exceptions needs a custom `JsonAdapter` registered — and there are many, across privacy-config,
  per-feature Room repositories, and the C-S-S boundary. Miss one and it fails at runtime, not
  compile time.
- It violates C2 directly: the DTO stops being the wire shape.
- Two parallel implementations of every member is a maintenance hazard — the two arms drift, and the
  flag becomes impossible to delete cleanly (you delete a *class*, not a *parameter*).
  → This part we fixed independently: one `RealDaxUrl` taking a `memoize: Boolean`, with per-member
  behaviour chosen by the `held(memoize) { … }` helper. Collapsing the flag later is deleting a
  parameter. (Agreed in the 24 Jul sync: *"share patch with the single Url class using a delegate …
  instead of a hierarchy with 2 different implementations"*.)

### Option B — process-wide service locator (`DaxUrlResolver`)

**Shape.** An `object DaxUrlResolver` holding a `@Volatile DaxUrlProvider?`, installed once at
startup from `DuckDuckGoApplication.onMainProcessCreate` (plus a `MainProcessLifecycleObserver`
safety net). `FeatureException.daxDomain` becomes a *computed property* reading through the locator.

This satisfies C1 (a data class can't be injected, but it *can* read a global) and dodges C3 (the
provider is resolved lazily, at first use, not at graph construction).

**Why we rejected it.**

- **Null-until-installed.** Any read before install returns `null`. Repositories that load and cache
  exceptions in an `init{}` IO coroutine — e.g. `RealMaliciousSiteProtectionRCRepository`, which
  caches into a `@Volatile` field refreshed only on `onPrivacyConfigDownloaded()` — could pin `null`
  for the whole session. Caching the *whole* `FeatureException` is safe; caching only the resolved
  domain is the hazard. (Documented as an action item in the 24 Jul sync.)
- Mitigations all leak: cache-only-non-null-and-retry, install-early-then-reinstall-from-a-lifecycle-observer,
  `reset()` for tests. None of it is load-bearing for correctness, which is precisely the smell —
  a lot of machinery to shrink a window that shouldn't exist.
- **Process-global mutable state** is untestable without explicit teardown, and leaks across tests.
- Flagged in the 7 Aug sync: *"the service locator approach for building the feature exceptions is
  not the best one so we will explore more what alternatives we have."*

Remnants still in the working tree: `DaxUrlResolver.kt`, `DaxUrlResolverInitializer.kt`.
**TODO:** delete both before the design lands (also drops a stray `println`).

### Option C — resolve in the producer; DTO untouched *(current, chosen)*

**Shape.**

- `FeatureException` stays an **untouched DTO** — `domain: String`, no `DaxDomain`, stored or
  computed. C2 satisfied, no Moshi adapters anywhere.
- `Toggle` gains `fun getExceptionDomains(): List<DaxDomain>` alongside a now-`@Deprecated`
  `getExceptions()`. The two coexist until the last matcher migrates.
- Resolution happens in `ToggleImpl` — which *is* built from the Dagger graph — via a
  `daxDomainResolver: (String) -> DaxDomain?` lambda threaded through `FeatureToggles.Builder`.
- The codegen'd `*_ProxyModule` (see `ContributesRemoteFeatureProcessor`) supplies that lambda from
  an injected **`Lazy<DaxUrlProvider>`**. `Lazy` is what breaks C3: eager injection would close the
  cycle back through the module that provides the flag the provider reads.
- Entries with no resolvable host are dropped at the boundary (`mapNotNull`), so consumers never
  null-check a domain.
- The resolved list is **memoized against the `State` instance**, not a timestamp: `store` is a
  `CachedToggleStore` returning the same `State` until a config write invalidates it, so reference
  identity is a self-invalidating cache key. Held as one `Pair` so a reader can't observe a `State`
  paired with another `State`'s domains; the unguarded write costs at most a duplicate resolve.

**Why it wins.**

- No service locator ⇒ **no install point, no startup-ordering window, no null-until-installed**.
  An `init{}`-block load resolves correctly on its first pass, because the provider is a constructor
  dependency and cannot be absent.
- No Moshi adapters, no DTO change, no C-S-S impact.
- Producer-side caching is where the hot-path win actually lives: exceptions resolved once per config
  version, and the incoming (child) URL resolved once at the match boundary rather than per
  exception.
- Deletable end-state: when the flag goes, `getExceptions()` and the `memoize` parameter go with it.

Cris B's framing (7 Aug): *"feature-toggles resolves the RC flag internally and exposes a new
`getExceptionDomains(): List<DaxDomain>`, which doesn't interfere with Moshi … since there's no SL,
there's no timing issue with caching exceptions on init."*

---

## 3. Comparison

| | A — polymorphic DTO field | B — service locator | C — resolve in producer |
|---|---|---|---|
| Moshi / Room impact | ❌ adapter at every parse site | ✅ none | ✅ none |
| C-S-S wire shape preserved | ❌ | ✅ | ✅ |
| Startup-ordering hazard | ✅ none | ❌ null-until-installed | ✅ none |
| Global mutable state | ✅ none | ❌ yes | ✅ none |
| Breaks the flag cycle (C3) | partially | ✅ (lazily) | ✅ (`Lazy<DaxUrlProvider>`) |
| Testability | adapters everywhere | needs `reset()` teardown | plain constructor injection |
| Cost to delete the flag later | delete a class hierarchy | delete the locator + installers | delete a parameter |

---

## 4. Open questions / follow-ups

- **Do not expose resolver *and* provider at once.** Agreed we should avoid two public entry points
  for the same thing; possibly an internal-api module. Explicitly time-boxed — *"no overthinking,
  end goal is to remove it as soon as we don't need feature flags."*
  **TODO:** decide the shape.
- **Raw exception strings for C-S-S.** Today exceptions are parsed → stored → stringified again to
  be handed to content-scope-scripts. There may already be a proposal to expose the raw string
  straight off the toggle. **TODO:** confirm with Youssef; would remove a whole conversion hop.
- **V1 scope: String + `android.net.Uri` only.** OkHttp `HttpUrl` deferred, along with checking
  whether the several top-private-domain implementations are actually equivalent (IPs,
  public-suffix handling).
- **`Uri`-holding call sites are not a clean rollback.** For sites that already hold a `Uri`,
  recompute mode is a *regression* vs today (it forces a `toString()` round-trip re-parse), so
  flag-off isn't a faithful baseline there. See the risks section of `migration.md`.
  **TODO:** decide — leave them on `Uri`, or add an Android-level `Uri` entry point.
- **URL manipulation/matching utilities.** Agreed to build a dedicated utilities interface rather
  than exposing generic string operations. **TODO:** extract it out of the malicious-site-protection
  integration patch.
- **Workflow.** Shift from patches to branches once the tech design lands.

---

## 5. Draft Asana update

> **TODO:** trim before posting.
>
> Spike on how feature-flag exceptions get onto the unified URL model is done. Three options:
>
> 1. **Polymorphic URL type on `FeatureException`** — rejected: Moshi can't deserialize an interface
>    field, so every exception parse/serialize site would need a custom `JsonAdapter`, and the DTO
>    stops being the C-S-S wire shape.
> 2. **Process-wide service locator** (`DaxUrlResolver.install(...)` + computed property on the DTO)
>    — rejected: resolves to `null` until installed, which is a real hazard for repositories caching
>    resolved domains from `init{}`; plus global mutable state and test leakage.
> 3. **Resolve in the producer** *(chosen)* — `FeatureException` stays an untouched DTO;
>    `Toggle.getExceptionDomains(): List<DaxDomain>` resolves inside `ToggleImpl` via a resolver
>    lambda that the codegen'd `*_ProxyModule` supplies from a `Lazy<DaxUrlProvider>` (`Lazy` breaks
>    the flag→provider→flag cycle). No install point, no startup window, no Moshi changes. The
>    resolved list is memoized against the `CachedToggleStore` `State` identity, so a config write
>    invalidates it for free.
>
> Next: tech design doc, then move from patches to branches.
