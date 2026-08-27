package takagi.ru.monica.steam.friends.groupchat.domain

internal fun steamGroupEventText(eventType: Int, stringParam: String): String = when (eventType) {
    1 -> stringParam.takeIf(String::isNotBlank)
        ?.let { "将群聊名称修改为「$it」" }
        ?: "修改了群聊名称"
    2 -> stringParam.ifBlank { "加入了群聊" }
    3 -> stringParam.ifBlank { "离开了群聊" }
    4 -> stringParam.ifBlank { "移除了一位成员" }
    5 -> stringParam.ifBlank { "邀请了一位成员加入群聊" }
    9 -> stringParam.takeIf(String::isNotBlank)
        ?.let { "将群简介修改为「$it」" }
        ?: "移除了群简介"
    10 -> stringParam.ifBlank { "修改了群头像" }
    11 -> stringParam.ifBlank { "更新了群聊信息" }
    12 -> memberCountText(stringParam, "加入了群聊")
    13 -> memberCountText(stringParam, "离开了群聊")
    14 -> memberCountText(stringParam, "受邀加入群聊")
    else -> stringParam.ifBlank { "更新了群聊信息" }
}

private fun memberCountText(rawCount: String, action: String): String {
    val count = rawCount.toIntOrNull()?.takeIf { it > 0 }
    return if (count != null) "$count 位成员$action" else "有成员$action"
}
