package com.duckduckgo.macos_api

/** Public interface for MacOs wait lsit */
interface MacOsWaitlist {
    /**
     * This method returns the current state of the user in the MacOs waitlist
     * @return a [MacWaitlistState]
     */
    fun getWaitlistState(): MacWaitlistState
}

/** Public data class for MacOs wait list */
sealed class MacWaitlistState {
    object NotJoinedQueue : MacWaitlistState()
    object JoinedWaitlist : MacWaitlistState()
    object InBeta : MacWaitlistState()
}
