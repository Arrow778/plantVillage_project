package edu.geng.plantapp.ui.screens.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.geng.plantapp.data.remote.ProfileResponse
import edu.geng.plantapp.repository.AuthRepository
import edu.geng.plantapp.repository.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    data class Success(val profile: ProfileResponse) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

class ProfileViewModel(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val uiState: StateFlow<ProfileState> = _uiState.asStateFlow()

    private val _logoutState = MutableStateFlow<Resource<String>?>(null)
    val logoutState: StateFlow<Resource<String>?> = _logoutState.asStateFlow()

    private val _verifyState = MutableStateFlow<Resource<String>?>(null)
    val verifyState: StateFlow<Resource<String>?> = _verifyState.asStateFlow()

    fun fetchProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileState.Loading
            when (val res = authRepo.getUserProfile()) {
                is Resource.Success -> _uiState.value = ProfileState.Success(res.data!!)
                is Resource.Error -> _uiState.value = ProfileState.Error(res.message ?: "获取失败")
                else -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _logoutState.value = Resource.Loading()
            val res = authRepo.logout()
            _logoutState.value = res
        }
    }

    fun verifyExpert(username: String, code: String) {
        viewModelScope.launch {
            _verifyState.value = Resource.Loading()
            val res = authRepo.verifyExpert(username, code)
            _verifyState.value = res
            if (res is Resource.Success) {
                fetchProfile()
            }
        }
    }

    fun resetVerifyState() {
        _verifyState.value = null
    }
}

class ProfileViewModelFactory(
    private val authRepo: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(authRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
