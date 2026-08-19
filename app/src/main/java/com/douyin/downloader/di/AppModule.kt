package com.douyin.downloader.di

import android.content.Context
import androidx.room.Room
import com.douyin.downloader.data.local.AppDatabase
import com.douyin.downloader.data.local.DownloadTaskDao
import com.douyin.downloader.data.local.HistoryDao
import com.douyin.downloader.data.local.SessionManager
import com.douyin.downloader.data.remote.DouyinCookieManager
import com.douyin.downloader.data.remote.MemoryCookieJar
import com.douyin.downloader.data.remote.ShizukuCookieFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: MemoryCookieJar): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        // 分享页下发的 ttwid 等需带到 iteminfo，否则易 encrypt_data_miss / 空响应；
        // 同时承载用户手动填入的抖音 Cookie（见 DouyinApi.ensureCookieSeeded）
        .cookieJar(cookieJar)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dy_history")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            // 任何未知旧版本都丢表重建——宁可丢历史也不闪退。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideDownloadTaskDao(db: AppDatabase): DownloadTaskDao = db.downloadTaskDao()

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        SessionManager(context)

    @Provides
    @Singleton
    fun provideDouyinCookieManager(@ApplicationContext context: Context): DouyinCookieManager =
        DouyinCookieManager(context)

    @Provides
    @Singleton
    fun provideShizukuFetcher(@ApplicationContext context: Context): ShizukuCookieFetcher =
        ShizukuCookieFetcher(context)
}
