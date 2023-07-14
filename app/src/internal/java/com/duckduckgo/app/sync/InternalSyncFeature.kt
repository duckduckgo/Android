package com.duckduckgo.app.sync

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.browser.R
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.sync.impl.ui.SyncInitialSetupActivity
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class InternalSyncFeature @Inject constructor(private val context: Context) : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return context.getString(R.string.syncSettingsTitle)
    }

    override fun internalFeatureSubtitle(): String {
        return context.getString(R.string.syncSettingsSubtitle)
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        activityContext.startActivity(Intent(activityContext, SyncInitialSetupActivity::class.java))
    }
}
