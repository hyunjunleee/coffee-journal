package com.coffeejournal.ui.ai

enum class AiService(val label: String) { GEMINI("Gemini"), TAVILY("Tavily"), OPENAI("OpenAI"), CLAUDE("Anthropic") }

enum class AiErrorKind {
    MISSING_KEY, KEY_INVALID, BILLING_NEEDED, CREDITS_EMPTY, QUOTA, MODEL_UNAVAILABLE, BUSY, BLOCKED, REFUSED,
    NO_SOURCES, NETWORK, REGION, SEARCH_DISABLED, UNSUPPORTED, BAD_REQUEST, OTHER,
}

/**
 * Why a question or a key check did not work, in words for the user ([message], [hint]); [detail] is the service's
 * own message (English), shown small underneath.
 */
data class AiError(
    val kind: AiErrorKind,
    val service: AiService?,
    val message: String,
    val hint: String? = null,
    val detail: String? = null,
) {
    /** The link under the message that leads to the right place in 설정, when the fix is there. */
    val settingsLink: String?
        get() = when (kind) {
            AiErrorKind.MISSING_KEY, AiErrorKind.KEY_INVALID -> AiErrors.LINK_KEYS
            AiErrorKind.MODEL_UNAVAILABLE -> AiErrors.LINK_MODEL
            AiErrorKind.BILLING_NEEDED -> AiErrors.LINK_PROVIDER
            else -> null
        }

    /** The one-word result of 키 확인. */
    val checkLabel: String
        get() = when (kind) {
            AiErrorKind.KEY_INVALID, AiErrorKind.MISSING_KEY -> "키가 틀려요"
            AiErrorKind.BILLING_NEEDED, AiErrorKind.CREDITS_EMPTY -> "결제가 필요해요"
            AiErrorKind.QUOTA -> "한도를 넘었어요"
            AiErrorKind.MODEL_UNAVAILABLE -> "이 모델은 쓸 수 없어요"
            AiErrorKind.NETWORK -> "연결 실패"
            else -> "확인하지 못했어요"
        }
}

/** Carries an [AiError] out of the service calls. */
class AiFailure(val error: AiError) : Exception(error.message)

/** Status codes and error bodies of each service → [AiError] (2026-09-26 docs and measurements, see the plan). */
object AiErrors {
    const val LINK_KEYS = "설정에서 키 넣기 →"
    const val LINK_MODEL = "설정에서 모델 바꾸기 →"
    const val LINK_PROVIDER = "설정에서 방식 바꾸기 →"

    const val NO_SOURCES = "출처를 찾지 못했어요. 다른 말로 물어봐 주세요."
    const val SEARCH_BILLING = "Google 검색은 결제를 켠 프로젝트에서만 돼요. 결제를 켜거나 'Gemini 무료 + Tavily'를 고르세요."

    fun noSources(detail: String? = null) = AiError(AiErrorKind.NO_SOURCES, null, NO_SOURCES, detail = detail)

    fun missingKeys(slots: List<AiKeySlot>) = AiError(
        AiErrorKind.MISSING_KEY, null,
        "${slots.joinToString(" · ") { it.label }}가 아직 없어요. 설정에서 키를 넣어 주세요.",
        hint = "키는 이 휴대폰에만 암호화해 저장돼요. 설정의 \"키 받는 방법\"에 받는 순서가 있어요.",
    )

    fun network(service: AiService, detail: String?) = AiError(
        AiErrorKind.NETWORK, service, "연결 실패: ${service.label}에 연결하지 못했어요. 인터넷 연결을 확인하고 다시 해 보세요.", detail = detail,
    )

    fun unreadable(service: AiService) = AiError(AiErrorKind.OTHER, service, "${service.label}의 답을 읽지 못했어요. 다시 해 보세요.")

    val unsupported = AiError(AiErrorKind.UNSUPPORTED, null, "이 기기에서는 아직 AI 노트 도우미를 쓸 수 없어요.")

    fun blocked(detail: String?) = AiError(AiErrorKind.BLOCKED, AiService.GEMINI, "안전 필터가 답을 막았어요. 다른 말로 물어봐 주세요.", detail = detail)

