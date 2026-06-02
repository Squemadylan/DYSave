package com.douyin.downloader.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val isLoggedIn: Boolean = false,
        val nickname: String? = null,
        val avatar: String? = null,
        val clientKey: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val loginStarted: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                isLoggedIn = authRepository.isLoggedIn,
                nickname = authRepository.nickname,
                avatar = authRepository.avatar,
                clientKey = authRepository.getClientKey() ?: "",
            )
        }
    }

    fun onClientKeyChanged(key: String) {
        _uiState.update { it.copy(clientKey = key, error = null) }
    }

    fun onLoginClick() {
        val clientKey = _uiState.value.clientKey.trim()
        if (clientKey.isEmpty()) {
            _uiState.update { it.copy(error = "请先填写 Client Key") }
            return
        }
        authRepository.saveClientKey(clientKey)
        _uiState.update { it.copy(loginStarted = true) }
    }

    /**
     * 由外部（AuthCallbackActivity）调用，告知登录流程已收到 code
     */
    fun onAuthCodeReceived(code: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val clientKey = authRepository.getClientKey() ?: return@launch
                authRepository.exchangeCode(
                    clientKey = clientKey,
                    clientSecret = "", // 由 Activity 传入
                    code = code,
                )
                authRepository.fetchUserInfo()
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "登录失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onLogoutClick() {
        authRepository.logout()
        refresh()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}