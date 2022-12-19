package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view

import androidx.annotation.StringRes
import com.duckduckgo.mobile.android.vpn.R

enum class DisableVpnDialogOptions(val order: Int, @StringRes val stringRes: Int) {
    DISABLE_APP(0, R.string.atp_DisableConfirmationDisableApp),
    DISABLE_VPN(1, R.string.atp_DisableConfirmationDisableAllApps),
    CANCEL(2, R.string.atp_DisableConfirmationCancel),
    ;

    companion object {
        fun asOptions(): List<Int> {
            return listOf(DISABLE_APP.stringRes, DISABLE_VPN.stringRes, CANCEL.stringRes)
        }
        fun getOptionFromPosition(position: Int): DisableVpnDialogOptions {
            var defaultOption = DISABLE_APP
            values().forEach {
                if (it.order == position) {
                    defaultOption = it
                }
            }
            return defaultOption
        }
    }
}
