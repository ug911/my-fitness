package com.ug911.myfitness.knowledge

import android.content.Context
import com.ug911.myfitness.data.model.KnowledgeBundle
import com.ug911.myfitness.data.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Where the knowledge base comes from.
 *
 * The app ships the built bundle as an asset, so a fresh install has the whole coach and
 * food table before it has ever seen the network. After that it can pull a newer bundle
 * from any URL you can publish to - a raw file in the repository, or a wiki page holding
 * the JSON.
 */
class KnowledgeSync(
    private val context: Context,
    private val repository: KnowledgeRepository,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Loads the bundle shipped with the app. Runs on first launch. */
    suspend fun loadBundledIfEmpty(): SyncOutcome = withContext(Dispatchers.IO) {
        if (!repository.isEmpty()) return@withContext SyncOutcome.AlreadyPresent
        loadBundled()
    }

    suspend fun loadBundled(): SyncOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val body = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val bundle = json.decodeFromString(KnowledgeBundle.serializer(), body)
            repository.replaceWith(bundle)
            SyncOutcome.Loaded(bundle.exercises.size, bundle.foods.size, "the copy shipped with the app")
        }.getOrElse { SyncOutcome.Failed(it.message ?: "Could not read the bundled knowledge base") }
    }

    /** Fetches and applies a bundle from [url]. */
    suspend fun syncFrom(url: String): SyncOutcome = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext SyncOutcome.Failed("No knowledge base URL set")
        runCatching {
            val request = Request.Builder().url(url).header("accept", "application/json, text/html").build()
            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val bundle = json.decodeFromString(KnowledgeBundle.serializer(), extractKnowledgeJson(body))
            if (bundle.exercises.isEmpty() && bundle.foods.isEmpty()) {
                error("That URL returned no exercises or foods")
            }
            repository.replaceWith(bundle)
            SyncOutcome.Loaded(bundle.exercises.size, bundle.foods.size, url)
        }.getOrElse { SyncOutcome.Failed(it.message ?: "Sync failed") }
    }

    companion object {
        const val ASSET = "knowledge-base.json"
    }
}

/**
 * A published wiki page is usually the JSON wrapped in HTML, so pull the document back
 * out of a `<pre>` or `<code>` block before parsing. A plain JSON response passes
 * through untouched.
 */
internal fun extractKnowledgeJson(body: String): String {
    val trimmed = body.trim()
    if (trimmed.startsWith("{")) return trimmed
    val fenced = Regex("<(?:pre|code)[^>]*>([\\s\\S]*?)</(?:pre|code)>", RegexOption.IGNORE_CASE)
        .find(trimmed)
        ?.groupValues
        ?.get(1)
    val candidate = fenced?.let(::unescapeHtml)?.trim() ?: trimmed
    if (candidate.startsWith("{")) return candidate
    // Last resort: the outermost object anywhere in the page.
    val start = candidate.indexOf('{')
    val end = candidate.lastIndexOf('}')
    if (start >= 0 && end > start) return candidate.substring(start, end + 1)
    error("That page did not contain a knowledge base")
}

private fun unescapeHtml(value: String): String = value
    .replace("&quot;", "\"")
    .replace("&#34;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")

sealed interface SyncOutcome {
    data class Loaded(val exercises: Int, val foods: Int, val from: String) : SyncOutcome {
        val summary: String get() = "$exercises exercises and $foods foods from $from"
    }

    data object AlreadyPresent : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}
