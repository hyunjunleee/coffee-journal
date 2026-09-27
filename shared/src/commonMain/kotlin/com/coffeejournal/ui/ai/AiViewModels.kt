package com.coffeejournal.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A saved key as the settings show it: only its last four characters, and the last 키 확인. */
data class KeyUi(val masked: String? = null, val checking: Boolean = false, val check: KeyCheck? = null)

data class AiSettingsUi(
    val loaded: Boolean = false,
    val settings: AiSettings = AiSettings(),
    val keys: Map<AiKeySlot, KeyUi> = emptyMap(),
    val storeSupported: Boolean = true,
)

/**
 * "설정에서 키 넣기" from the answer screen: 설정 scrolls to the AI section once (a Koin single, like BeanViewRequests;
 * Route.Settings takes no argument).
 */
class AiSettingsFocus {
    private val pending = MutableStateFlow(false)
    fun request() { pending.value = true }
    fun consume(): Boolean = pending.value.also { pending.value = false }
}

/** 설정 › AI 노트 도우미: the option, a model per option, and the keys (saved, masked, checked, cleared). */
class AiSettingsViewModel(
    private val prefs: AiPrefs,
    private val secrets: SecretStore,
    private val service: NoteHelperService,
) : ViewModel() {
    private val keyState = MutableStateFlow<Map<AiKeySlot, KeyUi>?>(null)
    private val writes = Mutex()

    val state: StateFlow<AiSettingsUi> = combine(prefs.observe(), keyState) { s, keys ->
        AiSettingsUi(loaded = keys != null, settings = s, keys = keys ?: emptyMap(), storeSupported = secrets.supported)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettingsUi())

    init {
        viewModelScope.launch {
            val saved = runCatching { service.keys(AiKeySlot.entries) }.getOrDefault(emptyMap())
            keyState.value = AiKeySlot.entries.associateWith { KeyUi(saved[it]?.let(::mask)) }
        }
    }

    fun setProvider(p: AiProvider) = write { prefs.setProvider(p) }

    fun setModel(p: AiProvider, model: String) = write { prefs.setModel(p, model) }

    fun setSearchDepth(depth: SearchDepth) = write { prefs.setSearchDepth(depth) }

    /** A pasted key, trimmed (a copied key often carries a line break). */
    fun saveKey(slot: AiKeySlot, typed: String) {
        val key = typed.trim().replace(Regex("\\s+"), "")
        if (key.isEmpty() || !secrets.supported) return
        write {
            secrets.put(NoteHelperService.secretName(slot), key)
            setKey(slot) { KeyUi(mask(key)) }
        }
    }

    fun clearKey(slot: AiKeySlot) = write {
        secrets.delete(NoteHelperService.secretName(slot))
        setKey(slot) { KeyUi() }
    }

    fun checkKey(slot: AiKeySlot) {
        if (keyState.value?.get(slot)?.checking == true) return
        setKey(slot) { it.copy(checking = true, check = null) }
        viewModelScope.launch {
            val result = service.checkKey(slot, prefs.load())
            setKey(slot) { it.copy(checking = false, check = result) }
        }
    }

    private fun setKey(slot: AiKeySlot, change: (KeyUi) -> KeyUi) {
        keyState.update { m -> (m ?: emptyMap()) + (slot to change(m?.get(slot) ?: KeyUi())) }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }

    companion object {
        fun mask(key: String): String = "…" + key.takeLast(4)
    }
}

data class NoteHelperArgs(val mode: NoteMode, val query: String, val returnToForm: Boolean)

sealed interface NoteHelperUi {
    data object Preparing : NoteHelperUi
    data class NeedsKey(val provider: AiProvider, val missing: List<AiKeySlot>) : NoteHelperUi
    data class NeedsConsent(val provider: AiProvider) : NoteHelperUi
    data class Declined(val provider: AiProvider) : NoteHelperUi
    data class Asking(val provider: AiProvider, val model: String) : NoteHelperUi
    data class Answered(val answer: GroundedAnswer, val candidates: List<String>) : NoteHelperUi
    data class Failed(val provider: AiProvider, val model: String, val error: AiError) : NoteHelperUi
}

/**
 * Route.NoteHelper: reads the chosen option, stops at a missing key or the first-question notice, then asks. The
 * answer is kept only in memory for this screen (no cache, no history).
 */
class NoteHelperViewModel(
    val args: NoteHelperArgs,
    private val prefs: AiPrefs,
    private val service: NoteHelperService,
) : ViewModel() {
    private val _state = MutableStateFlow<NoteHelperUi>(NoteHelperUi.Preparing)
    val state: StateFlow<NoteHelperUi> = _state.asStateFlow()

    /** Mode B: the candidate notes picked to go back to the record form. */
    private val _picked = MutableStateFlow<List<String>>(emptyList())
    val picked: StateFlow<List<String>> = _picked.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    /** From the top: settings, keys, notice, question ("다시 묻기" too). */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = NoteHelperUi.Preparing
            val s = prefs.load()
            val p = s.provider
            val missing = service.missingKeys(p)
            _state.value = when {
                missing.isNotEmpty() -> NoteHelperUi.NeedsKey(p, missing)
                p !in s.consents -> NoteHelperUi.NeedsConsent(p)
                else -> { ask(s); return@launch }
            }
        }
    }

    /** Back from 설정 with a key: try again (nothing is sent until the notice is confirmed). */
    fun onResume() {
        if (_state.value is NoteHelperUi.NeedsKey) start()
    }

    fun consent(accepted: Boolean) {
        val p = (_state.value as? NoteHelperUi.NeedsConsent)?.provider ?: return
        if (!accepted) { _state.value = NoteHelperUi.Declined(p); return }
        job?.cancel()
        job = viewModelScope.launch {
            prefs.setConsent(p, true)
            ask(prefs.load())
        }
    }

    fun toggle(term: String) = _picked.update { if (term in it) it - term else it + term }

    /** "이 검색어로 다시 묻기" (Gemini 무료 + Tavily): the same question with the search the user corrected. */
    fun askWith(typed: String) {
        val queries = SearchQueries.fromTyped(typed)
        if (queries.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch { ask(prefs.load(), queries) }
    }

    private suspend fun ask(s: AiSettings, queries: List<String>? = null) {
        val p = s.provider
        val model = s.model(p)
        _state.value = NoteHelperUi.Asking(p, model)
        _state.value = try {
            val answer = service.ask(NoteQuestion(args.mode, args.query), p, model, queries, s.searchDepth)
            val candidates = if (args.mode == NoteMode.DESCRIBE) NoteTerms.find(answer.plainText) else emptyList()
            NoteHelperUi.Answered(answer, candidates)
        } catch (e: AiFailure) {
            NoteHelperUi.Failed(p, model, e.error)
        }
    }
}
