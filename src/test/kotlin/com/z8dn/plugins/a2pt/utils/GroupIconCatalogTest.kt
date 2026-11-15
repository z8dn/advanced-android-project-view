package com.z8dn.plugins.a2pt.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ResourceBundle

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
    fun `keys are unique`() {
        val keys = GroupIconCatalog.entries.map { it.key }
        assertEquals("Duplicate catalog keys: ${keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}",
            keys.size, keys.toSet().size)
    }

    @Test
    fun `every entry has a non-blank display name in the message bundle`() {
        val bundle = ResourceBundle.getBundle("messages.AndroidViewBundle")
        for (entry in GroupIconCatalog.entries) {
            val bundleKey = GroupIconEntry.displayNameKey(entry.key)
            assertTrue("Missing bundle key $bundleKey", bundle.containsKey(bundleKey))
            assertTrue("Blank display name for $bundleKey", bundle.getString(bundleKey).isNotBlank())
        }
    }
}
