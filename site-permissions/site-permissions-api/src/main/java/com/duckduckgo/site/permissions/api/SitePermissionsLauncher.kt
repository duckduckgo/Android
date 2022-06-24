package com.duckduckgo.site.permissions.api

interface SitePermissionsLauncher {

    fun showSitePermissionsDialog(permissions: List<String>)
}
