package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.DownloadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 抖音开放平台 Open API 调用封装
 * 支持 OAuth 授权流程和已授权用户的数据接口调用
 *
 * 官方 API 文档：https://open.douyin.com/platform/
 */
@Singleton
class DouyinOpenApi @Inject constructor(
    private val client: OkHttpClient,
) {
    companion object {
        const val BASE_URL = "https://open.douyin.com"

        // OAuth endpoints
        const val URL_ACCESS_TOKEN = "$BASE_URL/oauth/access_token/"
        const val URL_REFRESH_TOKEN = "$BASE_URL/oauth/refresh_token/"
        const val URL_USER_INFO = "$BASE_URL/oauth/user_info/"

        // Video data endpoint
        const val URL_VIDEO_DATA = "$BASE_URL/api/apps/v1/video/query/"

        // 移动应用（Client Key / Secret）方式获取 access_token
        const val URL_CLIENT_TOKEN = "$BASE_URL/oauth/client_token/"
    }

    /**
     * 通过授权码换取用户 access_token
     *
     * @param clientKey    应用的 Client Key
     * @param clientSecret 应用的 Client Secret
     * @param code         授权码（由 SDK.authorize() 获取）
     * @param grantType    固定为 authorization_code
     */
    suspend fun exchangeAccessToken(
        clientKey: String,
        clientSecret: String,
        code: String,
        grantType: String = "authorization_code",
    ): TokenResponse = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("client_key", clientKey)
            put("client_secret", clientSecret)
            put("code", code)
            put("grant_type", grantType)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(URL_ACCESS_TOKEN)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException.DownloadFailed("获取 access_token 失败，HTTP ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw DownloadException.DownloadFailed("服务器响应为空")
            parseTokenResponse(bodyStr)
        }
    }

    /**
     * 通过 refresh_token 刷新 access_token
     */
    suspend fun refreshAccessToken(
        clientKey: String,
        clientSecret: String,
        refreshToken: String,
    ): TokenResponse = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("client_key", clientKey)
            put("client_secret", clientSecret)
            put("refresh_token", refreshToken)
            put("grant_type", "refresh_token")
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(URL_REFRESH_TOKEN)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException.DownloadFailed("刷新 access_token 失败，HTTP ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw DownloadException.DownloadFailed("服务器响应为空")
            parseTokenResponse(bodyStr)
        }
    }

    /**
     * 获取用户基本信息（需要已授权的 access_token）
     *
     * @param accessToken 已授权的 access_token
     */
    suspend fun getUserInfo(accessToken: String): UserInfoResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL_USER_INFO)
            .get()
            .header("access-token", accessToken)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException.DownloadFailed("获取用户信息失败，HTTP ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw DownloadException.DownloadFailed("服务器响应为空")
            parseUserInfoResponse(bodyStr)
        }
    }

    /**
     * 通过 client_credentials 方式获取 client_token（无需用户授权）
     * 可用于调用部分不要求用户授权的公开接口
     */
    suspend fun getClientToken(
        clientKey: String,
        clientSecret: String,
    ): TokenResponse = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("client_key", clientKey)
            put("client_secret", clientSecret)
            put("grant_type", "client_credential")
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(URL_CLIENT_TOKEN)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException.DownloadFailed("获取 client_token 失败，HTTP ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw DownloadException.DownloadFailed("服务器响应为空")
            parseTokenResponse(bodyStr)
        }
    }

    private fun parseTokenResponse(body: String): TokenResponse {
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: JSONObject(body)
        val errorCode = data.optInt("error_code", 0)
        if (errorCode != 0) {
            val desc = data.optString("description", "获取 token 失败")
            throw DownloadException.DownloadFailed("抖音 API 错误 [$errorCode]：$desc")
        }
        return TokenResponse(
            accessToken = data.optString("access_token", ""),
            refreshToken = data.optString("refresh_token", ""),
            expiresIn = data.optLong("expires_in", 0),
            refreshExpiresIn = data.optLong("refresh_expires_in", 0),
            openId = data.optString("open_id", ""),
            scope = data.optString("scope", ""),
        )
    }

    private fun parseUserInfoResponse(body: String): UserInfoResponse {
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: JSONObject(body)
        val errorCode = data.optInt("error_code", 0)
        if (errorCode != 0) {
            val desc = data.optString("description", "获取用户信息失败")
            throw DownloadException.DownloadFailed("抖音 API 错误 [$errorCode]：$desc")
        }
        return UserInfoResponse(
            openId = data.optString("open_id", ""),
            nickname = data.optString("nickname", ""),
            avatar = data.optString("avatar", ""),
            unionId = data.optString("union_id", ""),
        )
    }

    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val refreshExpiresIn: Long,
        val openId: String,
        val scope: String,
    )

    data class UserInfoResponse(
        val openId: String,
        val nickname: String,
        val avatar: String,
        val unionId: String,
    )
}