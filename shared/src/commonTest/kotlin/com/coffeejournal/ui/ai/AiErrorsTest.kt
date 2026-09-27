package com.coffeejournal.ui.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Status codes and error bodies → what the user reads, per service (fixtures: AiFixtures, synthetic). */
class AiErrorsTest {
    @Test fun gemini() {
        val gone = AiErrors.gemini(404, AiFixtures.GEMINI_404_NEW_USERS, "gemini-2.5-flash", search = false)
        assertEquals(AiErrorKind.MODEL_UNAVAILABLE, gone.kind)
        assertEquals("‘gemini-2.5-flash’ 모델은 쓸 수 없어요.", gone.message)
        assertTrue(gone.hint!!.startsWith("새로 만든 키에는 더 이상 열어 주지 않는 모델이에요."))
        assertTrue(gone.detail!!.contains("no longer available to new users"))
        assertEquals(AiErrors.LINK_MODEL, gone.settingsLink)
        assertEquals("이 모델은 쓸 수 없어요", gone.checkLabel)

        // the free tier has no Google Search: the 429 says to turn on billing or pick Tavily
        val search = AiErrors.gemini(429, AiFixtures.GEMINI_429_BILLING, "gemini-3.5-flash", search = true)
        assertEquals(AiErrorKind.BILLING_NEEDED, search.kind)
        assertEquals("Google 검색은 결제를 켠 프로젝트에서만 돼요. 결제를 켜거나 'Gemini 무료 + Tavily'를 고르세요.", search.message)
        assertTrue(search.detail!!.contains("check your plan and billing"))
        assertEquals(AiErrors.LINK_PROVIDER, search.settingsLink)
        // the same 429 without search is a quota
        val quota = AiErrors.gemini(429, AiFixtures.GEMINI_429_BILLING, "gemini-3.5-flash-lite", search = false)
        assertEquals(AiErrorKind.QUOTA, quota.kind)
        assertEquals("한도를 넘었어요", quota.checkLabel)

        val key = AiErrors.gemini(400, AiFixtures.GEMINI_400_KEY, "m", search = false)
        assertEquals(AiErrorKind.KEY_INVALID, key.kind)
        assertEquals("키가 틀려요", key.checkLabel)
        assertEquals(AiErrors.LINK_KEYS, key.settingsLink)
        assertEquals(AiErrorKind.KEY_INVALID, AiErrors.gemini(403, AiFixtures.GEMINI_403, "m", search = false).kind)
        val credits = AiErrors.gemini(402, AiFixtures.GEMINI_402, "m", search = true)
        assertEquals(AiErrorKind.CREDITS_EMPTY, credits.kind)
        assertEquals("결제가 필요해요", credits.checkLabel)
        val busy = AiErrors.gemini(503, AiFixtures.GEMINI_503, "m", search = false)
        assertEquals(AiErrorKind.BUSY, busy.kind)
        assertNull(busy.settingsLink)
        assertEquals(AiErrorKind.BAD_REQUEST, AiErrors.gemini(400, """{"error": {"code": 400, "message": "Invalid JSON payload"}}""", "m", false).kind)
    }

    @Test fun tavily() {
        assertEquals(AiErrorKind.KEY_INVALID, AiErrors.tavily(401, AiFixtures.TAVILY_401).kind)
        assertEquals("Unauthorized: missing or invalid API key.", AiErrors.tavily(401, AiFixtures.TAVILY_401).detail)
        val plan = AiErrors.tavily(432, AiFixtures.TAVILY_432)
        assertEquals(AiErrorKind.QUOTA, plan.kind)
        assertEquals("Tavily 이번 달 크레딧을 다 썼어요.", plan.message)
        assertEquals(AiErrorKind.QUOTA, AiErrors.tavily(433, "{}").kind)
        assertEquals(AiErrorKind.QUOTA, AiErrors.tavily(429, "{}").kind)
        assertEquals(AiErrorKind.BUSY, AiErrors.tavily(500, "{}").kind)
        assertEquals(AiErrorKind.BAD_REQUEST, AiErrors.tavily(400, "{}").kind)
    }

