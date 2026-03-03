package edu.geng.plantapp.ui.screens.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.geng.plantapp.data.remote.HistoryItem
import edu.geng.plantapp.repository.FeedbackRepository
import edu.geng.plantapp.repository.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import edu.geng.plantapp.ml.TFLiteHelper
import edu.geng.plantapp.data.remote.CloudPredictData

sealed class HomeState {
    object Idle : HomeState()
    object Loading : HomeState()
    data class Success(val history: List<HistoryItem>) : HomeState()
    data class Error(val message: String) : HomeState()
}

class HomeViewModel(
    private val feedbackRepo: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeState>(HomeState.Idle)
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<HistorySyncState>(HistorySyncState.Idle)
    val syncState: StateFlow<HistorySyncState> = _syncState.asStateFlow()

    private val _feedbackState = MutableStateFlow<Resource<String>?>(null)
    val feedbackState: StateFlow<Resource<String>?> = _feedbackState.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _recognitionResult = MutableStateFlow<TFLiteHelper.RecognitionResult?>(null)
    val recognitionResult: StateFlow<TFLiteHelper.RecognitionResult?> = _recognitionResult.asStateFlow()

    private val _cloudPredictState = MutableStateFlow<Resource<CloudPredictData>?>(null)
    val cloudPredictState: StateFlow<Resource<CloudPredictData>?> = _cloudPredictState.asStateFlow()

    fun setPrediction(bitmap: Bitmap, result: TFLiteHelper.RecognitionResult?) {
        _currentBitmap.value = bitmap
        _recognitionResult.value = result
        _cloudPredictState.value = null
    }

    fun clearPrediction() {
        _currentBitmap.value = null
        _recognitionResult.value = null
        _cloudPredictState.value = null
        resetSyncState()
    }

    fun predictCloud() {
        val bitmap = _currentBitmap.value ?: return
        viewModelScope.launch {
            _cloudPredictState.value = Resource.Loading()
            val res = feedbackRepo.predictCloud(bitmap)
            if (res is Resource.Success && res.data != null) {
                // Update recognition result to visually change it.
                _recognitionResult.value = TFLiteHelper.RecognitionResult(
                    label = res.data.prediction,
                    confidence = res.data.confidence.toFloat()
                )
            }
            _cloudPredictState.value = res
        }
    }

    fun fetchHistory(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = HomeState.Loading
            when (val res = feedbackRepo.getHistoryList(forceRefresh)) {
                is Resource.Success -> {
                    _uiState.value = HomeState.Success(res.data ?: emptyList())
                }
                is Resource.Error -> {
                    _uiState.value = HomeState.Error(res.message ?: "拉取失败")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun syncEdgeResult(bitmap: android.graphics.Bitmap, label: String, confidence: Float) {
        viewModelScope.launch {
            _syncState.value = HistorySyncState.Loading
            when (val res = feedbackRepo.syncEdgeResult(bitmap, label, confidence)) {
                is Resource.Success -> {
                    _syncState.value = HistorySyncState.Success(res.data ?: -1)
                    fetchHistory(forceRefresh = true) // Refresh the list after successful sync
                }
                is Resource.Error -> {
                    _syncState.value = HistorySyncState.Error(res.message ?: "图片存证上云失败")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun submitFeedback(historyId: Int, score: Int, comments: String) {
        viewModelScope.launch {
            _feedbackState.value = Resource.Loading()
            val res = feedbackRepo.submitFeedback(historyId, score, comments)
            _feedbackState.value = res
        }
    }

    fun resetSyncState() {
        _syncState.value = HistorySyncState.Idle
        _feedbackState.value = null
    }
}

sealed class HistorySyncState {
    object Idle : HistorySyncState()
    object Loading : HistorySyncState()
    data class Success(val historyId: Int) : HistorySyncState()
    data class Error(val message: String) : HistorySyncState()
}

class HomeViewModelFactory(
    private val feedbackRepo: FeedbackRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(feedbackRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
