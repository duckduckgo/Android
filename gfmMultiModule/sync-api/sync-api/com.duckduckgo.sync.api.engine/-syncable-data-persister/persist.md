//[sync-api](../../../index.md)/[com.duckduckgo.sync.api.engine](../index.md)/[SyncableDataPersister](index.md)/[persist](persist.md)

# persist

[androidJvm]\
abstract fun [persist](persist.md)(changes: [SyncChangesResponse](../-sync-changes-response/index.md), conflictResolution: [SyncableDataPersister.SyncConflictResolution](-sync-conflict-resolution/index.md)): [SyncMergeResult](../-sync-merge-result/index.md)&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)&gt;

Changes from Sync Client have been received Each feature is responsible for merging and solving conflicts
