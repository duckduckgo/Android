package com.test

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.`data`.store.api.SharedPreferencesProvider
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.InternalActivePluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.FALSE
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.INTERNAL
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.TRUE
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import javax.inject.Inject
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.Collection
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesPluginPoint(
  scope = AppScope::class,
  boundType = MyBoundPlugin::class,
)
@Suppress("unused")
private interface Trigger_TestPluginPointTrigger_ActivePluginPoint

@ContributesRemoteFeature(
  scope = AppScope::class,
  featureName = "pluginPointMyBoundPlugin",
  toggleStore = TestPluginPointTrigger_ActivePluginPoint_RemoteFeature_MultiProcessStore::class,
)
public interface TestPluginPointTrigger_ActivePluginPoint_RemoteFeature {
  @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
  public fun self(): Toggle
}

public class TestPluginPointTrigger_PluginPoint_ActiveWrapper @Inject constructor(
  private val toggle: TestPluginPointTrigger_ActivePluginPoint_RemoteFeature,
  private val pluginPoint: PluginPoint<@JvmSuppressWildcards MyBoundPlugin>,
  private val dispatcherProvider: DispatcherProvider,
) : InternalActivePluginPoint<@JvmSuppressWildcards MyBoundPlugin> {
  public override suspend fun getPlugins(): Collection<MyBoundPlugin> =
      kotlinx.coroutines.withContext(dispatcherProvider.io()) {
      if (toggle.self().isEnabled()) {
          pluginPoint.getPlugins().filter { it.isActive() }
      } else {
          emptyList()
      }
  }
}

@ContributesBinding(scope = AppScope::class)
@RemoteFeatureStoreNamed(value = TestPluginPointTrigger_ActivePluginPoint_RemoteFeature::class)
public class TestPluginPointTrigger_ActivePluginPoint_RemoteFeature_MultiProcessStore @Inject
    constructor(
  @AppCoroutineScope
  private val coroutineScope: CoroutineScope,
  private val dispatcherProvider: DispatcherProvider,
  private val sharedPreferencesProvider: SharedPreferencesProvider,
  private val moshi: Moshi,
) : Toggle.Store {
  private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences("com.duckduckgo.feature.toggle.pluginPointMyBoundPlugin.mp.store",
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

@Module
@ContributesTo(scope = AppScope::class)
public abstract class TestPluginPointTrigger_PluginPoint_ActiveWrapper_Binding_Module {
  @Binds
  public abstract
      fun bindsTestPluginPointTrigger_PluginPoint_ActiveWrapper(pluginPoint: TestPluginPointTrigger_PluginPoint_ActiveWrapper):
      InternalActivePluginPoint<@JvmSuppressWildcards MyBoundPlugin>
}
