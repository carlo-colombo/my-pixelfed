package com.example.pixelfed.data.repository

import android.content.Context
import android.net.Uri
import com.example.pixelfed.data.api.PixelfedApi
import com.example.pixelfed.data.api.StatusResponse
import com.example.pixelfed.data.auth.TokenManager
import com.example.pixelfed.utils.ImageUtils
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

class PixelfedRepository(private val context: Context, private val tokenManager: TokenManager) {

    private fun getRetrofit(baseUrl: String): Retrofit {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val gson = GsonBuilder()
            .setLenient()
            .create()
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(OkHttpClient.Builder().build())
            .build()
    }

    suspend fun registerApp(instanceUrl: String, redirectUri: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val api = getRetrofit(instanceUrl).create(PixelfedApi::class.java)
            val response = api.registerApp(
                clientName = "Pixelfed Android Client",
                redirectUris = redirectUri,
                scopes = "read write follow",
                website = "https://pixelfed.org"
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val clientId = body.getClientIdString()
                val clientSecret = body.getClientSecretString()
                if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
                    tokenManager.clientId = clientId
                    tokenManager.clientSecret = clientSecret
                    tokenManager.instanceUrl = instanceUrl
                    return@withContext Pair(clientId, clientSecret)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun exchangeCodeForToken(code: String, redirectUri: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val instanceUrl = tokenManager.instanceUrl ?: return@withContext Result.failure(Exception("Missing instance URL"))
            val clientId = tokenManager.clientId ?: return@withContext Result.failure(Exception("Missing Client ID"))
            val clientSecret = tokenManager.clientSecret ?: return@withContext Result.failure(Exception("Missing Client Secret"))

            val api = getRetrofit(instanceUrl).create(PixelfedApi::class.java)
            val response = api.fetchAccessToken(
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri,
                code = code,
                scope = "read write follow"
            )

            if (response.isSuccessful && response.body() != null) {
                val accessToken = response.body()!!.getAccessTokenString()
                if (!accessToken.isNullOrBlank()) {
                    tokenManager.accessToken = accessToken
                    return@withContext Result.success(accessToken)
                } else {
                    return@withContext Result.failure(Exception("Access token was empty in response"))
                }
            } else {
                val errBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                return@withContext Result.failure(Exception("Token error (${response.code()}): $errBody"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    suspend fun uploadPhotoAndCreateStatus(
        imageUri: Uri,
        caption: String,
        resizeTo8Mb: Boolean = false
    ): Result<StatusResponse> = withContext(Dispatchers.IO) {
        try {
            val instanceUrl = tokenManager.instanceUrl ?: return@withContext Result.failure(Exception("Not logged in"))
            val accessToken = tokenManager.accessToken ?: return@withContext Result.failure(Exception("Not logged in"))

            val api = getRetrofit(instanceUrl).create(PixelfedApi::class.java)
            val file = if (resizeTo8Mb) {
                ImageUtils.resizeImageDownToMaxBytes(context, imageUri, ImageUtils.MAX_BYTES_8MB)
            } else {
                getFileFromUri(imageUri)
            } ?: return@withContext Result.failure(Exception("Unable to process image file"))

            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val descRequestBody = caption.toRequestBody("text/plain".toMediaTypeOrNull())

            val mediaResponse = api.uploadMedia(
                authHeader = "Bearer $accessToken",
                file = multipartBody,
                description = descRequestBody
            )

            if (!mediaResponse.isSuccessful || mediaResponse.body() == null) {
                return@withContext Result.failure(Exception("Media upload failed: ${mediaResponse.code()} ${mediaResponse.errorBody()?.string()}"))
            }

            val mediaId = mediaResponse.body()!!.getIdString() ?: return@withContext Result.failure(Exception("Media upload response missing ID"))

            val statusResponse = api.createStatus(
                authHeader = "Bearer $accessToken",
                status = caption,
                mediaIds = listOf(mediaId)
            )

            if (statusResponse.isSuccessful && statusResponse.body() != null) {
                Result.success(statusResponse.body()!!)
            } else {
                Result.failure(Exception("Status creation failed: ${statusResponse.code()} ${statusResponse.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
