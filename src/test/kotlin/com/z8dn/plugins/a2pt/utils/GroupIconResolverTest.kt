package com.z8dn.plugins.a2pt.utils

import org.junit.Assert.assertSame
import org.junit.Test
import javax.swing.Icon
import javax.swing.ImageIcon

class GroupIconResolverTest {

    private val sentinelAuto: Icon = ImageIcon()
    private val autoDetect: () -> Icon = { sentinelAuto }

    @Test
    fun `null iconKey falls back to auto-detect`() {
        assertSame(sentinelAuto, GroupIconResolver.resolve(null, autoDetect))
    }

    @Test
    fun `unknown iconKey falls back to auto-detect`() {
        assertSame(sentinelAuto, GroupIconResolver.resolve("does.not.exist", autoDetect))
    }

    @Test
    fun `known iconKey returns the catalog icon`() {
        val entry = GroupIconCatalog.entries.firstOrNull()
            ?: error("Catalog is empty — cannot run this test")
        val resolved = GroupIconResolver.resolve(entry.key, autoDetect)
        assertSame(entry.icon, resolved)
    }
}
