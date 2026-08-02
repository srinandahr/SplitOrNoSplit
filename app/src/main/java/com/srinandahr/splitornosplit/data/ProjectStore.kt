package com.srinandahr.splitornosplit.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists the projects this device has created or joined, and which one is active.
 *
 * Multi-project is structural rather than bolted on: I Hate Money credentials are
 * per-project (there is no account, and therefore no endpoint that lists "your"
 * projects), so a user in a flat *and* on a trip genuinely holds two credentials.
 *
 * Credentials live in [SecurePrefs]. The pause flag deliberately stays in the original
 * plain prefs file: it is not a secret, it must survive the v1 upgrade, and SmsReceiver
 * reads it on every single SMS where keystore latency would be wasted.
 */
class ProjectStore(context: Context) {

    private val app = context.applicationContext
    private val secure by lazy { SecurePrefs.get(app) }
    private val plain by lazy { app.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE) }
    private val gson = Gson()

    // ---- projects -----------------------------------------------------------

    fun projects(): List<Project> {
        val raw = secure.getString(KEY_PROJECTS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<Project>>(raw, object : TypeToken<List<Project>>() {}.type)
        }.getOrNull().orEmpty()
    }

    fun active(): Project? {
        val all = projects()
        if (all.isEmpty()) return null
        val activeKey = secure.getString(KEY_ACTIVE, null)
        return all.firstOrNull { it.key == activeKey } ?: all.first()
    }

    fun setActive(project: Project) {
        secure.edit().putString(KEY_ACTIVE, project.key).apply()
    }

    /** Adds the project, or replaces the existing entry with the same instance + id. */
    fun upsert(project: Project) {
        val updated = projects().toMutableList()
        val index = updated.indexOfFirst { it.key == project.key }
        if (index >= 0) updated[index] = project else updated.add(project)
        writeProjects(updated)
        if (secure.getString(KEY_ACTIVE, null) == null || index < 0) setActive(project)
    }

    fun remove(project: Project) {
        val remaining = projects().filterNot { it.key == project.key }
        writeProjects(remaining)
        if (secure.getString(KEY_ACTIVE, null) == project.key) {
            val next = remaining.firstOrNull()
            if (next != null) setActive(next) else secure.edit().remove(KEY_ACTIVE).apply()
        }
    }

    private fun writeProjects(list: List<Project>) {
        secure.edit().putString(KEY_PROJECTS, gson.toJson(list)).apply()
    }

    // ---- pause --------------------------------------------------------------

    fun isPaused(): Boolean = plain.getBoolean(KEY_PAUSED, false)

    fun setPaused(paused: Boolean) {
        plain.edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    // ---- v1 migration -------------------------------------------------------

    /**
     * True when this device still holds a v1 Splitwise setup. Used once, to explain the
     * shutdown rather than silently presenting an empty app to an upgrading user.
     */
    fun hasLegacySplitwiseConfig(): Boolean =
        !plain.getString(LEGACY_KEY_API, "").isNullOrEmpty() && projects().isEmpty()

    fun clearLegacySplitwiseConfig() {
        plain.edit()
            .remove(LEGACY_KEY_API)
            .remove(LEGACY_KEY_GROUP_ID)
            .remove(LEGACY_KEY_GROUP_NAME)
            .apply()
    }

    fun clearAll() {
        secure.edit().clear().apply()
        plain.edit().clear().apply()
    }

    companion object {
        private const val LEGACY_FILE = "SplitAppPrefs"
        private const val KEY_PROJECTS = "projects"
        private const val KEY_ACTIVE = "active_project"
        private const val KEY_PAUSED = "isPaused"
        private const val LEGACY_KEY_API = "API_KEY"
        private const val LEGACY_KEY_GROUP_ID = "GROUP_ID"
        private const val LEGACY_KEY_GROUP_NAME = "GROUP_NAME"
    }
}

/** Identity of a project is the instance it lives on plus its id — ids are only unique per instance. */
val Project.key: String get() = "$instanceUrl|$projectId"
