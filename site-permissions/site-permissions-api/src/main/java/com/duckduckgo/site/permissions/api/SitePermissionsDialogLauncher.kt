package com.duckduckgo.site.permissions.api

import android.content.Context
import android.webkit.PermissionRequest
import androidx.activity.result.ActivityResultCaller

/** Public interface for Site Permissions dialog feature */
interface SitePermissionsDialogLauncher {

    fun registerPermissionLauncher(caller: ActivityResultCaller)

    fun askForSitePermission(context: Context, url: String, tabId: String, permissionsRequested: Array<String>, request: PermissionRequest)
}