    @Test fun openAi() {
        assertEquals(AiErrorKind.KEY_INVALID, AiErrors.openAi(401, AiFixtures.OPENAI_401).kind)
        val quota = AiErrors.openAi(429, AiFixtures.OPENAI_429_QUOTA)
        assertEquals(AiErrorKind.BILLING_NEEDED, quota.kind)
        assertEquals("결제가 필요해요", quota.checkLabel)
        assertEquals(AiErrorKind.QUOTA, AiErrors.openAi(429, AiFixtures.OPENAI_429_RATE).kind)
        assertEquals(AiErrorKind.CREDITS_EMPTY, AiErrors.openAi(402, "{}").kind)
        assertEquals(AiErrorKind.CREDITS_EMPTY, AiErrors.openAi(400, """{"error": {"message": "x", "type": "billing", "code": "credit_balance_exhausted"}}""").kind)
        assertEquals(AiErrorKind.MODEL_UNAVAILABLE, AiErrors.openAi(404, """{"error": {"message": "The model `gpt-9` does not exist", "code": "model_not_found"}}""").kind)
        assertEquals(AiErrorKind.REGION, AiErrors.openAi(403, """{"error": {"message": "Country not supported", "code": "unsupported_country_region_territory"}}""").kind)
        assertEquals(AiErrorKind.BUSY, AiErrors.openAi(503, "{}").kind)
    }

    @Test fun claude() {
        fun kind(status: Int, type: String, message: String) = AiErrors.claude(status, AiFixtures.claudeError(type, message)).kind
        assertEquals(AiErrorKind.CREDITS_EMPTY, kind(400, "invalid_request_error", "Your credit balance is too low to access the Anthropic API."))
        val search = AiErrors.claude(400, AiFixtures.claudeError("invalid_request_error", "Web search is disabled for this organization."))
        assertEquals(AiErrorKind.SEARCH_DISABLED, search.kind)
        assertTrue(search.hint!!.contains("https://platform.claude.com/settings/privacy"))
        assertEquals(AiErrorKind.BAD_REQUEST, kind(400, "invalid_request_error", "max_tokens: must be positive"))
        assertEquals(AiErrorKind.KEY_INVALID, kind(401, "authentication_error", "invalid x-api-key"))
        assertEquals(AiErrorKind.CREDITS_EMPTY, kind(402, "billing_error", "billing"))
        assertEquals(AiErrorKind.KEY_INVALID, kind(403, "permission_error", "no"))
        assertEquals(AiErrorKind.MODEL_UNAVAILABLE, kind(404, "not_found_error", "model: claude-x"))
        assertEquals(AiErrorKind.QUOTA, kind(429, "rate_limit_error", "slow down"))
        assertEquals(AiErrorKind.BUSY, kind(500, "api_error", "oops"))
        assertEquals(AiErrorKind.BUSY, kind(529, "overloaded_error", "Overloaded"))
        assertEquals("Overloaded", AiErrors.claude(529, AiFixtures.claudeError("overloaded_error", "Overloaded")).detail!!.substringAfter(" · "))
    }

    @Test fun common() {
        assertEquals("출처를 찾지 못했어요. 다른 말로 물어봐 주세요.", AiErrors.noSources().message)
        assertEquals("연결 실패", AiErrors.network(AiService.GEMINI, "timeout").checkLabel)
        val missing = AiErrors.missingKeys(listOf(AiKeySlot.GEMINI, AiKeySlot.TAVILY))
        assertEquals("Gemini API 키 · Tavily API 키가 아직 없어요. 설정에서 키를 넣어 주세요.", missing.message)
        assertEquals(AiErrors.LINK_KEYS, missing.settingsLink)
        assertEquals("plain text body", errorMessage("plain text body"))
    }
}
