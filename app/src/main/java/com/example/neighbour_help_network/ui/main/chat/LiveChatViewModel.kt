package com.example.neighbour_help_network.ui.main.chat

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.model.ChatMessage
import com.example.neighbour_help_network.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * LiveChatViewModel — Manages the real-time Firestore message stream for LiveChatFragment.
 *
 * Defaults to the "global_chat" room. Translation toggle state is held in [translateEnabled].
 */
class LiveChatViewModel : ViewModel() {

    private val repository = ChatRepository()
    private var listenerReg: ListenerRegistration? = null

    val messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val sendResult = MutableLiveData<Result<Unit>?>()
    val translateEnabled = MutableLiveData<Boolean>(false)

    private var currentChatId: String = "global_chat"

    /** Starts listening to messages for [chatId]. Removes any previous listener first. */
    fun startListening(chatId: String = "global_chat") {
        currentChatId = chatId
        listenerReg?.remove()
        listenerReg = repository.listenToMessages(chatId) { list ->
            messages.postValue(list)
        }
    }

    /** Sends a new text message using the current Firebase user's identity. */
    fun sendMessage(text: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val message = ChatMessage(
            senderId = user.uid,
            senderName = user.displayName ?: "Neighbour",
            text = text.trim()
        )
        viewModelScope.launch {
            val result = repository.sendMessage(currentChatId, message)
            sendResult.postValue(result)
        }
    }

    /** Toggles the Urdu translation overlay on/off. */
    fun toggleTranslate() {
        translateEnabled.value = !(translateEnabled.value ?: false)
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
