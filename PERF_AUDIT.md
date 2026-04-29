# Outrageous performance problems found in the DuckDuckGo Android repo

This is the result of a static audit of the codebase looking for the kind of issues that cause user-visible jank, ANRs, wasted CPU, or wasted memory. Findings are ranked roughly by impact. Every item links to specific files and line ranges.

---

## 1. `runBlocking { ... }` on every single resource load — main- and worker-thread

`WebViewClient.shouldInterceptRequest` and `shouldOverrideUrlLoading` fire many times per page (every script, image, XHR, frame, sub-resource). They are on a critical path. The current code wraps the whole thing in `runBlocking`, which actively parks the calling thread. There are at least four of these on the request path:

```674:686:app/src/main/java/com/duckduckgo/app/browser/BrowserWebViewClient.kt
runBlocking {
    val documentUrl = withContext(dispatcherProvider.main()) { webView.url }
    withContext(dispatcherProvider.main()) {
        loginDetector.onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, request))
    }
    logcat(VERBOSE) { "Intercepting resource ${request.url} type:${request.method} on page $documentUrl" }
    requestInterceptor.shouldIntercept(
        request,
        webView,
        documentUrl?.toUri(),
        webViewClientListener,
    )
}
```

```40:45:app/src/main/java/com/duckduckgo/app/browser/serviceworker/BrowserServiceWorkerClient.kt
override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
    return runBlocking {
        val documentUrl: Uri? = (request.requestHeaders[HEADER_ORIGIN] ?: request.requestHeaders[HEADER_REFERER])?.toUri()
        logcat(VERBOSE) { "Intercepting Service Worker resource ${request.url} type:${request.method} on page $documentUrl" }
        requestInterceptor.shouldInterceptFromServiceWorker(request, documentUrl)
    }
}
```

```77:87:app/src/main/java/com/duckduckgo/app/browser/urlextraction/UrlExtractingWebViewClient.kt
return runBlocking {
    val documentUrl = withContext(dispatcherProvider.main()) { webView.url?.toUri() }
    logcat(VERBOSE) { "Intercepting resource ${request.url} type:${request.method} on page $documentUrl" }
    requestInterceptor.shouldIntercept(
        request,
        webView,
        documentUrl,
        null,
    )
}
```

```226:258:app/src/main/java/com/duckduckgo/app/browser/webview/MaliciousSiteBlockerWebViewIntegration.kt
return runBlocking {
    if (!isEnabled()) {
        return@runBlocking IsMaliciousViewData.Ignored
    }
    // iframes always go through the shouldIntercept method, so we only need to check the main frame here
    if (isForMainFrame) {
        getProcessedOrExempted(url, url, true)?.let {
            return@runBlocking it
        }
        ...
    }
    IsMaliciousViewData.Ignored
}
```

The `BrowserWebViewClient` and `UrlExtractingWebViewClient` versions also bounce twice to the main thread inside the `runBlocking` (`withContext(dispatcherProvider.main()) { webView.url }`). Each main-thread hop adds at least one full UI-thread latency to **every sub-resource on every page**. This is the #1 reason `shouldInterceptRequest` shows up at the top of profiling traces in WebView-heavy apps.

Fix: hoist the URL grab to `onPageStarted`/`onLoadResource` once per navigation, and turn `RequestInterceptor.shouldIntercept` into a non-suspending function (its main consumers are already pre-cached state).

---

## 2. `UriString.cache` — a 250,000-entry LRU keyed on a 32-bit `String.hashCode()`

```30:38:browser-api/src/main/java/com/duckduckgo/app/browser/UriString.kt
class UriString {
    companion object {
        private const val LOCALHOST = "localhost"
        private const val SPACE = " "
        private val webUrlRegex by lazy { PatternsCompat.WEB_URL.toRegex() }
        private val domainRegex by lazy { PatternsCompat.DOMAIN_NAME.toRegex() }
        private val inputQueryCleanupRegex by lazy { "['\"\n]|\\s+".toRegex() }
        private val cache = LruCache<Int, Boolean>(250_000)
```

