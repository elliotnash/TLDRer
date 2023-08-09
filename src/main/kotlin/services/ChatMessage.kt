package services

data class ChatMessage (
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val conversationId: String,
    val text: String?,
    val fromSelf: Boolean,
    val fromBot: Boolean,
    val replyText: String?,
    val reactionEmoji: String?,
    val reactionText: String?,
    val attachmentsInfo: String?,
)
