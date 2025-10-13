package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

class LocaleToggleTargetMatcherTest {
    private val appBuildConfig: AppBuildConfig = mock()
    private val matcher = LocaleToggleTargetMatcher(appBuildConfig)

    @Test
    fun whenAnyLocaleAndNullTargetThenTrue() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)

        assertTrue(matcher.matchesTargetProperty(NULL_TARGET))
    }

    @Test
    fun whenLocaleCountryNotMatchingTargetThenFalse() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.CHINA)

        assertFalse(matcher.matchesTargetProperty(US_COUNTRY_TARGET))
    }

    @Test
    fun whenLocaleLanguageNotMatchingTargetThenFalse() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.CHINA)

        assertFalse(matcher.matchesTargetProperty(US_LANG_TARGET))
    }

    @Test
    fun whenLocaleLanguageMatchesButNotCountryThenFalse() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale(Locale.US.language, Locale.FRANCE.country))

        assertFalse(matcher.matchesTargetProperty(US_TARGET))
    }

    @Test
    fun whenLocaleLanguageMatchesThenTrue() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale(Locale.US.language, Locale.FRANCE.country))

        assertTrue(matcher.matchesTargetProperty(US_LANG_TARGET))
    }

    @Test
    fun whenLocaleCountryMatchesButNotLanguageThenFalse() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale(Locale.FRANCE.language, Locale.US.country))

        assertFalse(matcher.matchesTargetProperty(US_TARGET))
    }

    @Test
    fun whenLocaleCountryMatchesThenTrue() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale(Locale.FRANCE.language, Locale.US.country))

        assertTrue(matcher.matchesTargetProperty(US_COUNTRY_TARGET))
    }

    @Test
    fun testIgnoreCasing() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)

        assertTrue(matcher.matchesTargetProperty(US_TARGET_LOWERCASE))
    }

    companion object {
        private val NULL_TARGET = Toggle.State.Target(null, null, null, null, null, null)
        private val US_COUNTRY_TARGET = NULL_TARGET.copy(localeCountry = Locale.US.country)
        private val US_LANG_TARGET = NULL_TARGET.copy(localeLanguage = Locale.US.language)
        private val US_TARGET = NULL_TARGET.copy(localeLanguage = Locale.US.language, localeCountry = Locale.US.country)
        private val US_TARGET_LOWERCASE = NULL_TARGET.copy(
            localeLanguage = Locale.US.language.lowercase(),
            localeCountry = Locale.US.country.lowercase(),
        )
    }
}
