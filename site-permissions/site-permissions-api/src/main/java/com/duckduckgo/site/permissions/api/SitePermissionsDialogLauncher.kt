package com.duckduckgo.site.permissions.api

import android.content.Context

interface SitePermissionsDialogLauncher {

    fun askForSitePermission(context: Context, url: String, permissionsRequested: Array<String>)
}