```66:75:browser-api/src/main/java/com/duckduckgo/app/browser/UriString.kt
fun sameOrSubdomain(
    child: Domain?,
    parent: Domain,
): Boolean {
    child ?: return false
    val hash = (child.value + parent.value).hashCode()
    return cache.get(hash) ?: (parent == child || child.value.endsWith(".${parent.value}")).also {
        cache.put(hash, it)
    }
}
```

Two outrageous things at once:

1. **A 32-bit hash collision returns the cached boolean blind.** There is no `equals` check, no key tuple. Any pair `(child, parent)` whose concatenated hashCode collides with a previously cached pair gets the wrong result. With a 250 000 entry cache the birthday-collision probability is non-trivial (~4·10⁻⁶ per pair, but it is multiplied by ~10⁵ daily lookups). A wrong `true` here means a tracker is treated as first-party and **not blocked**. A wrong `false` means a legitimate request **is blocked**. This is a privacy & correctness bug, not just a perf bug, but it lives inside a hot perf path.
2. **Memory: 250 000 boxed Int → boxed Boolean entries, plus the LRU `LinkedHashMap` overhead.** Conservatively 16 bytes for the boxed `Int` key + 16 bytes for the boxed `Boolean` value + the `HashMap.Node` = ~80 B/entry → **≈20 MB resident** when full, for what is conceptually one bit of information per pair. And it is `static`, so it never goes away.

The non-cached overloads on the same class don't share this defect, which makes the broken one even more startling.

Fix: either remove the cache (`endsWith` on a few domains is cheap), or use a real key (`Pair<Domain, Domain>` → `Boolean`) and a sane size (a few thousand at most).

---

## 3. `RealContentScopeScripts` / `RealWebViewCompatContentScopeScripts` build a fresh `Moshi` for **every** script injection

```215:236:content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/RealContentScopeScripts.kt
private fun getExperimentsKeyValuePair(activeExperiments: List<Toggle>): String {
    return runBlocking {
        val type = Types.newParameterizedType(List::class.java, Experiment::class.java)
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<List<Experiment>> = moshi.adapter(type)
        ...
    }
}
```

```175:199:content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/RealContentScopeScripts.kt
private fun getUserUnprotectedDomainsJson(userUnprotectedDomains: List<String>): String {
    val type = Types.newParameterizedType(MutableList::class.java, String::class.java)
    val moshi = Builder().build()
    val jsonAdapter: JsonAdapter<List<String>> = moshi.adapter(type)
    return jsonAdapter.toJson(userUnprotectedDomains)
}

private fun getUnprotectedTemporaryJson(unprotectedTemporaryExceptions: List<FeatureException>): String {
    val type = Types.newParameterizedType(MutableList::class.java, FeatureException::class.java)
    val moshi = Builder().build()
    val jsonAdapter: JsonAdapter<List<FeatureException>> = moshi.adapter(type)
    return jsonAdapter.toJson(unprotectedTemporaryExceptions)
}
```

`getScript()` is invoked on every page load. It calls `getUserPreferencesJson` → `getExperimentsKeyValuePair`, plus `getUnprotectedTemporaryJson` and `getUserUnprotectedDomainsJson` whenever the inputs change. Each call:

- builds a brand new `Moshi` (loads built-in factories, allocations);
- calls `Types.newParameterizedType(...)` (reflective type construction);
- creates a new `JsonAdapter`.

The class is `@SingleInstanceIn(AppScope::class)`, so a single `private val moshi by lazy { ... }` plus three pre-built adapters would eliminate all of this on the page-load path. The same code is duplicated in `RealWebViewCompatContentScopeScripts.kt`.

Bonus: the same code wraps a synchronous, in-memory transform in `runBlocking { ... }` for no reason — there is no suspending call inside.

```220:237:content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/RealWebViewCompatContentScopeScripts.kt
private fun getExperimentsKeyValuePair(activeExperiments: List<Toggle>): String {
    return runBlocking {
        val type = Types.newParameterizedType(List::class.java, Experiment::class.java)
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<List<Experiment>> = moshi.adapter(type)
        ...
    }
}
```

