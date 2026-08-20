package com.example.pixelfed.ui.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pixelfed.data.repository.PixelfedRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    context: Context,
    repository: PixelfedRepository
) {
    var instanceUrl by remember { mutableStateOf("https://pixelfed.social") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val redirectUri = "pixelfed-app://oauth"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Connect to Pixelfed",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = instanceUrl,
            onValueChange = { instanceUrl = it },
            label = { Text("Pixelfed Instance URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                if (instanceUrl.isBlank()) {
                    errorMessage = "Please enter instance URL"
                    return@Button
                }

                var formattedUrl = instanceUrl.trim()
                if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                    formattedUrl = "https://$formattedUrl"
                }
                formattedUrl = formattedUrl.trimEnd('/')

                isLoading = true
                errorMessage = null

                scope.launch {
                    val result = repository.registerApp(formattedUrl, redirectUri)
                    isLoading = false
                    if (result != null) {
                        val clientId = result.first
                        val authUrl = Uri.parse(formattedUrl)
                            .buildUpon()
                            .appendPath("oauth")
                            .appendPath("authorize")
                            .appendQueryParameter("client_id", clientId)
                            .appendQueryParameter("redirect_uri", redirectUri)
                            .appendQueryParameter("response_type", "code")
                            .appendQueryParameter("scope", "read write follow")
                            .build()

                        val customTabsIntent = CustomTabsIntent.Builder().build()
                        customTabsIntent.launchUrl(context, authUrl)
                    } else {
                        errorMessage = "Failed to register app on instance"
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Log In with Pixelfed")
            }
        }
    }
}
