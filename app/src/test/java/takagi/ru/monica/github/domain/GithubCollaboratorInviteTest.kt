package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubCollaboratorInviteTest {
    @Test
    fun loginAcceptsOnlyWhatGithubAllows() {
        listOf("a", "octocat", "a-b", "Etoile-Android", "a".repeat(39)).forEach {
            assertTrue(it, GithubCollaboratorInvite.isValidLogin(it))
        }
        listOf("", "   ", "-a", "a-", "a--b", "a_b", "ünïcode", "a".repeat(40)).forEach {
            assertFalse(it, GithubCollaboratorInvite.isValidLogin(it))
        }
    }

    @Test
    fun everyAssignableRoleHasThePermissionGithubNamesItWith() {
        assertEquals(
            listOf(
                GithubCollaboratorRole.READ,
                GithubCollaboratorRole.TRIAGE,
                GithubCollaboratorRole.WRITE,
                GithubCollaboratorRole.MAINTAIN,
                GithubCollaboratorRole.ADMIN
            ),
            GithubCollaboratorRole.assignable
        )
        assertEquals(
            listOf("pull", "triage", "push", "maintain", "admin"),
            GithubCollaboratorRole.assignable.map { it.apiPermission }
        )
        assertNull(GithubCollaboratorRole.UNKNOWN.apiPermission)
    }

    @Test
    fun fromInputTrimsTheLoginAndRefusesARoleWithNoPermission() {
        val invite = GithubCollaboratorInvite.fromInput("  bob ", GithubCollaboratorRole.WRITE).getOrThrow()

        assertEquals("bob", invite.login)
        assertEquals("push", invite.permission)
        assertTrue(
            GithubCollaboratorInvite.fromInput("bob", GithubCollaboratorRole.UNKNOWN).isFailure
        )
        assertTrue(
            GithubCollaboratorInvite.fromInput("-bob", GithubCollaboratorRole.READ).isFailure
        )
    }
}
