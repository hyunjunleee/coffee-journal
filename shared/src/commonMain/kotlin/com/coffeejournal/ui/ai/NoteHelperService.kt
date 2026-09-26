package com.coffeejournal.ui.ai

import kotlinx.serialization.json.JsonElement

/** The result of 키 확인: [error] null means the key works. */
data class KeyCheck(val slot: AiKeySlot, val error: AiError?) {
    val ok: Boolean get() = error == null
    val label: String get() = error?.checkLabel ?: "정상"
}

/**
 * Asks the chosen service a [NoteQuestion] with the user's keys and returns an answer tied to sources, or throws
 * [AiFailure] (no key, a refused key, a quota, no sources, …). One [NoteAnswerer] per [AiProvider].
 */
class NoteHelperService(private val http: AiHttp, private val secrets: SecretStore) {
    /** The saved keys of [slots]; a missing one is absent from the map. */
    suspend fun keys(slots: List<AiKeySlot>): Map<AiKeySlot, String> =
        slots.mapNotNull { s -> secrets.get(secretName(s))?.takeIf { it.isNotBlank() }?.let { s to it } }.toMap()

    suspend fun missingKeys(provider: AiProvider): List<AiKeySlot> = provider.keys - keys(provider.keys).keys

    suspend fun ask(question: NoteQuestion, provider: AiProvider, model: String): GroundedAnswer {
        if (!http.supported || !secrets.supported) throw AiFailure(AiErrors.unsupported)
        val keys = keys(provider.keys)
        val missing = provider.keys - keys.keys
        if (missing.isNotEmpty()) throw AiFailure(AiErrors.missingKeys(missing))
        val answer = answerer(provider).answer(question, model, keys)
        // an answer with no sentence tied to a source is never shown
        if (answer.sources.isEmpty() || answer.citedRuns == 0) throw AiFailure(AiErrors.noSources())
        return answer
    }

    /** The cheapest request that tells whether [slot]'s saved key works (no search except Tavily's 1-credit one). */
    suspend fun checkKey(slot: AiKeySlot, settings: AiSettings): KeyCheck {
        if (!http.supported) return KeyCheck(slot, AiErrors.unsupported)
        val key = keys(listOf(slot))[slot] ?: return KeyCheck(slot, AiErrors.missingKeys(listOf(slot)))
        return try {
            when (slot) {
                AiKeySlot.GEMINI -> {
                    val p = if (settings.provider in GEMINI_OPTIONS) settings.provider else AiProvider.GEMINI_TAVILY
                    val model = settings.model(p)
                    exchange(AiService.GEMINI, GeminiApi.request(key, model, null, "안녕", search = false, maxOutputTokens = 8)) { AiErrors.gemini(it.status, it.body, model, search = false) }
                }
                AiKeySlot.TAVILY -> exchange(AiService.TAVILY, TavilyApi.checkRequest(key)) { AiErrors.tavily(it.status, it.body) }
                AiKeySlot.OPENAI -> {
                    val body = exchange(AiService.OPENAI, OpenAiApi.modelsRequest(key)) { AiErrors.openAi(it.status, it.body) }
                    checkModelListed(AiService.OPENAI, body, settings.model(AiProvider.OPENAI), complete = true)
                }
                AiKeySlot.CLAUDE -> {
                    val body = exchange(AiService.CLAUDE, ClaudeApi.modelsRequest(key)) { AiErrors.claude(it.status, it.body) }
                    // the list is paged (has_more): only a complete list proves a model missing
                    val complete = AiJson.parse(body)["has_more"].bool == false
                    checkModelListed(AiService.CLAUDE, body, settings.model(AiProvider.CLAUDE), complete)
                }
            }
            KeyCheck(slot, null)
        } catch (e: AiFailure) {
            KeyCheck(slot, e.error)
        }
    }

    private fun checkModelListed(service: AiService, body: String, model: String, complete: Boolean) {
        val ids = OpenAiApi.modelIds(body) ?: return
        if (!complete || ids.isEmpty()) return
        if (ids.none { it == model || it.startsWith("$model-") }) {
            throw AiFailure(AiError(AiErrorKind.MODEL_UNAVAILABLE, service, "키는 맞지만 ‘$model’ 모델은 이 키로 쓸 수 없어요.", hint = "설정에서 다른 모델을 골라 주세요."))
        }
    }

    private fun answerer(p: AiProvider): NoteAnswerer = when (p) {
        AiProvider.GEMINI_TAVILY -> GeminiTavilyAnswerer()
        AiProvider.GEMINI_SEARCH -> GeminiSearchAnswerer()
        AiProvider.OPENAI -> OpenAiAnswerer()
        AiProvider.CLAUDE -> ClaudeAnswerer()
    }

