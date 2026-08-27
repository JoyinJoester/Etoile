package takagi.ru.monica.steam.friends.groupchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoomType
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType

@Composable
internal fun SteamGroupChannelManagement(
    group: SteamGroupChatSummary,
    canEdit: Boolean,
    actionLoading: Boolean,
    voiceState: SteamVoiceCallState,
    onCreate: (String, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (String, String?) -> Unit,
    onJoinVoice: (String) -> Unit,
    onLeaveVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameRoom by remember { mutableStateOf<SteamGroupChatRoom?>(null) }
    var deleteRoom by remember { mutableStateOf<SteamGroupChatRoom?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("频道", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "文字和语音频道遵循 Steam 官方权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEdit) {
                    OutlinedButton(
                        onClick = { showCreate = true },
                        enabled = !actionLoading
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Text("创建", Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (group.rooms.isEmpty()) {
                Text("Steam 尚未返回频道", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                group.rooms
                    .sortedWith(compareBy<SteamGroupChatRoom> { it.sortOrder }.thenBy { it.chatId })
                    .forEachIndexed { index, room ->
                        val localVoiceRoom = voiceState.isActive &&
                            voiceState.target?.type == SteamVoiceTargetType.GROUP &&
                            voiceState.target?.groupId == group.groupId &&
                            voiceState.target?.chatId == room.chatId
                        val localVoiceMemberCount = if (localVoiceRoom) {
                            (voiceState.participants.map { it.steamId } + voiceState.accountSteamId)
                                .filter(String::isNotBlank)
                                .distinct()
                                .size
                        } else 0
                        ChannelManagementRow(
                            room = room,
                            canEdit = canEdit,
                            actionLoading = actionLoading,
                            canMoveUp = index > 0,
                            canMoveDown = index < group.rooms.lastIndex,
                            voiceJoined = localVoiceRoom,
                            voiceConnecting = localVoiceRoom && !voiceState.isConnected,
                            voiceBusy = voiceState.isActive && !localVoiceRoom,
                            voiceMemberCount = maxOf(
                                room.voiceMemberSteamIds.size,
                                localVoiceMemberCount
                            ),
                            onRename = { renameRoom = room },
                            onDelete = { deleteRoom = room },
                            onMoveUp = {
                                val moveAfter = group.rooms
                                    .sortedWith(compareBy<SteamGroupChatRoom> { it.sortOrder }.thenBy { it.chatId })
                                    .getOrNull(index - 2)
                                onReorder(room.chatId, moveAfter?.chatId)
                            },
                            onMoveDown = {
                                val next = group.rooms
                                    .sortedWith(compareBy<SteamGroupChatRoom> { it.sortOrder }.thenBy { it.chatId })
                                    .getOrNull(index + 1)
                                onReorder(room.chatId, next?.chatId)
                            },
                            onJoinVoice = { onJoinVoice(room.chatId) },
                            onLeaveVoice = onLeaveVoice
                        )
                    }
            }
        }
    }

    if (showCreate) {
        CreateChannelDialog(
            saving = actionLoading,
            onDismiss = { if (!actionLoading) showCreate = false },
            onCreate = { name, voice ->
                onCreate(name, voice)
                showCreate = false
            }
        )
    }
    renameRoom?.let { room ->
        RenameChannelDialog(
            room = room,
            saving = actionLoading,
            onDismiss = { if (!actionLoading) renameRoom = null },
            onRename = { name ->
                onRename(room.chatId, name)
                renameRoom = null
            }
        )
    }
    deleteRoom?.let { room ->
        AlertDialog(
            onDismissRequest = { if (!actionLoading) deleteRoom = null },
            title = { Text("删除频道？") },
            text = { Text("将从 Steam 群组中删除“${room.name}”。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(room.chatId)
                        deleteRoom = null
                    },
                    enabled = !actionLoading && group.rooms.size > 1
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteRoom = null }, enabled = !actionLoading) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ChannelManagementRow(
    room: SteamGroupChatRoom,
    canEdit: Boolean,
    actionLoading: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    voiceJoined: Boolean,
    voiceConnecting: Boolean,
    voiceBusy: Boolean,
    voiceMemberCount: Int,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onJoinVoice: () -> Unit,
    onLeaveVoice: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(room.type.icon, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(room.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (room.type == SteamGroupChatRoomType.VOICE) "语音频道" else "文字频道",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEdit) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp && !actionLoading) {
                        Icon(Icons.Default.ArrowUpward, "上移")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown && !actionLoading) {
                        Icon(Icons.Default.ArrowDownward, "下移")
                    }
                    IconButton(onClick = onRename, enabled = !actionLoading) {
                        Icon(Icons.Default.Edit, "重命名")
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = !actionLoading,
                        modifier = Modifier.size(40.dp)
                    ) { Icon(Icons.Default.DeleteOutline, "删除") }
                }
            }
            if (room.type == SteamGroupChatRoomType.VOICE) {
                FilterChip(
                    selected = voiceJoined,
                    onClick = if (voiceJoined) onLeaveVoice else onJoinVoice,
                    enabled = !actionLoading && !voiceConnecting && (!voiceBusy || voiceJoined),
                    label = {
                        Text(
                            when {
                                voiceConnecting -> "正在加入"
                                voiceJoined -> "退出语音"
                                voiceBusy -> "已有进行中的通话"
                                voiceMemberCount > 0 -> "加入语音 · $voiceMemberCount 人"
                                else -> "加入语音"
                            }
                        )
                    },
                    leadingIcon = {
                        if (voiceConnecting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Phone, null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CreateChannelDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建频道") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 64) name = it },
                    label = { Text("频道名称") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("允许语音", Modifier.weight(1f))
                    Switch(checked = voice, onCheckedChange = { voice = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim(), voice) }, enabled = name.isNotBlank() && !saving) {
                Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun RenameChannelDialog(
    room: SteamGroupChatRoom,
    saving: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(room.chatId) { mutableStateOf(room.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名频道") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 64) name = it },
                label = { Text("频道名称") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank() && !saving) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

private val SteamGroupChatRoomType.icon: ImageVector
    get() = when (this) {
        SteamGroupChatRoomType.TEXT -> Icons.Default.ChatBubbleOutline
        SteamGroupChatRoomType.VOICE -> Icons.Default.Phone
    }
