package com.duckduckgo.macos_api

sealed class MacOsWaitlistState {
    object NotJoinedQueue : MacOsWaitlistState()
    data class JoinedWaitlist(val notify: Boolean = false) : MacOsWaitlistState()
    data class InBeta(val inviteCode: String) : MacOsWaitlistState()
}

object MacOsNotificationsEvent {
    const val MACOS_WAITLIST_CODE = "com.duckduckgo.notification.macos.waitlist.code"
}
