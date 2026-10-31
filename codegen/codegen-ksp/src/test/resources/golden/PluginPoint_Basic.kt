package com.test

import com.duckduckgo.common.utils.plugins.PluginPoint
import javax.inject.Inject
import kotlin.Int
import kotlin.collections.Collection
import kotlin.collections.Map
import kotlin.collections.Set

public class TestPlugin_PluginPoint @Inject constructor(
  private val setPlugins: Set<TestPlugin>,
  private val mapPlugins: Map<Int, TestPlugin>,
) : PluginPoint<TestPlugin> {
  private val sortedPlugins: Collection<TestPlugin> by lazy {
        mapPlugins.entries
        .sortedWith(compareBy({ it.key }, { it.value.javaClass.name }))
        .map { it.value }
        .toMutableList()
        .apply {
            addAll(setPlugins.toList().sortedBy { it.javaClass.name })
        }}


  public override fun getPlugins(): Collection<TestPlugin> {
    // Sort plugins by class name to ensure execution consistency
    return sortedPlugins
  }
}
