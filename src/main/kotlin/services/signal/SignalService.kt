package services.signal

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import me.xdrop.fuzzywuzzy.FuzzySearch
import services.ChatMessage
import services.ChatService
import java.lang.Exception
import java.util.UUID

class SignalService(
    private val accountNumber: String
) : ChatService {
    override val name = "Signal"
    private val logger = KotlinLogging.logger {}

    private val json = Json {
        serializersModule = SerializersModule {
            polymorphicDefaultSerializer(RpcMessage::class) { RpcMessageSerializer }
            polymorphicDefaultDeserializer(RpcMessage::class) { RpcMessageSerializer }
        }
    }

    private val process = ProcessBuilder("signal-cli", "-a", accountNumber, "jsonRpc").start()
    private val reader = process.inputStream.reader()
    private val writer = process.outputStream.writer()

    private val messageListeners = mutableSetOf<suspend (ChatMessage) -> Unit>()

    private val rpcQueue = mutableMapOf<String, CompletableDeferred<RpcResponse>>()

    private var contacts = listOf<SignalContact>()
    private var groups = listOf<SignalGroup>()

    private suspend fun sendRpcMessage(message: RpcCall): RpcResponse {
        // get request ID
        val id = message.id ?: UUID.randomUUID().toString()
        // Add completable to queue
        rpcQueue[id] = CompletableDeferred()
        // Send request
        withContext(Dispatchers.IO) {
            writer.write(json.encodeToString(message)+"\n")
            writer.flush()
        }
        return rpcQueue[id]!!.await()
    }

    override fun start(scope: CoroutineScope) {
        // Reader coroutine
        scope.launch {
            withContext(Dispatchers.IO) {
                reader.forEachLine {
                    scope.launch {
                        handleMessage(json.decodeFromString<RpcMessage>(it))
                    }
                }
            }
        }
        scope.launch {
            fetchContactList()
            fetchGroups()
        }
    }

    private suspend fun handleMessage(message: RpcMessage) {
        if (message is RpcResponse) {
            // If it's a result, we need to complete the rpcQueue
            rpcQueue.remove(message.id)?.complete(message)
        } else if (message is RpcCall) {
            // Else this is a message received
            try {
                val envelope = message.params!!.jsonObject["envelope"]!!.jsonObject
                // We don't care about read/delivered receipts
                if (envelope.containsKey("dataMessage") || (envelope.containsKey("syncMessage") && envelope["syncMessage"]!!.jsonObject.containsKey("sentMessage"))) {
                    val signalMessage = SignalMessage.fromJsonObject(envelope)
                    logger.trace { "Decoded update: $signalMessage from ${json.encodeToString(message)}" }
                    // Add decoded message to database
                    SignalDatabase.addMessage(signalMessage)
                    // Notifier listeners
                    if (!signalMessage.isReactionRemove && signalMessage.remoteDelete == null) {
                        notifyMessageListeners(signalMessage.toChatMessage(contacts))
                    }
                } else {
                    logger.debug { "Received non-decodable update: $message" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logger.warn { "Failed to decode decodable message:" }
                logger.warn { message }
                logger.warn { json.encodeToString(message) }
            }
        }
    }

    private suspend fun notifyMessageListeners(message: ChatMessage) = coroutineScope {
        for (listener in messageListeners) {
            launch {
                listener(message)
            }
        }
    }
    override fun addMessageListener(listener: suspend (ChatMessage) -> Unit) {
        messageListeners.add(listener)
    }

    override fun removeMessageListener(listener: suspend (ChatMessage) -> Unit) {
        messageListeners.remove(listener)
    }

    suspend fun sendSyncRequest() {
        val call = RpcCall("sendSyncRequest")
        val response = sendRpcMessage(call)
        if (response !is RpcResult) {
            logger.warn { "Error in sendSyncRequest: $response" }
        }
    }

    suspend fun fetchContactList() {
        val call = RpcCall("listContacts")
        val response = sendRpcMessage(call)
        if (response !is RpcResult) {
            logger.warn { "Error fetching contacts: $response" }
            return
        }

        contacts = response.result.jsonArray.toList().map {
            SignalContact.fromJsonObject(it.jsonObject)
        }.filterNotNull()
    }

    suspend fun fetchGroups() {
        val call = RpcCall("listGroups")
        val response = sendRpcMessage(call)
        if (response !is RpcResult) {
            logger.warn { "Error fetching groups: $response" }
            return
        }

        groups = response.result.jsonArray.toList().map {
            SignalGroup.fromJsonObject(it.jsonObject)
        }
    }

    fun lookupConversationId(conversationName: String): String? {
        val conversationMap = mutableMapOf<String, String>()
        for (contact in contacts) {
            if (contact.contactName != null) {
                conversationMap[contact.contactName] = contact.number
            }
            if (contact.profileName != null) {
                conversationMap[contact.profileName] = contact.number
            }
        }
        for (group in groups) {
            conversationMap[group.name] = group.id
        }

        val result = FuzzySearch.extractOne(conversationName, conversationMap.keys)
        // Only allow results that are close enough
        println(result)
        if (result.score < 70) {
            return null
        }
        return conversationMap[result.string]!!
    }

    override suspend fun sendMessage(conversationId: String, message: String) {
        val messageMap = mutableMapOf<String, JsonElement>(
            "message" to JsonPrimitive(message)
        )

        // Phone numbers start with +1
        val group = !conversationId.startsWith("+")

        if (group) {
            // It's a group chat
            messageMap["groupId"] = JsonPrimitive(conversationId)
        } else {
            // Then it's a number, we should send it as a regular chat
            messageMap["recipient"] = JsonArray(listOf(JsonPrimitive(conversationId)))
        }

        val call = RpcCall(
            "send",
            JsonObject(messageMap)
        )

        val response = sendRpcMessage(call)
        if (response !is RpcResult) {
            logger.warn{ "Error sending message: $response" }
            return
        }

        val timestamp = response.result.jsonObject["timestamp"]!!.jsonPrimitive.long
        // Get own name from contact list
        val accountName = contacts.find { it.number == accountNumber }?.profileName ?: accountNumber

        val signalMessage = SignalMessage(
            timestamp,
            accountNumber,
            accountName,
            conversationId,
            message,
            fromSelf = true,
            fromBot = true,
            quoteId = null,
            quoteText = null,
            reactionEmoji = null,
            reactionTarget = null,
            attachmentsInfo = null,
            isReactionRemove = false,
            remoteDelete = null,
        )
        SignalDatabase.addMessage(signalMessage)
        notifyMessageListeners(signalMessage.toChatMessage(contacts))
    }

    override fun getMessage(timestamp: Long) =
        SignalDatabase.getMessage(timestamp)?.toChatMessage(contacts)

    override fun getPreviousMessage(timestamp: Long) =
        SignalDatabase.getPreviousMessage(timestamp)?.toChatMessage(contacts)

    override fun getMessages(conversationId: String, since: Long?, before: Long?, limit: Int?) =
        SignalDatabase.getMessages(conversationId, since, before, limit).map {
            it.toChatMessage(contacts)
        }
}
