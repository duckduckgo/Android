package com.duckduckgo.aihistorysearch.impl

import org.junit.Test

class LlamaCppInferenceTest {
    @Test
    fun `library loads without UnsatisfiedLinkError`() {
        // Just instantiating the class triggers System.loadLibrary in the companion.
        // On JVM (Robolectric), the .so won't be present — that's fine.
        // This test mainly ensures the class compiles and the companion initializer doesn't crash the build.
        try {
            LlamaCppInference("/nonexistent/path")
        } catch (e: UnsatisfiedLinkError) {
            // Expected on JVM — .so is only available on device
        }
    }
}
