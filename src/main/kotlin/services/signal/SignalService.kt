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

    private val messageListeners = mutableSetOf<(ChatMessage) -> Unit>()

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
                    handleMessage(json.decodeFromString<RpcMessage>(it))
                }
            }
        }
        scope.launch {
            fetchContactList()
            sendMessage("+12508807560", "Testing to self")
        }
    }

    private fun handleMessage(message: RpcMessage) {
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
                    val chatMessage = signalMessage.toChatMessage(contacts)
                    for (listener in messageListeners) {
                        listener(chatMessage)
                    }
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

    override fun addMessageListener(listener: (ChatMessage) -> Unit) {
        messageListeners.add(listener)
    }

    override fun removeMessageListener(listener: (ChatMessage) -> Unit) {
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

    suspend fun sendMessage(conversationNumber: String, message: String) {
        val messageMap = mutableMapOf<String, JsonElement>(
            "message" to JsonPrimitive(message)
        )

        // Phone numbers start with +1
        val group = !conversationNumber.startsWith("+")

        if (group) {
            // It's a group chat
            messageMap["groupId"] = JsonPrimitive(conversationNumber)
        } else {
            // Then it's a number, we should send it as a regular chat
            messageMap["recipient"] = JsonArray(listOf(JsonPrimitive(conversationNumber)))
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
            conversationNumber,
            message,
            true,
            null,
            null
        )
        SignalDatabase.addMessage(signalMessage)
    }
}
