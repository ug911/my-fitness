package com.ug911.myfitness.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MEDIA = "application/json".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

internal fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(120, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private suspend fun OkHttpClient.postJson(request: Request): JsonObject = withContext(Dispatchers.IO) {
    newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw AiRequestException("HTTP ${response.code}: ${body.take(500)}")
        }
        json.parseToJsonElement(body).jsonObject
    }
}

/** Anthropic Messages API. */
class AnthropicClient(
    private val apiKey: String,
    override val model: String = DEFAULT_MODEL,
    private val http: OkHttpClient = defaultHttpClient(),
) : AiClient {
    override val providerId = "anthropic"

    override suspend fun complete(system: String, user: String): String {
        if (apiKey.isBlank()) throw MissingApiKeyException()
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 2048)
            put("system", system)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", user)
                        },
                    )
                },
            )
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = http.postJson(request)
        return response["content"]?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw AiRequestException("Anthropic reply contained no text block")
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5"
    }
}

/** Google Gemini generateContent API. */
class GeminiClient(
    private val apiKey: String,
    override val model: String = DEFAULT_MODEL,
    private val http: OkHttpClient = defaultHttpClient(),
) : AiClient {
    override val providerId = "gemini"

    override suspend fun complete(system: String, user: String): String {
        if (apiKey.isBlank()) throw MissingApiKeyException()
        val payload = buildJsonObject {
            put(
                "system_instruction",
                buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
                },
            )
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", user) }) })
                        },
                    )
                },
            )
            put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .header("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = http.postJson(request)
        return response["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw AiRequestException("Gemini reply contained no text part")
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.0-flash"
    }
}

/** OpenAI chat completions API. */
class OpenAiClient(
    private val apiKey: String,
    override val model: String = DEFAULT_MODEL,
    private val http: OkHttpClient = defaultHttpClient(),
) : AiClient {
    override val providerId = "openai"

    override suspend fun complete(system: String, user: String): String {
        if (apiKey.isBlank()) throw MissingApiKeyException()
        val payload = buildJsonObject {
            put("model", model)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", system)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", user)
                        },
                    )
                },
            )
        }
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = http.postJson(request)
        return response["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: throw AiRequestException("OpenAI reply contained no message content")
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