---

## 4. `BlockListInterceptorApiPlugin.intercept()` calls `runBlocking { ... }` 3-6× per HTTP request

```54:93:app/src/main/java/com/duckduckgo/app/trackerdetection/blocklist/BlockListInterceptorApiPlugin.kt
override fun intercept(chain: Chain): Response {
    if (!okHttpInterceptorRefactorFeature.self().isEnabled()) {
        return interceptLegacy(chain)
    }
    val originalRequest = chain.request()

    val tdsRequired = originalRequest.tag(Invocation::class.java)
        ?.method()
        ?.isAnnotationPresent(TdsRequired::class.java) == true

    if (!tdsRequired) {
        return chain.proceed(originalRequest)
    }

    val activeExperiment = runBlocking {
        inventory.activeTdsFlag().also { it?.enroll() }
    }
    ...
    val path = when {
        runBlocking { activeExperiment.isEnrolledAndEnabled(TREATMENT) } -> config["treatmentUrl"]
        runBlocking { activeExperiment.isEnrolledAndEnabled(CONTROL) } -> config["controlUrl"]
        else -> config["nextUrl"]
    } ?: return chain.proceed(originalRequest)
    ...
}
```

The legacy branch (lines 95–127) does the same thing again, so it's effectively up to six `runBlocking` blocks per outgoing TDS request — and each one is launching a coroutine on `Dispatchers.Default` to call something that ultimately just reads from feature flag storage. Cache the experiment + cohort decisions in a `@Volatile` field that is updated whenever flags change.

`AppTPBlockListInterceptorApiPlugin` (count: 13) has the same pattern.

---

## 5. OkHttp interceptors block the network thread on every request

Every outbound network request goes through these:

```47:64:network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/pixels/VpnLatencyPixelInterceptor.kt
override fun intercept(chain: Chain): Response {
    ...
    val url = originalRequest.url.newBuilder()
        .addQueryParameter(PARAM_LOCATION, getLocationParamValue())
        ...
}
private fun getLocationParamValue(): String {
    return runBlocking {
        if (netPGeoswitchingRepository.getUserPreferredLocation().countryCode != null) {
```

```57:65:network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/configuration/NetpControllerRequestInterceptor.kt
newRequest.addHeader(
    name = "Authorization",
    // this runBlocking is fine as we're already in a background thread
    value = runBlocking { authorizationHeaderValue() },
)
```

```43:51:pir/pir-impl/src/main/java/com/duckduckgo/pir/impl/service/PirAuthInterceptor.kt
return if (authRequired) {
    val accessToken = runBlocking { subscriptions.getAccessToken() }
        ?: throw IOException("Can't obtain access token for requestCan't obtain access token for request")
```

```62:64:subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/auth/AuthInterceptor.kt
private fun getAccessToken(): String? {
    val accessTokenResult = runBlocking { subscriptionsManager.getAccessToken() }
    return (accessTokenResult as? Success)?.accessToken?.takeIf { it.isNotBlank() }
}
```

The "this runBlocking is fine as we're already in a background thread" comment is the giveaway: the OkHttp dispatcher's threads are now serialized on whatever the suspend call decides to do (token refresh, Retrofit call, DB lookup). Enough concurrent network traffic + a slow token refresh = thread-pool exhaustion + dropped requests.

---

## 6. Tracker detector regex compiled per-request, per-rule

```83:114:app/src/main/java/com/duckduckgo/app/trackerdetection/TdsClient.kt
tracker.rules.forEach { rule ->
    val regex = ".*${rule.rule}.*".toRegex()
    if (url.matches(regex)) {
        ...
    }
}
```

`TdsClient.matchesTrackerEntry` is called for every blocked tracker hit on every page (TDS list size: thousands of trackers, many with multiple rules). Every call **recompiles** every rule's regex from scratch — and it wraps it in `.*…*` even though `containsMatchIn` would do the same job without recompilation.

Same anti-pattern in tracker allowlist:

