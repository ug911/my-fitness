package com.ug911.myfitness.ai

/** One text completion call. Providers differ only in how the request is shaped. */
interface AiClient {
    val providerId: String
    val model: String

    suspend fun complete(system: String, user: String): String
}

class MissingApiKeyException : IllegalStateException("No API key configured for the AI provider")

class AiRequestException(message: String) : IllegalStateException(message)
