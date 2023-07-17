//[sync-api](../../../index.md)/[com.duckduckgo.sync.api.engine](../index.md)/[SyncableDataPersister](index.md)

# SyncableDataPersister

[androidJvm]\
interface [SyncableDataPersister](index.md)

## Types

| Name | Summary |
|---|---|
| [SyncConflictResolution](-sync-conflict-resolution/index.md) | [androidJvm]<br>enum [SyncConflictResolution](-sync-conflict-resolution/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[SyncableDataPersister.SyncConflictResolution](-sync-conflict-resolution/index.md)&gt; <br>Represent each possible conflict resolution strategy that Sync supports See Tech Design: Sync Engine https://app.asana.com/0/481882893211075/1204303361994831/f [DEDUPLICATION](-sync-conflict-resolution/-d-e-d-u-p-l-i-c-a-t-i-o-n/index.md) -> Remote and Local data will be deduplicated and merged (Account Flows) [REMOTE_WINS](-sync-conflict-resolution/-r-e-m-o-t-e_-w-i-n-s/index.md) -> Objects present in Remote will override objets in Local [LOCAL_WINS](-sync-conflict-resolution/-l-o-c-a-l_-w-i-n-s/index.md) -> Object present in Local wins, Remote object is discarded [TIMESTAMP](-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md) -> The last modified object wins, either from Remote or Local |

## Functions

| Name | Summary |
|---|---|
| [onSyncDisabled](on-sync-disabled.md) | [androidJvm]<br>abstract fun [onSyncDisabled](on-sync-disabled.md)()<br>Sync Feature has been disabled / device has been removed This is an opportunity for Features to do some local cleanup if needed |
| [persist](persist.md) | [androidJvm]<br>abstract fun [persist](persist.md)(changes: [SyncChangesResponse](../-sync-changes-response/index.md), conflictResolution: [SyncableDataPersister.SyncConflictResolution](-sync-conflict-resolution/index.md)): [SyncMergeResult](../-sync-merge-result/index.md)&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)&gt;<br>Changes from Sync Client have been received Each feature is responsible for merging and solving conflicts |
