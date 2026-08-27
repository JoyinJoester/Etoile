package takagi.ru.monica.steam.friends.voice.background

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamVoiceMediaIntegrationGuardTest {
    @Test
    fun webViewMediaUsesSteamOpusPolicyAndObservesTheOutboundTrack() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/media/SteamVoiceWebViewEngine.kt"
        ).readText()

        assertTrue(source.contains("minptime=10;useinbandfec=1;usedtx=1"))
        assertTrue(source.contains("report.type === \"outbound-rtp\""))
        assertTrue(source.contains("onMediaStats"))
        assertTrue(source.contains("onLocalMediaReady"))
        assertTrue(source.contains("descriptionChain"))
        assertTrue(source.contains("onEngineTerminated"))
    }

    @Test
    fun runtimeRebuildsMediaAfterIceRendererAndOutboundFailures() {
        val runtime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/presentation/SteamVoiceCallRuntime.kt"
        ).readText()

        assertTrue(runtime.contains("SteamVoiceRecoveryBudget"))
        assertTrue(runtime.contains("SteamVoiceMediaHealthMonitor"))
        assertTrue(runtime.contains("localMediaReady"))
        assertTrue(runtime.contains("OUTBOUND_STALLED"))
        assertTrue(runtime.contains("ICE_DISCONNECT_GRACE_MILLIS"))
        assertTrue(runtime.contains("restartMediaEngine"))
    }

    @Test
    fun audioSessionExposesSelectableCommunicationRoutes() {
        val audioSession = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/media/SteamVoiceAudioSession.kt"
        ).readText()
        val models = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/domain/SteamVoiceModels.kt"
        ).readText()
        val voiceUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/ui/SteamVoiceCallUi.kt"
        ).readText()

        assertTrue(models.contains("SteamVoiceAudioRoute"))
        assertTrue(audioSession.contains("registerAudioDeviceCallback"))
        assertTrue(audioSession.contains("setCommunicationDevice"))
        assertTrue(audioSession.contains("clearCommunicationDevice"))
        assertTrue(voiceUi.contains("VoiceOutputMenu"))
        assertTrue(voiceUi.contains("DropdownMenu"))
    }

    @Test
    fun manifestDeclaresMicrophonePlaybackForegroundService() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(manifest.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertTrue(manifest.contains(".steam.friends.voice.background.SteamVoiceCallService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone|mediaPlayback\""))
        assertFalse(manifest.contains(".steam.friends.voice.background.SteamVoiceActionReceiver"))
    }

    @Test
    fun mediaUsesPlatformWebRtcWithoutAddingTheLargeNativeWebRtcDependency() {
        val engine = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/media/SteamVoiceWebViewEngine.kt"
        ).readText()
        val dependencies = projectFile("app/build.gradle").readText()

        assertTrue(engine.contains("navigator.mediaDevices.getUserMedia"))
        assertTrue(engine.contains("new RTCPeerConnection"))
        assertTrue(engine.contains("RESOURCE_AUDIO_CAPTURE"))
        assertTrue(engine.contains("onRenderProcessGone"))
        assertFalse(dependencies.contains("webrtc-sdk"))
        assertFalse(dependencies.contains("org.webrtc"))
    }

    @Test
    fun incomingCallsUseHighPriorityNotificationActionsWithoutStartingTheMicService() {
        val publisher = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/background/SteamVoiceNotificationPublisher.kt"
        ).readText()
        val runtime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/presentation/SteamVoiceCallRuntime.kt"
        ).readText()

        assertTrue(publisher.contains("IMPORTANCE_HIGH"))
        assertTrue(publisher.contains("PendingIntent.getForegroundService"))
        assertTrue(runtime.contains("notificationPublisher.post(_state.value)"))
        assertTrue(runtime.indexOf("gateway.answerDirectVoice") < runtime.indexOf("initialVoiceChatId = request.voiceChatId"))
    }

    @Test
    fun foregroundCallOwnsAudioFocusAndWakeLockOnlyWhileMediaIsActive() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/background/SteamVoiceCallService.kt"
        ).readText()

        assertTrue(service.contains("PowerManager.PARTIAL_WAKE_LOCK"))
        assertTrue(service.contains("startActiveResources"))
        assertTrue(service.contains("stopActiveResources"))
        assertTrue(service.contains("SteamVoiceCallServiceMode.INCOMING"))
        assertTrue(service.contains("ACTION_ACCEPT -> runtime.acceptIncomingFromNotification()"))
    }

    @Test
    fun chatUiUsesTheSharedVoiceRuntimeAcrossThreadsListsAndChannelManagement() {
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val selectedContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()
        val chatDialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreenDialogs.kt"
        ).readText()
        val directThread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val conversationList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamConversationList.kt"
        ).readText()
        val groupThread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val groupGateway = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/domain/SteamGroupChatGateway.kt"
        ).readText()

        assertTrue(selectedContent.contains("voiceRuntime.startDirect"))
        assertTrue(selectedContent.contains("voiceRuntime.startGroup"))
        assertTrue(chatDialogs.contains("voiceRuntime.acceptIncoming"))
        assertTrue(directThread.contains("SteamVoiceStatusBanner"))
        assertTrue(conversationList.contains("active-voice-call"))
        assertTrue(groupThread.contains("SteamVoiceChannelPanel"))
        assertFalse(groupGateway.contains("SteamGroupChatVoiceSession"))
        assertFalse(groupGateway.contains("joinVoiceChat"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile).canonicalFile
        }
        return File(directory, path)
    }
}
