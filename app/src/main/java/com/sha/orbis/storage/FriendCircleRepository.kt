package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.R
import com.sha.orbis.model.FriendCircle
import org.json.JSONArray
import java.io.File

class FriendCircleRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val storageFile: File by lazy {
        AccountStorageManager.getAccountFile(context, "friend_circles.json", currentAccountId)
    }

    private fun getDefaultCircles(): List<FriendCircle> = listOf(
        FriendCircle("circle_family", context.getString(R.string.social_circle_family), "👨‍👩‍👧‍👦", emptyList(), "#10B981"),
        FriendCircle("circle_close", context.getString(R.string.social_circle_close), "⭐", emptyList(), "#F59E0B"),
        FriendCircle("circle_work", context.getString(R.string.social_circle_work), "💼", emptyList(), "#3B82F6"),
        FriendCircle("circle_community", context.getString(R.string.social_circle_community), "🌐", emptyList(), "#8B5CF6")
    )

    @Synchronized
    fun loadCircles(): List<FriendCircle> {
        if (!storageFile.exists()) {
            val defaults = getDefaultCircles()
            saveCircles(defaults)
            return defaults
        }
        return try {
            val jsonStr = storageFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<FriendCircle>()
            for (i in 0 until array.length()) {
                list.add(FriendCircle.fromJson(array.getJSONObject(i)))
            }
            if (list.isEmpty()) {
                val defaults = getDefaultCircles()
                saveCircles(defaults)
                defaults
            } else {
                list
            }
        } catch (_: Exception) {
            getDefaultCircles()
        }
    }

    @Synchronized
    fun saveCircles(circles: List<FriendCircle>) {
        try {
            val array = JSONArray()
            circles.forEach { array.put(it.toJson()) }
            storageFile.writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    @Synchronized
    fun addMemberToCircle(circleId: String, phone: String) {
        val current = loadCircles().toMutableList()
        val index = current.indexOfFirst { it.id == circleId }
        if (index >= 0) {
            val circle = current[index]
            if (!circle.memberPhones.contains(phone)) {
                current[index] = circle.copy(memberPhones = circle.memberPhones + phone)
                saveCircles(current)
                try {
                    com.sha.orbis.telemetry.FeedTelemetryTracker.trackCircleAction(context)
                } catch (_: Exception) {}
            }
        }
    }

    @Synchronized
    fun removeMemberFromCircle(circleId: String, phone: String) {
        val current = loadCircles().toMutableList()
        val index = current.indexOfFirst { it.id == circleId }
        if (index >= 0) {
            val circle = current[index]
            current[index] = circle.copy(memberPhones = circle.memberPhones - phone)
            saveCircles(current)
            try {
                com.sha.orbis.telemetry.FeedTelemetryTracker.trackCircleAction(context)
            } catch (_: Exception) {}
        }
    }

    fun getFamilyCircle(): FriendCircle {
        return loadCircles().find { it.id == "circle_family" } ?: getDefaultCircles().first()
    }

    fun getFamilyMembers(): List<String> {
        return getFamilyCircle().memberPhones
    }

    fun isFamilyMember(phone: String): Boolean {
        return getFamilyMembers().any { FriendRequestRepository.isSamePhone(it, phone) }
    }

    fun addFamilyMember(phone: String) {
        addMemberToCircle("circle_family", phone)
    }

    fun removeFamilyMember(phone: String) {
        removeMemberFromCircle("circle_family", phone)
    }
}
