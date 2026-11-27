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
public abstract class TriggerInterface_PluginPoint_Module {
  @Multibinds(allowEmpty = true)
  public abstract fun bindSetEmptyTriggerInterface_PluginPoint(): Set<MyBoundPlugin>

  @Multibinds(allowEmpty = true)
  public abstract fun bindMapEmptyTriggerInterface_PluginPoint(): Map<Int, MyBoundPlugin>

  @Binds
  public abstract fun bindTriggerInterface_PluginPoint(pluginPoint: TriggerInterface_PluginPoint):
      PluginPoint<MyBoundPlugin>
}
