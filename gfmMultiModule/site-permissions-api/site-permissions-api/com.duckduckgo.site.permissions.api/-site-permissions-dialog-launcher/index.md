//[site-permissions-api](../../../index.md)/[com.duckduckgo.site.permissions.api](../index.md)/[SitePermissionsDialogLauncher](index.md)

# SitePermissionsDialogLauncher

[androidJvm]\
interface [SitePermissionsDialogLauncher](index.md)

Public interface for requesting microphone and/or camera permissions when website requests it

## Functions

| Name | Summary |
|---|---|
| [askForSitePermission](ask-for-site-permission.md) | [androidJvm]<br>abstract fun [askForSitePermission](ask-for-site-permission.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), permissionsRequested: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;, request: [PermissionRequest](https://developer.android.com/reference/kotlin/android/webkit/PermissionRequest.html), permissionsGrantedListener: [SitePermissionsGrantedListener](../-site-permissions-granted-listener/index.md))<br>This method should be called if website requests site permissions (audio or video). It will launch dialogs flow for asking the user. |
| [registerPermissionLauncher](register-permission-launcher.md) | [androidJvm]<br>abstract fun [registerPermissionLauncher](register-permission-launcher.md)(caller: [ActivityResultCaller](https://developer.android.com/reference/kotlin/androidx/activity/result/ActivityResultCaller.html))<br>Registers callbacks for system permissions requests. This *must* be called unconditionally, as part of initialization path, typically as a field initializer of an Activity or Fragment. |
