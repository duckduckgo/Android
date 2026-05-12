package dagger.android

import kotlin.reflect.KClass

/**
 * Type alias for the injector factory map used in Metro mode.
 * Uses KClass<*> keys, which is what Metro's @ClassKey interop produces.
 */
typealias InjectorFactoryMap = Map<@JvmSuppressWildcards KClass<*>, @JvmSuppressWildcards AndroidInjector.Factory<*, *>>

/** Look up a factory by its Class key, converting to KClass. */
fun InjectorFactoryMap.getFactory(key: Class<*>): AndroidInjector.Factory<*, *>? = this[key.kotlin]
