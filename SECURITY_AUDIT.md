# Security Audit Report

**Date:** 2026-04-13
**Scope:** Static analysis of production source code (`src/main` trees, excluding test sources)

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 4 |
| High | 4 |
| Medium | 8 |
| Low | 6 |

---

## Critical Findings

### C1. Auth Token Logged in Plaintext

**File:** `subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/SubscriptionsManager.kt:1230`

```kotlin
logcat { "Subs auth token is ${authRepository.getAuthToken()}" }
```

The full subscription auth token is written to logcat. On rooted devices or via ADB, any app with `READ_LOGS` permission (or the user) can capture this token and impersonate the subscription session.

**Recommendation:** Remove the token value from the log statement. Log a boolean indicating presence/absence or a truncated hash instead.

---

### C2. HTTP Authorization Headers Logged in Full

**File:** `network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/configuration/NetpControllerRequestInterceptor.kt:71`

```kotlin
chain.proceed(
    newRequest.build().also { logcat { "headers: ${it.headers}" } },
)
```

After adding an `Authorization: Bearer <token>` header (and on internal builds, `NetP-Debug-Code`), the interceptor logs the complete header set. This exposes bearer tokens in logcat.

**Recommendation:** Remove the header dump or redact `Authorization` and other sensitive headers before logging.

---

### C3. X-Auth-Token Header Logged in Full

**File:** `malicious-site-protection/malicious-site-protection-impl/src/main/kotlin/com/duckduckgo/malicioussiteprotection/impl/data/network/MaliciousSiteProtectionRequestInterceptor.kt:51`

```kotlin
newRequest.build().also { logcat { "headers: ${it.headers}" } },
```

Same pattern as C2. The `X-Auth-Token` value (a build-time constant) is logged along with all other headers.

**Recommendation:** Remove the header dump or redact sensitive header values before logging.

---

### C4. WireGuard Private Key Stored in Unencrypted SharedPreferences

**File:** `network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/configuration/WgTunnel.kt:254-272`

```kotlin
private val prefs: SharedPreferences by lazy {
    sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = false)
}

var wireguardConfig: Config?
    ...
    set(value) {
        prefs.edit(commit = true) { putString(KEY_WG_CONFIG, value?.toWgQuickString()) }
    }
```

The entire WireGuard config (including the long-term private key) is serialized to `wg-quick` format and stored in plaintext SharedPreferences. On a rooted device or via backup extraction, this key can be recovered and used to impersonate the VPN client.

**Recommendation:** Use `EncryptedSharedPreferences` (as `SyncStore` does) or store the private key in the Android Keystore.

---

## High Findings

### H1. VPN Auth Token Stored in Unencrypted SharedPreferences

**File:** `network-protection/network-protection-store/src/main/java/com/duckduckgo/networkprotection/store/NetpDataStore.kt:42-52`

```kotlin
override var authToken: String?
    get() = preferences.getString(KEY_AUTH_TOKEN, null)
    set(value) {
        preferences.edit(commit = true) { putString(KEY_AUTH_TOKEN, value) }
    }
```

The VPN auth token is stored in unencrypted SharedPreferences. Other stores in the codebase (e.g. `SyncStore`) correctly use `EncryptedSharedPreferences` for similar secrets.

**Recommendation:** Migrate to `getEncryptedSharedPreferences` or store the token using the Android Keystore.

---

### ~~H2. SQL String Interpolation in Cookie Modifier~~ (Downgraded to Low — code hygiene only)

**File:** `cookies/cookies-impl/src/main/java/com/duckduckgo/cookies/impl/features/firstparty/FirstPartyCookiesModifier.kt:83-97`

```kotlin
private fun buildSQLWhereClause(..., excludedSites: List<String>): String {
    return excludedSites.foldIndexed("") { pos, acc, site ->
        if (pos == 0) {
            "... AND host_key != '$site' AND host_key NOT LIKE '%.$site'"
        } else { ... }
    }
}
```