    fun refused(detail: String?) = AiError(AiErrorKind.REFUSED, AiService.CLAUDE, "요청이 거절됐어요. 다른 말로 물어봐 주세요.", detail = detail)

    private fun busy(service: AiService, status: Int, detail: String) =
        AiError(AiErrorKind.BUSY, service, "${service.label} 서버가 붐벼요. 잠시 뒤 다시 해 보세요.", detail = "HTTP $status · $detail")

    private fun other(service: AiService, status: Int, detail: String) =
        AiError(if (status == 400) AiErrorKind.BAD_REQUEST else AiErrorKind.OTHER, service, "${service.label} 요청이 실패했어요 (HTTP $status).", detail = detail)

    /**
     * Gemini generateContent. [search]: the request used Google Search grounding, where 429 on a free project means
     * the free tier has no Search grounding at all (measured 2026-09-26: every 3.x model).
     */
    fun gemini(status: Int, body: String, model: String, search: Boolean): AiError {
        val detail = errorMessage(body)
        val s = AiService.GEMINI
        return when {
            status == 400 && ("API_KEY_INVALID" in body || "API key not valid" in body) ->
                AiError(AiErrorKind.KEY_INVALID, s, "Gemini 키가 맞지 않아요. 설정에서 키를 다시 넣어 주세요.", detail = detail)
            status == 400 && "location is not supported" in body ->
                AiError(AiErrorKind.REGION, s, "지금 있는 지역에서는 이 Gemini 등급을 쓸 수 없어요.", detail = detail)
            status == 401 || status == 403 ->
                AiError(AiErrorKind.KEY_INVALID, s, "이 Gemini 키로는 쓸 수 없어요(권한 없음). 설정에서 키를 확인해 주세요.", detail = detail)
            status == 404 -> AiError(
                AiErrorKind.MODEL_UNAVAILABLE, s, "‘$model’ 모델은 쓸 수 없어요.",
                hint = if ("no longer available to new users" in body) {
                    "새로 만든 키에는 더 이상 열어 주지 않는 모델이에요. 설정에서 gemini-3.5-flash-lite 같은 다른 모델을 골라 주세요."
                } else {
                    "모델 이름을 확인하거나 설정에서 다른 모델을 골라 주세요."
                },
                detail = detail,
            )
            status == 429 && search -> AiError(
                AiErrorKind.BILLING_NEEDED, s, SEARCH_BILLING,
                hint = "결제를 이미 켰다면 한도를 넘은 거예요. 잠시 뒤 다시 해 보세요.", detail = detail,
            )
            status == 429 -> AiError(
                AiErrorKind.QUOTA, s, "Gemini 사용 한도를 넘었어요. 잠시 뒤 다시 해 보세요.",
                hint = "하루 한도는 태평양 시간 자정(한국 시간 오후 4~5시)에 다시 채워져요. 설정에서 다른 모델을 골라도 돼요.", detail = detail,
            )
            status == 402 -> AiError(
                AiErrorKind.CREDITS_EMPTY, s, "Gemini 선불 크레딧이 떨어졌어요. AI Studio의 Billing에서 크레딧을 충전해 주세요.", detail = detail,
            )
            status >= 500 -> busy(s, status, detail)
            else -> other(s, status, detail)
        }
    }

    /** Tavily /search (docs.tavily.com: 400, 401, 429, 432 plan limit, 433 pay-as-you-go limit, 500). */
    fun tavily(status: Int, body: String): AiError {
        val detail = errorMessage(body)
        val s = AiService.TAVILY
        return when (status) {
            401, 403 -> AiError(AiErrorKind.KEY_INVALID, s, "Tavily 키가 맞지 않아요. 설정에서 키를 다시 넣어 주세요.", detail = detail)
            429 -> AiError(AiErrorKind.QUOTA, s, "Tavily 요청이 너무 잦아요. 잠시 뒤 다시 해 보세요.", detail = detail)
            432 -> AiError(
                AiErrorKind.QUOTA, s, "Tavily 이번 달 크레딧을 다 썼어요.",
                hint = "무료 요금제는 매달 1,000크레딧이 다시 채워져요. 질문 한 번에 설정에 따라 1–4크레딧을 써요.", detail = detail,
            )
            433 -> AiError(AiErrorKind.QUOTA, s, "Tavily 종량제 사용 한도를 넘었어요. Tavily 대시보드에서 한도를 확인해 주세요.", detail = detail)
            in 500..599 -> busy(s, status, detail)
            else -> other(s, status, detail)
        }
    }

