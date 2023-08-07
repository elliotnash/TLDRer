package services

data class ChatMessage (
    val timestamp: Long,
    val senderName: String,
    val conversationId: String,
    val message: String,
    val fromSelf: Boolean,
    val reply: String?
)