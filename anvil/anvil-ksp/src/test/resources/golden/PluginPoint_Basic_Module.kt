package com.test

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.DaggerMap
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.Multibinds
import kotlin.Int

@Module
@ContributesTo(scope = AppScope::class)
public abstract class TestPlugin_PluginPoint_Module {
  @Multibinds
  public abstract fun bindSetEmptyTestPlugin_PluginPoint(): DaggerSet<TestPlugin>

  @Multibinds
  public abstract fun bindMapEmptyTestPlugin_PluginPoint(): DaggerMap<Int, TestPlugin>

  @Binds
  public abstract fun bindTestPlugin_PluginPoint(pluginPoint: TestPlugin_PluginPoint):
      PluginPoint<TestPlugin>
}