```53:63:privacy-config/privacy-config-impl/src/main/java/com/duckduckgo/privacy/config/impl/features/trackerallowlist/RealTrackerAllowlist.kt
private fun matches(
    url: String,
    documentUrl: String,
    trackerAllowlist: TrackerAllowlistEntity,
): Boolean {
    val cleanedUrl = removePortFromUrl(url)
    return trackerAllowlist.rules.any {
        val regex = ".*${it.rule}.*".toRegex()
        cleanedUrl.matches(regex) && (it.domains.contains("<all>") || it.domains.any { domain -> UriString.sameOrSubdomain(documentUrl, domain) })
    }
}
```

Fix: compile `Regex` once at TDS load time and store it on `TdsRule`/`TrackerAllowlistRule`. This is hot code — every single ad/script/img/font subresource passes through it.

---

## 7. `WebLocalStorageManager` recompiles N×M regexes inside an iteration over every LevelDB entry

```70:105:app/src/main/java/com/duckduckgo/app/browser/weblocalstorage/WebLocalStorageManager.kt
db.iterator().use { iterator ->
    iterator.seekToFirst()

    while (iterator.hasNext()) {
        val entry = iterator.next()
        val key = String(entry.key, StandardCharsets.UTF_8)

        val domainForMatchingAllowedKey =
            getDomainForMatchingAllowedKey(key, domains, matchingRegex)
        ...
    }
}

private fun getDomainForMatchingAllowedKey(
    key: String,
    domains: List<String>,
    matchingRegex: List<String>,
): String? {
    for (domain in domains) {
        val escapedDomain = Regex.escape(domain)
        val regexPatterns = matchingRegex.map { pattern ->
            pattern.replace("{domain}", escapedDomain)
        }
        if (regexPatterns.any { pattern -> Regex(pattern).matches(key) }) {
            return domain
        }
    }
    return null
}
```

For every LevelDB key (potentially tens of thousands), for every allowed domain, the code re-escapes the domain, re-allocates a list, and recompiles each pattern's `Regex`. Compute `regexPatterns: List<Regex>` once per allowed domain before the iterator loop. At a few hundred allowed domains × a few patterns × 50 000 LevelDB keys you are looking at millions of `Regex` compilations on a *fire data* / clear-storage path that is supposed to be fast.

---

## 8. SharedPreferences `commit()` (synchronous fsync) instead of `apply()` on hot paths

`commit()` blocks until the write is fsync'd. `apply()` queues the write asynchronously. The lint rule `NoUseKtx` is suppressed at these sites:

```43:48:content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/features/pagecontext/store/PageContextStore.kt
@SuppressLint("UseKtx")
override suspend fun insertJsonData(jsonData: String): Boolean {
    return preferences.edit().putString(KEY_JSON_DATA, jsonData).commit()
}
```

```46:49:content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/features/apimanipulation/store/ApiManipulationStore.kt
@SuppressLint("UseKtx")
override suspend fun insertJsonData(jsonData: String): Boolean {
    return preferences.edit().putString(KEY_JSON_DATA, jsonData).commit()
}
```

These are called every time the privacy config is downloaded or refreshed, with multi-kB JSON payloads. The KSP-generated stores have the same shape:

```113:118:content-scope-privacy-features/content-scope-privacy-features-ksp/src/main/kotlin/com/duckduckgo/contentscopeprivacyfeatures/ksp/ContentScopePrivacyFeatureProcessor.kt
|    @SuppressLint("UseKtx")
|    fun insertJsonData(jsonData: String): Boolean {
|        val success = preferences.edit().putString("json_data", jsonData).commit()
```

Multiply this across every content-scope feature that uses the generated store and you get a synchronous fsync per feature on every privacy-config refresh.

---

## 9. `LottiePrivacyShieldAnimationHelper.setAnimationView` — `runBlocking` on a UI thread, every privacy-shield change

