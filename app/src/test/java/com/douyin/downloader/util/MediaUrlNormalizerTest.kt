package com.douyin.downloader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUrlNormalizerTest {
    @Test
    fun upgradesHttpToHttps() {
        assertEquals(
            "https://q.weishi.qq.com/v.mp4",
            MediaUrlNormalizer.preferHttps("http://q.weishi.qq.com/v.mp4"),
        )
    }

    @Test
    fun keepsHttpsUnchanged() {
        assertEquals(
            "https://cdn.example/a.mp4",
            MediaUrlNormalizer.preferHttps("https://cdn.example/a.mp4"),
        )
    }
}
