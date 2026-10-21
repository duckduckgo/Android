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
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.collections.List

@BindingContainer
@ContributesTo(scope = AppScope::class)
public object TestFeature_ProxyModule {
  @Provides
  @SingleIn(scope = AppScope::class)
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
  @SingleIn(scope = AppScope::class)
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

  @Provides
  @IntoSet
  public fun providesTestFeaturePrivacyFeaturePlugin(remoteFeature: TestFeature_RemoteFeature):
      PrivacyFeaturePlugin = remoteFeature
}
