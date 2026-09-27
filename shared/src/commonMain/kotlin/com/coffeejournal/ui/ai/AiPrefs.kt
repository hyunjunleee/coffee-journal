package com.coffeejournal.ui.ai

import com.coffeejournal.data.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 설정 › AI 노트 도우미 without the keys: the chosen option, a model per option, and the options the user agreed to. */
data class AiSettings(
    val provider: AiProvider = AiProvider.DEFAULT,
    /** What the user typed per option; blank or missing means the option's default model. */
    val models: Map<AiProvider, String> = emptyMap(),
    /** Options whose "what is sent where" notice the user confirmed before the first question. */
    val consents: Set<AiProvider> = emptySet(),
    /** How deep Tavily searches (Gemini 무료 + Tavily). */
    val searchDepth: SearchDepth = SearchDepth.DEFAULT,
) {
    fun model(p: AiProvider = provider): String = models[p]?.trim()?.takeIf { it.isNotEmpty() } ?: p.defaultModel
    fun typedModel(p: AiProvider = provider): String = models[p] ?: ""
}

/**
 * The non-secret AI settings, kept in [SettingsRepository] under device keys ([SettingsRepository.DEVICE_PREFIX]) so a
 * JSON backup neither carries nor restores them. The keys themselves live in [SecretStore].
 */
class AiPrefs(private val settings: SettingsRepository) {
    fun observe(): Flow<AiSettings> {
        val flows: List<Flow<String?>> = listOf(settings.observe(KEY_PROVIDER), settings.observe(KEY_SEARCH_DEPTH)) +
            AiProvider.entries.map { settings.observe(modelKey(it)) } +
            AiProvider.entries.map { settings.observe(consentKey(it)) }
        return combine(flows) { values -> decode(values.toList()) }
    }

    suspend fun load(): AiSettings = decode(
        listOf(settings.get(KEY_PROVIDER), settings.get(KEY_SEARCH_DEPTH)) + AiProvider.entries.map { settings.get(modelKey(it)) } +
            AiProvider.entries.map { settings.get(consentKey(it)) },
    )

    suspend fun setProvider(p: AiProvider) = settings.put(KEY_PROVIDER, p.name)

    suspend fun setSearchDepth(depth: SearchDepth) = settings.put(KEY_SEARCH_DEPTH, depth.name)

    suspend fun setModel(p: AiProvider, model: String) = settings.put(modelKey(p), model)

    suspend fun setConsent(p: AiProvider, given: Boolean) {
        if (given) settings.put(consentKey(p), "true") else settings.delete(consentKey(p))
    }

    companion object {
        const val KEY_PROVIDER = SettingsRepository.DEVICE_PREFIX + "ai.provider"
        const val KEY_SEARCH_DEPTH = SettingsRepository.DEVICE_PREFIX + "ai.searchDepth"
        fun modelKey(p: AiProvider) = SettingsRepository.DEVICE_PREFIX + "ai.model." + p.name
        fun consentKey(p: AiProvider) = SettingsRepository.DEVICE_PREFIX + "ai.consent." + p.name

        /** [values]: the provider, the search depth, then one model and one consent per [AiProvider] entry (in entry order). */
        private fun decode(values: List<String?>): AiSettings {
            val n = AiProvider.entries.size
            val models = AiProvider.entries.mapIndexedNotNull { i, p -> values[2 + i]?.let { p to it } }.toMap()
            val consents = AiProvider.entries.filterIndexed { i, _ -> values[2 + n + i] == "true" }.toSet()
            return AiSettings(AiProvider.of(values[0]) ?: AiProvider.DEFAULT, models, consents, SearchDepth.of(values[1]))
        }
    }
}
