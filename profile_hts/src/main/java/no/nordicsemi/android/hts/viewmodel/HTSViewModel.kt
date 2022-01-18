package no.nordicsemi.android.hts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import no.nordicsemi.android.hts.data.HTSRepository
import no.nordicsemi.android.hts.view.DisconnectEvent
import no.nordicsemi.android.hts.view.DisplayDataState
import no.nordicsemi.android.hts.view.HTSScreenViewEvent
import no.nordicsemi.android.hts.view.HTSState
import no.nordicsemi.android.hts.view.LoadingState
import no.nordicsemi.android.hts.view.OnTemperatureUnitSelected
import no.nordicsemi.android.service.BleManagerStatus
import no.nordicsemi.android.utils.exhaustive
import javax.inject.Inject

@HiltViewModel
internal class HTSViewModel @Inject constructor(
    private val repository: HTSRepository
) : ViewModel() {

    val state = repository.data.combine(repository.status) { data, status ->
        when (status) {
            BleManagerStatus.CONNECTING -> HTSState(LoadingState)
            BleManagerStatus.OK -> HTSState(DisplayDataState(data))
            BleManagerStatus.DISCONNECTED -> HTSState(DisplayDataState(data), false)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, HTSState(LoadingState))

    fun onEvent(event: HTSScreenViewEvent) {
        when (event) {
            DisconnectEvent -> onDisconnectButtonClick()
            is OnTemperatureUnitSelected -> onTemperatureUnitSelected(event)
        }.exhaustive
    }

    private fun onDisconnectButtonClick() {
        repository.sendDisconnectCommand()
        repository.clear()
    }

    private fun onTemperatureUnitSelected(event: OnTemperatureUnitSelected) {
        repository.setTemperatureUnit(event.value)
    }

    override fun onCleared() {
        super.onCleared()
        repository.clear()
    }
}
