package edu.geng.plantapp.repository

import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.PredictApi
import edu.geng.plantapp.data.remote.WikiResponse
import kotlinx.coroutines.flow.first

class PredictRepository(
    private val predictApi: PredictApi,
    private val dsManager: DataStoreManager
) {
    companion object {
        // ✅ 全局共享缓存（进程内持久），HomeScreen 和 ResultScreen 共用同一份
        private val wikiCache = mutableMapOf<String, WikiResponse>()

        fun clearCacheForLabel(label: String) {
            wikiCache.remove(label)
        }
    }

    suspend fun fetchWikiContent(label: String): Resource<WikiResponse> {
        // ✅ 缓存命中 → 直接返回，0 网络请求
        wikiCache[label]?.let { return Resource.Success(it) }

        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) {
                return Resource.Error("身份验证令牌已遗失，请重新登入")
            }
            
            val response = predictApi.getWikiDetail("Bearer $token", label)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.status == "success") {
                    wikiCache[label] = body  // ✅ 写入缓存
                    Resource.Success(body)
                } else {
                    Resource.Error(body?.msg ?: "基础百科接口返回异常")
                }
            } else {
                val errorMsg = parseApiError(response.errorBody()?.string(), "百科云服务暂时离线: ${response.code()}")
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络连接异常: " + e.localizedMessage)
        }
    }

}
