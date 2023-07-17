//[sync-api](../../../../index.md)/[com.duckduckgo.sync.api.engine](../../index.md)/[SyncEngine](../index.md)/[SyncTrigger](index.md)

# SyncTrigger

[androidJvm]\
enum [SyncTrigger](index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[SyncEngine.SyncTrigger](index.md)&gt; 

Represent each possible trigger fo See Tech Design: Sync Engine https://app.asana.com/0/481882893211075/1204303361994831/f [BACKGROUND_SYNC](-b-a-c-k-g-r-o-u-n-d_-s-y-n-c/index.md) -> Sync triggered by a Background Worker [APP_OPEN](-a-p-p_-o-p-e-n/index.md) -> Sync triggered after App is opened [FEATURE_READ](-f-e-a-t-u-r-e_-r-e-a-d/index.md) -> Sync triggered when a feature screen is opened (Bookmarks screen, etc...) [DATA_CHANGE](-d-a-t-a_-c-h-a-n-g-e/index.md) -> Sync triggered because data associated to a Syncable object has changed (new bookmark added) [ACCOUNT_CREATION](-a-c-c-o-u-n-t_-c-r-e-a-t-i-o-n/index.md) -> Sync triggered after creating a new Sync account [ACCOUNT_LOGIN](-a-c-c-o-u-n-t_-l-o-g-i-n/index.md) -> Sync triggered after login into an already existing sync account

## Entries

| | |
|---|---|
| [BACKGROUND_SYNC](-b-a-c-k-g-r-o-u-n-d_-s-y-n-c/index.md) | [androidJvm]<br>[BACKGROUND_SYNC](-b-a-c-k-g-r-o-u-n-d_-s-y-n-c/index.md) |
| [APP_OPEN](-a-p-p_-o-p-e-n/index.md) | [androidJvm]<br>[APP_OPEN](-a-p-p_-o-p-e-n/index.md) |
| [FEATURE_READ](-f-e-a-t-u-r-e_-r-e-a-d/index.md) | [androidJvm]<br>[FEATURE_READ](-f-e-a-t-u-r-e_-r-e-a-d/index.md) |
| [DATA_CHANGE](-d-a-t-a_-c-h-a-n-g-e/index.md) | [androidJvm]<br>[DATA_CHANGE](-d-a-t-a_-c-h-a-n-g-e/index.md) |
| [ACCOUNT_CREATION](-a-c-c-o-u-n-t_-c-r-e-a-t-i-o-n/index.md) | [androidJvm]<br>[ACCOUNT_CREATION](-a-c-c-o-u-n-t_-c-r-e-a-t-i-o-n/index.md) |
| [ACCOUNT_LOGIN](-a-c-c-o-u-n-t_-l-o-g-i-n/index.md) | [androidJvm]<br>[ACCOUNT_LOGIN](-a-c-c-o-u-n-t_-l-o-g-i-n/index.md) |

## Properties

| Name | Summary |
|---|---|
| [name](../../-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-372974862%2FProperties%2F414053090) | [androidJvm]<br>val [name](../../-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-372974862%2FProperties%2F414053090): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [ordinal](../../-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-739389684%2FProperties%2F414053090) | [androidJvm]<br>val [ordinal](../../-syncable-data-persister/-sync-conflict-resolution/-t-i-m-e-s-t-a-m-p/index.md#-739389684%2FProperties%2F414053090): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [valueOf](value-of.md) | [androidJvm]<br>fun [valueOf](value-of.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [SyncEngine.SyncTrigger](index.md)<br>Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |
| [values](values.md) | [androidJvm]<br>fun [values](values.md)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[SyncEngine.SyncTrigger](index.md)&gt;<br>Returns an array containing the constants of this enum type, in the order they're declared. |
