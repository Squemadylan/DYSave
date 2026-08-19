package com.douyin.downloader.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XhsCookieHelperTest {
    @Test
    fun loggedInWhenWebSessionPresent() {
        assertTrue(
            XhsCookieHelper.looksLoggedIn("a1=xxx; web_session=abc123; other=1"),
        )
    }

    @Test
    fun notLoggedInWhenOnlyA1() {
        assertFalse(XhsCookieHelper.looksLoggedIn("a1=device-id-only"))
    }

    @Test
    fun notLoggedInWhenEmptyWebSession() {
        assertFalse(XhsCookieHelper.looksLoggedIn("web_session=; a1=x"))
    }

    @Test
    fun notLoggedInWhenNullOrBlank() {
        assertFalse(XhsCookieHelper.looksLoggedIn(null))
        assertFalse(XhsCookieHelper.looksLoggedIn(""))
    }
}
