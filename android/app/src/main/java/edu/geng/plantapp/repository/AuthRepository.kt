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

/**
 * 全局错误消息解析工具：从 API errorBody 中提取可读的错误信息。
 * 支持的字段优先级：msg > message > error > detail
 * 如果 errorBody 为空或解析失败，则返回 fallback。
 */
fun parseApiError(errorBody: String?, fallback: String = "操作失败"): String {
    if (errorBody.isNullOrBlank()) return fallback
    return try {
        val json = JSONObject(errorBody)
        json.optString("msg", null)
            ?: json.optString("message", null)
            ?: json.optString("error", null)
            ?: json.optString("detail", null)
            ?: fallback
    } catch (e: Exception) {
        // 如果不是JSON格式（如纯文本），直接返回 fallback 而非原始内容
        fallback
    }
}

class AuthRepository(
    private val authApi: AuthApi,
    private val dsManager: DataStoreManager
) {


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
                val errorMsg = parseApiError(response.errorBody()?.string(), "登录失败: ${response.code()}")
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
                val errorMsg = parseApiError(response.errorBody()?.string(), "注册失败: ${response.code()}")
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
                val body = response.body()
                // ✅ 关键：用后端签发的新 Token（含 is_expert=true）替换本地旧 Token
                // 否则旧 Token 的 JWT Payload 里 is_expert 仍为 false，专家接口会一直报 403
                val newToken = body?.new_access_token
                if (!newToken.isNullOrEmpty()) {
                    dsManager.saveToken(newToken)
                }
                Resource.Success(body?.msg ?: "认证成功")
            } else {
                val errorMsg = parseApiError(response.errorBody()?.string(), "专家认证失败: ${response.code()}")
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络连接异常: ${e.localizedMessage}")
        }
    }

}
