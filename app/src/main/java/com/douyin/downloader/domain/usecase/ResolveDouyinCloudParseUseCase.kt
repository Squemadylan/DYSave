package com.douyin.downloader.domain.usecase

import com.douyin.downloader.data.repository.ContentRepository
import javax.inject.Inject

class ResolveDouyinCloudParseUseCase @Inject constructor(
    private val repository: ContentRepository,
) {
    suspend operator fun invoke(rawUrl: String): String = repository.resolveDouyinCloudParse(rawUrl)
}
