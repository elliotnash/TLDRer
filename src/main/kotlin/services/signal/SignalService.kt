package services.signal

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import services.ChatMessage
import services.ChatService
import java.lang.Exception
import java.util.UUID

class SignalService(
    val accountNumber: String
) : ChatService {
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
            sendMessage("+12508807560", "Testing to self")
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
                    val signalMessage = SignalMessage.fromEnvelope(envelope)
                    // Add decoded message to database
                    SignalDatabase.addMessage(signalMessage)
                    // Notifier listeners
                    notifyMessageListeners(signalMessage.toChatMessage(contacts))
                } else {
                    println("Received non-decodable update: $message")
                }
            } catch (e: Exception) {
                println("Failed to decode decodable message:")
                println(message)
                println(json.encodeToString(message))
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
            println("Error in sendSyncRequest: $response")
        }
    }

    suspend fun fetchContactList() {
        val call = RpcCall("listContacts")
        val response = sendRpcMessage(call)
        if (response !is RpcResult) {
            println("Error fetching contacts: $response")
            return
        }

        contacts = response.result.jsonArray.toList().map {
            SignalContact.fromJsonObject(it.jsonObject)
        }.filterNotNull()
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
            println("Error sending message: $response")
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
            quoteText = null
        )
        SignalDatabase.addMessage(signalMessage)
        notifyMessageListeners(signalMessage.toChatMessage(contacts))
    }
}
