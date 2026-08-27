package takagi.ru.monica.steam.token.loginerror.domain

internal object SteamLoginErrorPolicy {
    private val mobileFormChallengeResults = setOf(63, 85, 101)

    fun shouldFallbackToMobileForm(
        eResult: Int?,
        httpStatusCode: Int?
    ): Boolean {
        if (eResult == null) return false
        if (httpStatusCode != null && httpStatusCode !in 200..299) return false
        return eResult in mobileFormChallengeResults
    }

    fun shouldFallbackToLegacyWeb(eResult: Int?): Boolean = eResult == 101

    fun messageForEresult(eResult: Int?): String? = when (eResult) {
        null, 1 -> null
        2 -> "Steam 暂时无法处理登录请求，请稍后重试"
        3 -> "无法连接 Steam，请检查网络后重试"
        5 -> "Steam 登录失败：账号或密码错误"
        6, 34, 50 -> "该账号已在其他位置登录，请稍后重试"
        10 -> "Steam 登录服务正忙，请稍后重试"
        16 -> "Steam 登录请求超时，请检查网络后重试"
        20 -> "Steam 登录服务暂时不可用，请稍后重试"
        25 -> "Steam 登录请求已达到限制，请稍后重试"
        29 -> "Steam 检测到重复登录请求，请勿连续点击"
        63 -> "Steam 要求完成邮箱或设备验证"
        65 -> "Steam 登录失败：验证码无效"
        66 -> "Steam 拒绝登录，且该账号无法使用邮箱验证"
        71 -> "Steam 登录验证码已过期，请重新获取"
        72 -> "当前网络地址受到 Steam 登录限制"
        73 -> "该 Steam 账号当前处于锁定状态"
        74 -> "Steam 要求先验证账号邮箱"
        76 -> "Steam 返回了无效的登录响应，请稍后重试"
        84 -> "Steam 登录请求过于频繁，请稍后重试"
        85 -> "Steam 要求输入两步验证代码"
        87 -> "Steam 暂时限制了登录请求。请停止重复尝试，等待几分钟后再登录；频繁重试会延长限制时间。"
        88 -> "Steam 登录失败：令牌验证码错误"
        89 -> "Steam 登录失败：短信或邮箱验证码错误"
        93 -> "设备时间与 Steam 不同步，请校准系统时间后重试"
        94 -> "Steam 短信验证码验证失败"
        101 -> "Steam 要求完成图形验证码"
        105 -> "当前网络地址已被 Steam 暂时封禁"
        126 -> "Steam 登录凭据已失效，请重新输入账号和密码"
        else -> "Steam 登录失败（EResult=$eResult）"
    }

    fun userMessage(
        eResult: Int?,
        rawMessage: String?,
        fallback: String = "Steam 登录请求失败，请检查网络后重试"
    ): String {
        messageForEresult(eResult)?.let { return it }
        translateKnownMessage(rawMessage)?.let { return it }
        safeChineseMessage(rawMessage)?.let { return it }
        return fallback
    }

    fun isRetryable(eResult: Int?): Boolean = eResult !in setOf(
        5, 65, 73, 74, 88, 89, 105
    )

    fun challengeMessage(rawMessage: String?): String? {
        val normalized = rawMessage?.trim()?.lowercase().orEmpty()
        return when {
            normalized.isBlank() -> null
            "mobile app" in normalized || "approve" in normalized ->
                "请在 Steam 移动应用中确认登录"
            "email" in normalized && "code" in normalized ->
                "请输入 Steam 发送到邮箱的验证码"
            "steam guard" in normalized || "authenticator" in normalized ->
                "请输入 Steam 令牌验证码"
            "captcha" in normalized -> "请输入图片中的验证码"
            else -> safeChineseMessage(rawMessage)
        }
    }

    private fun translateKnownMessage(rawMessage: String?): String? {
        val normalized = rawMessage?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return null
        return when {
            "thrott" in normalized || "too many" in normalized ||
                "rate limit" in normalized ->
                "Steam 暂时限制了登录请求，请等待几分钟后再登录"
            "invalid password" in normalized || "incorrect password" in normalized ->
                "Steam 登录失败：账号或密码错误"
            "captcha" in normalized -> "Steam 要求完成图形验证码"
            "two factor" in normalized || "two-factor" in normalized ->
                "Steam 要求输入两步验证代码"
            "timed out" in normalized || "timeout" in normalized ->
                "Steam 登录请求超时，请检查网络后重试"
            "no connection" in normalized || "unable to connect" in normalized ||
                "network" in normalized ->
                "无法连接 Steam，请检查网络后重试"
            "service unavailable" in normalized || "server busy" in normalized ->
                "Steam 登录服务暂时不可用，请稍后重试"
            else -> null
        }
    }

    private fun safeChineseMessage(rawMessage: String?): String? {
        val message = rawMessage
            ?.replace(Regex("[\\r\\n\\t]+"), " ")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (message.length > 160) return null
        if (message.startsWith("<") || message.startsWith("{") || message.startsWith("[")) {
            return null
        }
        if (
            "exception" in message.lowercase() ||
            "steam api failed" in message.lowercase() ||
            "http://" in message.lowercase() ||
            "https://" in message.lowercase()
        ) {
            return null
        }
        return message.takeIf { value -> value.any { it.code in 0x4E00..0x9FFF } }
    }
}
