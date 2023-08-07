package services.signal

import kotlinx.coroutines.*
// import kotlinx.coroutines.future.
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import services.ChatService
import services.signal.jsonrpc.RpcCall
import services.signal.jsonrpc.RpcMessage
import services.signal.jsonrpc.RpcMessageSerializer
import services.signal.jsonrpc.RpcResult
import java.io.BufferedReader
import java.util.Scanner
import java.util.UUID

class SignalService : ChatService {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphicDefaultSerializer(RpcMessage::class) { RpcMessageSerializer }
            polymorphicDefaultDeserializer(RpcMessage::class) { RpcMessageSerializer }
        }
    }
    private val process = ProcessBuilder("signal-cli", "jsonRpc").start()
    private val reader = process.inputStream.reader()
    private val writer = process.outputStream.writer()

    private val rpcQueue = mutableMapOf<String, CompletableDeferred<RpcResult>>()
    private suspend fun sendRpcMessage(message: RpcCall): RpcResult {
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
                    val message = json.decodeFromString<RpcMessage>(it)
                    if (message is RpcResult) {
                        // If it's a result, we need to complete the rpcQueue
                        rpcQueue.remove(message.id)?.complete(message)
                    } else if (message is RpcCall) {
                        // Else this is a message received
                        println(message)
                    }
                }
            }
        }
        scope.launch {
             sendMessage(listOf("+12508807560"), "Testing sendMessage function 3")
        }
    }
    suspend fun sendMessage(recipients: List<String>, message: String) {
        val call = RpcCall(
            "send",
            JsonObject(
                mapOf(
                    "recipient" to JsonArray(recipients.map { JsonPrimitive(it) }),
                    "message" to JsonPrimitive(message)
                )
            )
        )
        sendRpcMessage(call)
    }
}