```45:64:app/src/main/java/com/duckduckgo/app/browser/omnibar/animations/addressbar/LottiePrivacyShieldAnimationHelper.kt
override fun setAnimationView(
    holder: LottieAnimationView,
    privacyShield: PrivacyShield,
    viewMode: ViewMode,
    useLightAnimation: Boolean?,
) {
    val protectedShield: Int
    val protectedShieldDark: Int
    if (viewMode is ViewMode.CustomTab && !omnibarRepository.isNewCustomTabEnabled) {
        protectedShield = R.raw.protected_shield_custom_tab
        protectedShieldDark = R.raw.dark_protected_shield_custom_tab
    } else {
        if (runBlocking { addressBarTrackersAnimationManager.isFeatureEnabled() }) {
```

`setAnimationView` is called every time the omnibar shield state changes (page load, navigation, scroll into a different tab). The feature flag value is constant for the lifetime of the process — caching it once at construction makes this a non-blocking property read.

Same pattern in `AndroidFeaturesHeaderProvider.mapFeature`:

```70:91:app/src/main/java/com/duckduckgo/app/browser/trafficquality/remote/AndroidFeaturesHeaderProvider.kt
private fun mapFeature(feature: String): String? {
    return runBlocking {
        when (feature) {
            CPM_HEADER -> {
                autoconsent.isAutoconsentEnabled().toString()
            }
            ...
        }
    }
}
```

This is invoked from the OkHttp interceptor that adds the `X-Android-Features` header. Every API request, blocking on a coroutine, to read what is essentially three booleans.

---

## 10. `ExperimentFiltersManager.computeFilters` blocks on `subscriptions.isEligible()` for every variant assignment

```46:71:experiments/experiments-impl/src/main/java/com/duckduckgo/experiments/impl/ExperimentFiltersManager.kt
override fun computeFilters(entity: VariantConfig): (AppBuildConfig) -> Boolean {
    ...
    if (entity.filters?.privacyProEligible != null) {
        val isEligible = runBlocking(dispatcherProvider.io()) { subscriptions.isEligible() }
        filters[SUBSCRIPTION_ELIGIBLE] = entity.filters?.privacyProEligible == isEligible
    }
    return { filters.filter { !it.value }.isEmpty() }
}
```

`computeFilters` runs at app start to assign experiment variants. `subscriptions.isEligible()` can hit the network or the encrypted-prefs store. Doing it from `runBlocking` on the startup critical path is a likely contributor to cold-start regressions whenever experiments are evaluated.

---

## 11. `RxPixelSender` does `runBlocking` inside `Single.fromCallable` inside an Rx chain inside a coroutine consumer

```103:150:statistics/statistics-impl/src/main/java/com/duckduckgo/app/statistics/api/RxPixelSender.kt
override fun sendPixel(
    ...
): Single<PixelSender.SendPixelResult> = Single.fromCallable {
    runBlocking {
        if (shouldFirePixel(pixelName, type)) {
            api.fire(...).blockingAwait()
            storePixelFired(pixelName, type)
            PixelSender.SendPixelResult.PIXEL_SENT
        } else {
            PixelSender.SendPixelResult.PIXEL_IGNORED
        }
    }
}

override fun enqueuePixel(
    ...
): Single<PixelSender.EnqueuePixelResult> {
    return Single.fromCallable {
        runBlocking {
            ...
        }
    }
}
```

Every single pixel fire goes through: kotlin coroutine → Rx Single → coroutine builder → Rx blockingAwait. There is one thread doing real work, three threads idling waiting for it. This file alone is responsible for a long tail of pixel-related thread contention. The whole class should be a `suspend fun` with no Rx.

---

## 12. `BrowserTabFragment` (5826 lines) and `BrowserTabViewModel` (5055 lines)

```bash
$ wc -l BrowserTabFragment.kt BrowserTabViewModel.kt BrowserActivity.kt
  5826 BrowserTabFragment.kt
  5055 BrowserTabViewModel.kt
  1722 BrowserActivity.kt
```

This isn't a single perf bug, but it is a perf-relevant architectural cost:

