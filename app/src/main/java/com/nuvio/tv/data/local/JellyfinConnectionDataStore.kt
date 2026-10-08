package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class JellyfinConnection(
    val serverUrl: String = "",
    val username: String = "",
    val userId: String = "",
    val accessToken: String = ""
) {
    val isConnected: Boolean
        get() = serverUrl.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()
}

/** Per-profile, local-only Jellyfin connection details. Passwords are never saved. */
@Singleton
class JellyfinConnectionDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    private companion object {
        const val FEATURE = "jellyfin_connection"
        val serverUrlKey = stringPreferencesKey("server_url")
        val usernameKey = stringPreferencesKey("username")
        val userIdKey = stringPreferencesKey("user_id")
        val accessTokenKey = stringPreferencesKey("access_token")
    }

    val connection: Flow<JellyfinConnection> = profileManager.activeProfileId.flatMapLatest { profileId ->
        factory.get(profileId, FEATURE).data.map { preferences ->
            JellyfinConnection(
                serverUrl = preferences[serverUrlKey] ?: JellyfinConnection().serverUrl,
                username = preferences[usernameKey].orEmpty(),
                userId = preferences[userIdKey].orEmpty(),
                accessToken = preferences[accessTokenKey].orEmpty()
            )
        }
    }

    suspend fun save(connection: JellyfinConnection) {
        val normalizedUrl = connection.serverUrl.trim().trimEnd('/')
        factory.get(profileManager.activeProfileId.value, FEATURE).edit { preferences ->
            preferences[serverUrlKey] = normalizedUrl
            preferences[usernameKey] = connection.username.trim()
            preferences[userIdKey] = connection.userId
            preferences[accessTokenKey] = connection.accessToken
        }
    }

    suspend fun clearSession() {
        factory.get(profileManager.activeProfileId.value, FEATURE).edit { preferences ->
            preferences.remove(userIdKey)
            preferences.remove(accessTokenKey)
        }
    }
}
