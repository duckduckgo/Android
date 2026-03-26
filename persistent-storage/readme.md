# Persistent Storage
This module provides a small abstraction for storing key/value data that can survive app reinstall and device restore flows.
On Play/Internal builds it is backed by Google Play Block Store, and on F-Droid it is backed by a dummy implementation that reports unavailability.

## Module structure
- `persistent-storage-api`: public contract (`PersistentStorage`, availability model, and key abstraction)
- `persistent-storage-impl`: real implementation using Block Store (`play` and `internal` flavors)
- `persistent-storage-dummy-impl`: fallback implementation for `fdroid` flavor

## How it works
- Consumers inject `PersistentStorage` from `persistent-storage-api`
- Keys are module-owned subclasses of `PersistentStorageKey` with a globally unique `key` string
- `checkAvailability()` should be called before read/write operations when a feature depends on persistence being available
- `store`, `retrieve`, and `clear` return `Result` and fail with `PersistentStorageUnavailableException` when unsupported
- `shouldBackupToCloud` controls whether stored values are eligible for cloud backup

## Example usage
```kotlin
object MyFeatureKeys {
    object SessionSeed : PersistentStorageKey(
        key = "com.duckduckgo.myfeature.session.seed",
        shouldBackupToCloud = false,
    )
}

class MyFeatureRepository @Inject constructor(
    private val persistentStorage: PersistentStorage,
) {
    suspend fun save(seed: ByteArray): Result<Unit> {
        return persistentStorage.store(MyFeatureKeys.SessionSeed, seed)
    }
}
```

`## Who can help you better understand this feature?`
- Craig Russell

## More information
- [Internal Knowledge Sharing](https://app.asana.com/1/137249556945/project/1202552961248957/task/1213312950225699?focus=true)
- [Official docs](https://developer.android.com/identity/block-store)