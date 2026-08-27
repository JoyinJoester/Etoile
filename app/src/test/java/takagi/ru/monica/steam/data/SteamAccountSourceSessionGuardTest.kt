package takagi.ru.monica.steam.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin

class SteamAccountSourceSessionGuardTest {
    @Test
    fun sessionWritesAcceptAnExplicitOriginAndReloadMdbxAfterSourceSwitch() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountSourceRepository.kt"
        ).readText()

        assertTrue(source.contains("origin: SteamAccountSessionOrigin"))
        assertTrue(source.contains("SteamAccountSessionOrigin(SteamStorageSource.Local)"))
        assertTrue(source.contains("entryId = record.entryId"))
        assertTrue(source.contains("mdbxAccountStore.loadAccounts(source.databaseId)"))
        assertTrue(source.contains("if (_state.value.storageSource == source)"))
        val explicitUpdate = source
            .substringAfter("suspend fun updateSessionTokens(\n        origin:")
            .substringBefore("fun sessionHandle(")
        assertFalse(explicitUpdate.contains("when (val source = _state.value.storageSource)"))
    }

    @Test
    fun originStableKeysSeparateRoomAndMdbxRecords() {
        val room = SteamAccountSessionOrigin(SteamStorageSource.Local)
        val first = SteamAccountSessionOrigin(
            SteamStorageSource.Mdbx(7L),
            entryId = "one"
        )
        val second = SteamAccountSessionOrigin(
            SteamStorageSource.Mdbx(7L),
            entryId = "two"
        )

        assertTrue(room.stableKey != first.stableKey)
        assertTrue(first.stableKey != second.stableKey)
    }

    @Test
    fun storeAndLibraryProductionPathsUseTheSharedResolver() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt"
        ).readText()

        assertTrue(store.contains("private val sessionResolver: SteamAccountSessionResolver? = null"))
        assertTrue(library.contains("private val sessionResolver: SteamAccountSessionResolver? = null"))
        assertTrue(store.contains("sessionResolver.resolveOrKeep(account, force)"))
        assertTrue(library.contains("sessionResolver.resolveOrKeep(account, force)"))
        assertTrue(store.contains("sessionResolver = accountSourceRepository.sessionResolver()"))
        assertTrue(library.contains("sessionResolver = accountSourceRepository.sessionResolver()"))
        assertFalse(store.contains("SteamSessionRefreshService"))
        assertFalse(library.contains("SteamSessionRefreshService"))
    }

    @Test
    fun sourceRepositoryExposesTheSameResolverToSocialFeatures() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountSourceRepository.kt"
        ).readText()
        val factory = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatViewModelFactory.kt"
        ).readText()

        assertTrue(source.contains("fun sessionResolver(): SteamAccountSessionResolver"))
        assertTrue(source.contains("sessionManager.resolve(handle, forceRefresh).account"))
        assertTrue(factory.contains("accountSourceRepository.sessionResolver()"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
