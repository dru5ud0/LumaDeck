package com.nuvio.tv.ui.screens.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.jellyfin.JellyfinLocalLibraryClient
import com.nuvio.tv.data.jellyfin.JellyfinVideo
import com.nuvio.tv.data.local.JellyfinConnection
import com.nuvio.tv.data.local.JellyfinConnectionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalLibraryUiState(
    val connection: JellyfinConnection = JellyfinConnection(),
    val videos: List<JellyfinVideo> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class LocalLibraryViewModel @Inject constructor(
    private val connectionDataStore: JellyfinConnectionDataStore,
    private val jellyfin: JellyfinLocalLibraryClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalLibraryUiState())
    val uiState: StateFlow<LocalLibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionDataStore.connection.collectLatest { connection ->
                _uiState.update { it.copy(connection = connection) }
                if (connection.isConnected) refresh()
            }
        }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching {
                jellyfin.signIn(serverUrl, username, password).also { session ->
                    connectionDataStore.save(
                        JellyfinConnection(
                            serverUrl = serverUrl,
                            username = session.username,
                            userId = session.userId,
                            accessToken = session.accessToken
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, message = error.message ?: "Could not connect to Jellyfin.") }
            }
        }
    }

    fun refresh() {
        val connection = _uiState.value.connection
        if (!connection.isConnected) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching { jellyfin.videos(connection) }
                .onSuccess { videos -> _uiState.update { it.copy(videos = videos, isLoading = false) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, message = error.message ?: "Could not load your local videos.") }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            connectionDataStore.clearSession()
            _uiState.update { it.copy(videos = emptyList(), message = "Signed out of local server.") }
        }
    }

    fun streamUrl(videoId: String): String = jellyfin.streamUrl(_uiState.value.connection, videoId)
}
