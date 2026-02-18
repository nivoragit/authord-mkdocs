package com.authord.mkdocs.ui.intellij

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.AuthordUiBundle"

/**
 * Message bundle accessor for user-facing UI copy.
 */
object AuthordUiBundle : DynamicBundle(BUNDLE_NAME) {
    fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
