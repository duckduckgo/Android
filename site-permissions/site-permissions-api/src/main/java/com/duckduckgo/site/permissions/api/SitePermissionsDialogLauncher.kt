package com.duckduckgo.site.permissions.api

import android.app.Activity
import android.webkit.PermissionRequest
import androidx.activity.result.ActivityResultCaller

/** Public interface for Site Permissions dialog feature */
interface SitePermissionsDialogLauncher {

    fun registerPermissionLauncher(caller: ActivityResultCaller)

    fun askForSitePermission(activity: Activity, url: String, tabId: String, permissionsRequested: Array<String>, request: PermissionRequest)
}
