//[sync-api](../../../index.md)/[com.duckduckgo.sync.api](../index.md)/[SyncState](index.md)

# SyncState

[androidJvm]\
enum [SyncState](index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[SyncState](index.md)&gt; 

Represent each possible sync state See Tech Design: Sync Engine https://app.asana.com/0/481882893211075/1204303361994831/f [READY](-r-e-a-d-y/index.md) -> Sync is enabled, data will sync according to the specifications above. Next state can be [IN_PROGRESS](-i-n_-p-r-o-g-r-e-s-s/index.md) if a new sync operation stars, or [OFF](-o-f-f/index.md) if user disables Sync. [IN_PROGRESS](-i-n_-p-r-o-g-r-e-s-s/index.md) -> Sync operation in progress. Next state can be [FAILED](-f-a-i-l-e-d/index.md) if operation fails or [READY](-r-e-a-d-y/index.md) if operation succeeds [FAILED](-f-a-i-l-e-d/index.md) -> Last Sync operation failed. Next state can be [IN_PROGRESS](-i-n_-p-r-o-g-r-e-s-s/index.md) if a new operation starts or [OFF](-o-f-f/index.md) if user disables Sync. [OFF](-o-f-f/index.md) -> Sync is disabled. Next state can be [READY](-r-e-a-d-y/index.md) if the user enabled sync for this device.

## Entries

| | |
|---|---|
| [READY](-r-e-a-d-y/index.md) | [androidJvm]<br>[READY](-r-e-a-d-y/index.md) |
| [IN_PROGRESS](-i-n_-p-r-o-g-r-e-s-s/index.md) | [androidJvm]<br>[IN_PROGRESS](-i-n_-p-r-o-g-r-e-s-s/index.md) |
| [FAILED](-f-a-i-l-e-d/index.md) | [androidJvm]<br>[FAILED](-f-a-i-l-e-d/index.md) |
| [OFF](-o-f-f/index.md) | [androidJvm]<br>[OFF](-o-f-f/index.md) |

## Properties

| Name | Summary |
|---|---|
| [name](../../com.duckduckgo.sync.api.engine/-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-372974862%2FProperties%2F414053090) | [androidJvm]<br>val [name](../../com.duckduckgo.sync.api.engine/-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-372974862%2FProperties%2F414053090): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [ordinal](../../com.duckduckgo.sync.api.engine/-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-739389684%2FProperties%2F414053090) | [androidJvm]<br>val [ordinal](../../com.duckduckgo.sync.api.engine/-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-739389684%2FProperties%2F414053090): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [valueOf](value-of.md) | [androidJvm]<br>fun [valueOf](value-of.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [SyncState](index.md)<br>Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |
| [values](values.md) | [androidJvm]<br>fun [values](values.md)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[SyncState](index.md)&gt;<br>Returns an array containing the constants of this enum type, in the order they're declared. |
