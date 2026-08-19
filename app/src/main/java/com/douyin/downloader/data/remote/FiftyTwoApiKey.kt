package com.douyin.downloader.data.remote

import android.util.Base64

internal object FiftyTwoApiKey {
    // Base64 of the key, split so a simple strings dump is less obvious
    private val p1 = "OU5nbWhDMVYwcWxU"
    private val p2 = "bDRMTGVsUThqSm43WGs="

    val value: String
        get() = String(Base64.decode(p1 + p2, Base64.DEFAULT), Charsets.UTF_8)
}