    /** One way of answering from web sources. */
    private fun interface NoteAnswerer {
        suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>): GroundedAnswer
    }

    /** Tavily finds five pages; Gemini (no tools) answers from them with [n] marks; the app checks the quotes. */
    private inner class GeminiTavilyAnswerer : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>): GroundedAnswer {
            val found = exchange(AiService.TAVILY, TavilyApi.request(keys.getValue(AiKeySlot.TAVILY), NoteHelperPrompts.tavilyQuery(q))) {
                AiErrors.tavily(it.status, it.body)
            }
            val results = TavilyApi.parse(found) ?: throw AiFailure(AiErrors.unreadable(AiService.TAVILY))
            if (results.isEmpty()) throw AiFailure(AiErrors.noSources("Tavily: 0 results"))
            val user = NoteHelperPrompts.withSources(NoteHelperPrompts.question(q), results)
            val body = exchange(AiService.GEMINI, GeminiApi.request(keys.getValue(AiKeySlot.GEMINI), model, NoteHelperPrompts.SOURCES_SYSTEM, user, search = false)) {
                AiErrors.gemini(it.status, it.body, model, search = false)
            }
            val reply = GeminiApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.GEMINI))
            GeminiApi.blocked(reply)?.let { throw AiFailure(AiErrors.blocked(it)) }
            return SourcedAnswer.answer(reply.text, results, model, GeminiApi.truncationNotes(reply.finishReason))
        }
    }

    /** Gemini with Google Search grounding: the grounding metadata ties the answer to its sources. */
    private inner class GeminiSearchAnswerer : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>): GroundedAnswer {
            val request = GeminiApi.request(keys.getValue(AiKeySlot.GEMINI), model, NoteHelperPrompts.SEARCH_SYSTEM, NoteHelperPrompts.question(q), search = true)
            val body = exchange(AiService.GEMINI, request) { AiErrors.gemini(it.status, it.body, model, search = true) }
            val reply = GeminiApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.GEMINI))
            GeminiApi.blocked(reply)?.let { throw AiFailure(AiErrors.blocked(it)) }
            return GeminiApi.groundedAnswer(reply, model)
        }
    }

    private inner class OpenAiAnswerer : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>): GroundedAnswer {
            val body = exchange(AiService.OPENAI, OpenAiApi.request(keys.getValue(AiKeySlot.OPENAI), model, NoteHelperPrompts.question(q))) {
                AiErrors.openAi(it.status, it.body)
            }
            val (text, annotations) = OpenAiApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.OPENAI))
            val incomplete = AiJson.parse(body)["status"].str == "incomplete"
            return OpenAiApi.answer(text, annotations, model, if (incomplete) listOf("답이 끝나기 전에 멈췄어요.") else emptyList())
        }
    }

    /** Claude with web search; a paused turn (pause_turn) is resumed by sending the content back, up to 3 times. */
    private inner class ClaudeAnswerer : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>): GroundedAnswer {
            val key = keys.getValue(AiKeySlot.CLAUDE)
            val question = NoteHelperPrompts.question(q)
            val content = mutableListOf<JsonElement>()
            var continuations = 0
            while (true) {
                val body = exchange(AiService.CLAUDE, ClaudeApi.request(key, model, question, content)) { AiErrors.claude(it.status, it.body) }
                val reply = ClaudeApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.CLAUDE))
                // a declined request: its content is not an answer
                if (reply.stopReason == "refusal") throw AiFailure(AiErrors.refused(reply.refusal))
                content += reply.content
                if (reply.stopReason == "pause_turn" && continuations < ClaudeApi.MAX_CONTINUATIONS) {
                    continuations++
                    continue
                }
                val notes = when (reply.stopReason) {
                    "max_tokens" -> listOf("답이 길어 끝이 잘렸어요.")
                    "pause_turn" -> listOf("검색이 길어져 중간에 멈췄어요.")
                    else -> emptyList()
                }
                val answer = ClaudeApi.answer(content, model, notes)
                if (answer.citedRuns == 0) {
                    throw AiFailure(AiErrors.noSources(ClaudeApi.searchErrors(content).takeIf { it.isNotEmpty() }?.joinToString(prefix = "web_search: ")))
                }
                return answer
            }
        }
    }

    /** Sends [request]; a non-2xx status becomes [error]'s [AiFailure], no response a network one. Returns the body. */
    private suspend fun exchange(service: AiService, request: AiHttpRequest, error: (AiHttpResponse) -> AiError): String {
        val response = try {
            http.send(request)
        } catch (e: AiConnectionException) {
            throw AiFailure(AiErrors.network(service, e.message))
        }
        if (response.status !in 200..299) throw AiFailure(error(response))
        return response.body
    }

    companion object {
        private val GEMINI_OPTIONS = setOf(AiProvider.GEMINI_TAVILY, AiProvider.GEMINI_SEARCH)

        /** The name a key is stored under in [SecretStore]. */
        fun secretName(slot: AiKeySlot): String = slot.name.lowercase()
    }
}
