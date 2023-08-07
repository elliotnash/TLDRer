package services.signal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SignalContact(
    val number: String,
    val uuid: String,
    val contactName: String?,
    val profileName: String?,
) {
    val displayName: String
        get() {
            if (profileName != null) {
                val first = profileName.split(" ").first()
                if (first.length > 1) {
                    return first
                }
            }
            if (contactName != null) {
                return contactName.split(" ").first()
            }
            return number
        }

    companion object {
        fun fromJsonObject(json: JsonObject): SignalContact? {
            val number = json["number"]?.jsonPrimitive?.contentOrNull ?: return null
            val uuid = json["uuid"]?.jsonPrimitive?.contentOrNull ?: return null
            val contactName = json["name"]!!.jsonPrimitive.contentOrNull

            val profile = json["profile"]!!.jsonObject
            val givenName = profile["givenName"]?.jsonPrimitive?.contentOrNull ?: ""
            val familyName = profile["familyName"]?.jsonPrimitive?.contentOrNull ?: ""
            var profileName: String? = "$givenName $familyName".trim()
            if (profileName.isNullOrBlank()) {
                profileName = null
            }

            return SignalContact(
                number,
                uuid,
                contactName,
                profileName
            )
        }
    }
}
