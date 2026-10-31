package com.sparky.iptv.data

import org.json.JSONArray
import org.json.JSONObject

/** A favorites folder (channels only). [keys] are `live:` favorite keys in the folder's own order. */
data class FavFolder(val id: String, val name: String, val keys: List<String>)

/** Pure helpers for favorites order and folders, kept free of Android so the rules are easy to read. */
object Favs {
    const val MAX_NAME = 30

    fun orderToJson(order: List<String>): String = JSONArray(order).toString()

    fun orderFromJson(raw: String?): List<String>? = raw?.let {
        runCatching { val a = JSONArray(it); List(a.length()) { i -> a.getString(i) } }.getOrNull()
    }

    fun foldersToJson(folders: List<FavFolder>): JSONArray = JSONArray().apply {
        folders.forEach { f -> put(JSONObject().put("id", f.id).put("name", f.name).put("keys", JSONArray(f.keys))) }
    }

    fun foldersFromJson(raw: String?): List<FavFolder> = runCatching { foldersFromArray(JSONArray(raw ?: "[]")) }.getOrDefault(emptyList())

    fun foldersFromArray(a: JSONArray): List<FavFolder> = (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id").ifBlank { return@mapNotNull null }
        val keysArr = o.optJSONArray("keys") ?: JSONArray()
        val keys = List(keysArr.length()) { keysArr.optString(it) }.filter { it.startsWith("live:") }.distinct()
        FavFolder(id, o.optString("name").take(MAX_NAME).ifBlank { "Folder" }, keys)
    }

    /**
     * Every favorite exactly once: the saved order first (dropping removed favorites),
     * then any favorite the order doesn't know yet, at the end.
     */
    fun normalize(order: List<String>, favorites: Set<String>): List<String> {
        val seen = LinkedHashSet<String>()
        order.forEach { if (it in favorites) seen.add(it) }
        favorites.forEach { seen.add(it) }
        return seen.toList()
    }

    /**
     * Puts [newVisible] (a reordering of some keys of [full]) back into [full]: the slots those keys
     * occupied are filled with them in the new order, everything else stays where it was. This lets a
     * filtered view (only channels, or only the ones the provider still has) reorder the full list.
     */
    fun merge(full: List<String>, newVisible: List<String>): List<String> {
        val set = newVisible.toHashSet()
        val it = newVisible.iterator()
        return full.map { k -> if (k in set && it.hasNext()) it.next() else k }
    }

    /** Moves [key] by [delta] places inside [list]; returns the same list when it can't move. */
    fun moveBy(list: List<String>, key: String, delta: Int): List<String> {
        val i = list.indexOf(key)
        if (i < 0) return list
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (j == i) return list
        val m = list.toMutableList()
        m.removeAt(i)
        m.add(j, key)
        return m
    }

    /** Checks a folder name; returns an error message, or null when it's fine. */
    fun nameError(name: String, folders: List<FavFolder>, exceptId: String? = null): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Enter a name"
            n.length > MAX_NAME -> "Use at most $MAX_NAME characters"
            folders.any { it.id != exceptId && it.name.equals(n, ignoreCase = true) } -> "There's already a folder called $n"
            else -> null
        }
    }

    /** A folder id not used yet: f1, f2, … */
    fun newId(folders: List<FavFolder>): String {
        val used = folders.map { it.id }.toHashSet()
        var n = 1
        while ("f$n" in used) n++
        return "f$n"
    }
}
