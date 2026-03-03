package edu.geng.plantapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String?
)

data class RegisterResponse(
    val msg: String?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val access_token: String?,
    val username: String?,
    val is_expert: Boolean?,
    val msg: String? // Flask 异常时可能会存在该字段
)

data class ProfileResponse(
    val username: String?,
    val is_expert: Boolean?,
    val is_admin: Boolean?,
    val total_recognitions: Int?,
    val msg: String?
)

data class LogoutResponse(
    val msg: String?
)

data class VerifyExpertRequest(
    val username: String,
    val expert_code: String
)

data class VerifyExpertResponse(
    val msg: String?
)

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): retrofit2.Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): retrofit2.Response<RegisterResponse>

    @retrofit2.http.GET("auth/me")
    suspend fun me(@retrofit2.http.Header("Authorization") token: String): retrofit2.Response<ProfileResponse>

    @retrofit2.http.DELETE("auth/logout")
    suspend fun logout(@retrofit2.http.Header("Authorization") token: String): retrofit2.Response<LogoutResponse>

    @POST("auth/verify_expert")
    suspend fun verifyExpert(@Body request: VerifyExpertRequest): retrofit2.Response<VerifyExpertResponse>
}
