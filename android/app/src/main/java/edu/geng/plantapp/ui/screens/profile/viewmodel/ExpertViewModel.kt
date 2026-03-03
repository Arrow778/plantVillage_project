package edu.geng.plantapp.ui.screens.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.geng.plantapp.data.remote.ExpertStatsResponse
import edu.geng.plantapp.data.remote.ContributionListResponse
import edu.geng.plantapp.repository.ExpertRepository
import edu.geng.plantapp.repository.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ExpertStatsState {
    object Idle : ExpertStatsState()
    object Loading : ExpertStatsState()
    data class Success(val stats: ExpertStatsResponse) : ExpertStatsState()
    data class Error(val message: String) : ExpertStatsState()
}

sealed class ContributionListState {
    object Idle : ContributionListState()
    object Loading : ContributionListState()
    data class Success(val listResp: ContributionListResponse) : ContributionListState()
    data class Error(val message: String) : ContributionListState()
}

class ExpertViewModel(
    private val expertRepo: ExpertRepository
) : ViewModel() {

    private val _statsState = MutableStateFlow<ExpertStatsState>(ExpertStatsState.Idle)
    val statsState: StateFlow<ExpertStatsState> = _statsState.asStateFlow()

    private val _contributeState = MutableStateFlow<Resource<String>?>(null)
    val contributeState: StateFlow<Resource<String>?> = _contributeState.asStateFlow()

    private val _listState = MutableStateFlow<ContributionListState>(ContributionListState.Idle)
    val listState: StateFlow<ContributionListState> = _listState.asStateFlow()

    fun fetchStats() {
        viewModelScope.launch {
            _statsState.value = ExpertStatsState.Loading
            when (val res = expertRepo.getStats()) {
                is Resource.Success -> _statsState.value = ExpertStatsState.Success(res.data!!)
                is Resource.Error -> _statsState.value = ExpertStatsState.Error(res.message ?: "获取失败")
                else -> {}
            }
        }
    }

    fun submitContribution(name: String, plan: String, imageUris: List<android.net.Uri>) {
        viewModelScope.launch {
            _contributeState.value = Resource.Loading()
            val res = expertRepo.contribute(name, plan, imageUris)
            _contributeState.value = res
            if (res is Resource.Success) {
                fetchStats() // refresh stats on success
                fetchContributions(1) // refresh list
            }
        }
    }

    fun resubmitContribution(id: Int, name: String, plan: String, imageUris: List<android.net.Uri>) {
        viewModelScope.launch {
            _contributeState.value = Resource.Loading()
            val res = expertRepo.resubmitContribution(id, name, plan, imageUris)
            _contributeState.value = res
            if (res is Resource.Success) {
                fetchStats()
                fetchContributions(1)
            }
        }
    }

    fun fetchContributions(page: Int) {
        viewModelScope.launch {
            _listState.value = ContributionListState.Loading
            when (val res = expertRepo.getContributions(page)) {
                is Resource.Success -> _listState.value = ContributionListState.Success(res.data!!)
                is Resource.Error -> _listState.value = ContributionListState.Error(res.message ?: "获取失败")
                else -> {}
            }
        }
    }

    fun resetContributeState() {
        _contributeState.value = null
    }

}

class ExpertViewModelFactory(
    private val expertRepo: ExpertRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpertViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpertViewModel(expertRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
