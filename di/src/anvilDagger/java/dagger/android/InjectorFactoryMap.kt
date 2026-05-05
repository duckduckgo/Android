package dagger.android

import com.duckduckgo.di.DaggerMap

/**
 * Type alias for the injector factory map used in Anvil+Dagger mode.
 * Uses Class<*> keys, which is what Dagger's @ClassKey produces.
 */
typealias InjectorFactoryMap = DaggerMap<Class<*>, AndroidInjector.Factory<*, *>>

/** Look up a factory by its Class key. */
fun InjectorFactoryMap.getFactory(key: Class<*>): AndroidInjector.Factory<*, *>? = this[key]
