package edu.geng.plantapp.repository

import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.PredictApi
import edu.geng.plantapp.data.remote.WikiResponse
import kotlinx.coroutines.flow.first

class PredictRepository(
    private val predictApi: PredictApi,
    private val dsManager: DataStoreManager
) {

    suspend fun fetchWikiContent(label: String): Resource<WikiResponse> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) {
                return Resource.Error("身份验证令牌已遗失，请重新登入")
            }
            
            val response = predictApi.getWikiDetail("Bearer $token", label)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.status == "success") {
                    Resource.Success(body)
                } else {
                    Resource.Error(body?.msg ?: "基础百科接口返回异常")
                }
            } else {
                 Resource.Error("百科云服务暂时离线: ${response.code()} \nMsg: ${response.errorBody()?.string() ?: "未知错误"}")
            }
        } catch (e: Exception) {
            Resource.Error("网络连接异常: " + e.localizedMessage)
        }
    }

}
