package services

import kotlinx.coroutines.CoroutineScope

interface ChatService {
    val name: String
    fun start(scope: CoroutineScope)
    fun addMessageListener(listener: suspend (ChatMessage) -> Unit)
    fun removeMessageListener(listener: suspend (ChatMessage) -> Unit)
    suspend fun sendMessage(conversationId: String, message: String)
    fun getMessage(timestamp: Long): ChatMessage?
    fun getLastMessage(conversationId: String, senderId: String, before: Long? = null): ChatMessage?
    fun getMessages(conversationId: String, since: Long? = null, before: Long? = null, limit: Int? = null): List<ChatMessage>
}
