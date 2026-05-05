package com.test

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.DaggerMap
import com.duckduckgo.di.DaggerSet
import javax.inject.Inject
import kotlin.Int
import kotlin.collections.Collection

public class TriggerInterface_PluginPoint @Inject constructor(
  private val setPlugins: DaggerSet<MyBoundPlugin>,
  private val mapPlugins: DaggerMap<Int, MyBoundPlugin>,
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
