package edu.geng.plantapp.ui.screens.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.geng.plantapp.data.remote.RegisterRequest
import edu.geng.plantapp.data.remote.RegisterResponse
import edu.geng.plantapp.repository.AuthRepository
import edu.geng.plantapp.repository.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    data class Success(val response: RegisterResponse) : RegisterState()
    data class Error(val message: String) : RegisterState()
}

class RegisterViewModel(private val authRepo: AuthRepository) : ViewModel() {

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState = _registerState.asStateFlow()

    fun register(username: String, email: String, hashContent: String) {
        viewModelScope.launch {
            _registerState.value = RegisterState.Loading
            val result = authRepo.registerUser(RegisterRequest(username, hashContent, email))
            when (result) {
                is Resource.Success -> {
                    _registerState.value = RegisterState.Success(result.data!!)
                }
                is Resource.Error -> {
                    _registerState.value = RegisterState.Error(result.message!!)
                }
                else -> {}
            }
        }
    }

    fun resetState() {
        _registerState.value = RegisterState.Idle
    }
}

class RegisterViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RegisterViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
