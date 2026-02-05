package com.agcforge.videodownloader.utils

import android.content.Context
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agcforge.videodownloader.data.model.Application
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.data.model.Platform
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

// DataStore instance remains a top-level extension for easy access throughout the app
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Manages all application preferences using DataStore.
 * This class encapsulates the logic for saving and retrieving user data and settings.
 */
class PreferenceManager(private val context: Context) {

    companion object {
        // All preference keys are now organized within the PreferenceManager companion object
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_AVATAR_KEY = stringPreferencesKey("user_avatar")
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val LANGUAGE_KEY = stringPreferencesKey("language_code")
		private val STORAGE_LOCATION_KEY = stringPreferencesKey("storage_location")
		private val APPLICATION_KEY = stringPreferencesKey("application_config")
        private val HISTORY_KEY = stringPreferencesKey("history")
        private val PLATFORMS_KEY = stringPreferencesKey("platforms")
    }

    // --- Flows to observe preference changes ---
    private val gson = Gson()
    val authToken: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL_KEY] }
    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME_KEY] }
    val userAvatar: Flow<String?> = context.dataStore.data.map { it[USER_AVATAR_KEY] }
    val theme: Flow<String?> = context.dataStore.data.map { it[THEME_KEY] }
    val language: Flow<String?> = context.dataStore.data.map { it[LANGUAGE_KEY] }
	val storageLocation: Flow<String?> = context.dataStore.data.map { it[STORAGE_LOCATION_KEY] }
    val applicationConfig: Flow<Application?> = context.dataStore.data
        .map { preferences ->
            val json = preferences[APPLICATION_KEY]
            try {
                if (!json.isNullOrEmpty()) {
                    Gson().fromJson(json, Application::class.java)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("DataStoreManager", "Error parsing application config", e)
                null
            }
        }
        .catch { e ->
            Log.e("DataStoreManager", "Error in application config flow", e)
            emit(null)
        }

    val history: Flow<List<DownloadTask>> = context.dataStore.data
        .map { preferences ->
            val historyString = preferences[HISTORY_KEY] ?: ""
            try {
                if (historyString.isNotEmpty()) {
                    val type = object : TypeToken<List<DownloadTask>>() {}.type
                    gson.fromJson<List<DownloadTask>>(historyString, type) ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("DataStoreManager", "Error parsing history JSON", e)
                emptyList()
            }
        }
        .catch { e ->
            Log.e("DataStoreManager", "Error reading history", e)
            emit(emptyList())
        }

    // --- Suspend functions to modify preferences ---
    suspend fun saveBoolean(key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }
    suspend fun saveInt(key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }

    suspend fun saveString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }

    suspend fun getBoolean(key: String): Boolean? {
        val prefKey = booleanPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }

    suspend fun getInt(key: String): Int? {
        val prefKey = intPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }

    suspend fun getString(key: String): String? {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }

    suspend fun saveAuthToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun saveUserInfo(userId: String, email: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_NAME_KEY] = name
        }
    }

    suspend fun saveUserProfile(name: String, avatarUrl: String?) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
            avatarUrl?.let { preferences[USER_AVATAR_KEY] = it }
        }
    }

    suspend fun saveTheme(themeMode: String) {
        context.dataStore.edit { it[THEME_KEY] = themeMode }
    }

    suspend fun saveLanguage(languageCode: String) {
        context.dataStore.edit { it[LANGUAGE_KEY] = languageCode }
    }

	suspend fun saveStorageLocation(location: String) {
		context.dataStore.edit { it[STORAGE_LOCATION_KEY] = location }
	}

    suspend fun saveApplication(app: Application) {
        try {
            val json = gson.toJson(app)
            context.dataStore.edit { preferences ->
                preferences[APPLICATION_KEY] = json
            }
        } catch (e: Exception) {
            Log.e("DataStoreManager", "Error saving application config", e)
            throw e
        }
    }

    suspend fun getApplication(): Application? {
        return try {
            val json = context.dataStore.data.first()[APPLICATION_KEY]
            if (!json.isNullOrEmpty()) {
                gson.fromJson(json, Application::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("DataStoreManager", "Error getting application config", e)
            null
        }
    }

    suspend fun clearApplication() {
        context.dataStore.edit { it.remove(APPLICATION_KEY) }
    }


    suspend fun clearUserData() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun addToHistory(task: DownloadTask) {
        context.dataStore.edit { preferences ->
            val currentHistoryString = preferences[HISTORY_KEY] ?: ""
            val currentHistory = try {
                if (currentHistoryString.isNotEmpty()) {
                    val type = object : TypeToken<MutableList<DownloadTask>>() {}.type
                    gson.fromJson<MutableList<DownloadTask>>(currentHistoryString, type)
                        ?: mutableListOf()
                } else {
                    mutableListOf()
                }
            } catch (e: Exception) {
                mutableListOf()
            }

				currentHistory.removeIf { it.id == task.id }
            currentHistory.add(0, task)

            if (currentHistory.size > 100) {
                currentHistory.subList(0, 100)
            }

            val updatedJson = gson.toJson(currentHistory)
            preferences[HISTORY_KEY] = updatedJson
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(HISTORY_KEY) }
    }

    suspend fun deleteHistoryItem(task: DownloadTask) {
        context.dataStore.edit { preferences ->
            val currentHistoryString = preferences[HISTORY_KEY] ?: ""
            if (currentHistoryString.isEmpty()) return@edit

            try {
                val type = object : TypeToken<MutableList<DownloadTask>>() {}.type
                val currentHistory = gson.fromJson<MutableList<DownloadTask>>(
                    currentHistoryString,
                    type
                ) ?: mutableListOf()

                currentHistory.removeIf { existingTask ->
                    existingTask.id == task.id
                }

                val updatedJson = gson.toJson(currentHistory)
                preferences[HISTORY_KEY] = updatedJson
            } catch (e: Exception) {
                Log.e("DataStoreManager", "Error deleting history item", e)
            }
        }
    }

    suspend fun getHistory(limit: Int = -1): List<DownloadTask> {
        return history.first().let { list ->
            if (limit > 0 && list.size > limit) {
                list.take(limit)
            } else {
                list
            }
        }
    }

    suspend fun deleteHistoryItemByUrl(url: String) {
        context.dataStore.edit { preferences ->
            val currentHistoryString = preferences[HISTORY_KEY] ?: ""
            if (currentHistoryString.isEmpty()) return@edit

            try {
                val type = object : TypeToken<MutableList<DownloadTask>>() {}.type
                val currentHistory = gson.fromJson<MutableList<DownloadTask>>(
                    currentHistoryString,
                    type
                ) ?: mutableListOf()

                currentHistory.removeIf { it.originalUrl == url }

                val updatedJson = gson.toJson(currentHistory)
                preferences[HISTORY_KEY] = updatedJson
            } catch (e: Exception) {
                Log.e("DataStoreManager", "Error deleting history item", e)
            }
        }
    }

    suspend fun updateStatusHistory(task: DownloadTask) {
        context.dataStore.edit { preferences ->
            val currentHistoryString = preferences[HISTORY_KEY] ?: ""
            if (currentHistoryString.isEmpty()) return@edit

            try {
                val type = object : TypeToken<MutableList<DownloadTask>>() {}.type
                val currentHistory = gson.fromJson<MutableList<DownloadTask>>(
                    currentHistoryString,
                    type
                ) ?: mutableListOf()

                currentHistory.set(
                    currentHistory.indexOfFirst { it.id == task.id },
                    currentHistory.first { it.id == task.id }.copy(
                        status = task.status
                    )
                )
                val updatedJson = gson.toJson(currentHistory)
                preferences[HISTORY_KEY] = updatedJson
            } catch (e: Exception) {
                Log.e("DataStoreManager", "Error updating history item", e)
            }
        }
    }

    suspend fun updateStatusHistoryById(id: String, status: String) {
        context.dataStore.edit { preferences ->
            val currentHistoryString = preferences[HISTORY_KEY] ?: ""
            if (currentHistoryString.isEmpty()) return@edit

            try {
                val type = object : TypeToken<MutableList<DownloadTask>>() {}.type
                val currentHistory = gson.fromJson<MutableList<DownloadTask>>(
                    currentHistoryString,
                    type
                ) ?: mutableListOf()

                currentHistory.set(
                    currentHistory.indexOfFirst { it.id == id },
                    currentHistory.first { it.id == id }.copy(
                        status = status
                    )
                )
                val updatedJson = gson.toJson(currentHistory)
                preferences[HISTORY_KEY] = updatedJson
            } catch (e: Exception) {
                Log.e("DataStoreManager", "Error updating history item", e)
            }
        }
    }


    fun getApplicationSync(): Application? = runBlocking { getApplication() }
    fun getBooleanSync(key: String): Boolean? = runBlocking { getBoolean(key) }
    fun getIntSync(key: String): Int? = runBlocking { getInt(key) }
    fun getStringSync(key: String): String? = runBlocking { getString(key) }
}

// --- General-purpose Utility Functions ---

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun ImageView.loadImage(url: String?, placeholder: Int = android.R.drawable.ic_menu_gallery) {
    Glide.with(this.context)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(this)
}

fun String.formatDate(inputFormat: String = "yyyy-MM-dd'T'HH:mm:ss", outputFormat: String = "MMM dd, yyyy"): String {
    return try {
        val input = SimpleDateFormat(inputFormat, Locale.getDefault())
        val output = SimpleDateFormat(outputFormat, Locale.getDefault())
        val date = input.parse(this)
        date?.let { output.format(it) } ?: this
    } catch (e: Exception) {
        this
    }
}

fun Long.formatFileSize(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        this < 1024 * 1024 * 1024 -> "${this / (1024 * 1024)} MB"
        else -> "${this / (1024 * 1024 * 1024)} GB"
    }
}

fun Long.formatDate(outputFormat: String = "MMM dd, yyyy"): String {
	return try {
		val output = SimpleDateFormat(outputFormat, Locale.getDefault())
		output.format(Date(this))
	} catch (e: Exception) {
		try {
			val output = SimpleDateFormat(outputFormat, Locale.getDefault())
			output.format(Date(this))
		} catch (e: Exception) {
			toString()
		}
	}
}

fun applyTheme(theme: String?) {
    when (theme) {
        "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}