Host strings are interpolated directly into SQL `WHERE` clauses without parameterization. However, **this is not realistically exploitable:**

- **User-driven sources** (fireproof sites, user allowlist) store `Uri.host` or domains validated by `UriString.isValidDomain()` (DOMAIN_NAME regex). Single quotes are not valid in hostnames and won't appear in these values.
- **Remote config sources** (cookie exceptions, unprotected temporary) are server-controlled JSON. Exploiting this would require a compromised backend, at which point cookie DB manipulation is moot.

The sibling class `SQLCookieRemover` correctly uses `?` placeholders, so this is inconsistent style rather than a vulnerability.

**Recommendation:** Use parameterized queries for consistency and defense-in-depth, but this is a code hygiene issue, not an exploitable vulnerability.

---

### H3. SQL Injection in Database Migration

**File:** `app/src/main/java/com/duckduckgo/app/global/db/AppDatabase.kt:213`

```kotlin
database.execSQL("UPDATE `tabs` SET position=$index where `tabId` = \"$tabId\"")
```

In migration 4-to-5, `tabId` values read from the database are interpolated directly into an `execSQL` call. A crafted `tabId` containing double quotes could break or alter the SQL statement.

**Recommendation:** Use `compileStatement` with `?` binding or `ContentValues`-based updates.

---

### H4. Path Traversal via Unsanitized URI lastPathSegment

**Files:**
- `app/src/main/java/com/duckduckgo/app/browser/filechooser/capture/postprocess/MediaCaptureImageMover.kt:44-46`
- `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/filechooser/capture/postprocess/MediaCaptureImageMover.kt:44-46`

```kotlin
val filename = contentUri.lastPathSegment ?: return@withContext null
val newDestinationFile = File(newDestinationDirectory, filename)
```

`lastPathSegment` from a content URI is used directly as a filename without sanitization. If it contains `../` sequences or path separators, the resulting `File` could resolve outside the intended `browser-uploads` cache directory.

**Recommendation:** Sanitize the filename — strip path separators, reject `..` sequences, and validate that `newDestinationFile.canonicalPath` starts with the intended parent directory.

---

### H5. Autofill JSON with Potential Credentials Logged on Parse Failure

**File:** `autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/jsbridge/request/AutofillRequestParser.kt:59`

```kotlin
.onFailure { logcat(WARN) { "Failed to parse autofill JSON for AutofillDataRequest ${it.asLog()}\n$request" } }
```

The full raw JSON `request` from the autofill JS bridge is logged when parsing fails. This payload can contain usernames, passwords, and form field data.

**Recommendation:** Remove the `$request` from the log statement or log only non-sensitive metadata (message type, length).

---

## Medium Findings

### M1. Exported BrowserActivity Accepts Powerful Extras Without Caller Verification

**File:** `app/src/main/AndroidManifest.xml:325-331` and `app/src/main/java/com/duckduckgo/app/browser/BrowserActivity.kt:645-755`

`BrowserActivity` is `exported="true"` without an intent filter. Any app can launch it with extras like `PERFORM_FIRE_ON_ENTRY_EXTRA` (triggers full data clearing), `OPEN_DUCK_CHAT` + `DUCK_CHAT_URL` (opens arbitrary URL in Duck Chat), or `OPEN_EXISTING_TAB_ID_EXTRA` (switches to a specific tab). There is no caller identity verification.

**Recommendation:** Either set `exported="false"` and route external launches through `IntentDispatcherActivity` (which marks traffic as external), or add signature-level permission checks on sensitive extras.

---

### M2. Global Cleartext Traffic Permitted in Network Security Config

**Files:**
- `app/src/play/res/xml/network_security_config.xml:26`
- `app/src/fdroid/res/xml/network_security_config.xml:26`
- `app/src/internal/res/xml/network_security_config.xml:26`

```xml
<base-config cleartextTrafficPermitted="true">
```

