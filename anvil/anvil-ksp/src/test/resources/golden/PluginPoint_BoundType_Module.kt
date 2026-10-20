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
public abstract class TriggerInterface_PluginPoint_Module {
  @Multibinds(allowEmpty = true)
  public abstract fun bindSetEmptyTriggerInterface_PluginPoint(): DaggerSet<MyBoundPlugin>

  @Multibinds(allowEmpty = true)
  public abstract fun bindMapEmptyTriggerInterface_PluginPoint(): DaggerMap<Int, MyBoundPlugin>

  @Binds
  public abstract fun bindTriggerInterface_PluginPoint(pluginPoint: TriggerInterface_PluginPoint):
      PluginPoint<MyBoundPlugin>
}
