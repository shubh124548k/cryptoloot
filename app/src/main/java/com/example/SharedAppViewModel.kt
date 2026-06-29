package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedAppViewModel(private val userRepo: UserRepository) : ViewModel() {
    private val _dailyResetTimerState = MutableStateFlow(0)
    val dailyResetTimerState: StateFlow<Int> = _dailyResetTimerState.asStateFlow()

    private var countdownJob: Job? = null

    fun setInitialCountdown(seconds: Int) {
        cancelCountdown()
        if (seconds > 0) {
            _dailyResetTimerState.value = seconds
            startCountdown()
        } else {
            _dailyResetTimerState.value = 0
        }
    }

    private fun startCountdown() {
        countdownJob = viewModelScope.launch {
            while (_dailyResetTimerState.value > 0) {
                delay(1000L)
                _dailyResetTimerState.value -= 1
                if (_dailyResetTimerState.value <= 0) {
                    refreshHandshake()
                }
            }
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    override fun onCleared() {
        cancelCountdown()
        super.onCleared()
    }

    private suspend fun refreshHandshake() {
        // Automatically fire background refresh when it drops to 0
        try {
            val response = userRepo.performDeviceHandshake()
            if (response.daily_reset_seconds_remaining != null && response.daily_reset_seconds_remaining > 0) {
                setInitialCountdown(response.daily_reset_seconds_remaining)
            }
        } catch (e: Exception) {
            // handle error if needed
        }
    }
}
