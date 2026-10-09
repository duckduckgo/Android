package com.test

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlin.Int
import kotlin.collections.Map
import kotlin.collections.Set

@BindingContainer
@ContributesTo(scope = AppScope::class)
public abstract class TestPlugin_PluginPoint_Module {
  @Multibinds(allowEmpty = true)
  public abstract fun bindSetEmptyTestPlugin_PluginPoint(): Set<TestPlugin>

  @Multibinds(allowEmpty = true)
  public abstract fun bindMapEmptyTestPlugin_PluginPoint(): Map<Int, TestPlugin>

  @Binds
  public abstract fun bindTestPlugin_PluginPoint(pluginPoint: TestPlugin_PluginPoint):
      PluginPoint<TestPlugin>
}
