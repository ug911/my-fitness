package com.ug911.myfitness.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pulling the bundle back out of whatever a publishing target wrapped it in. The app
 * promises it accepts a wiki page holding the JSON, so that path is tested rather than
 * assumed.
 */
class KnowledgeSyncTest {

    private val bundle = """{"version":1,"exercises":[],"foods":[]}"""

    @Test
    fun `a plain JSON response passes through`() {
        assertEquals(bundle, extractKnowledgeJson("  $bundle  "))
    }

    @Test
    fun `JSON inside a wiki page's pre block is recovered`() {
        val page = """
            <html><body><h1>Knowledge base</h1>
            <pre class="language-json">$bundle</pre>
            </body></html>
        """.trimIndent()

        assertEquals(bundle, extractKnowledgeJson(page))
    }

    @Test
    fun `HTML-escaped quotes inside a code block are unescaped`() {
        val page = "<code>{&quot;version&quot;:1,&quot;exercises&quot;:[]}</code>"

        assertEquals("""{"version":1,"exercises":[]}""", extractKnowledgeJson(page))
    }

    @Test
    fun `a page with the JSON loose in the body still works`() {
        val page = "<html><body>Here it is: $bundle</body></html>"

        assertEquals(bundle, extractKnowledgeJson(page))
    }

    @Test
    fun `a page with no knowledge base is refused rather than half-parsed`() {
        val failure = runCatching { extractKnowledgeJson("<html><body>Not found</body></html>") }

        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("did not contain"))
    }
}
