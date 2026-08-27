package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.store.purchase.data.SteamStorePackageMetadataParser

class SteamStorePackageMetadataParserTest {
    @Test
    fun usesOfficialPackageImageWhenSteamProvidesOne() {
        val payload = """{"469":{"success":true,"data":{"name":"The Orange Box","page_image":"https://cdn.example/subs/469/header.jpg","small_logo":"https://cdn.example/subs/469/capsule.jpg"}}}"""

        val metadata = SteamStorePackageMetadataParser.parse(469, payload)

        assertEquals("The Orange Box", metadata?.name)
        assertEquals("https://cdn.example/subs/469/header.jpg", metadata?.imageUrl)
    }
}
