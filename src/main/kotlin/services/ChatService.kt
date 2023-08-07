package services

import kotlinx.coroutines.CoroutineScope

interface ChatService {
    fun start(scope: CoroutineScope)

    fun addMessageListener(listener: (ChatMessage) -> Unit)
    fun removeMessageListener(listener: (ChatMessage) -> Unit)
}
