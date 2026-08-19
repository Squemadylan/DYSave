package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.ContentInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserItemInfoTest {
    private val parser = HtmlParser()

    @Test
    fun parseVideoFromItemInfoResponse() {
        val json =
            """
            {
              "status_code": 0,
              "item_list": [{
                "aweme_id": "7663142592183307554",
                "desc": "太空计划｜旅行者号",
                "author": {"nickname": "阿布档案"},
                "video": {
                  "play_addr": {
                    "uri": "v0300fg10000d9cf677og65mtifj68ag",
                    "url_list": [
                      "https://aweme.snssdk.com/aweme/v1/playwm/?video_id=v0300fg10000d9cf677og65mtifj68ag&ratio=720p&line=0"
                    ]
                  },
                  "cover": {
                    "url_list": ["https://cdn.example/cover.jpg"]
                  },
                  "bit_rate": [{
                    "bit_rate": 1200000,
                    "gear_name": "720p",
                    "play_addr": {
                      "url_list": [
                        "https://aweme.snssdk.com/aweme/v1/playwm/?video_id=v0300fg10000d9cf677og65mtifj68ag&ratio=720p&line=0"
                      ]
                    }
                  }]
                }
              }]
            }
            """.trimIndent()

        val info = parser.parseItemInfo(json, "7663142592183307554")
        assertTrue(info is ContentInfo.Video)
        info as ContentInfo.Video
        assertEquals("太空计划｜旅行者号", info.title)
        assertEquals("阿布档案", info.author)
        assertTrue(info.videoUrl.contains("play/?") || info.videoUrl.contains("/play/"))
        assertTrue(!info.videoUrl.contains("playwm"))
        assertTrue(info.qualities.isNotEmpty())
        val labels = info.qualities.map { it.label }
        assertTrue(labels.contains("1080p"))
        assertTrue(labels.contains("720p"))
        assertEquals(
            "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0300fg10000d9cf677og65mtifj68ag&ratio=1080p&line=0",
            info.qualities.first().url,
        )
    }

    @Test
    fun routerDataWithoutItemListIsNotPlayable() {
        val router =
            """{"loaderData":{"video_(id)/page":{"itemId":"1","webId":"2"}}}"""
        assertEquals(false, parser.hasPlayableItem(router))
    }
}
