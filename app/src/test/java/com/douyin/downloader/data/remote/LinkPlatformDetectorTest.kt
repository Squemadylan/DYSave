package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.LinkPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkPlatformDetectorTest {
    @Test fun douyinShort() =
        assertEquals(LinkPlatform.DOUYIN, LinkPlatformDetector.detect("https://v.douyin.com/i2q93e3N/"))

    @Test fun shipinhao() =
        assertEquals(LinkPlatform.SHIPINHAO, LinkPlatformDetector.detect("https://weixin.qq.com/sph/AJfZ6d7Y37"))

    @Test fun haokan() =
        assertEquals(LinkPlatform.HAOKAN, LinkPlatformDetector.detect("https://haokan.hao123.com/v?vid=12080566475671209040"))

    @Test fun weishi() =
        assertEquals(LinkPlatform.WEISHI, LinkPlatformDetector.detect("https://isee.weishi.qq.com/ws/app-pages/share/index.html?id=xxx"))

    @Test fun weishiVideoHost() =
        assertEquals(LinkPlatform.WEISHI, LinkPlatformDetector.detect("https://video.weishi.qq.com/rSLEyOdO"))

    @Test fun weishiUrlWithTrailingChinese() {
        val raw = "老外测试“煤气罐”，结果让人意想不到 >> https://video.weishi.qq.com/rSLEyOdO微视的链接"
        assertEquals("https://video.weishi.qq.com/rSLEyOdO", LinkPlatformDetector.extractUrl(raw))
        assertEquals(LinkPlatform.WEISHI, LinkPlatformDetector.detect(raw))
    }

    @Test fun xhs() =
        assertEquals(LinkPlatform.XIAOHONGSHU, LinkPlatformDetector.detect("http://xhslink.com/a/TarVNoFYclGeb"))

    @Test fun unknown() =
        assertEquals(LinkPlatform.UNKNOWN, LinkPlatformDetector.detect("https://www.example.com/video/1"))

    @Test fun extractFromShareText() {
        val raw = "复制打开抖音，看看【连蜜.】的作品 https://v.douyin.com/i2q93e3N/ 很棒"
        assertEquals("https://v.douyin.com/i2q93e3N/", LinkPlatformDetector.extractUrl(raw))
        assertEquals(LinkPlatform.DOUYIN, LinkPlatformDetector.detect(raw))
    }

    @Test fun extractDouyinShareWithNoiseTokens() {
        val raw = "7.46 复制打开抖音，看看【阿布档案的作品】太空计划｜旅行者号 # 旅行者1号 # 旅行者 #... https://v.douyin.com/7lGSMyiiceA/ reB:/ 02/27 k@p.dN :3pm"
        assertEquals("https://v.douyin.com/7lGSMyiiceA/", LinkPlatformDetector.extractUrl(raw))
        assertEquals(LinkPlatform.DOUYIN, LinkPlatformDetector.detect(raw))
    }
}
