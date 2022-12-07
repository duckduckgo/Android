package com.duckduckgo.app.sync

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.settings.extension.InternalFeaturePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.ui.SyncInitialSetupActivity
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class InternalSyncFeature @Inject constructor(private val context: Context) : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return "Sync"
    }

    override fun internalFeatureSubtitle(): String {
        return "Sync setup internal test"
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        activityContext.startActivity(Intent(activityContext, SyncInitialSetupActivity::class.java))
    }
}
