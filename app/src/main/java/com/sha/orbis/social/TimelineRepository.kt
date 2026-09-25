package com.sha.orbis.social

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TimelineRepository(private val context: Context) {
    private val file = File(context.filesDir, "timeline.json")

    fun save(items: List<TimelineItem>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        file.writeText(JSONObject().put("items", array).toString(2), Charsets.UTF_8)
    }

    fun load(): List<TimelineItem> {
        if (!file.exists()) return emptyList()
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val array = json.optJSONArray("items") ?: JSONArray()
        return List(array.length()) { index -> TimelineItem.fromJson(array.getJSONObject(index)) }
    }

    fun add(item: TimelineItem) {
        val current = load().toMutableList()
        current.add(item)
        save(current)
    }
}
