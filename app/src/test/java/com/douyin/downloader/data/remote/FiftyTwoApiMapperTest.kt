package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.ContentInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiftyTwoApiMapperTest {
    @Test
    fun mapsVideo() {
        val data = JSONObject(
            """{"work_title":"t","work_author":"a","work_cover":"c.jpg","work_type":"video","work_url":"https://cdn.example/v.mp4"}"""
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "https://v.douyin.com/x/")
        assertTrue(info is ContentInfo.Video)
        info as ContentInfo.Video
        assertEquals("t", info.title)
        assertEquals("https://cdn.example/v.mp4", info.videoUrl)
    }

    @Test
    fun mapsImageGalleryFromArray() {
        val data = JSONObject(
            """{"work_title":"t","work_author":"a","work_cover":"c.jpg","work_type":"image","work_url":["https://cdn.example/1.jpg","https://cdn.example/2.jpg"],"music":{"url":"https://cdn.example/m.mp3"}}"""
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "http://xhslink.com/a/x")
        assertTrue(info is ContentInfo.ImageGallery)
        info as ContentInfo.ImageGallery
        assertEquals(2, info.images.size)
        assertEquals("https://cdn.example/m.mp3", info.musicUrl)
    }

    @Test
    fun mapsShipinhaoVideoFields() {
        val data = JSONObject(
            """{"video_title":"看懂就回不去了！","video_author":"宗金儿","video_cover":"https://cdn.example/c.jpg","video_url":"https://finder.video.qq.com/v.mp4"}"""
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "https://weixin.qq.com/sph/APAxXlwnzO")
        assertTrue(info is ContentInfo.Video)
        info as ContentInfo.Video
        assertEquals("看懂就回不去了！", info.title)
        assertEquals("宗金儿", info.author)
        assertEquals("https://finder.video.qq.com/v.mp4", info.videoUrl)
        assertEquals("https://cdn.example/c.jpg", info.cover)
    }

    @Test
    fun mapsHaokanPlayUrlList() {
        val data = JSONObject(
            """
            {
              "video_title":"好看标题",
              "video_authorName":"爱尔兰小龙包",
              "video_cover":"https://cdn.example/c.jpg",
              "video_playUrl":[
                {"key":"sd","title":"标清","url":"http://cdn.example/sd.mp4"},
                {"key":"hd","title":"高清","url":"http://cdn.example/hd.mp4"},
                {"key":"sc","title":"超清","url":"http://cdn.example/sc.mp4"}
              ]
            }
            """.trimIndent()
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "https://haokan.hao123.com/v?vid=1")
        assertTrue(info is ContentInfo.Video)
        info as ContentInfo.Video
        assertEquals("好看标题", info.title)
        assertEquals("爱尔兰小龙包", info.author)
        assertEquals("http://cdn.example/sc.mp4", info.videoUrl)
        assertEquals(3, info.qualities.size)
        assertEquals("超清", info.qualities[0].label)
    }

    @Test
    fun mapsWeishiVideoFields() {
        val data = JSONObject(
            """{"video_title":"老外测试煤气罐","video_authorName":"小李不知道","video_cover":"https://cdn.example/c.jpg","video_url":"http://v.weishi.qq.com/v.mp4"}"""
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "https://video.weishi.qq.com/rSLEyOdO")
        assertTrue(info is ContentInfo.Video)
        info as ContentInfo.Video
        assertEquals("老外测试煤气罐", info.title)
        assertEquals("小李不知道", info.author)
        assertEquals("http://v.weishi.qq.com/v.mp4", info.videoUrl)
    }

    @Test
    fun mapsWorkAuthorNameAndImagesField() {
        val data = JSONObject(
            """{"work_title":"图文","work_authorName":"作者甲","work_cover":"c.jpg","images":["https://cdn.example/1.jpg","https://cdn.example/2.jpg"]}"""
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "http://xhslink.com/a/y")
        assertTrue(info is ContentInfo.ImageGallery)
        info as ContentInfo.ImageGallery
        assertEquals("作者甲", info.author)
        assertEquals(2, info.images.size)
    }
}
