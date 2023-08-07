package services.signal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    private fun sendRpcMessage(message: RpcMessage) {
        writer.write(json.encodeToString(message)+"\n")
        writer.flush()
    }
    override fun start(scope: CoroutineScope) {
        // Reader coroutine
        scope.launch {
            reader.forEachLine {
                val message = json.decodeFromString<RpcMessage>(it)
                println(message)
            }
        }
    }
    fun sendMessage(recipients: List<String>, message: String) {
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
