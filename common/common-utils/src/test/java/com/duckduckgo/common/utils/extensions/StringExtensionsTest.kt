package com.duckduckgo.common.utils.extensions

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun whenSemanticVersionsAreEqualThenReturnZero() {
        assertEquals(0, "1.0.0".compareSemanticVersion("1.0.0"))
        assertEquals(0, "2.1.3.5".compareSemanticVersion("2.1.3.5"))
        assertEquals(0, "1.0".compareSemanticVersion("1.0.0"))
        assertEquals(0, "1.0.0".compareSemanticVersion("1.0"))
        assertEquals(0, "1.0.0".compareSemanticVersion("1.0"))
        assertEquals(0, "126.0.6478.40".compareSemanticVersion("126.0.6478.40"))
    }

    @Test
    fun whenCurrentSemanticVersionIsGreaterThanTargetSemanticVersionThenReturnOne() {
        assertEquals(1, "1.2.0".compareSemanticVersion("1.1.9"))
        assertEquals(1, "1.10.0".compareSemanticVersion("1.2.5"))
        assertEquals(1, "2.0".compareSemanticVersion("1.9.9"))
        assertEquals(1, "1.2.1".compareSemanticVersion("1.2"))
        assertEquals(1, "1.0.1".compareSemanticVersion("1.0"))
        assertEquals(1, "1.1".compareSemanticVersion("1.0.0"))
        assertEquals(1, "1.0.0.1".compareSemanticVersion("1.0"))
        assertEquals(1, "127.0.6478.40".compareSemanticVersion("126.0.6478.40"))
        assertEquals(1, "126.1.6478.40".compareSemanticVersion("126.0.6478.40"))
        assertEquals(1, "126.0.6479.40".compareSemanticVersion("126.0.6478.40"))
        assertEquals(1, "126.0.6478.41".compareSemanticVersion("126.0.6478.40"))
        assertEquals(1, "127.amazon-webview-v122-6261-tablet.6261.140.26".compareSemanticVersion("126.0.6478.40"))
    }

    @Test
    fun whenCurrentSemanticVersionIsSmallerThanTargetSemanticVersionThenReturnNegativeOne() {
        assertEquals(-1, "1.0.0".compareSemanticVersion("1.0.1"))
        assertEquals(-1, "1.2.3".compareSemanticVersion("1.2.4"))
        assertEquals(-1, "1.0".compareSemanticVersion("1.0.1"))
        assertEquals(-1, "1.0".compareSemanticVersion("1.0.0.1"))
        assertEquals(-1, "1.0.0".compareSemanticVersion("1.0.0.1"))
        assertEquals(-1, "1".compareSemanticVersion("1.0.1"))
        assertEquals(-1, "125.0.6478.40".compareSemanticVersion("126.0.6478.40"))
        assertEquals(-1, "125.1.6478.40".compareSemanticVersion("126.0.6478.40"))
        assertEquals(-1, "126.0.6477.40".compareSemanticVersion("126.0.6478.40"))
        assertEquals(-1, "126.0.6478.39".compareSemanticVersion("126.0.6478.40"))
        assertEquals(-1, "125.amazon-webview-v122-6261-tablet.6261.140.26".compareSemanticVersion("126.0.6478.40"))
    }

    @Test
    fun whenSemanticVersionsAreInvalidThenReturnNull() {
        assertNull("1..0".compareSemanticVersion("1.0.0"))
        assertNull("1.0.a".compareSemanticVersion("1.0.0"))
        assertNull("1.0.0".compareSemanticVersion("1..0"))
        assertNull("1.0.0".compareSemanticVersion("1.0.a"))
        assertNull("a.b.c.d".compareSemanticVersion("1.0.0"))
        assertNull("1".compareSemanticVersion("a.1.2"))
        assertNull("126.amazon-webview-v122-6261-tablet.6261.140.26".compareSemanticVersion("126.0.6478.40"))
        assertNull("126.0.6478a.40".compareSemanticVersion("126.0.6478.40"))
        assertNull("126.0.6478.40a".compareSemanticVersion("126.0.6478.40"))
    }
}
