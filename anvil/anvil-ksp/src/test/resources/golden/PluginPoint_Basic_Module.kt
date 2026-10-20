package com.test

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.DaggerMap
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlin.Int

@BindingContainer
@ContributesTo(scope = AppScope::class)
public abstract class TestPlugin_PluginPoint_Module {
  @Multibinds(allowEmpty = true)
  public abstract fun bindSetEmptyTestPlugin_PluginPoint(): DaggerSet<TestPlugin>

  @Multibinds(allowEmpty = true)
  public abstract fun bindMapEmptyTestPlugin_PluginPoint(): DaggerMap<Int, TestPlugin>

  @Binds
  public abstract fun bindTestPlugin_PluginPoint(pluginPoint: TestPlugin_PluginPoint):
      PluginPoint<TestPlugin>
}
