package com.duckduckgo.subscriptions.impl.auth2

import org.junit.Assert.*
import org.junit.Test

class PkceGeneratorImplTest {

    @Test
    fun `should generate correct code challenge`() {
        val codeVerifier = "oas6Ov1EcKzKjM-w9Q97cs6bYDU1cCI_hQwhAt0mLiE"

        assertEquals(
            "JP1GpQFSca0OUo-5Xxe5fzu2K_Sa84q2yCeHb-bw1zM",
            PkceGeneratorImpl().generateCodeChallenge(codeVerifier),
        )
    }
}
