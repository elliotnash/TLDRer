package services.signal

import kotlinx.serialization.json.*
import services.ChatMessage

data class SignalMessage (
    val timestamp: Long,
    val sourceNumber: String,
    val sourceName: String,
    val conversationNumber: String,
    val message: String?,
    val fromSelf: Boolean,
    val fromBot: Boolean,
    val quoteId: Long?,
    val quoteText: String?,
    val reactionEmoji: String?,
    val reactionTarget: Long?,
    val isReactionRemove: Boolean?
) {
    companion object {
        fun fromEnvelope(envelope: JsonObject): SignalMessage {
            val fromSelf = envelope.containsKey("syncMessage")

            var timestamp = envelope["timestamp"]!!.jsonPrimitive.long
            val sourceNumber = envelope["sourceNumber"]!!.jsonPrimitive.content
            val sourceName = envelope["sourceName"]!!.jsonPrimitive.content

            // Gets the message regardless of who sent it
            var dataMessage = if (fromSelf) {
                // Then this is a message you sent
                envelope["syncMessage"]!!.jsonObject["sentMessage"]!!.jsonObject
            } else {
                // This is a message sent to you
                envelope["dataMessage"]!!.jsonObject
            }

            // The conversationNumber is a number that will stay constant
            // in a given conversation no matter the sender or recipient
            // On group chats, this is the groupId, and on individual conversations
            // this is the other persons number.
            val conversationNumber = if (dataMessage.containsKey("groupInfo")) {
                dataMessage["groupInfo"]!!.jsonObject["groupId"]!!.jsonPrimitive.content
            } else {
                if (fromSelf) {
                    // Destination number is the other persons number when sent from self.
                    dataMessage["destinationNumber"]!!.jsonPrimitive.content
                } else {
                    // Source number is the other persons number when sent from them.
                    envelope["sourceNumber"]!!.jsonPrimitive.content
                }
            }

            // For edited messages, the actual message is within an editMessage field
            // Since we do a replace in the database and don't care about message
            // History, we'll just set the timestamp as the targetSentTimestamp
            if (dataMessage.containsKey("editMessage")) {
                dataMessage = dataMessage["editMessage"]!!.jsonObject
                timestamp = dataMessage["targetSentTimestamp"]!!.jsonPrimitive.long
            }

            val message = dataMessage["message"]!!.jsonPrimitive.contentOrNull

            // Get quote information
            var quoteId: Long? = null
            var quoteText: String? = null

            if (dataMessage.containsKey("quote")) {
                val quote = dataMessage["quote"]!!.jsonObject
                quoteId = quote["id"]!!.jsonPrimitive.long
                quoteText = quote["text"]!!.jsonPrimitive.content
            }

            // Get reaction information
            var reactionEmoji: String? = null
            var reactionTarget: Long? = null
            var isReactionRemove: Boolean? = null

            if (dataMessage.containsKey("reaction")) {
                val reaction = dataMessage["reaction"]!!.jsonObject
                reactionEmoji = reaction["emoji"]?.jsonPrimitive?.contentOrNull
                reactionTarget = reaction["targetSentTimestamp"]?.jsonPrimitive?.longOrNull
                isReactionRemove = reaction["isRemove"]?.jsonPrimitive?.booleanOrNull
            }

            return SignalMessage(
                timestamp,
                sourceNumber,
                sourceName,
                conversationNumber,
                message,
                fromSelf,
                fromBot = false,
                quoteId,
                quoteText,
                reactionEmoji,
                reactionTarget,
                isReactionRemove
            )
        }
    }

    fun toChatMessage(contacts: List<SignalContact>): ChatMessage {
        val name = contacts.find { it.number == sourceNumber }?.displayName
            ?: sourceName.split(" ").first()

        return ChatMessage(
            timestamp,
            name,
            conversationNumber,
            message,
            fromSelf,
            fromBot,
            quoteText,
            reactionEmoji,
            "MESSAGE NOT LOOKED UP"
        )
    }
}
