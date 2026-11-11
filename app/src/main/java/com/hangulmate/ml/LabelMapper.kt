package com.hangulmate.ml

import android.content.Context
import org.json.JSONObject

/**
 * Maps ImageNet labels to Korean words using the bundled assets.
 *
 * - `assets/ml/labels.txt` — 1001 lines, index 0 = `background`.
 * - `assets/ml/label_aliases.json` — 269 verified `{"label", "korean"}`
 *   mappings, every Korean value from the app's core vocabulary.
 *
 * Lookups are case-insensitive. A label with no alias returns null: the
 * caller must show an honest "could not identify" state, never invent a
 * Korean word. Missing assets degrade to empty tables (never a crash).
 */
class LabelMapper(context: Context) {

    /** ImageNet labels in index order; empty when labels.txt is missing. */
    val labels: List<String> = runCatching {
        context.assets.open("ml/labels.txt").bufferedReader().readLines()
    }.getOrDefault(emptyList())

    private val aliases: Map<String, String> = runCatching {
        val json = context.assets.open("ml/label_aliases.json")
            .bufferedReader().use { it.readText() }
        val array = JSONObject(json).optJSONArray("aliases") ?: return@runCatching emptyMap<String, String>()
        buildMap {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val label = entry.optString("label", "").trim().lowercase()
                val korean = entry.optString("korean", "").trim()
                if (label.isNotEmpty() && korean.isNotEmpty()) {
                    put(label, korean)
                }
            }
        }
    }.getOrDefault(emptyMap())

    /** Number of alias mappings actually loaded (269 when assets are intact). */
    val aliasCount: Int get() = aliases.size

    /** Label at [index], or null when out of range / labels are missing. */
    fun labelForIndex(index: Int): String? = labels.getOrNull(index)

    /** Verified Korean word for an ImageNet [label], or null when unmapped. */
    fun koreanForLabel(label: String): String? = aliases[label.trim().lowercase()]

    /** Verified Korean word for the class at [index], or null. */
    fun koreanForIndex(index: Int): String? =
        labelForIndex(index)?.let { koreanForLabel(it) }
}
