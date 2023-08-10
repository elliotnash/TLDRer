package org.elliotnash.services.signal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.util.UUID

@Serializable
sealed interface RpcMessage {
    val jsonrpc: String
}

interface RpcResponse : RpcMessage {
    val id: String
}

@Serializable
data class RpcCall (
    override val jsonrpc: String,
    val method: String,
    val params: JsonElement? = null,
    val id: String? = null,
) : RpcMessage {
    constructor(method: String, params: JsonElement): this(
        "2.0",
        method,
        params,
        UUID.randomUUID().toString(),
    )
    constructor(method: String): this(
        "2.0",
        method,
        null,
        UUID.randomUUID().toString()
    )
}

@Serializable
data class RpcResult (
    override val jsonrpc: String,
    override val id: String,
    val result: JsonElement,
) : RpcResponse

@Serializable
data class RpcError (
    override val jsonrpc: String,
    override val id: String,
    val error: Details
) : RpcResponse {
    @Serializable
    data class Details(
        val code: Int,
        val message: String,
        val data: JsonElement
    )
}

object RpcMessageSerializer : JsonContentPolymorphicSerializer<RpcMessage>(RpcMessage::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "result" in element.jsonObject -> RpcResult.serializer()
        "error" in element.jsonObject -> RpcError.serializer()
        else -> RpcCall.serializer()
    }
}
