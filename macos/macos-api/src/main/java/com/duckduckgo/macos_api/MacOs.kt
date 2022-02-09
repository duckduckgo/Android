package com.duckduckgo.macos_api

interface MacOsWaitlist

sealed class MacOsWaitlistState {
    object NotJoinedQueue : MacOsWaitlistState()
    data class JoinedWaitlist(val notify: Boolean = false) : MacOsWaitlistState()
    data class InBeta(val inviteCode: String) : MacOsWaitlistState()
}

object MacOsNotificationEvent {
    const val MACOS_WAITLIST_CODE = "com.duckduckgo.notification.macos.waitlist.code"
}
