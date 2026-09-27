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

    /**
     * [queries], [depth] and [people]: GEMINI_TAVILY only, the main search the user corrected on the answer screen
     * ("검색어 고치기", replacing the query step), how Tavily searches it (설정 › 검색) and the extra Korean-blog search
     * (설정 › 사람들 의견). The other services choose their own searches.
     */
    suspend fun ask(
        question: NoteQuestion,
        provider: AiProvider,
        model: String,
        queries: List<String>? = null,
        depth: SearchDepth = SearchDepth.DEFAULT,
        people: Boolean = false,
    ): GroundedAnswer {
        if (!http.supported || !secrets.supported) throw AiFailure(AiErrors.unsupported)
        val keys = keys(provider.keys)
        val missing = provider.keys - keys.keys
        if (missing.isNotEmpty()) throw AiFailure(AiErrors.missingKeys(missing))
        val answer = answerer(provider, depth, people).answer(question, model, keys, queries?.takeIf { it.isNotEmpty() })
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

    private fun answerer(p: AiProvider, depth: SearchDepth, people: Boolean): NoteAnswerer = when (p) {
        AiProvider.GEMINI_TAVILY -> GeminiTavilyAnswerer(depth, people)
        AiProvider.GEMINI_SEARCH -> GeminiSearchAnswerer()
        AiProvider.OPENAI -> OpenAiAnswerer()
        AiProvider.CLAUDE -> ClaudeAnswerer()
    }

    /** One way of answering from web sources. */
    private fun interface NoteAnswerer {
        suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>, queries: List<String>?): GroundedAnswer
    }

    /**
     * Gemini writes one English query; Tavily searches it at [depth] and, with [people], searches Korean blogs once more
     * in Korean; Gemini (no tools) answers from the pages with [n] marks, and the app checks the quotes. A corrected
     * query ([queries]) skips the first step. A failed blog search leaves the answer without people's impressions; the
     * question fails only when every main search does (with the first one's error).
     */
    private inner class GeminiTavilyAnswerer(private val depth: SearchDepth, private val people: Boolean) : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>, queries: List<String>?): GroundedAnswer {
            val geminiKey = keys.getValue(AiKeySlot.GEMINI)
            val main = (queries ?: writeQueries(q, model, geminiKey)).first()
            val plan = TavilyPlan.searches(main, q, depth, people)
            val found = mutableListOf<Pair<TavilySearch, List<TavilyResult>>>()
            var firstError: AiError? = null
            var blogsFailed = false
            for (search in plan) {
                try {
                    val body = exchange(AiService.TAVILY, TavilyApi.request(keys.getValue(AiKeySlot.TAVILY), search)) { AiErrors.tavily(it.status, it.body) }
                    found += search to (TavilyApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.TAVILY)))
                } catch (e: AiFailure) {
                    if (search.people) blogsFailed = true else if (firstError == null) firstError = e.error
                }
            }
            if (found.none { !it.first.people }) throw AiFailure(firstError ?: AiErrors.noSources())
            val results = TavilyPlan.order(found)
            if (results.isEmpty()) throw AiFailure(AiErrors.noSources("Tavily: 0 results"))
            // without the blog pages there is nothing for the people line to come from, and the note says it is left out
            val user = NoteHelperPrompts.withSources(NoteHelperPrompts.question(q, people && !blogsFailed), results)
            val body = exchange(AiService.GEMINI, GeminiApi.request(geminiKey, model, NoteHelperPrompts.SOURCES_SYSTEM, user, search = false)) {
                AiErrors.gemini(it.status, it.body, model, search = false)
            }
            val reply = GeminiApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.GEMINI))
            GeminiApi.blocked(reply)?.let { throw AiFailure(AiErrors.blocked(it)) }
            val notes = GeminiApi.truncationNotes(reply.finishReason) + if (blogsFailed) listOf(AiTexts.BLOGS_FAILED) else emptyList()
            return SourcedAnswer.answer(reply.text, results, model, notes, plan.map { it.query }.distinct())
        }
    }

    /**
     * The query step: Gemini turns the note or the described taste into one short English query. Anything but
     * a refused key (an error status, no connection, a reply that is not the asked JSON) falls back quietly to the fixed
     * template; the answer step reports a real problem with the model or the quota anyway.
     */
    private suspend fun writeQueries(q: NoteQuestion, model: String, key: String): List<String> {
        val fallback = listOf(NoteHelperPrompts.fallbackQuery(q))
        val response = try {
            http.send(GeminiApi.queryRequest(key, model, NoteHelperPrompts.QUERY_SYSTEM, NoteHelperPrompts.queryInput(q)))
        } catch (e: AiConnectionException) {
            return fallback
        }
        if (response.status !in 200..299) {
            val error = AiErrors.gemini(response.status, response.body, model, search = false)
            if (error.kind == AiErrorKind.KEY_INVALID) throw AiFailure(error)
            return fallback
        }
        return SearchQueries.parse(GeminiApi.parse(response.body)?.text) ?: fallback
    }

    private inner class GeminiSearchAnswerer : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>, queries: List<String>?): GroundedAnswer {
            val request = GeminiApi.request(keys.getValue(AiKeySlot.GEMINI), model, NoteHelperPrompts.SEARCH_SYSTEM, NoteHelperPrompts.question(q), search = true)
            val body = exchange(AiService.GEMINI, request) { AiErrors.gemini(it.status, it.body, model, search = true) }
            val reply = GeminiApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.GEMINI))
            GeminiApi.blocked(reply)?.let { throw AiFailure(AiErrors.blocked(it)) }
            return GeminiApi.groundedAnswer(reply, model)
        }
    }

    private inner class OpenAiAnswerer : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>, queries: List<String>?): GroundedAnswer {
            val body = exchange(AiService.OPENAI, OpenAiApi.request(keys.getValue(AiKeySlot.OPENAI), model, NoteHelperPrompts.question(q))) {
                AiErrors.openAi(it.status, it.body)
            }
            val (text, annotations) = OpenAiApi.parse(body) ?: throw AiFailure(AiErrors.unreadable(AiService.OPENAI))
            val incomplete = AiJson.parse(body)["status"].str == "incomplete"
            val notes = if (incomplete) listOf("답이 끝나기 전에 멈췄어요.") else emptyList()
            return OpenAiApi.answer(text, annotations, model, notes, OpenAiApi.searchQueries(body))
        }
    }

    /** Claude with web search; a paused turn (pause_turn) is resumed by sending the content back, up to 3 times. */
    private inner class ClaudeAnswerer : NoteAnswerer {
        override suspend fun answer(q: NoteQuestion, model: String, keys: Map<AiKeySlot, String>, queries: List<String>?): GroundedAnswer {
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
