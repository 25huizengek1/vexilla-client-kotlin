package me.huizengek.vexilla.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val uuid = "b7e91cc5-ec76-4ec3-9c1c-075032a13a1a"

class HasherTests {
    @Test
    fun testWorkingSeed() {
        assertTrue(uuid.hash(0.11) <= 40)
    }

    @Test
    fun testNonWorkingSeed() {
        assertFalse(uuid.hash(0.22) <= 40)
    }
}
