package com.pizzadue.voice

import android.content.Context
import java.io.File

/**
 * The speech models ship inside the APK as assets. The recogniser needs real files,
 * so on first start (and after an update that changes a model) they are copied out once.
 */
object ModelInstaller {
    /** Returns the folder holding the unpacked model, or null when the APK has no model for [lang]. */
    fun ensure(context: Context, lang: Lang): File? {
        val name = lang.name.lowercase()
        val assets = context.assets
        val assetDir = "models/$name"
        val wanted = runCatching { assets.open("$assetDir/version.txt").bufferedReader().use { it.readText().trim() } }
            .getOrNull() ?: return null
        val target = File(context.filesDir, "models/$name")
        val marker = File(target, "version.txt")
        if (marker.exists() && marker.readText().trim() == wanted) return target
        target.deleteRecursively()
        copy(context, assetDir, target)
        return target
    }

    private fun copy(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            target.mkdirs()
            // version.txt last, so a copy interrupted half-way is redone next time
            children.sortedBy { it == "version.txt" }.forEach { copy(context, "$assetPath/$it", File(target, it)) }
        }
    }
}
