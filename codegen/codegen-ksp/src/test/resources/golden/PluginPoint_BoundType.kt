package com.test

import com.duckduckgo.common.utils.plugins.PluginPoint
import javax.inject.Inject
import kotlin.Int
import kotlin.collections.Collection
import kotlin.collections.Map
import kotlin.collections.Set

public class TriggerInterface_PluginPoint @Inject constructor(
  private val setPlugins: Set<MyBoundPlugin>,
  private val mapPlugins: Map<Int, MyBoundPlugin>,
) : PluginPoint<MyBoundPlugin> {
  private val sortedPlugins: Collection<MyBoundPlugin> by lazy {
        mapPlugins.entries
        .sortedWith(compareBy({ it.key }, { it.value.javaClass.name }))
        .map { it.value }
        .toMutableList()
        .apply {
            addAll(setPlugins.toList().sortedBy { it.javaClass.name })
        }}


  public override fun getPlugins(): Collection<MyBoundPlugin> {
    // Sort plugins by class name to ensure execution consistency
    return sortedPlugins
  }
}