All three build flavors allow cleartext HTTP globally. While intentional for a browser (users navigate to `http://` sites), this also means any accidental `http://` API call or SDK request is not blocked by the network security policy.

**Recommendation:** Use `<domain-config>` to restrict cleartext traffic to specific domains if possible, or at least document this as an accepted risk with monitoring for unintended HTTP API calls.

---

### M3. Internal Build Trusts User-Installed CAs in base-config

**File:** `app/src/internal/res/xml/network_security_config.xml:31`

```xml
<base-config cleartextTrafficPermitted="true">
    <trust-anchors>
        ...
        <certificates src="user" />
    </trust-anchors>
</base-config>
```

Unlike Play/F-Droid flavors, the internal build includes user-installed CAs in the default trust configuration (not just `debug-overrides`). This allows MITM via a user-installed CA on internal builds.

**Recommendation:** If internal builds are distributed to a wide audience, move user CAs back to `debug-overrides`. If this is intentional for development, document the rationale.

---

### M4. WebView SSL Error Auto-Proceed for Custom Trust Store

**File:** `app/src/main/java/com/duckduckgo/app/browser/BrowserWebViewClient.kt:736-758`

```kotlin
if (trusted is CertificateValidationState.TrustedChain) {
    handler.proceed()
}
```

When a WebView SSL error occurs with `SSL_UNTRUSTED`, the code checks a custom `trustedCertificateStore` and automatically proceeds if it validates. This bypasses the standard Android certificate validation. The security of this path depends entirely on `trustedCertificateStore`'s implementation — if it is too permissive, it enables MITM.

**Recommendation:** Audit `TrustedCertificateStore.validateSslCertificateChain()` to ensure it implements strict chain validation. Consider logging/telemetry when this path is triggered to monitor for abuse.

---

### M5. Mixed Content Mode Set to COMPATIBILITY_MODE Globally

**Files:** Multiple WebView configurations across the app (e.g., `WebViewActivity.kt:100`, `BrowserTabFragment.kt`, `SubscriptionsWebViewActivity.kt`, etc.)

```kotlin
mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
```

All WebViews use `MIXED_CONTENT_COMPATIBILITY_MODE` which allows some insecure content on HTTPS pages (e.g., passive mixed content like images). This weakens HTTPS guarantees.

**Recommendation:** Use `MIXED_CONTENT_NEVER_ALLOW` where feasible, particularly for sensitive WebViews (subscriptions, email protection, PIR dashboard).

---

### M6. ContentScopeScripts JS Messaging Has Empty allowedDomains (Global Allow)

**File:** `content-scope-scripts/content-scope-scripts-impl/src/main/java/com/duckduckgo/contentscopescripts/impl/messaging/ContentScopeScriptsJsMessaging.kt:62,77`

```kotlin
override val allowedDomains: List<String> = emptyList()
```

With `isUrlAllowed()`, an empty list means **all domains are allowed** at the messaging layer. While individual handlers apply their own `allowedDomains`, the `addDebugFlag` method bypasses handler-level checks (lines 83-91) and is forwarded for **all** features.

**Recommendation:** Review whether `addDebugFlag` should also be subject to per-handler domain restrictions. Consider making the empty-means-allow-all semantics explicit and documented.

---

### M7. Broad FileProvider Path Configuration

**File:** `app/src/main/res/xml/provider_paths.xml:19-21`

```xml
<external-path
    name="external_files"
    path="." />
```

The FileProvider exposes the entire external storage root under the `external_files` name. While the provider itself is `exported="false"`, any code path that grants a URI under this root could inadvertently share sensitive files.

**Recommendation:** Narrow the `path` to specific subdirectories that actually need to be shared (e.g., `Download/`).

---

### M8. Hardcoded JS Messaging Secret Shared Across Features

**Files:** Multiple — `SubscriptionMessagingInterface.kt:133`, `ItrMessagingInterface.kt:102`, `DuckPlayerScriptsJsMessaging.kt:109`, `ChatSuggestionsJsMessaging.kt:43`, `DuckChatDeleterJsMessaging.kt:46`, `PirDashboardWebConstants.kt:28`

