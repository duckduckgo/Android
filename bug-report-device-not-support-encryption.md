# Bug report — "Device does not support encryption" shown when accessing Passwords

## Summary

Since the early/mid-April 2026 release, some users see the **"device does not support encryption"** screen when opening Passwords in the browser, even though their device worked fine on previous versions and nothing has changed about their keystore.

This is caused by the combination of two PRs merged on **April 13, 2026** that together make password availability depend on a successful Harmony (multi-process `EncryptedSharedPreferences`) migration for *every* user.

## User-visible symptom

The Passwords screen routes to `AutofillManagementDeviceUnsupportedMode`, which displays:

> "To store passwords securely, we need to encrypt and store them on your device. Because your device does not support encrypted storage, saving and autofilling passwords is unavailable."

(`deviceNotSupportedSubtitle` in `strings-autofill-impl.xml`)

## Code path

1. `AutofillPasswordsManagementViewModel.launchDeviceAuth()` calls `autofillStore.autofillAvailable()`. If `false` → `ShowDeviceUnsupportedMode`.

   ```kotlin
   suspend fun launchDeviceAuth() {
       if (!autofillStore.autofillAvailable()) {
           logcat(VERBOSE) { "Can't access secure storage so can't offer autofill functionality" }
           deviceUnsupported()
           return
       }
       ...
   }
   ```
   (`autofill/autofill-impl/.../AutofillPasswordsManagementViewModel.kt`, lines 314–319)

2. `SecureStoreBackedAutofillStore.autofillAvailable()` → `SecureStorage.canAccessSecureStorage()` → `L2DataTransformer.canProcessData()` → `SecureStorageKeyProvider.canAccessKeyStore()` → `SecureStorageKeyRepository.canUseEncryption()` → `RealSecureStorageKeyStore.canUseEncryption()`.

3. Current `canUseEncryption` implementation:

   ```kotlin
   override suspend fun canUseEncryption(): Boolean = withContext(dispatcherProvider.io()) {
       val harmonyFlags = harmonyFlags()
       when {
           harmonyFlags.multiProcess -> getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
           harmonyFlags.useHarmony   -> getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
           else                      -> getEncryptedPreferences() != null
       }
   }
   ```
   (`autofill/autofill-impl/.../store/keys/SecureStorageKeyStore.kt`, lines 539–546)

## Root cause — two PRs merged the same day

### PR #8256 — "Enable harmony by default" (`0fdd4e3df1`, Apr 13)

Flipped the `AutofillFeature.useHarmony()` default from `FALSE` → `TRUE`. Every install now follows the Harmony branch above immediately, without waiting for remote config.

```diff
- @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.FALSE)
+ @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
  fun useHarmony(): Toggle
```

### PR #8255 — "Check legacy when canUseEncryption in multiprocess" (`046719f61e`, Apr 13)

Tightened the encryption availability check. Before: multi-process required only `harmonyPrefs != null`. After: both legacy and Harmony prefs must be non-null.

```diff
- harmonyFlags.multiProcess -> getHarmonyEncryptedPreferences() != null
- harmonyFlags.useHarmony && !harmonyFlags.multiProcess ->
-     getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
+ harmonyFlags.multiProcess -> getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
+ harmonyFlags.useHarmony   -> getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
```

### Combined effect

Since Apr 13, password availability is effectively `legacy != null && harmony != null` for essentially every user. If the Harmony side fails for any reason, the user gets the device-unsupported screen even though the legacy `EncryptedSharedPreferences` is fully functional and was on previous releases.

## Why the Harmony branch can return null in normal conditions

`harmonyPreferencesDeferred` calls `sharedPreferencesProvider.getMigratedEncryptedSharedPreferences(legacyPreferences, FILENAME_V3)`, which can return `null` for any of:

- `getEncryptedHarmonyDestination` throws (keystore / harmony file init issue) — pixel `DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED`
- `isAlreadyMigratedToHarmony` throws when reading the migrated flag — pixel `DATA_STORE_MIGRATE_ENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED`
- `migrateContentsToHarmony` fails on an unmigratable value type, mixed-type `Set`, or `editor.commit()` returning `false` — pixel `DATA_STORE_MIGRATE_ENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED`
- The outer `harmonyPreferencesDeferred` block catches a generic exception — pixel `AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED`

