package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import org.junit.Assert.*
import org.junit.Test

class DefaultImportedPasswordValidatorTest {
    private val testee = DefaultImportedPasswordValidator()

    @Test
    fun whenThen() {
        assertTrue(testee.isValid(validCreds()))
    }

    private fun validCreds(): LoginCredentials {
        return LoginCredentials(
            username = "username",
            password = "password",
            domain = "example.com",
        )
    }
}
