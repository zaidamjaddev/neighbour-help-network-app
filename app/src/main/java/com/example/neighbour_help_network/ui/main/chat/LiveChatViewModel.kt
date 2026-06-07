package com.example.neighbour_help_network.ui.main.chat

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.local.LocalAiEngine
import com.example.neighbour_help_network.data.model.ChatMessage
import com.example.neighbour_help_network.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * LiveChatViewModel — Manages the real-time Firestore message stream for LiveChatFragment.
 * Now powered by Gemini AI for real-time Urdu translation.
 */
class LiveChatViewModel : ViewModel() {

    private val repository = ChatRepository()
    private var listenerReg: ListenerRegistration? = null

    val messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val sendResult = MutableLiveData<Result<Unit>?>()
    val translateEnabled = MutableLiveData<Boolean>(false)
    val partnerPhoneNumber = MutableLiveData<String?>()
    val partnerName = MutableLiveData<String?>()

    private var currentChatId: String = "global_chat"

    /** Starts listening to messages for [chatId]. Removes any previous listener first. */
    fun startListening(chatId: String = "global_chat") {
        currentChatId = chatId
        listenerReg?.remove()
        listenerReg = repository.listenToMessages(chatId) { list ->
            // If translation is enabled, translate new messages
            if (translateEnabled.value == true) {
                translateMessages(list)
            } else {
                messages.postValue(list)
            }
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

    /** Toggles the Urdu translation overlay on/off and translates existing messages if needed. */
    fun toggleTranslate() {
        val newState = !(translateEnabled.value ?: false)
        translateEnabled.value = newState
        if (newState) {
            translateMessages(messages.value ?: emptyList())
        }
    }

    private fun translateMessages(list: List<ChatMessage>) {
        viewModelScope.launch {
            val translatedList = list.map { msg ->
                if (msg.translatedText == null) {
                    val translation = LocalAiEngine.translateToUrdu(msg.text)
                    msg.copy(translatedText = translation)
                } else {
                    msg
                }
            }
            messages.postValue(translatedList)
        }
    }

    /** Fetches the other chat participant's phone number and displayName from Firestore. */
    fun fetchPartnerPhoneNumber(chatId: String) {
        if (chatId == "global_chat") return
        val db = FirebaseFirestore.getInstance()
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("help_requests").document(chatId).get()
            .addOnSuccessListener { requestDoc ->
                if (requestDoc.exists()) {
                    val userId = requestDoc.getString("userId") ?: ""
                    val acceptedBy = requestDoc.getString("acceptedBy") ?: ""

                    val otherUid = if (currentUid == userId) {
                        acceptedBy
                    } else {
                        userId
                    }

                    if (otherUid.isNotEmpty()) {
                        db.collection("users").document(otherUid).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    val phone = userDoc.getString("phone")
                                    val name = userDoc.getString("displayName")
                                    partnerPhoneNumber.postValue(phone)
                                    partnerName.postValue(name)
                                }
                            }
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
