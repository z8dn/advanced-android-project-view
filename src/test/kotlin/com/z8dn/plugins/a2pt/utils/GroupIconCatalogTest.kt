package com.z8dn.plugins.a2pt.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupIconCatalogTest {

    @Test
    fun `find returns null for null key`() {
        assertNull(GroupIconCatalog.find(null))
    }

    @Test
    fun `find returns null for unknown key`() {
        assertNull(GroupIconCatalog.find("does.not.exist"))
    }

    @Test
    fun `entries is non-empty`() {
        assertTrue("Catalog should expose at least one entry", GroupIconCatalog.entries.isNotEmpty())
    }

    @Test
    fun `every entry is findable by its own key`() {
        for (entry in GroupIconCatalog.entries) {
            val found = GroupIconCatalog.find(entry.key)
            assertNotNull("Expected to find entry for key=${entry.key}", found)
            assertEquals(entry.key, found?.key)
        }
    }

    @Test
    fun `entry display names are non-blank`() {
        for (entry in GroupIconCatalog.entries) {
            assertTrue("Display name blank for key=${entry.key}", entry.displayName.isNotBlank())
        }
    }
}
