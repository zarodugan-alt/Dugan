package com.dugan.agent.ui.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dugan.agent.data.repository.ContactEntry
import com.dugan.agent.data.repository.ContactRepository
import com.dugan.agent.data.repository.RecentCall
import com.dugan.agent.domain.telecom.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val callManager: CallManager,
) : ViewModel() {

    private val _entered = MutableStateFlow("")
    val entered: StateFlow<String> = _entered.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentCall>>(emptyList())
    val recents: StateFlow<List<RecentCall>> = _recents.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contacts: StateFlow<List<ContactEntry>> = _contacts.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _recents.value = contactRepository.recentCalls()
            _contacts.value = contactRepository.contacts(_query.value)
            _loading.value = false
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        viewModelScope.launch { _contacts.value = contactRepository.contacts(value) }
    }

    fun press(digit: String) {
        _entered.value += digit
    }

    fun longPressZero() {
        _entered.value += "+"
    }

    fun longPressStar() {
        // Comma is the dialler's "wait for tone" separator.
        _entered.value += ","
    }

    fun backspace() {
        _entered.value = _entered.value.dropLast(1)
    }

    fun clear() {
        _entered.value = ""
    }

    fun call(number: String) {
        if (number.isBlank()) return
        callManager.placeCall(number)
    }

    fun deleteRecent(id: Long) {
        viewModelScope.launch {
            contactRepository.deleteRecentCall(id)
            _recents.value = contactRepository.recentCalls()
        }
    }
}
