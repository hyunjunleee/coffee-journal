package com.coffeejournal.ui.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Lenient reading of the services' JSON: a missing or differently typed field reads as null / empty. */
internal object AiJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()
    fun parseObject(text: String): JsonObject? = parse(text) as? JsonObject
}

internal val JsonElement?.obj: JsonObject? get() = this as? JsonObject
internal val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray)?.toList() ?: emptyList()
internal val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.contentOrNull
internal val JsonElement?.int: Int? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
internal val JsonElement?.dbl: Double? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
internal val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/** A service's error message out of its error body ({"error":{"message"}}, {"detail":{"error"}}, …), or the raw text. */
internal fun errorMessage(body: String): String {
    val root = AiJson.parse(body)
    val message = root["error"]["message"].str
        ?: root["error"].str
        ?: root["detail"]["error"].str
        ?: root["detail"].str
        ?: root["message"].str
    return (message ?: body).trim().take(400)
}