```kotlin
override val secret: String = "duckduckgo-android-messaging-secret"
```

A single hardcoded string is used as the "secret" for WebView-to-native messaging across multiple features. This value is trivially extractable from the APK. While `RealContentScopeScripts` correctly generates per-instance random secrets via `UUID`, these other interfaces use the static value.

**Recommendation:** Follow the `RealContentScopeScripts` pattern — generate a unique random secret per WebView instance for all messaging interfaces.

---

## Low Findings

### L1. SHA-1 Hash Helper Exposed as Public API

**File:** `common/common-utils/src/main/java/com/duckduckgo/common/utils/HashUtilities.kt:32`

SHA-1 is available as a public extension property. While no current production callers were found, the API surface risks future misuse for integrity or authentication where SHA-256 should be used.

**Recommendation:** Deprecate the `sha1` extension property and add a lint warning.

---

### L2. kotlin.random.Random Used for Feature Rollout Percentiles

**File:** `feature-toggles/feature-toggles-api/src/main/java/com/duckduckgo/feature/toggles/api/FeatureToggles.kt:591`

```kotlin
val random = Random.nextDouble(100.0)
```

`kotlin.random.Random` is not cryptographically secure. For feature rollout thresholds that are persisted and determine feature access, a predictable RNG could theoretically allow gaming of rollout assignments.

**Recommendation:** Use `SecureRandom` for rollout threshold generation if fairness/unpredictability is a concern.

---

### L3. PendingIntent with FLAG_MUTABLE in Autofill

**File:** `autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/service/AutofillProviderSuggestions.kt:232-255`

Two `PendingIntent.getActivity()` calls use `FLAG_MUTABLE`. While often required for autofill inline suggestions, mutable PendingIntents can be manipulated if the underlying intent is not fully explicit.

**Recommendation:** Verify that the `Intent` objects are fully explicit (component set, no implicit resolution) and document why `FLAG_MUTABLE` is required.

---

### L4. Downloads to Public External Storage

**Files:** `downloads/downloads-api/src/main/java/com/duckduckgo/downloads/api/FileDownloader.kt:35-37`, `app/src/main/java/com/duckduckgo/app/buildconfig/RealAppBuildConfig.kt:123`

Downloaded files default to `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` which is world-readable. This is standard browser behavior but worth noting if sensitive documents are downloaded.

**Recommendation:** Document as accepted risk. Consider offering users an option to download to app-private storage for sensitive content.

---

### L5. NewTabSettingsActivity Exported Without Permission

**File:** `new-tab-page/new-tab-page-impl/src/main/AndroidManifest.xml:21-24`

`NewTabSettingsActivity` is `exported="true"` without a custom permission. Any app can open this settings screen.

**Recommendation:** Set `exported="false"` if external access is not needed, or add a signature-level permission.

---

## Positive Observations

The codebase demonstrates several strong security practices:

1. **Encrypted storage for autofill/sync secrets** — `SecureStorageDatabaseFactory` (SQLCipher) and `SyncStore` (EncryptedSharedPreferences) properly protect the most sensitive data.
2. **Zip slip protection** — `BrokerDataDownloader.kt` validates `canonicalPath` before extraction.
3. **Intent sanitization** — `Intent.sanitize()` strips malicious Parcelables before processing.
4. **No custom TrustManagers or SSLContext hacks** — OkHttp/Retrofit configurations use platform defaults.
5. **No KAPT, no dynamic class loading** — reduced attack surface.
6. **Content Providers not exported** — `AppStartUpTracer` is `exported="false"`.
7. **Autofill service properly permission-gated** — `BIND_AUTOFILL_SERVICE` permission required.
8. **LoginCredentials.toString() redacts passwords** — only `********` shown in logs.
9. **Per-instance random secrets in ContentScopeScripts** — good pattern for JS bridge authentication.
