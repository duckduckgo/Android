package com.duckduckgo.autofill.impl.configuration.integration.modern.listener.email

import org.junit.Assert.*
import org.junit.Test

class EmailProtectionUrlTest {

    @Test
    fun whenNotADuckDuckGoAddressThenNotIdentifiedAsEmailProtectionUrl() {
        assertFalse(EmailProtectionUrl.isEmailProtectionUrl("https://example.com"))
    }

    @Test
    fun whenADuckDuckGoAddressButNotEmailThenNotIdentifiedAsEmailProtectionUrl() {
        assertFalse(EmailProtectionUrl.isEmailProtectionUrl("https://duckduckgo.com"))
    }

    @Test
    fun whenIsDuckDuckGoEmailUrlThenIdentifiedAsEmailProtectionUrl() {
        assertTrue(EmailProtectionUrl.isEmailProtectionUrl("https://duckduckgo.com/email"))
    }

    @Test
    fun whenIsDuckDuckGoEmailUrlWithTrailingSlashThenIdentifiedAsEmailProtectionUrl() {
        assertTrue(EmailProtectionUrl.isEmailProtectionUrl("https://duckduckgo.com/email/"))
    }

    @Test
    fun whenIsDuckDuckGoEmailUrlWithExtraUrlPartsThenIdentifiedAsEmailProtectionUrl() {
        assertTrue(EmailProtectionUrl.isEmailProtectionUrl("https://duckduckgo.com/email/foo/bar"))
    }
}