- D8 / R8 takes longer to optimise these files; class-init time grows with the number of injected fields.
- Every change forces a full recompilation of these megafiles, dominating incremental build time.
- They each inject ~80 dependencies (each one a `Provider`/`Lazy`); every fragment instantiation = ~80 graph lookups + reference fields the GC has to walk.
- Memory: a `BrowserTabFragment` instance is enormous. With multi-tab support, the heap dominates.

These should be split along clear boundaries (autofill, omnibar, downloads, subscriptions, privacy dashboard, etc.) into per-feature collaborators. Many features even already have dedicated `*Plugin` interfaces — they just aren't used to break this fragment apart.

---

## 13. `ContributesActivePluginPoint` codegen and `AppTPBlockListInterceptorApiPlugin` compound-blocking

`AppTPBlockListInterceptorApiPlugin` has 13 `runBlocking { ... }` calls in a single interceptor. Each network request can trigger most of them. (See `app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/blocklist/AppTPBlockListInterceptorApiPlugin.kt`.) Same pattern as #4 but worse — and this one is on the AppTP/VPN process critical path.

---

## 14. `FirstPartyCookiesModifier.buildSQLWhereClause` builds an O(n) string by `foldIndexed`

```83:97:cookies/cookies-impl/src/main/java/com/duckduckgo/cookies/impl/features/firstparty/FirstPartyCookiesModifier.kt
return excludedSites.foldIndexed(
    "",
) { pos, acc, site ->
    if (pos == 0) {
        "expires_utc > $timestampThreshold AND $httpOnly = 0 AND host_key != '$site' AND host_key NOT LIKE '%.$site'"
    } else {
        "$acc AND host_key != '$site' AND host_key NOT LIKE '%.$site'"
    }
}
```

This is the classic "string concatenation in a loop" — each iteration allocates a brand new String containing the entire previous accumulator. For N sites this is O(N²). With ~450 sites per chunk that's ~100 000 character copies per chunk; the file says "Max depth is 1000" — for 1000 sites it's quadratically worse. Use `StringBuilder`, or better, parametrise the SQL.

The same code is **also** vulnerable to SQL injection: `'$site'` is interpolated directly into a SQL string. Allowed sites come from remote config. (Mostly off-topic for perf, but worth noting.)

---

## 15. Each `WebTrackerBlocked` insert runs sync on the calling thread

```470:473:app/src/main/java/com/duckduckgo/app/browser/WebViewRequestInterceptor.kt
private fun recordTrackerBlocked(trackingEvent: TrackingEvent) {
    val trackerCompany = trackingEvent.entity?.displayName ?: "Undefined"
    webTrackersBlockedDao.insert(WebTrackerBlocked(trackerUrl = trackingEvent.trackerUrl, trackerCompany = trackerCompany))
}
```

`recordTrackerBlocked` is invoked synchronously inside `getWebResourceResponse`, which is invoked inside the `shouldIntercept` `runBlocking` block. Every blocked tracker = one synchronous Room write on the WebView IO thread. Pages with hundreds of blocked trackers (which is normal on big news sites) get hundreds of synchronous DB writes serialised in front of the page load.

---

## 16. `AnrSupervisor.checkStopped` and `Thread.sleep(2_000)`

```131:141:anrs/anrs-impl/src/main/java/com/duckduckgo/app/anr/AnrSupervisor.kt
@Synchronized
@Throws(InterruptedException::class)
private fun checkStopped() {
    if (stopped.get()) {
        Thread.sleep(2_000)
        if (stopped.get()) {
            throw InterruptedException()
        }
    }
}
```

The ANR supervisor watches for ANRs by holding a synchronised method while sleeping 2 seconds. Coupled with the 5-second `Thread.sleep(ANR_CHALLENGE_PERIOD_MILLIS)` immediately after (line 111), and the fact that the watcher posts to the main thread to challenge it — this is itself a candidate for ANRs in low-memory scenarios. Not high impact, but ironic.

---

## 17. `LottieAnimationView` driven by every privacy-shield update without batching

