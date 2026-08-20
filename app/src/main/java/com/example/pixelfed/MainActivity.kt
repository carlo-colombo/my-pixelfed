package com.example.pixelfed

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.pixelfed.data.auth.TokenManager
import com.example.pixelfed.data.repository.PixelfedRepository
import com.example.pixelfed.ui.auth.LoginScreen
import com.example.pixelfed.ui.upload.UploadScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var repository: PixelfedRepository

    private var isAuthProcessing = mutableStateOf(false)
    private var isLoggedInState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)
        repository = PixelfedRepository(this, tokenManager)

        isLoggedInState.value = tokenManager.isLoggedIn()

        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAuthProcessing.value) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (isLoggedInState.value) {
                        UploadScreen(
                            repository = repository,
                            onLogout = {
                                tokenManager.clear()
                                isLoggedInState.value = false
                            }
                        )
                    } else {
                        LoginScreen(
                            context = this,
                            repository = repository
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "pixelfed-app" && uri.host == "oauth") {
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")
            if (error != null) {
                Toast.makeText(this, "OAuth Error: ${errorDescription ?: error}", Toast.LENGTH_LONG).show()
                return
            }

            val code = uri.getQueryParameter("code")
            if (code != null) {
                isAuthProcessing.value = true
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val success = repository.exchangeCodeForToken(code, "pixelfed-app://oauth")
                        isAuthProcessing.value = false
                        if (success) {
                            isLoggedInState.value = true
                            Toast.makeText(this@MainActivity, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "OAuth login failed", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isAuthProcessing.value = false
                        Toast.makeText(this@MainActivity, "Authentication error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
