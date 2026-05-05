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
public abstract class TriggerInterface_PluginPoint_Module {
  @Multibinds
  public abstract fun bindSetEmptyTriggerInterface_PluginPoint(): DaggerSet<MyBoundPlugin>

  @Multibinds
  public abstract fun bindMapEmptyTriggerInterface_PluginPoint(): DaggerMap<Int, MyBoundPlugin>

  @Binds
  public abstract fun bindTriggerInterface_PluginPoint(pluginPoint: TriggerInterface_PluginPoint):
      PluginPoint<MyBoundPlugin>
}
