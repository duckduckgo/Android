package com.test

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.`data`.store.api.SharedPreferencesProvider
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.FALSE
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.INTERNAL
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.TRUE
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(
  scope = AppScope::class,
  boundType = TestActivePluginBase::class,
)
public class MyPlugin_ActivePlugin @Inject constructor(
  private val activePlugin: MyPlugin,
  private val toggle: MyPlugin_ActivePlugin_RemoteFeature,
) : TestActivePluginBase by activePlugin {
  public override suspend fun isActive(): Boolean = toggle.pluginMyPlugin().isEnabled()
}

@ContributesRemoteFeature(
  scope = AppScope::class,
  featureName = "pluginPointTestPlugin",
  toggleStore = MyPlugin_ActivePlugin_RemoteFeature_MultiProcessStore::class,
)
public interface MyPlugin_ActivePlugin_RemoteFeature {
  @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
  public fun self(): Toggle

  @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
  public fun pluginMyPlugin(): Toggle
}

@ContributesBinding(scope = AppScope::class)
@RemoteFeatureStoreNamed(value = MyPlugin_ActivePlugin_RemoteFeature::class)
public class MyPlugin_ActivePlugin_RemoteFeature_MultiProcessStore @Inject constructor(
  @AppCoroutineScope
  private val coroutineScope: CoroutineScope,
  private val dispatcherProvider: DispatcherProvider,
  private val sharedPreferencesProvider: SharedPreferencesProvider,
  private val moshi: Moshi,
) : Toggle.Store {
  private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences("com.duckduckgo.feature.toggle.pluginPointTestPlugin.mp.store",
            multiprocess = true, migrate = false)}


  private val stateAdapter: JsonAdapter<Toggle.State> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(Toggle.State::class.java)}


  public override fun `set`(key: String, state: Toggle.State): Unit {
    coroutineScope.launch(dispatcherProvider.io()) {
        preferences.edit(commit = true) { putString(key, stateAdapter.toJson(state)) }
    }
  }

  public override fun `get`(key: String): Toggle.State? = preferences.getString(key, null)?.let {
      stateAdapter.fromJson(it)
  }
}
