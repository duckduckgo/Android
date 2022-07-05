package com.duckduckgo.site.permissions.api

import android.content.Context
import android.webkit.PermissionRequest
import androidx.activity.result.ActivityResultCaller

interface SitePermissionsDialogLauncher {

    fun registerPermissionLauncher(caller: ActivityResultCaller)

    fun askForSitePermission(context: Context, url: String, permissionsRequested: Array<String>, request: PermissionRequest)
}
