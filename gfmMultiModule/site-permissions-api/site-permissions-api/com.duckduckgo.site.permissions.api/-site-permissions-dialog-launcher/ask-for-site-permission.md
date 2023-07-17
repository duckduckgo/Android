//[site-permissions-api](../../../index.md)/[com.duckduckgo.site.permissions.api](../index.md)/[SitePermissionsDialogLauncher](index.md)/[askForSitePermission](ask-for-site-permission.md)

# askForSitePermission

[androidJvm]\
abstract fun [askForSitePermission](ask-for-site-permission.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), permissionsRequested: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;, request: [PermissionRequest](https://developer.android.com/reference/kotlin/android/webkit/PermissionRequest.html), permissionsGrantedListener: [SitePermissionsGrantedListener](../-site-permissions-granted-listener/index.md))

This method should be called if website requests site permissions (audio or video). It will launch dialogs flow for asking the user.

#### Parameters

androidJvm

| | |
|---|---|
| activity | where this method is called from |
| url | unmodified URL taken from the URL bar (containing subdomains, query params etc...) |
| tabId | id from the tab where this method is called from |
| permissionsRequested | array of permissions that need to be asked filtered by user permissions settings. *Note: This could be different from `request.getResources()` |
| request | from onPermissionRequest callback in BrowserChromeClient. It is needed to grant site permissions. |
| permissionsGrantedListener | interface that fragment or activity needs to implement to handle special cases when granting permissions |
