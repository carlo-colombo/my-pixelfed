package com.example.pixelfed.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PixelfedApi {

    @FormUrlEncoded
    @POST("api/v1/apps")
    suspend fun registerApp(
        @Field("client_name") clientName: String,
        @Field("redirect_uris") redirectUris: String,
        @Field("scopes") scopes: String,
        @Field("website") website: String
    ): Response<RegisterAppResponse>

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun fetchAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("scope") scope: String
    ): Response<TokenResponse>

    @Multipart
    @POST("api/v1/media")
    suspend fun uploadMedia(
        @Header("Authorization") authHeader: String,
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody? = null
    ): Response<MediaResponse>

    @FormUrlEncoded
    @POST("api/v1/statuses")
    suspend fun createStatus(
        @Header("Authorization") authHeader: String,
        @Field("status") status: String,
        @Field("media_ids[]") mediaIds: List<String>
    ): Response<StatusResponse>
}

data class RegisterAppResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("scope") val scope: String,
    @SerializedName("created_at") val createdAt: Long?
)

data class MediaResponse(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("preview_url") val previewUrl: String?
)

data class StatusResponse(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String?
)
