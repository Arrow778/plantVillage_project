package edu.geng.plantapp.ui.screens.result.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.geng.plantapp.data.remote.WikiResponse
import edu.geng.plantapp.repository.Resource
import edu.geng.plantapp.repository.PredictRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WikiState {
    object Idle : WikiState()
    object Loading : WikiState()
    data class Success(val response: WikiResponse) : WikiState()
    data class Error(val message: String) : WikiState()
}

class ResultViewModel(
    private val predictRepository: PredictRepository
) : ViewModel() {

    private val _wikiState = MutableStateFlow<WikiState>(WikiState.Idle)
    val wikiState: StateFlow<WikiState> = _wikiState.asStateFlow()

    fun fetchWikiInfo(diseaseName: String) {
        viewModelScope.launch {
            _wikiState.value = WikiState.Loading
            when (val res = predictRepository.fetchWikiContent(diseaseName)) {
                is Resource.Success -> {
                    res.data?.let { response ->
                        _wikiState.value = WikiState.Success(response)
                    } ?: run {
                        _wikiState.value = WikiState.Error("服务器返回空字典实体")
                    }
                }
                is Resource.Error -> {
                    _wikiState.value = WikiState.Error(res.message ?: "未知网络错误")
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** 强制绕过缓存重新请求，用于"重新检测"按钮 */
    fun retryWikiInfo(diseaseName: String) {
        PredictRepository.clearCacheForLabel(diseaseName)  // 清 repo 缓存
        fetchWikiInfo(diseaseName)
    }

    fun resetWikiState() {
        _wikiState.value = WikiState.Idle
    }
}

class ResultViewModelFactory(
    private val predictRepository: PredictRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResultViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResultViewModel(predictRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
