package edu.geng.plantapp.ui.screens.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.geng.plantapp.data.remote.LoginRequest
import edu.geng.plantapp.data.remote.LoginResponse
import edu.geng.plantapp.repository.AuthRepository
import edu.geng.plantapp.repository.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val userResponse: LoginResponse) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(private val authRepo: AuthRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState = _loginState.asStateFlow()

    fun login(username: String, hashContent: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _loginState.value = AuthState.Loading
            val result = authRepo.loginUser(LoginRequest(username, hashContent), rememberMe)
            when (result) {
                is Resource.Success -> {
                    _loginState.value = AuthState.Success(result.data!!)
                }
                is Resource.Error -> {
                    _loginState.value = AuthState.Error(result.message!!)
                }
                // should not reach here on Resource.Loading
                else -> {}
            }
        }
    }

    // Reset state after consuming error gracefully to not spam snackbars repeatedly
    fun resetState() {
        _loginState.value = AuthState.Idle
    }
}

// Helper factory to inject Repository into ViewModel easily without Hilt / Dagger
class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
