//[site-permissions-api](../../../index.md)/[com.duckduckgo.site.permissions.api](../index.md)/[SitePermissionsDialogLauncher](index.md)/[registerPermissionLauncher](register-permission-launcher.md)

# registerPermissionLauncher

[androidJvm]\
abstract fun [registerPermissionLauncher](register-permission-launcher.md)(caller: [ActivityResultCaller](https://developer.android.com/reference/kotlin/androidx/activity/result/ActivityResultCaller.html))

Registers callbacks for system permissions requests. This *must* be called unconditionally, as part of initialization path, typically as a field initializer of an Activity or Fragment.

#### Parameters

androidJvm

| | |
|---|---|
| caller | class that can call Activity.startActivityForResult-style APIs without having to manage request codes, and converting request/response to an Intent |
