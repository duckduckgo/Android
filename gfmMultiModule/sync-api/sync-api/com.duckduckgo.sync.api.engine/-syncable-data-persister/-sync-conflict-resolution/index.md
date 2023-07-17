//[sync-api](../../../../index.md)/[com.duckduckgo.sync.api.engine](../../index.md)/[SyncableDataPersister](../index.md)/[SyncConflictResolution](index.md)

# SyncConflictResolution

[androidJvm]\
enum [SyncConflictResolution](index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[SyncableDataPersister.SyncConflictResolution](index.md)&gt; 

Represent each possible conflict resolution strategy that Sync supports See Tech Design: Sync Engine https://app.asana.com/0/481882893211075/1204303361994831/f [DEDUPLICATION](-d-e-d-u-p-l-i-c-a-t-i-o-n/index.md) -> Remote and Local data will be deduplicated and merged (Account Flows) [REMOTE_WINS](-r-e-m-o-t-e_-w-i-n-s/index.md) -> Objects present in Remote will override objets in Local [LOCAL_WINS](-l-o-c-a-l_-w-i-n-s/index.md) -> Object present in Local wins, Remote object is discarded [TIMESTAMP](-t-i-m-e-s-t-a-m-p/index.md) -> The last modified object wins, either from Remote or Local

## Entries

| | |
|---|---|
| [DEDUPLICATION](-d-e-d-u-p-l-i-c-a-t-i-o-n/index.md) | [androidJvm]<br>[DEDUPLICATION](-d-e-d-u-p-l-i-c-a-t-i-o-n/index.md) |
| [REMOTE_WINS](-r-e-m-o-t-e_-w-i-n-s/index.md) | [androidJvm]<br>[REMOTE_WINS](-r-e-m-o-t-e_-w-i-n-s/index.md) |
| [LOCAL_WINS](-l-o-c-a-l_-w-i-n-s/index.md) | [androidJvm]<br>[LOCAL_WINS](-l-o-c-a-l_-w-i-n-s/index.md) |
| [TIMESTAMP](-t-i-m-e-s-t-a-m-p/index.md) | [androidJvm]<br>[TIMESTAMP](-t-i-m-e-s-t-a-m-p/index.md) |

## Properties

| Name | Summary |
|---|---|
| [name](-t-i-m-e-s-t-a-m-p/index.md#-372974862%2FProperties%2F414053090) | [androidJvm]<br>val [name](-t-i-m-e-s-t-a-m-p/index.md#-372974862%2FProperties%2F414053090): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [ordinal](-t-i-m-e-s-t-a-m-p/index.md#-739389684%2FProperties%2F414053090) | [androidJvm]<br>val [ordinal](-t-i-m-e-s-t-a-m-p/index.md#-739389684%2FProperties%2F414053090): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [valueOf](value-of.md) | [androidJvm]<br>fun [valueOf](value-of.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [SyncableDataPersister.SyncConflictResolution](index.md)<br>Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |
| [values](values.md) | [androidJvm]<br>fun [values](values.md)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[SyncableDataPersister.SyncConflictResolution](index.md)&gt;<br>Returns an array containing the constants of this enum type, in the order they're declared. |
