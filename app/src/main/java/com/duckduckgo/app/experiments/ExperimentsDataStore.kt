package com.duckduckgo.app.experiments

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface ExperimentsDataStore {
    val isDuckDiveExperimentEnabled: StateFlow<Boolean>
    suspend fun setDuckDiveExperimentEnabled(isEnabled: Boolean)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesExperimentsDataStore @Inject constructor(
    @Experiments private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ExperimentsDataStore {

    private object Keys {
        val DUCK_DIVE_EXPERIMENT_ENABLED = booleanPreferencesKey(name = "DUCK_DIVE_EXPERIMENT_ENABLED")
    }

    override val isDuckDiveExperimentEnabled: StateFlow<Boolean> = store.data
        .map { prefs ->
            prefs[Keys.DUCK_DIVE_EXPERIMENT_ENABLED] ?: false
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    override suspend fun setDuckDiveExperimentEnabled(isEnabled: Boolean) {
        store.edit { prefs ->
            prefs[Keys.DUCK_DIVE_EXPERIMENT_ENABLED] = isEnabled
        }
    }
}
