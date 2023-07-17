//[sync-api](../../../index.md)/[com.duckduckgo.sync.api.engine](../index.md)/[SyncEngine](index.md)

# SyncEngine

[androidJvm]\
interface [SyncEngine](index.md)

## Types

| Name | Summary |
|---|---|
| [SyncTrigger](-sync-trigger/index.md) | [androidJvm]<br>enum [SyncTrigger](-sync-trigger/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[SyncEngine.SyncTrigger](-sync-trigger/index.md)&gt; <br>Represent each possible trigger fo See Tech Design: Sync Engine https://app.asana.com/0/481882893211075/1204303361994831/f [BACKGROUND_SYNC](-sync-trigger/-b-a-c-k-g-r-o-u-n-d_-s-y-n-c/index.md) -> Sync triggered by a Background Worker [APP_OPEN](-sync-trigger/-a-p-p_-o-p-e-n/index.md) -> Sync triggered after App is opened [FEATURE_READ](-sync-trigger/-f-e-a-t-u-r-e_-r-e-a-d/index.md) -> Sync triggered when a feature screen is opened (Bookmarks screen, etc...) [DATA_CHANGE](-sync-trigger/-d-a-t-a_-c-h-a-n-g-e/index.md) -> Sync triggered because data associated to a Syncable object has changed (new bookmark added) [ACCOUNT_CREATION](-sync-trigger/-a-c-c-o-u-n-t_-c-r-e-a-t-i-o-n/index.md) -> Sync triggered after creating a new Sync account [ACCOUNT_LOGIN](-sync-trigger/-a-c-c-o-u-n-t_-l-o-g-i-n/index.md) -> Sync triggered after login into an already existing sync account |

## Functions

| Name | Summary |
|---|---|
| [onSyncDisabled](on-sync-disabled.md) | [androidJvm]<br>abstract fun [onSyncDisabled](on-sync-disabled.md)()<br>Sync Feature has been disabled / device has been removed This is an opportunity for Features to do some local cleanup if needed |
| [triggerSync](trigger-sync.md) | [androidJvm]<br>abstract fun [triggerSync](trigger-sync.md)(trigger: [SyncEngine.SyncTrigger](-sync-trigger/index.md))<br>Entry point to the Sync Engine See Tech Design: Sync Updating/Polling Strategy https://app.asana.com/0/481882893211075/1204040479708519/f |