Any of these → `getHarmonyEncryptedPreferences() == null` → `canUseEncryption() == false` → "device does not support encryption".

There is also a one-way latch in `harmonyFlags()`:

```kotlin
// Latch: once multiProcess is true, it stays true for this session to prevent
// mid-execution flag changes from causing us to miss keys that exist only in Harmony.
if (initialMultiProcessValue == null) {
    initialMultiProcessValue = autofillServiceFlag
}
val effectiveMultiProcess = initialMultiProcessValue == true || autofillServiceFlag
```

Once `autofillService` has been enabled in a process, `multiProcess` stays true for the lifetime of that process. A user who has ever hit the multi-process path can therefore not escape the new strict check by toggling `useHarmony` off remotely — they will still go through the multi-process branch, which is also strict.

## Timing and scope

| Date | Commit | Change |
|---|---|---|
| 2026-03-19 | `96913b6f9b` | sqlcipher-loader migration (DB-side, separate path) |
| 2026-03-26 | `a111f2b413` | Reduce risk of `KeyAlreadyExistsException` races |
| 2026-04-01 | `3df740e87e` | Fixes for autofill service (multi-process) |
| 2026-04-01 | `307957f0c4` | Make L2 key writes atomic |
| **2026-04-13** | **`0fdd4e3df1`** | **`useHarmony` default flipped FALSE → TRUE (#8256)** |
| **2026-04-13** | **`046719f61e`** | **`canUseEncryption` requires both legacy + harmony (#8255)** |
| 2026-04-27 | `aea1ff3389` | Diagnostics-only pixel param addition |
| 2026-04-28 | `1e31cf7ea8` | UI-side `getCredentialsCount` Result migration |

User reports onset "early–mid April", which aligns with the Apr 13 release train.

## Diagnostics to confirm on affected users

Pull these pixels (all already wired up) to identify the failure point:

- `AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED`
- `DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED`
- `DATA_STORE_MIGRATE_ENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED`
- `DATA_STORE_MIGRATE_ENCRYPTED_QUERY_ALL_PREFERENCES_ORIGIN_FAILED`
- `DATA_STORE_MIGRATE_ENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED`
- `AUTOFILL_HARMONY_KEY_MISSING`, `AUTOFILL_HARMONY_KEY_MISMATCH`, `AUTOFILL_PREFERENCES_KEY_MISSING`

A spike correlated with the Apr 13 release would corroborate this diagnosis.

## Suggested mitigations

1. **Decouple "can use encryption" from harmony migration success.** Treat harmony as best-effort — if `getEncryptedPreferences() != null` but harmony returns null, still report the device as supported and continue using the legacy store, while firing the diagnostic pixel. The current logic conflates "user has no working keystore" (genuine device-unsupported) with "harmony migration failed" (recoverable).
2. **Reconsider the latch on `multiProcess`** for users who do not actually use the autofill service in a given session — it currently makes the strict check sticky for an entire process lifetime.
3. **Verify the remote-config kill switch.** Flipping `useHarmony` off remotely only falls through to the legacy-only branch if `multiProcess` is also false. If a user has ever activated the autofill service, this toggle alone won't help them.
4. **Consider gating the new strict check behind its own remote feature flag** so it can be disabled independently of `useHarmony` if pixels show this is widespread.
5. **String review.** If migration failure is a possible cause, the user-facing message ("your device does not support encryption") is misleading and dead-ends the user. Either keep falling back to legacy, or differentiate the messaging.

## References

- View model branching: `autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/ui/credential/management/AutofillPasswordsManagementViewModel.kt` lines 314–335, 377–383
- Strict check: `autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/store/keys/SecureStorageKeyStore.kt` lines 539–546
- Harmony prefs init: same file, lines 150–185
- Migration: `data-store/data-store-impl/src/main/java/com/duckduckgo/data/store/impl/SharedPreferencesProviderImpl.kt` lines 266–379
- PRs: #8255, #8256 (both merged Apr 13, 2026)
