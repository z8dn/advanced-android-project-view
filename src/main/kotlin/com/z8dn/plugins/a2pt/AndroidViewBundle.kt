package com.z8dn.plugins.a2pt

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.AndroidViewBundle"

// DynamicBundle(String) infers the classloader from the caller, which is @ApiStatus.Obsolete on
// 261 and @Deprecated from 263 on. Handing it this class pins the lookup to the plugin's own
// classloader, which is the platform's own pattern for its bundles.
object AndroidViewBundle : DynamicBundle(AndroidViewBundle::class.java, BUNDLE) {

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
