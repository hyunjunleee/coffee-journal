package com.coffeejournal.ui.nav

import kotlinx.serialization.Serializable

/** Type-safe destinations. Tab roots come first; every other route is a full-screen page. */
sealed interface Route {
    @Serializable data object Extract : Route
    @Serializable data object Calendar : Route
    @Serializable data object Bean : Route
    @Serializable data object Misc : Route

    /** mode: "extract" (원두), "cafe", "cupping". entryId != null edits an existing record. */
    @Serializable data class RecordForm(val mode: String = "extract", val entryId: String? = null, val cuppingType: String? = null) : Route
    @Serializable data class EntryDetail(val entryId: String) : Route
    @Serializable data object Pantry : Route
    @Serializable data class PantryEditor(val itemId: String? = null) : Route
    @Serializable data class BlendForm(val blendId: String? = null) : Route
    @Serializable data class BookForm(val bookId: String? = null) : Route
    @Serializable data class VideoForm(val videoId: String? = null) : Route
    @Serializable data class ClassForm(val classId: String? = null) : Route
    @Serializable data class MiscForm(val type: String, val itemId: String? = null) : Route
    @Serializable data class FlatItemForm(val type: String, val itemId: String? = null) : Route
    @Serializable data object MyRecipes : Route
    @Serializable data object Backup : Route
    @Serializable data class CountryDetail(val en: String) : Route
    @Serializable data class NoteDetail(val kind: String, val noteKey: String) : Route
    @Serializable data class VarietyDetail(val varietyKey: String) : Route
    @Serializable data class ProcessDetail(val name: String, val seg: String? = null) : Route
    @Serializable data class RoasteryDetail(val name: String) : Route
    @Serializable data object About : Route
}

object FormMode {
    const val EXTRACT = "extract"
    const val CAFE = "cafe"
    const val CUPPING = "cupping"
}
