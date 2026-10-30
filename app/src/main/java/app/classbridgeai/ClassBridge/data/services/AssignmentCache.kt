package app.classbridgeai.ClassBridge.data.services

import android.content.Context
import app.classbridgeai.ClassBridge.data.models.Assignment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AssignmentCache {
    private const val PREFS_NAME = "assignment_cache"
    private const val KEY_ASSIGNMENTS = "cached_assignments"
    private val gson = Gson()

    fun save(context: Context, assignments: List<Assignment>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ASSIGNMENTS, gson.toJson(assignments))
            .apply()
    }

    fun load(context: Context): List<Assignment> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ASSIGNMENTS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Assignment>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