    /** OpenAI ({"error":{"message","type","code"}}). */
    fun openAi(status: Int, body: String): AiError {
        val detail = errorMessage(body)
        val code = AiJson.parse(body)["error"]["code"].str ?: AiJson.parse(body)["error"]["type"].str ?: ""
        val s = AiService.OPENAI
        return when {
            status == 401 -> AiError(AiErrorKind.KEY_INVALID, s, "OpenAI 키가 맞지 않아요. 설정에서 키를 다시 넣어 주세요.", detail = detail)
            status == 402 || code == "credit_balance_exhausted" ->
                AiError(AiErrorKind.CREDITS_EMPTY, s, "OpenAI 크레딧이 다 떨어졌어요. 결제 페이지에서 충전해 주세요.", detail = detail)
            status == 429 && code == "insufficient_quota" -> AiError(
                AiErrorKind.BILLING_NEEDED, s, "OpenAI 결제가 필요해요. 크레딧을 충전하거나 사용 한도를 확인해 주세요.", detail = detail,
            )
            status == 429 -> AiError(AiErrorKind.QUOTA, s, "OpenAI 요청 한도를 넘었어요. 잠시 뒤 다시 해 보세요.", detail = detail)
            status == 404 || code == "model_not_found" -> AiError(
                AiErrorKind.MODEL_UNAVAILABLE, s, "이 모델은 이 키로 쓸 수 없어요.", hint = "설정에서 gpt-5-nano 같은 다른 모델을 골라 주세요.", detail = detail,
            )
            status == 403 && code == "unsupported_country_region_territory" ->
                AiError(AiErrorKind.REGION, s, "지금 있는 지역에서는 OpenAI를 쓸 수 없어요.", detail = detail)
            status == 403 -> AiError(AiErrorKind.KEY_INVALID, s, "이 OpenAI 키로는 쓸 수 없어요(권한 없음). 키의 프로젝트 권한을 확인해 주세요.", detail = detail)
            status >= 500 -> busy(s, status, detail)
            else -> other(s, status, detail)
        }
    }

    /** Anthropic ({"type":"error","error":{"type","message"}}; 529 overloaded). */
    fun claude(status: Int, body: String): AiError {
        val detail = errorMessage(body)
        val lower = detail.lowercase()
        val s = AiService.CLAUDE
        return when {
            status == 400 && "credit balance" in lower -> AiError(
                AiErrorKind.CREDITS_EMPTY, s, "Anthropic 크레딧이 부족해요. 콘솔의 Billing에서 크레딧을 사 주세요.", detail = detail,
            )
            status == 400 && ("web search" in lower || "web_search" in lower) -> AiError(
                AiErrorKind.SEARCH_DISABLED, s, "이 조직에서 웹 검색을 쓸 수 없어요.",
                hint = "조직 관리자가 https://platform.claude.com/settings/privacy 에서 웹 검색을 켤 수 있어요.", detail = detail,
            )
            status == 401 -> AiError(AiErrorKind.KEY_INVALID, s, "Anthropic 키가 맞지 않아요. 설정에서 키를 다시 넣어 주세요.", detail = detail)
            status == 402 -> AiError(AiErrorKind.CREDITS_EMPTY, s, "Anthropic 결제에 문제가 있어요. 콘솔의 Billing을 확인해 주세요.", detail = detail)
            status == 403 -> AiError(AiErrorKind.KEY_INVALID, s, "이 Anthropic 키로는 쓸 수 없어요(권한 없음).", detail = detail)
            status == 404 -> AiError(
                AiErrorKind.MODEL_UNAVAILABLE, s, "이 모델은 이 키로 쓸 수 없어요.", hint = "설정에서 claude-sonnet-5 같은 다른 모델을 골라 주세요.", detail = detail,
            )
            status == 429 -> AiError(AiErrorKind.QUOTA, s, "Anthropic 요청 한도를 넘었어요. 잠시 뒤 다시 해 보세요.", detail = detail)
            status >= 500 -> busy(s, status, detail)
            else -> other(s, status, detail)
        }
    }
}
