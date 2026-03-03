package edu.geng.plantapp.repository

import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.AuthApi
import edu.geng.plantapp.data.remote.LoginRequest
import edu.geng.plantapp.data.remote.LoginResponse
import kotlinx.coroutines.flow.first
import org.json.JSONObject

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}

class AuthRepository(
    private val authApi: AuthApi,
    private val dsManager: DataStoreManager
) {

    private fun parseErrorMsg(errorBody: String?, fallback: String): String {
        if (errorBody.isNullOrEmpty()) return fallback
        return try {
            val jsonObject = JSONObject(errorBody)
            jsonObject.optString("msg", fallback)
        } catch (e: Exception) {
            errorBody
        }
    }

    suspend fun loginUser(req: LoginRequest, rememberMe: Boolean): Resource<LoginResponse> {
        return try {
            val response = authApi.login(req)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.access_token != null) {
                    // Safe token to Jetpack Datastore
                    dsManager.saveToken(body.access_token)
                    // Persist remember me preference and credentials ONLY on success
                    dsManager.saveRememberMe(rememberMe, req.username, req.password)
                    Resource.Success(body)
                } else {
                    Resource.Error(body?.msg ?: "无法获取访问令牌")
                }
            } else {
                // Read Retrofit Error Body
                val errorBodyStr = response.errorBody()?.string()
                val errorMsg = parseErrorMsg(errorBodyStr, response.message())
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络连接异常: " + e.localizedMessage)
        }
    }

    suspend fun registerUser(req: edu.geng.plantapp.data.remote.RegisterRequest): Resource<edu.geng.plantapp.data.remote.RegisterResponse> {
        return try {
            val response = authApi.register(req)
            if (response.isSuccessful) {
                Resource.Success(response.body()!!)
            } else {
                val errorBodyStr = response.errorBody()?.string()
                val errorMsg = parseErrorMsg(errorBodyStr, "注册失败: ${response.code()}")
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络连接异常: " + e.localizedMessage)
        }
    }

    suspend fun getUserProfile(): Resource<edu.geng.plantapp.data.remote.ProfileResponse> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")
            val response = authApi.me("Bearer $token")
            if (response.isSuccessful) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("获取用户信息失败")
            }
        } catch (e: Exception) {
            Resource.Error("网络连接异常: ${e.localizedMessage}")
        }
    }

    suspend fun logout(): Resource<String> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (!token.isNullOrEmpty()) {
                authApi.logout("Bearer $token")
            }
            dsManager.clearToken()
            Resource.Success("登出成功")
        } catch (e: Exception) {
            dsManager.clearToken()
            Resource.Success("登出异常，但在本地清除成功")
        }
    }

    suspend fun verifyExpert(username: String, expertCode: String): Resource<String> {
        return try {
            val req = edu.geng.plantapp.data.remote.VerifyExpertRequest(username, expertCode)
            val response = authApi.verifyExpert(req)
            if (response.isSuccessful) {
                Resource.Success(response.body()?.msg ?: "认证成功")
            } else {
                val errorBodyStr = response.errorBody()?.string()
                val errorMsg = parseErrorMsg(errorBodyStr, response.message())
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络连接异常: ${e.localizedMessage}")
        }
    }
}
