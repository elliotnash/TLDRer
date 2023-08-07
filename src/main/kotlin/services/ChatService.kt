package services

import kotlinx.coroutines.CoroutineScope

interface ChatService {
    fun start(scope: CoroutineScope)

    fun addMessageListener(listener: suspend (ChatMessage) -> Unit)
    fun removeMessageListener(listener: suspend (ChatMessage) -> Unit)
    suspend fun sendMessage(conversationId: String, message: String)
}
