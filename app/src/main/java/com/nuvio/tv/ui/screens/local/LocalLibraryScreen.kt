package com.nuvio.tv.ui.screens.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.data.jellyfin.JellyfinVideo
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun LocalLibraryScreen(
    showBuiltInHeader: Boolean = true,
    viewModel: LocalLibraryViewModel = hiltViewModel(),
    onPlay: (JellyfinVideo, String, Map<String, String>) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var serverUrl by rememberSaveable(state.connection.serverUrl) { mutableStateOf(state.connection.serverUrl) }
    var username by rememberSaveable(state.connection.username) { mutableStateOf(state.connection.username) }
    var password by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(
                start = NuvioTheme.spacing.xxl,
                end = NuvioTheme.spacing.xxl,
                top = if (showBuiltInHeader) NuvioTheme.spacing.xl else 84.dp,
                bottom = NuvioTheme.spacing.xl
            )
    ) {
        if (!state.connection.isConnected) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Local Library", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Connect to Jellyfin running on your laptop. Your files stay on your home Wi-Fi.",
                    color = NuvioTheme.colors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server address") },
                    supportingText = { Text("Example: http://YOUR_SERVER_IP:8096") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Jellyfin username") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Jellyfin password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.signIn(serverUrl, username, password) },
                    enabled = !state.isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                ) { Text("Connect") }
                state.message?.let { Text(it, color = NuvioTheme.colors.TextSecondary) }
                if (state.isLoading) LoadingIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Local Library", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "${state.videos.size} videos from ${state.connection.username}'s Jellyfin server",
                            color = NuvioTheme.colors.TextSecondary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = viewModel::refresh, enabled = !state.isLoading) { Text("Refresh") }
                        Button(onClick = viewModel::signOut, enabled = !state.isLoading) { Text("Disconnect") }
                        state.message?.let { Text(it, color = NuvioTheme.colors.TextSecondary) }
                    }
                }
                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                }
                items(state.videos, key = { it.id }) { video ->
                    Card(
                        onClick = {
                            onPlay(
                                video,
                                viewModel.streamUrl(video.id),
                                mapOf("X-Emby-Token" to state.connection.accessToken)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.colors(containerColor = NuvioTheme.colors.Surface)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(video.name, style = MaterialTheme.typography.titleLarge)
                            video.durationMs?.let { duration ->
                                val minutes = duration / 60_000
                                Text("$minutes min", color = NuvioTheme.colors.TextSecondary)
                            }
                            video.overview?.let { Text(it, color = NuvioTheme.colors.TextSecondary, maxLines = 2) }
                        }
                    }
                }
                if (!state.isLoading && state.videos.isEmpty()) {
                    item { Text("No videos found yet. Make sure the folder has been added to Jellyfin and scan the library.") }
                }
            }
        }
    }
}
