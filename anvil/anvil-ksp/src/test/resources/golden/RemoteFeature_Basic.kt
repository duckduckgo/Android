package com.test

import android.content.Context
import android.content.SharedPreferences
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.`internal`.api.JsonException
import com.duckduckgo.feature.toggles.`internal`.api.JsonFeature
import com.duckduckgo.feature.toggles.`internal`.api.REMOTE_FEATURE_MOSHI
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import okio.Buffer

@ContributesMultibinding(
  scope = AppScope::class,
  boundType = PrivacyFeaturePlugin::class,
  ignoreQualifier = true,
)
@ContributesBinding(
  scope = AppScope::class,
  boundType = Toggle.Store::class,
)
@RemoteFeatureStoreNamed(value = TestFeature::class)
@SingleInstanceIn(scope = AppScope::class)
public class TestFeature_RemoteFeature @Inject constructor(
  @RemoteFeatureStoreNamed(value = TestFeature::class)
  private val settingsStore: FeatureSettings.Store,
  private val feature: Lazy<TestFeature>,
  private val appBuildConfig: AppBuildConfig,
  private val variantManager: VariantManager,
  private val context: Context,
) : PrivacyFeaturePlugin, Toggle.Store {
  private val moshi: Moshi = REMOTE_FEATURE_MOSHI

  private val preferences: SharedPreferences
    get() = context.getSharedPreferences("com.duckduckgo.feature.toggle.testFeature",
        Context.MODE_PRIVATE)

  public override val featureName: String = "testFeature"

  public override fun hash(): String? {
    try {
        // try to hash with all sub-features
        val concatMethodNames = this.feature.get().javaClass
            .declaredMethods
            .map { it.name }
            .sorted()
            .joinToString(separator = "")
        val hash = Buffer().writeUtf8(concatMethodNames).md5().hex()
        return hash
    } catch(e: Throwable) {
        // fallback to just featureName
        return this.featureName
    }
  }

  public override fun store(featureName: String, jsonString: String): Boolean {
    if (featureName == this.featureName) {
        val feature = parseJson(jsonString) ?: return false

        // feature hash is the hash of the feature + hash coming from remote config
        // this way we evaluate either when remote config has changes OR when feature changes
        // when the feature.hash (remote config) is null we always re-evaluate
        if (feature.hash != null) {
            val _hash = hash() + feature.hash
            if (compareAndSetHash(_hash)) return true
        }

        val exceptions = parseExceptions(feature.exceptions)

        val isEnabled = (feature.state == "enabled") || (appBuildConfig.flavor ==
        BuildFlavor.INTERNAL && feature.state == "internal")
        this.feature.get().invokeMethod("self").setRawStoredState(
            Toggle.State(
                remoteEnableState = isEnabled,
                enable = isEnabled,
                minSupportedVersion = feature.minSupportedVersion,
                targets = emptyList(),
                cohorts = emptyList(),
                settings = feature.settings?.toString(),
                exceptions = exceptions,
            )
        )

        // Handle sub-features
        feature.features?.forEach { subfeature ->
            subfeature.value.let { jsonToggle ->
                // try-catch to just skip any issues with a particular
                // sub-feature and continue with the rest of them
                try {
                    val previousState =
        this.feature.get().invokeMethod(subfeature.key).getRawStoredState()
                    // we try to honour the previous state
                    // else we resort to compute it using isEnabled()
                    val previousStateValue = previousState?.enable ?:
        this.feature.get().invokeMethod(subfeature.key).isEnabled()

                    val previousRolloutThreshold = previousState?.rolloutThreshold
                    val previousAssignedCohort = previousState?.assignedCohort
                    val newStateValue = (jsonToggle.state == "enabled" || (appBuildConfig.flavor ==
        BuildFlavor.INTERNAL && jsonToggle.state == "internal"))
                    val targets = jsonToggle?.targets?.map { target ->
                        Toggle.State.Target(
                            variantKey = target.variantKey,
                            localeCountry = target.localeCountry,
                            localeLanguage = target.localeLanguage,
                            isReturningUser = target.isReturningUser,
                            isPrivacyProEligible = target.isPrivacyProEligible,
                            entitlement = target.entitlement,
                            minSdkVersion = target.minSdkVersion,
                        )
                    } ?: emptyList()
                    val cohorts = jsonToggle?.cohorts?.map { cohort ->
                        Toggle.State.Cohort(
                            name = cohort.name,
                            weight = cohort.weight,
                        )
                    } ?: emptyList()
                    val settings = jsonToggle?.settings?.toString()
                    val subFeatureExceptions = parseExceptions(jsonToggle.exceptions)
                    this.feature.get().invokeMethod(subfeature.key).setRawStoredState(
                        Toggle.State(
                            remoteEnableState = newStateValue,
                            enable = previousStateValue,
                            minSupportedVersion = jsonToggle.minSupportedVersion?.toInt(),
                            rollout = jsonToggle?.rollout?.steps?.map { it.percent },
                            rolloutThreshold = previousRolloutThreshold,
                            assignedCohort = previousAssignedCohort,
                            targets = targets,
                            cohorts = cohorts,
                            settings = settings,
                            exceptions = subFeatureExceptions,
                        ),
                    )
                } catch(e: Throwable) {
                    // noop
                }
            }
        }

        // handle settings
        feature.settings?.let {
            settingsStore.store(it.toString())
        }

        return true
    }
    return false
  }

  public override fun `set`(key: String, state: Toggle.State): Unit {
    val jsonAdapter = moshi.adapter(Toggle.State::class.java)
    preferences.edit().putString(key, jsonAdapter.toJson(state)).apply()
  }

  public override fun `get`(key: String): Toggle.State? {
    val jsonAdapter = moshi.adapter(Toggle.State::class.java)
    return kotlin.runCatching { jsonAdapter.fromJson(preferences.getString(key, null)) }.getOrNull()
  }

  private fun compareAndSetHash(hash: String?): Boolean {
    if (hash == null) return false
    val currentHash = preferences.getString("hash", null)
    if (hash == currentHash) return true
    preferences.edit().putString("hash", hash).apply()
    return false
  }

  private fun parseJson(jsonString: String): JsonFeature? {
    val jsonAdapter = moshi.adapter(JsonFeature::class.java)
    return jsonAdapter.fromJson(jsonString)
  }

  private fun parseExceptions(exceptions: List<JsonException>): List<FeatureException> {
    val featureExceptions = mutableListOf<FeatureException>()
    exceptions?.map { ex ->
        featureExceptions.add(FeatureException(ex.domain, ex.reason))
    }
    return featureExceptions.toList()
  }

  public fun TestFeature.invokeMethod(name: String): Toggle {
    val toggle = kotlin.runCatching {
        this.javaClass.getDeclaredMethod(name)
    }.getOrNull()?.invoke(this) as Toggle
    return toggle
  }
}
