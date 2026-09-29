package com.coffeejournal.ui.ai

/**
 * Answers each request with the first rule whose URL part (and body part, when given) matches, in the order added;
 * records every request.
 */
class FakeAiHttp : AiHttp {
    override var supported: Boolean = true
    val requests = mutableListOf<AiHttpRequest>()
    private class Rule(val url: String, val body: String?, val reply: (AiHttpRequest) -> AiHttpResponse)
    private val rules = mutableListOf<Rule>()

    fun on(urlPart: String, status: Int = 200, bodyPart: String? = null, body: () -> String) =
        apply { rules += Rule(urlPart, bodyPart) { AiHttpResponse(status, body()) } }

    /** Several replies for the same URL (and body part), one per request, the last one repeated. */
    fun sequence(urlPart: String, vararg replies: Pair<Int, String>, bodyPart: String? = null) = apply {
        var i = 0
        rules += Rule(urlPart, bodyPart) { replies[minOf(i++, replies.lastIndex)].let { (s, b) -> AiHttpResponse(s, b) } }
    }

    fun offline(urlPart: String, bodyPart: String? = null) = apply { rules += Rule(urlPart, bodyPart) { throw AiConnectionException("Unable to resolve host") } }

    override suspend fun send(request: AiHttpRequest): AiHttpResponse {
        requests += request
        val rule = rules.firstOrNull { request.url.contains(it.url) && (it.body == null || request.body?.contains(it.body) == true) }
            ?: throw AiConnectionException("no fake reply for ${request.url}")
        return rule.reply(request)
    }
}

class MemorySecretStore(override val supported: Boolean = true) : SecretStore {
    val values = mutableMapOf<String, String>()
    override suspend fun get(name: String): String? = values[name]
    override suspend fun put(name: String, value: String) { values[name] = value }
    override suspend fun delete(name: String) { values.remove(name) }
}
