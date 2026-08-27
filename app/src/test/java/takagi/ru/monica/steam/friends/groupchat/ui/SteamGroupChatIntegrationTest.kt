package takagi.ru.monica.steam.friends.groupchat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGroupChatIntegrationTest {
    @Test
    fun groupChatIsAnIndependentSteamBackedModule() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/groupchat")
        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("presentation").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)

        val service = root.resolve("data/SteamGroupChatService.kt").readText()
        val attachmentTargets = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/data/SteamChatAttachmentTargetFields.kt"
        ).readText()
        assertTrue(service.contains("ChatRoom.\$method#1"))
        assertTrue(service.contains("CreateChatRoomGroup"))
        assertTrue(service.contains("GetMyChatRoomGroups"))
        assertTrue(service.contains("GetMessageHistory"))
        assertTrue(service.contains("SendChatMessage"))
        assertTrue(service.contains("InviteFriendToChatRoomGroup"))
        assertTrue(attachmentTargets.contains("chat_group_id"))
        assertTrue(attachmentTargets.contains("chat_id"))
    }

    @Test
    fun chatPageExposesGroupListCreateInviteAndFullScreenThread() {
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val chatRoot = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatRootContent.kt"
        ).readText()
        val selectedContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()
        val chatDialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreenDialogs.kt"
        ).readText()
        val threadLifecycle = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThreadLifecycle.kt"
        ).readText()
        val groupList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatList.kt"
        ).readText()
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val dialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatDialogs.kt"
        ).readText()

        assertTrue(chatScreen.contains("SteamGroupChatViewModel"))
        assertTrue(chatRoot.contains("SteamConversationList("))
        assertFalse(chatRoot.contains("SteamGroupChatList("))
        assertTrue(selectedContent.contains("SteamGroupChatThreadHost("))
        assertTrue(chatDialogs.contains("SteamGroupChatDialogsHost("))
        assertTrue(chatScreen.contains("groupChatState.selectedChatId != null"))
        assertTrue(chatScreen.contains("SteamChatThreadLifecycle("))
        assertTrue(threadLifecycle.contains("richMediaViewModel.selectGroupRoom"))
        assertTrue(threadLifecycle.contains("groupChatViewModel.refreshThread()"))
        assertTrue(groupList.contains("SteamExpressivePullToRefresh"))
        assertTrue(groupList.contains("ExtendedFloatingActionButton"))
        assertTrue(thread.contains("SteamGroupChatThread("))
        assertTrue(thread.contains("SteamChatRichMessageContent"))
        assertTrue(thread.contains("SteamChatComposer("))
        assertTrue(thread.contains("onUploadAttachment"))
        assertTrue(thread.contains("steamWindowTopPadding()"))
        assertTrue(dialogs.contains("SteamCreateGroupDialog("))
        assertTrue(dialogs.contains("SteamInviteFriendDialog("))
        assertTrue(dialogs.contains("FriendSelectionList"))
        assertTrue(chatScreen.contains("groupChatViewModel.openRoom(createdGroup.groupId"))
    }

    @Test
    fun channelNavigationUsesMonicaQuickFilterAndPreferredEntryRoom() {
        val quickFilter = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChannelQuickFilter.kt"
        ).readText()
        val conversationList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamConversationList.kt"
        ).readText()
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()

        assertTrue(quickFilter.contains("MonicaExpressiveFilterChip"))
        assertTrue(quickFilter.contains("rooms.size <= 1"))
        assertTrue(thread.contains("SteamGroupChannelQuickFilter("))
        assertTrue(conversationList.contains("group.preferredChatId"))
    }

    @Test
    fun groupInfoExposesOfficialChannelManagementActions() {
        val management = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChannelManagement.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/data/SteamGroupChatService.kt"
        ).readText()
        val voiceService = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/data/SteamVoiceService.kt"
        ).readText()
        val infoScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()

        assertTrue(management.contains("onCreate:"))
        assertTrue(management.contains("onDelete:"))
        assertTrue(management.contains("onReorder:"))
        assertTrue(management.contains("onJoinVoice:"))
        assertTrue(infoScreen.contains("onCreateChannel"))
        assertTrue(service.contains("CreateChatRoom"))
        assertTrue(service.contains("DeleteChatRoom"))
        assertTrue(voiceService.contains("JoinVoiceChat"))
    }

    @Test
    fun groupAdminScreenExposesInvitesRolesBansAndMemberActions() {
        val adminScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupAdminScreen.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/data/SteamGroupChatService.kt"
        ).readText()
        val selectedContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()

        assertTrue(adminScreen.contains("分享链接"))
        assertTrue(adminScreen.contains("已邀请账户"))
        assertTrue(adminScreen.contains("角色与权限"))
        assertTrue(adminScreen.contains("封禁账户"))
        assertTrue(adminScreen.contains("MemberRolesDialog"))
        assertTrue(service.contains("GetInviteLinksForGroup"))
        assertTrue(service.contains("SetUserBanState"))
        assertTrue(service.contains("ReplaceRoleActions"))
        assertTrue(selectedContent.contains("SteamGroupAdminScreen("))
    }

    @Test
    fun groupMessagesExposeSteamReactionReportCopyAndDeleteActions() {
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/data/SteamGroupChatService.kt"
        ).readText()

        assertTrue(thread.contains("SteamChatMessageActionMenu("))
        assertTrue(thread.contains("SteamChatReactionPicker("))
        assertTrue(thread.contains("SteamChatReportDialog("))
        assertTrue(thread.contains("onDeleteMessage"))
        assertTrue(service.contains("UpdateMessageReaction"))
        assertTrue(service.contains("ReportMessage"))
        assertTrue(service.contains("DeleteChatMessages"))
    }

    @Test
    fun inviteDialogPinsActionsInsideSafeScreenBounds() {
        val dialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatDialogs.kt"
        ).readText()

        assertTrue(dialogs.contains("usePlatformDefaultWidth = false"))
        assertTrue(dialogs.contains("windowInsetsPadding(WindowInsets.safeDrawing)"))
        assertTrue(dialogs.contains("Modifier.weight(1f)"))
    }

    @Test
    fun groupThreadResizesForImeWithoutPanningTheHeader() {
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(thread.contains("imePadding()"))
        assertTrue(thread.contains("steamWindowBottomPadding(suppressWhenImeVisible = true)"))
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    @Test
    fun everyGroupConversationSurfaceUsesTheDedicatedAvatarLoader() {
        val groupList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatList.kt"
        ).readText()
        val conversationList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamConversationList.kt"
        ).readText()
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val editor = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamGroupAvatarEditor.kt"
        ).readText()

        assertTrue(groupList.contains("SteamGroupAvatarImage("))
        assertTrue(conversationList.contains("SteamGroupAvatarImage("))
        assertTrue(thread.contains("SteamGroupAvatarImage("))
        assertTrue(editor.contains("SteamGroupAvatarImage("))
        assertTrue(groupList.contains("members = groupMembers"))
        assertTrue(conversationList.contains("members = entry.groupMembers"))
        assertTrue(thread.contains("members = members"))
        assertTrue(editor.contains("members = members"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