`LottiePrivacyShieldAnimationHelper.setAnimationView` (item 9) is called every time the omnibar shield changes. There's no debouncing/coalescing, and `holder.tag` is being abused as state storage. On rapid navigation events (e.g. SPA route transitions firing many shield updates), Lottie can leak frames and reset progress unnecessarily.

---

## 18. ContentScopeScripts plugin string concat in a loop

```124:142:content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/RealContentScopeScripts.kt
private fun getPluginParameters(): PluginParameters {
    var config = ""
    var preferences = ""
    val plugins = pluginPoint.getPlugins()
    plugins.forEach { plugin ->
        if (config.isNotEmpty()) {
            config += ","
        }
        config += plugin.config()
        plugin.preferences()?.let { pluginPreferences ->
            if (preferences.isNotEmpty()) {
                preferences += ","
            }
            preferences += pluginPreferences
        }
    }
    return PluginParameters(config, preferences)
}
```

`config += plugin.config()` allocates a new String each time. With ~30 plugins each producing multi-kB JSON, this is yet another O(N²) string-concat path, run on every page load. `joinToString` or `StringBuilder` is the correct shape.

---

## 19. `JsLoginDetector` opens raw resources on UI thread on every navigation if not yet cached

```98:114:app/src/main/java/com/duckduckgo/app/browser/logindetection/DOMLoginDetector.kt
private fun getFunctionsJS(context: Context): String {
    if (!this::functions.isInitialized) {
        functions = context.resources.openRawResource(R.raw.login_form_detection_functions).bufferedReader().use { it.readText() }
    }
    return functions
}

private fun getHandlersJS(context: Context): String {
    if (!this::handlers.isInitialized) {
        handlers = context.resources.openRawResource(R.raw.login_form_detection_handlers).bufferedReader().use { it.readText() }
    }
    return handlers
}
```

Lazy init pattern is fine, but it's not thread safe (`isInitialized` + assignment are not atomic) and the *first* call happens from `onPageStarted` on the UI thread, which means a synchronous resource read + UTF-8 decode on the UI thread on the first page load.

---

## 20. `RealUserAllowListRepository` exposes a mutable `CopyOnWriteArrayList` directly

```59:61:app/src/main/java/com/duckduckgo/app/privacy/db/RealUserAllowListRepository.kt
override fun domainsInUserAllowList(): List<String> {
    return userAllowList
}
```

Callers compare it for equality and iterate it on the request path:

```99:102:content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/RealContentScopeScripts.kt
if (cachedUserUnprotectedDomains != userAllowListRepository.domainsInUserAllowList()) {
    cacheUserUnprotectedDomains(userAllowListRepository.domainsInUserAllowList())
```

`CopyOnWriteArrayList.equals` compares element by element, with snapshot copies, twice per page load. Fine when small; pathological when users have hundreds of allowlisted domains. And because the list is the live one rather than a snapshot, `cachedUserUnprotectedDomains.addAll(userUnprotectedDomains)` (with `userUnprotectedDomains` being the same reference) goes into infinite/inconsistent territory if the underlying list mutates mid-iteration.

---

## Summary of the biggest wins

| # | Where | Likely impact |
|---|---|---|
| 1 | WebView clients `runBlocking` in `shouldInterceptRequest` | Page-load latency, jank |
| 2 | `UriString.cache` 250k LRU keyed on hashCode | 20 MB heap + correctness/privacy bug |
| 3 | Moshi rebuilt every page in ContentScopeScripts | GC pressure, page-load CPU |
| 4 | Block-list interceptor `runBlocking ×6` per request | Network throughput |
| 5 | Auth/latency interceptors `runBlocking` | OkHttp dispatcher exhaustion |
| 6 | `TdsClient` regex compiled per request | CPU per page load |
| 7 | `WebLocalStorageManager` regex compiled per LevelDB key | "Clear data" UX latency |
| 8 | SharedPreferences `commit()` instead of `apply()` | Main-thread fsync ANR risk |

Items 1–8 alone would, if fixed, materially reduce page-load CPU and the chance of ANRs. Items 12 (mega-files) and 6/7 are the largest CPU savings on hot paths.
