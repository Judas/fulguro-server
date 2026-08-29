package com.fulgurogo.api.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminAccessTest {
    private val allowedRoles = setOf("moderation", "hermit")

    @Test
    fun `moderation role is allowed`() {
        assertTrue(AdminAccess.isAllowed(listOf("moderation"), allowedRoles))
    }

    @Test
    fun `hermit role is allowed`() {
        assertTrue(AdminAccess.isAllowed(listOf("hermit"), allowedRoles))
    }

    @Test
    fun `both roles are allowed`() {
        assertTrue(AdminAccess.isAllowed(listOf("moderation", "hermit"), allowedRoles))
    }

    @Test
    fun `unrelated roles are forbidden`() {
        assertFalse(AdminAccess.isAllowed(listOf("other"), allowedRoles))
        assertFalse(AdminAccess.isAllowed(emptyList(), allowedRoles))
    }

    @Test
    fun `configured roles are parsed from a comma separated value`() {
        assertEquals(allowedRoles, AdminAccess.parseRoleIds(" moderation, hermit ,, moderation "))
        assertTrue(AdminAccess.parseRoleIds(null).isEmpty())
        assertTrue(AdminAccess.parseRoleIds("  ").isEmpty())
    }
}
