package com.douyin.downloader.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinReflowTokenTest {

    @Test
    fun encryptsXssTokenWithWebIdPrefixAsAesKey() {
        val webId = "7673767960453105162"
        val xss =
            "f8536561d203eb18acd9ebdf45efdee47d5f26da0b49a601c6fe0263a8d669af66bd2c27407fc038a13ecc311f9a73b2"
        val expected =
            "gpCezq5/ersEj7TrET7TSt7COa6YUv4Mh7RBLZ49vm2HFR+19nDHHNfHbcH3vL2NJpkTAL6Vpaux+fzHtqwp2sSoSPBrTlivYUqGWdhZW7sFODT9P+KK0hruDVfFem1VKy/GNyXLmUOU0KhXY63+Rg=="
        assertEquals(expected, DouyinReflowToken.encrypt(webId, xss))
    }

    @Test
    fun extractsWebIdAndXssTokenFromShareHtml() {
        val html =
            """
            <div id='douyin_reflow_webId' webId=7673767960453105162 usercip=1.2.3.4 ></div>
            <div id=douyin_reflow_token xsstoken=f8536561d203eb18acd9ebdf45efdee47d5f26da0b49a601c6fe0263a8d669af66bd2c27407fc038a13ecc311f9a73b2 ></div>
            """.trimIndent()
        assertEquals("7673767960453105162", DouyinReflowToken.extractWebId(html))
        assertEquals(
            "f8536561d203eb18acd9ebdf45efdee47d5f26da0b49a601c6fe0263a8d669af66bd2c27407fc038a13ecc311f9a73b2",
            DouyinReflowToken.extractXssToken(html),
        )
    }

    @Test
    fun extractWebIdReturnsNullWhenPlaceholderUndefined() {
        val html = "<div id='douyin_reflow_webId' webId=undefined usercip=1.2.3.4 ></div>"
        assertNull(DouyinReflowToken.extractWebId(html))
    }

    @Test
    fun extractWebIdFallsBackToRouterJson() {
        val html = """window._ROUTER_DATA = {"loaderData":{"video_(id)/page":{"webId":"7673767339544036910"}}}"""
        assertEquals("7673767339544036910", DouyinReflowToken.extractWebId(html))
    }
}
