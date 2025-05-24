package com.duckduckgo.savedsites.impl.bookmarks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages a set of hidden IDs (e.g., bookmarks or folders) and exposes a StateFlow for observation.
 */
class HiddenIdsManager private constructor() {
    private val hiddenIds = MutableStateFlow<List<String>>(emptyList())

    fun add(id: String) {
        hiddenIds.update { it + id }
    }

    fun remove(id: String) {
        hiddenIds.update { it - id }
    }

    fun contains(id: String): Boolean {
        return hiddenIds.value.contains(id)
    }

    fun getAll(): List<String> = hiddenIds.value
    fun getFlow(): Flow<List<String>> = hiddenIds

    companion object {
        @Volatile
        private var instance: HiddenIdsManager? = null
        
        fun getInstance(): HiddenIdsManager =
            instance ?: synchronized(this) {
                instance ?: HiddenIdsManager().also { instance = it }
            }
    }
}