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
    }

    @Test
    fun whenCurrentSemanticVersionIsSmallerThanTargetSemanticVersionThenReturnNegativeOne() {
        assertEquals(-1, "1.0.0".compareSemanticVersion("1.0.1"))
        assertEquals(-1, "1.2.3".compareSemanticVersion("1.2.4"))
        assertEquals(-1, "1.0".compareSemanticVersion("1.0.1"))
        assertEquals(-1, "1.0".compareSemanticVersion("1.0.0.1"))
        assertEquals(-1, "1.0.0".compareSemanticVersion("1.0.0.1"))
        assertEquals(-1, "1".compareSemanticVersion("1.0.1"))
    }

    @Test
    fun whenSemanticVersionsAreInvalidThenReturnNull() {
        assertNull("1..0".compareSemanticVersion("1.0.0"))
        assertNull("1.0.a".compareSemanticVersion("1.0.0"))
        assertNull("1.0.0".compareSemanticVersion("1..0"))
        assertNull("1.0.0".compareSemanticVersion("1.0.a"))
        assertNull("a.b.c.d".compareSemanticVersion("1.0.0"))
        assertNull("1".compareSemanticVersion("a.1.2"))
    }
}
