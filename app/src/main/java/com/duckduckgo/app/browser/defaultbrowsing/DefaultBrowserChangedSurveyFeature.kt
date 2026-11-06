package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.codegen.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "defaultBrowserChangedSurvey",
)
interface DefaultBrowserChangedSurveyFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle
}
