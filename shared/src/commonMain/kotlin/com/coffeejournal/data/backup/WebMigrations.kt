package com.coffeejournal.data.backup

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.ScaForm
import kotlin.math.floor

/**
 * The web's one-off load-time migrations (loadEntries, script3.js 335-512) for records that reach the app through a
 * web backup. The web runs them once per browser behind storage flags, so a backup exported by an older web version,
 * or re-exported after such a backup was restored into an already migrated browser, still carries pre-migration
 * values. Each rule here only touches values that cannot be current data, so applying it to every decoded record,
 * any number of times, is safe:
 *
 * - 유어홈 (script3.js 372-409): the recipe once had a 140g second pour, 18g dose and 300g water. Only records that
 *   still carry that 0:50 · 140g pour are corrected, so a record the user brewed at 18g/300g on purpose stays as it is.
 * - SCA quality scores on the old 1-5 scale (attr-scale-migrated-v1, 423-440): values in (0, 5] of the 6-10
 *   attributes get +5. Uniformity / Clean Cup / Sweetness run 0-10 in steps of 2, where 2 and 4 are real scores, so
 *   they are left alone.
 * - The legacy `aroma` score (attr-aroma-split-v1, 441-455) moves to `aromaIntensity` (after the same +5 rescale the
 *   web applied first). Neither key is on the current form or in the total; this only keeps the stored data identical
 *   to what the web would hold.
 * - Intensities on the 0-15 scale (sca-intensity-scale-0-15-v1 / -1-5-half-v2, 456-496): values above 5 map to
 *   1-5 in 0.5 steps (`round(v / 3 * 2) / 2`, clamped). Values in 1-5 are already on the current scale.
 *
 * The farm / producer merge (413-419) happens while decoding (`farmProducer`), and the "(로스터리, 셀렉트)" name split
 * is display-only on the web too, so neither needs a migration.
 */
object WebMigrations {
    private val yourHomeName = Regex("유어홈|Your Home", RegexOption.IGNORE_CASE)
    private val secondPourNote = Regex("2차 푸어")
    private const val LEGACY_POUR_TIME = "0:50"
    private const val LEGACY_POUR_WATER = "140"
    private const val FIXED_POUR_WATER = "190"
    private const val LEGACY_AROMA = "aroma"
    private const val AROMA_INTENSITY = "aromaIntensity"

    /** Quality attributes scored 6-10 (the 1-5 → 6-10 rescale applies to these only). */
    private val sixToTenKeys: Set<String> = ScaForm.attrs.filter { it.min >= 6.0 }.map { it.key }.toSet()

    fun apply(entry: Entry): Entry = migrateAttributes(migrateYourHome(entry))

    // ───────────── 유어홈 recipe correction ─────────────

    fun migrateYourHome(en: Entry): Entry {
        val steps = en.steps
        val hasLegacySteps = steps.size == 4 &&
            steps[0].time == "0:00" && steps[0].water == "50" &&
            steps[1].time == "0:10" && steps[1].water.isEmpty() &&
            steps[2].time == LEGACY_POUR_TIME && steps[2].water == LEGACY_POUR_WATER &&
            steps[3].time == "1:20" && steps[3].water.isEmpty()
        val named = yourHomeName.containsMatchIn(en.recipeRef?.name.orEmpty())
        val isYourHome = named || (en.dose == "18" && en.water == "300" && hasLegacySteps)
        if (!isYourHome) return en
        // app refinement: only records made while the recipe was wrong still hold its 140g pour
        val carriesLegacyPour = (steps + en.recipeRef?.steps.orEmpty()).any(::isLegacyPour)
        if (!carriesLegacyPour) return en
        return en.copy(
            dose = if (en.dose == "18") "15" else en.dose,
            water = if (en.water == "300") "240" else en.water,
            steps = steps.map(::fixPour),
            recipeRef = en.recipeRef?.let { ref -> RecipeRef(ref.name, ref.steps.map(::fixPour)) },
        )
    }

    private fun isLegacyPour(s: RecipeStep) = s.time == LEGACY_POUR_TIME && s.water == LEGACY_POUR_WATER

    private fun fixPour(s: RecipeStep): RecipeStep =
        if (!isLegacyPour(s)) s
        else s.copy(water = FIXED_POUR_WATER, note = if (secondPourNote.containsMatchIn(s.note)) "2차 푸어" else s.note)

    // ───────────── SCA scales ─────────────

    fun migrateAttributes(en: Entry): Entry {
        val migrated = migrateAttributes(en.attributes)
        return if (migrated == en.attributes) en else en.copy(attributes = migrated)
    }

    fun migrateAttributes(attributes: Map<String, Double>): Map<String, Double> {
        if (attributes.isEmpty()) return attributes
        val out = LinkedHashMap<String, Double>(attributes.size)
        for ((key, value) in attributes) {
            out[key] = when {
                key in sixToTenKeys || key == LEGACY_AROMA -> if (value > 0 && value <= 5) value + 5 else value
                key in ScaForm.intensityKeys -> if (value > 5) intensityFromFifteen(value) else value
                else -> value
            }
        }
        val aroma = out[LEGACY_AROMA]
        if (aroma != null && aroma > 0 && !((out[AROMA_INTENSITY] ?: 0.0) > 0)) {
            out.remove(LEGACY_AROMA)
            out[AROMA_INTENSITY] = aroma
        }
        return out
    }

    /** Web: `Math.max(1, Math.min(5, Math.round((value / 3) * 2) / 2))`; JS rounds halves up. */
    private fun intensityFromFifteen(value: Double): Double = (floor(value / 3 * 2 + 0.5) / 2).coerceIn(1.0, 5.0)
}
