package services

import kotlinx.coroutines.CoroutineScope

interface ChatService {
    fun start(scope: CoroutineScope)
}
