package com.duckduckgo.site.permissions.api

import android.app.Activity
import android.webkit.PermissionRequest
import androidx.activity.result.ActivityResultCaller
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions

/** Public interface for requesting microphone and/or camera permissions when website requests it */
interface SitePermissionsDialogLauncher {

    /**
     * Registers callbacks for system permissions requests. This *must* be called unconditionally, as part of initialization path,
     * typically as a field initializer of an Activity or Fragment.
     *
     * @param caller class that can call Activity.startActivityForResult-style APIs without having to manage request codes, and converting
     * request/response to an Intent
     */
    fun registerPermissionLauncher(caller: ActivityResultCaller)

    /**
     * This method should be called if website requests site permissions (audio, video, location or DRM). It will launch dialogs flow for asking the user.
     *
     * @param activity where this method is called from
     * @param url URL taken from the permissions request object
     * @param tabId id from the tab where this method is called from
     * @param permissionsRequested maps of permissions where keys are the type [PermissionsKey] and have a list of [String] as values.
     * @param request from onPermissionRequest callback in BrowserChromeClient. It is needed to grant site permissions.
     * @param permissionsGrantedListener interface that fragment or activity needs to implement to handle special cases when granting permissions
     */
    fun askForSitePermission(
        activity: Activity,
        url: String,
        tabId: String,
        permissionsRequested: SitePermissions,
        request: PermissionRequest,
        permissionsGrantedListener: SitePermissionsGrantedListener,
    )
}
