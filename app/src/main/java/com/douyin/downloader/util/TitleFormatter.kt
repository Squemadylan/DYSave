package com.douyin.downloader.util

/**
 * 工具：把视频/图文的标题处理成下载页签可展示 / 落盘使用的简短文字。
 * - 去除 emoji 表情
 * - 去除首尾空白和换行
 * - 去除文件名非法字符（/ \ : * ? " < > |）
 * - 超过 [maxChars] 个字符时截断为前 [maxChars] 字
 * - 如果过滤后为空，回退到 fallback
 */
object TitleFormatter {

    fun format(title: String, maxChars: Int = 6, fallback: String = "下载内容"): String {
        return clean(title, maxChars, fallback)
    }

    /**
     * 给下载中心 / 任务列表卡片用的展示名。
     * 规则：`{author}_{title前N字}[suffix]`
     * - suffix 会原样拼接（不再做清洗），但**始终保留**，方便区分「图1」「合成」等
     * - 总长不超过 [maxTotal]；超出时先砍 stem（保留 suffix）
     * - 用于人眼阅读，不参与文件落盘（落盘走 [formatFilenameStem]）
     */
    fun formatDisplayName(
        author: String,
        title: String,
        suffix: String = "",
        titleChars: Int = 6,
        maxTotal: Int = 32,
    ): String {
        val cleanAuthor = clean(author, maxChars = 10, fallback = "")
        val cleanTitle = clean(title, maxChars = titleChars, fallback = "")

        val stem = when {
            cleanAuthor.isNotEmpty() && cleanTitle.isNotEmpty() -> "${cleanAuthor}_$cleanTitle"
            cleanAuthor.isNotEmpty() -> cleanAuthor
            cleanTitle.isNotEmpty() -> cleanTitle
            else -> "douyin_content"
        }

        val withSuffix = if (suffix.isEmpty()) stem else stem + suffix
        return if (withSuffix.length > maxTotal) {
            val keep = (maxTotal - suffix.length).coerceAtLeast(1)
            stem.substring(0, keep) + suffix
        } else {
            withSuffix
        }
    }

    /**
     * 生成最终落盘的文件名（不含扩展名）。
     * 规则：`{author}_{title前N字}`，全部经过 clean() 清洗。
     * - author 为空时退化为 `{title前N字}`
     * - title 为空时退化为 `douyin_{author}`
     * - 两者都为空时退化为 `douyin_content`
     * - 截断后总长度不超过 [maxTotal]（默认 32），避免部分文件系统限制
     */
    fun formatFilenameStem(
        author: String,
        title: String,
        titleChars: Int = 6,
        maxTotal: Int = 32,
    ): String {
        val cleanAuthor = clean(author, maxChars = 10, fallback = "")
        val cleanTitle = clean(title, maxChars = titleChars, fallback = "")

        val combined = when {
            cleanAuthor.isNotEmpty() && cleanTitle.isNotEmpty() -> "${cleanAuthor}_$cleanTitle"
            cleanAuthor.isNotEmpty() -> cleanAuthor
            cleanTitle.isNotEmpty() -> cleanTitle
            else -> "douyin_content"
        }

        return if (combined.length > maxTotal) combined.substring(0, maxTotal) else combined
    }

    private fun clean(input: String, maxChars: Int, fallback: String): String {
        if (input.isEmpty()) return fallback
        val cleaned = input
            .replace(EMOJI_REGEX, "")
            .replace(ILLEGAL_CHARS_REGEX, "")
            .replace(CONTROL_CHARS_REGEX, "")
            .trim()
        if (cleaned.isEmpty()) return fallback
        return if (cleaned.length > maxChars) cleaned.substring(0, maxChars) else cleaned
    }

    // 匹配大部分 emoji。
    // 注意：Java 的 java.util.regex.Pattern 不支持 \u{XXXX}，必须用 \uXXXX（4 位 16 进制）。
    // 这里覆盖：
    //   U+2600..U+27BF   杂项符号 / 装饰符号（BMP 内的天气、箭头、棋盘等）
    //   区域指示符（旗帜） 代理对 \uD83C\uDDE6..\uD83C\uDDFF
    //   U+1F300..U+1F9FF SMP 符号、表情、补充象形，代理对 \uD83C\uDF00..\uD83E\uDDFF
    private val EMOJI_REGEX = Regex(
        "[\\u2600-\\u27BF" +
            "\\uD83C\\uDDE6-\\uD83C\\uDDFF" +
            "\\uD83C\\uDF00-\\uD83E\\uDDFF]"
    )

    // 文件名非法字符：/ \ : * ? " < > | 以及换行 / 制表
    private val ILLEGAL_CHARS_REGEX = Regex("[/\\\\:*?\"<>|]+")

    // 控制字符（换行、回车、制表等）
    private val CONTROL_CHARS_REGEX = Regex("[\\p{Cntrl}]+")
}
