package com.example.pixelfed.data.repository

import android.content.Context
import android.net.Uri
import com.example.pixelfed.data.api.PixelfedApi
import com.example.pixelfed.data.api.StatusResponse
import com.example.pixelfed.data.api.StatusItem
import com.example.pixelfed.data.auth.TokenManager
import com.example.pixelfed.utils.ImageUtils
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
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

    suspend fun registerApp(instanceUrl: String, redirectUri: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val cachedInstance = tokenManager.instanceUrl
            val cachedClientId = tokenManager.clientId
            val cachedClientSecret = tokenManager.clientSecret

            if (cachedInstance.equals(instanceUrl, ignoreCase = true) &&
                !cachedClientId.isNullOrBlank() &&
                !cachedClientSecret.isNullOrBlank()) {
                return@withContext Result.success(Pair(cachedClientId, cachedClientSecret))
            }

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
                    return@withContext Result.success(Pair(clientId, clientSecret))
                } else {
                    return@withContext Result.failure(Exception("Registration response missing client_id or client_secret"))
                }
            } else {
                val rawErrBody = response.errorBody()?.string()?.trim()
                val parsedMsg = if (!rawErrBody.isNullOrEmpty()) {
                    parseErrorResponseBody(rawErrBody)
                } else {
                    response.message().ifBlank { "HTTP ${response.code()}" }
                }

                val fullError = "Registration error (HTTP ${response.code()}): $parsedMsg"
                return@withContext Result.failure(Exception(fullError))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            val causeMessage = e.localizedMessage ?: e.message ?: e.toString()
            val errorMsg = "Network/Registration failed (${e.javaClass.simpleName}): $causeMessage"
            return@withContext Result.failure(Exception(errorMsg, e))
        }
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

    suspend fun getUserTopTags(forceRefresh: Boolean = false): Result<List<String>> = withContext(Dispatchers.IO) {
        val cacheDurationMs = 12 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()

        if (!forceRefresh) {
            val cachedTime = tokenManager.tagsCacheTime
            val cachedJson = tokenManager.cachedTagsJson
            if (cachedJson != null && (currentTime - cachedTime) < cacheDurationMs) {
                try {
                    val listType = object : TypeToken<List<String>>() {}.type
                    val cachedList: List<String> = Gson().fromJson(cachedJson, listType)
                    return@withContext Result.success(cachedList)
                } catch (e: Exception) {
                    // fall back to fetching if JSON parsing fails
                }
            }
        }

        try {
            val instanceUrl = tokenManager.instanceUrl ?: return@withContext Result.failure(Exception("Not logged in"))
            val accessToken = tokenManager.accessToken ?: return@withContext Result.failure(Exception("Not logged in"))

            val api = getRetrofit(instanceUrl).create(PixelfedApi::class.java)
            val authHeader = "Bearer $accessToken"

            val accountResponse = api.verifyCredentials(authHeader)
            if (!accountResponse.isSuccessful || accountResponse.body() == null) {
                return@withContext Result.failure(Exception("Failed to verify user credentials"))
            }

            val userId = accountResponse.body()!!.getIdString()
                ?: return@withContext Result.failure(Exception("User ID not found"))

            val statusesResponse = api.getUserStatuses(authHeader, userId, limit = 100)
            if (!statusesResponse.isSuccessful || statusesResponse.body() == null) {
                return@withContext Result.failure(Exception("Failed to fetch user statuses"))
            }

            val statuses = statusesResponse.body()!!
            val topTags = extractTopTagsFromStatuses(statuses, topCount = 20)

            tokenManager.cachedTagsJson = Gson().toJson(topTags)
            tokenManager.tagsCacheTime = currentTime

            Result.success(topTags)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun parseErrorResponseBody(rawErrBody: String): String {
            return try {
                val jsonElement = com.google.gson.JsonParser.parseString(rawErrBody)
                if (jsonElement.isJsonObject) {
                    val jsonObject = jsonElement.asJsonObject

                    val errorElem = jsonObject.get("error")
                    val descElem = jsonObject.get("error_description")
                    val msgElem = jsonObject.get("message")

                    fun getStringFromElem(elem: com.google.gson.JsonElement?): String? = when {
                        elem == null || elem.isJsonNull -> null
                        elem.isJsonPrimitive -> elem.asString
                        else -> elem.toString()
                    }

                    val errorVal = getStringFromElem(errorElem)
                    val descVal = getStringFromElem(descElem)
                    val msgVal = getStringFromElem(msgElem)

                    when {
                        !errorVal.isNullOrBlank() && !descVal.isNullOrBlank() -> "$errorVal: $descVal"
                        !descVal.isNullOrBlank() -> descVal
                        !errorVal.isNullOrBlank() -> errorVal
                        !msgVal.isNullOrBlank() -> msgVal
                        else -> rawErrBody
                    }
                } else {
                    rawErrBody
                }
            } catch (t: Throwable) {
                rawErrBody
            }
        }

        fun extractTopTagsFromStatuses(statuses: List<StatusItem>, topCount: Int = 20): List<String> {
            val tagCounts = mutableMapOf<String, Int>()

            for (status in statuses) {
                val seenInStatus = mutableSetOf<String>()

                // 1. Tags array provided by API
                status.tags?.forEach { tag ->
                    val tagName = tag.name?.trim()?.removePrefix("#")
                    if (!tagName.isNullOrEmpty()) {
                        seenInStatus.add(tagName.lowercase())
                    }
                }

                // 2. Fallback / supplementary hashtag parsing from content HTML or text
                val content = status.content
                if (!content.isNullOrEmpty()) {
                    val hashtagRegex = Regex("""#(\w+)""")
                    hashtagRegex.findAll(content).forEach { matchResult ->
                        val tag = matchResult.groupValues[1].lowercase()
                        if (tag.isNotEmpty()) {
                            seenInStatus.add(tag)
                        }
                    }
                }

                for (tag in seenInStatus) {
                    tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
                }
            }

            return tagCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(topCount)
                .map { it.key }
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
