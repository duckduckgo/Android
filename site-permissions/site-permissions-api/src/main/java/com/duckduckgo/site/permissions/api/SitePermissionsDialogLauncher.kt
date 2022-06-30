package com.duckduckgo.site.permissions.api

import android.content.Context

interface SitePermissionsDialogLauncher {

    fun showSitePermissionDialog(context: Context, permissionsRequested: Array<String>)
}
