//[sync-api](../../../index.md)/[com.duckduckgo.sync.api](../index.md)/[SyncStateMonitor](index.md)/[syncState](sync-state.md)

# syncState

[androidJvm]\
abstract fun [syncState](sync-state.md)(): Flow&lt;[SyncState](../-sync-state/index.md)&gt;

Returns a flow of Sync state changes It follows the following truth table:

- 
   when the user is not signed with Sync is disabled the flow will emit a [SyncState.OFF](../-sync-state/-o-f-f/index.md)
- 
   else it will map the state to the last SyncAttempt
