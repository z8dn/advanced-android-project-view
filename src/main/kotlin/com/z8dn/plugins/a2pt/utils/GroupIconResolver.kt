package com.z8dn.plugins.a2pt.utils

import javax.swing.Icon

object GroupIconResolver {
    fun resolve(iconKey: String?, autoDetect: () -> Icon): Icon =
        GroupIconCatalog.find(iconKey)?.icon ?: autoDetect()
}
