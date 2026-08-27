package takagi.ru.monica.steam.library.screenshots.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGameScreenshotsParserTest {
    @Test
    fun parsesBackgroundAndInlineImagesAndKeepsOnlySelectedGame() {
        val result = SteamGameScreenshotsParser.parse(
            html = """
                <html><body>
                  <a class="profile_media_item" data-appid="730"
                     data-publishedfileid="3780926562" data-desired-aspect="1.7777778">
                    <div class="imgWallItem" style="background-image: url('https://images.steamusercontent.com/ugc/1/A/?imw=640&amp;letterbox=true');"></div>
                  </a>
                  <a class="profile_media_item" data-appid="730"
                     data-publishedfileid="3777288560" data-desired-aspect="1.3333333">
                    <div class="imgWallItem">
                      <img src="https://images.steamusercontent.com/ugc/2/B/?imw=1024" />
                    </div>
                  </a>
                  <a class="profile_media_item" data-appid="570"
                     data-publishedfileid="999">
                    <div class="imgWallItem" style="background-image: url('https://images.steamusercontent.com/ugc/3/C/');"></div>
                  </a>
                  <form id="MoreContentForm"></form>
                </body></html>
            """.trimIndent(),
            expectedAppId = 730
        )

        assertEquals(listOf("3780926562", "3777288560"), result.screenshots.map { it.publishedFileId })
        assertEquals(
            "https://images.steamusercontent.com/ugc/1/A/",
            result.screenshots.first().imageUrl
        )
        assertEquals(
            "https://images.steamusercontent.com/ugc/2/B/",
            result.screenshots.last().imageUrl
        )
        assertTrue(result.hasMore)
    }

    @Test
    fun rejectsUntrustedImagesAndRecognizesLoginForm() {
        val result = SteamGameScreenshotsParser.parse(
            html = """
                <a class="profile_media_item" data-appid="730" data-publishedfileid="1">
                  <div class="imgWallItem" style="background-image: url('https://example.org/fake.jpg');"></div>
                </a>
            """.trimIndent(),
            expectedAppId = 730
        )

        assertTrue(result.screenshots.isEmpty())
        assertFalse(result.hasMore)
        assertTrue(
            SteamGameScreenshotsParser.isAuthenticationPage(
                """
                    <form id="login_form" action="/login/" method="post">
                      <input name="username" />
                      <input name="password" type="password" />
                    </form>
                """.trimIndent()
            )
        )
        assertFalse(
            SteamGameScreenshotsParser.isAuthenticationPage(
                "<a href='/login/home/'>Sign in</a><div id='NoItemsContainer'></div>"
            )
        )
    }
}
