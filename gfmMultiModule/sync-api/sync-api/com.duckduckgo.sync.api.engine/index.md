//[sync-api](../../index.md)/[com.duckduckgo.sync.api.engine](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [SyncableDataPersister](-syncable-data-persister/index.md) | [androidJvm]<br>interface [SyncableDataPersister](-syncable-data-persister/index.md) |
| [SyncableDataProvider](-syncable-data-provider/index.md) | [androidJvm]<br>interface [SyncableDataProvider](-syncable-data-provider/index.md) |
| [SyncableType](-syncable-type/index.md) | [androidJvm]<br>enum [SyncableType](-syncable-type/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[SyncableType](-syncable-type/index.md)&gt; |
| [SyncChangesRequest](-sync-changes-request/index.md) | [androidJvm]<br>data class [SyncChangesRequest](-sync-changes-request/index.md)(val type: [SyncableType](-syncable-type/index.md), val jsonString: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val modifiedSince: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [SyncChangesResponse](-sync-changes-response/index.md) | [androidJvm]<br>data class [SyncChangesResponse](-sync-changes-response/index.md)(val type: [SyncableType](-syncable-type/index.md), val jsonString: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [SyncDataValidationResult](-sync-data-validation-result/index.md) | [androidJvm]<br>sealed class [SyncDataValidationResult](-sync-data-validation-result/index.md)&lt;out [R](-sync-data-validation-result/index.md)&gt; |
| [SyncEngine](-sync-engine/index.md) | [androidJvm]<br>interface [SyncEngine](-sync-engine/index.md) |
| [SyncMergeResult](-sync-merge-result/index.md) | [androidJvm]<br>sealed class [SyncMergeResult](-sync-merge-result/index.md)&lt;out [R](-sync-merge-result/index.md)&gt; |
