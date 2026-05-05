package com.test

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.`internal`.api.FeatureTogglesCallback
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import kotlin.collections.List

@Module
@ContributesTo(scope = AppScope::class)
public object TestFeature_ProxyModule {
  @Provides
  @SingleInstanceIn(scope = AppScope::class)
  public fun providesTestFeature(
    @RemoteFeatureStoreNamed(value = TestFeature::class) toggleStore: Toggle.Store,
    callback: FeatureTogglesCallback,
    appBuildConfig: AppBuildConfig,
    variantManager: VariantManager,
  ): TestFeature = FeatureToggles.Builder()
      .store(toggleStore)
      .appVersionProvider({ appBuildConfig.versionCode })
      .flavorNameProvider({ appBuildConfig.flavor.name })
      .featureName("testFeature")
      .appVariantProvider({ appBuildConfig.variantName })
      .callback(callback)
      // save empty variants will force the default variant to be set
      .forceDefaultVariantProvider({ variantManager.updateVariants(emptyList()) })
      .build()
      .create(TestFeature::class.java)

  @Provides
  @RemoteFeatureStoreNamed(value = TestFeature::class)
  public fun providesNoopSettingsStore(): FeatureSettings.Store = FeatureSettings.EMPTY_STORE

  @Provides
  @SingleInstanceIn(scope = AppScope::class)
  @IntoSet
  public fun providesTestFeatureInventory(feature: TestFeature): FeatureTogglesInventory {
        return object : FeatureTogglesInventory {
            override suspend fun getAll(): List<Toggle> {
                return listOf(
                    feature.self(),
    feature.fooFeature()
                )
            }
        }
  }
}
