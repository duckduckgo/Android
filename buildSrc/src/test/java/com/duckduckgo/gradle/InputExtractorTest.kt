package com.duckduckgo.gradle

import com.duckduckgo.gradle.ModuleType.ApiAndroid
import com.duckduckgo.gradle.ModuleType.ApiPureKotlin
import com.duckduckgo.gradle.ModuleType.Impl
import com.duckduckgo.gradle.ModuleType.Internal
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InputExtractorTest{

    private val testee = InputExtractor()

    @Test
    fun whenFeatureNameIsEmptyThenExceptionThrown() {
        assertThrows<GradleException> { testee.extractFeatureNameAndTypes("") }
    }

    @Test
    fun whenFeatureNameHasInvalidCharsThenExceptionThrown() {
        assertThrows<GradleException> { testee.extractFeatureNameAndTypes("UPPERCASE") }
        assertThrows<GradleException> { testee.extractFeatureNameAndTypes("!@£") }
        assertThrows<GradleException> { testee.extractFeatureNameAndTypes("spaces not allowed") }
    }

    @Test
    fun whenFeatureNameStartsWithForwardSlashThenExceptionThrown() {
        assertThrows<GradleException> { testee.extractFeatureNameAndTypes("/feature") }
    }

    @Test
    fun whenFeatureNameHasMoreThanOneForwardSlashThenExceptionThrown() {
        assertThrows<GradleException> { testee.extractFeatureNameAndTypes("feature/foo/bar") }
    }

    @Test
    fun whenFeatureNameMissingAForwardSlashThenExceptionThrown() {
        assertThrows<GradleException> { testee.extractFeatureNameAndTypes("feature") }
    }

    @Test
    fun whenFeatureNameHasDashSeparatorsThenValidates() {
        val result =testee.extractFeatureNameAndTypes("feature-name/api")
        assertEquals("feature-name", result.first)
    }

    @Test
    fun whenFeatureNameEndsWithForwardSlashThenExceptionThrown() {
        assertThrows<GradleException> {  testee.extractFeatureNameAndTypes("feature/") }
    }

    @Test
    fun whenFeatureNameSpecifiesStoreModuleTypeThenExceptionThrown() {
        assertThrows<GradleException> {testee.extractFeatureNameAndTypes("feature/store") }
    }

    @Test
    fun whenInputSpeciesApiTypeThenPureKotlinApiTypeExtracted() {
        val result = testee.extractFeatureNameAndTypes("feature/api")
        assertEquals("feature", result.first)
        assertEquals(ApiPureKotlin, result.second)
    }

    @Test
    fun whenInputSpeciesAndroidApiTypeThenAndroidApiTypeExtracted() {
        val result = testee.extractFeatureNameAndTypes("feature/apiandroid")
        assertEquals("feature", result.first)
        assertEquals(ApiAndroid, result.second)
    }

    @Test
    fun whenInputSpeciesImplTypeThenImplTypeExtracted() {
        val result = testee.extractFeatureNameAndTypes("feature/impl")
        assertEquals("feature", result.first)
        assertEquals(Impl, result.second)
    }

    @Test
    fun whenInputSpeciesInternalTypeThenInternalTypeExtracted() {
        val result = testee.extractFeatureNameAndTypes("feature/internal")
        assertEquals("feature", result.first)
        assertEquals(Internal, result.second)
    }

    @Test
    fun whenInputDoesNotSpecifyTypeTrailingForwardSlashThenExceptionThrown() {
        assertThrows<GradleException> {  testee.extractFeatureNameAndTypes("feature/") }
    }
}
