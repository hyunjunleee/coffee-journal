package com.coffeejournal.ui.ai

/** Answers each request with the first rule whose URL part matches (in the order added); records every request. */
class FakeAiHttp : AiHttp {
    val requests = mutableListOf<AiHttpRequest>()
    private val rules = mutableListOf<Pair<String, (AiHttpRequest) -> AiHttpResponse>>()

    fun on(urlPart: String, status: Int = 200, body: () -> String) = apply { rules += urlPart to { _ -> AiHttpResponse(status, body()) } }

    /** Several replies for the same URL, one per request, the last one repeated. */
    fun sequence(urlPart: String, vararg replies: Pair<Int, String>) = apply {
        var i = 0
        rules += urlPart to { _ -> replies[minOf(i++, replies.lastIndex)].let { (s, b) -> AiHttpResponse(s, b) } }
    }

    fun offline(urlPart: String) = apply { rules += urlPart to { _ -> throw AiConnectionException("Unable to resolve host") } }

    override suspend fun send(request: AiHttpRequest): AiHttpResponse {
        requests += request
        val rule = rules.firstOrNull { request.url.contains(it.first) } ?: throw AiConnectionException("no fake reply for ${request.url}")
        return rule.second(request)
    }
}

class MemorySecretStore(override val supported: Boolean = true) : SecretStore {
    val values = mutableMapOf<String, String>()
    override suspend fun get(name: String): String? = values[name]
    override suspend fun put(name: String, value: String) { values[name] = value }
    override suspend fun delete(name: String) { values.remove(name) }
}
