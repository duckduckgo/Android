//[sync-api](../../index.md)/[com.duckduckgo.sync.api](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [DeviceSyncState](-device-sync-state/index.md) | [androidJvm]<br>interface [DeviceSyncState](-device-sync-state/index.md)<br>Public interface for Device Sync State |
| [SyncActivityWithEmptyParams](-sync-activity-with-empty-params/index.md) | [androidJvm]<br>object [SyncActivityWithEmptyParams](-sync-activity-with-empty-params/index.md) : [GlobalActivityStarter.ActivityParams](../../../navigation-api/navigation-api/com.duckduckgo.navigation.api/-global-activity-starter/-activity-params/index.md)<br>Use this class to launch the sync screen without parameters |
| [SyncCrypto](-sync-crypto/index.md) | [androidJvm]<br>interface [SyncCrypto](-sync-crypto/index.md)<br>Public interface to encrypt and decrypt Sync related data |
| [SyncState](-sync-state/index.md) | [androidJvm]<br>enum [SyncState](-sync-state/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[SyncState](-sync-state/index.md)&gt; <br>Represent each possible sync state See Tech Design: Sync Engine https://app.asana.com/0/481882893211075/1204303361994831/f [READY](-sync-state/-r-e-a-d-y/index.md) -> Sync is enabled, data will sync according to the specifications above. Next state can be [IN_PROGRESS](-sync-state/-i-n_-p-r-o-g-r-e-s-s/index.md) if a new sync operation stars, or [OFF](-sync-state/-o-f-f/index.md) if user disables Sync. [IN_PROGRESS](-sync-state/-i-n_-p-r-o-g-r-e-s-s/index.md) -> Sync operation in progress. Next state can be [FAILED](-sync-state/-f-a-i-l-e-d/index.md) if operation fails or [READY](-sync-state/-r-e-a-d-y/index.md) if operation succeeds [FAILED](-sync-state/-f-a-i-l-e-d/index.md) -> Last Sync operation failed. Next state can be [IN_PROGRESS](-sync-state/-i-n_-p-r-o-g-r-e-s-s/index.md) if a new operation starts or [OFF](-sync-state/-o-f-f/index.md) if user disables Sync. [OFF](-sync-state/-o-f-f/index.md) -> Sync is disabled. Next state can be [READY](-sync-state/-r-e-a-d-y/index.md) if the user enabled sync for this device. |
| [SyncStateMonitor](-sync-state-monitor/index.md) | [androidJvm]<br>interface [SyncStateMonitor](-sync-state-monitor/index.md) |
