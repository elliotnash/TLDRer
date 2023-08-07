package services.signal.jsonrpc

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

@Serializable
sealed interface RpcMessage {
    val jsonrpc: String
}

@Serializable
data class RpcCall (
    override val jsonrpc: String,
    val method: String,
    val params: JsonObject,
    val id: String? = null,
) : RpcMessage {
    constructor(method: String, params: JsonObject): this(
        "2.0",
        method,
        params,
        UUID.randomUUID().toString(),
    )
}

@Serializable
data class RpcResult (
    override val jsonrpc: String,
    val result: JsonObject,
    val id: String,
) : RpcMessage

object RpcMessageSerializer : JsonContentPolymorphicSerializer<RpcMessage>(RpcMessage::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "result" in element.jsonObject -> RpcResult.serializer()
        else -> RpcCall.serializer()
    }
}
