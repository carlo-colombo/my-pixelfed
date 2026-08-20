package com.example.pixelfed.data.repository

import android.content.Context
import android.net.Uri
import com.example.pixelfed.data.api.MediaResponse
import com.example.pixelfed.data.api.PixelfedApi
import com.example.pixelfed.data.api.StatusResponse
import com.example.pixelfed.data.auth.TokenManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

class PixelfedRepository(private val context: Context, private val tokenManager: TokenManager) {

    private fun getRetrofit(baseUrl: String): Retrofit {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().build())
            .build()
    }

    suspend fun registerApp(instanceUrl: String, redirectUri: String): Pair<String, String>? {
        val api = getRetrofit(instanceUrl).create(PixelfedApi::class.java)
        val response = api.registerApp(
            clientName = "Pixelfed Android Client",
            redirectUris = redirectUri,
            scopes = "read write follow",
            website = "https://pixelfed.org"
        )
        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            tokenManager.clientId = body.clientId
            tokenManager.clientSecret = body.clientSecret
            tokenManager.instanceUrl = instanceUrl
            return Pair(body.clientId, body.clientSecret)
        }
        return null
    }

    suspend fun exchangeCodeForToken(code: String, redirectUri: String): Boolean {
        val instanceUrl = tokenManager.instanceUrl ?: return false
        val clientId = tokenManager.clientId ?: return false
        val clientSecret = tokenManager.clientSecret ?: return false

        val api = getRetrofit(instanceUrl).create(PixelfedApi::class.java)
        val response = api.fetchAccessToken(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
            code = code,
            scope = "read write follow"
        )

        if (response.isSuccessful && response.body() != null) {
            tokenManager.accessToken = response.body()!!.accessToken
            return true
        }
        return false
    }

    suspend fun uploadPhotoAndCreateStatus(imageUri: Uri, caption: String): Result<StatusResponse> {
        return try {
            val instanceUrl = tokenManager.instanceUrl ?: return Result.failure(Exception("Not logged in"))
            val accessToken = tokenManager.accessToken ?: return Result.failure(Exception("Not logged in"))

            val api = getRetrofit(instanceUrl).create(PixelfedApi::class.java)
            val file = getFileFromUri(imageUri) ?: return Result.failure(Exception("Unable to process image file"))

            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val descRequestBody = caption.toRequestBody("text/plain".toMediaTypeOrNull())

            val mediaResponse = api.uploadMedia(
                authHeader = "Bearer $accessToken",
                file = multipartBody,
                description = descRequestBody
            )

            if (!mediaResponse.isSuccessful || mediaResponse.body() == null) {
                return Result.failure(Exception("Media upload failed: ${mediaResponse.code()} ${mediaResponse.errorBody()?.string()}"))
            }

            val mediaId = mediaResponse.body()!!.id

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
