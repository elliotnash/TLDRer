package services.signal

import kotlinx.serialization.json.*

data class SignalGroup(
    val id: String,
    val name: String,
    val description: String,
    val isMember: Boolean,
    val isBlocked: Boolean,
) {
    companion object {
        fun fromJsonObject(json: JsonObject): SignalGroup {
            val id = json["id"]!!.jsonPrimitive.content
            val name = json["name"]!!.jsonPrimitive.content
            val description = json["description"]!!.jsonPrimitive.content
            val isMember = json["isMember"]!!.jsonPrimitive.boolean
            val isBlocked = json["isBlocked"]!!.jsonPrimitive.boolean

            return SignalGroup(
                id,
                name,
                description,
                isMember,
                isBlocked
            )
        }
    }
}
