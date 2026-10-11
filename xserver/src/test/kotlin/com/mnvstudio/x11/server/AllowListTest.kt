package com.mnvstudio.x11.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowListTest {
    @Test fun rules() {
        val s = XServer(64, 64)
        assertFalse(s.allowed("192.168.1.5"))
        s.allowList = listOf("192.168.1.*", "10.0.0.7")
        assertTrue(s.allowed("192.168.1.5"))
        assertTrue(s.allowed("10.0.0.7"))
        assertFalse(s.allowed("10.0.0.70"))
        assertFalse(s.allowed("192.168.2.5"))
    }
}
