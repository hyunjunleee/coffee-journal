// Generated from the web app reference data by scratchpad/site/gen_refs.py. Do not edit by hand.
package com.coffeejournal.domain.reference

object ScaForm {
    data class Attr(val key: String, val label: String, val min: Double, val max: Double, val step: Double, val after: String? = null)

    /** SCA 2004 cupping form: 10 scored attributes (web ATTRS). */
    val attrs: List<Attr> = listOf(
        Attr("fragranceAroma", "Fragrance / Aroma", 6.0, 10.0, 0.25),
        Attr("flavor", "Flavor", 6.0, 10.0, 0.25),
        Attr("aftertaste", "Aftertaste", 6.0, 10.0, 0.25),
        Attr("acidity", "Acidity", 6.0, 10.0, 0.25),
        Attr("body", "Body", 6.0, 10.0, 0.25),
        Attr("balance", "Balance", 6.0, 10.0, 0.25),
        Attr("uniformity", "Uniformity", 0.0, 10.0, 2.0),
        Attr("cleancup", "Clean Cup", 0.0, 10.0, 2.0),
        Attr("sweetness", "Sweetness", 0.0, 10.0, 2.0),
        Attr("overall", "Overall", 6.0, 10.0, 0.25),
    )

    /** Intensity rows shown under their parent attribute (web SCA_INTENSITIES). */
    val intensities: List<Attr> = listOf(
        Attr("fragranceIntensity", "Aroma Intensity", 1.0, 5.0, 0.5, "fragranceAroma"),
        Attr("acidityIntensity", "Acidity Intensity", 1.0, 5.0, 0.5, "acidity"),
        Attr("bodyIntensity", "Body Intensity", 1.0, 5.0, 0.5, "body"),
    )

    val attrKeys: Set<String> get() = attrs.map { it.key }.toSet()
    val intensityKeys: Set<String> get() = intensities.map { it.key }.toSet()
    /** Attributes that default to 10 on a fresh form (no defects). */
    val defaultTenKeys: Set<String> = setOf("uniformity", "cleancup", "sweetness")

    /** Per-bean cupping evaluation fields (web CUPPING_EVALUATION_FIELDS). */
    val cuppingEvaluationFields: List<Pair<String, String>> = listOf(
        "aroma" to "Fragrance / Aroma",
        "flavor" to "Flavor",
        "aftertaste" to "Aftertaste",
        "acidity" to "Acidity",
        "sweetness" to "Sweetness",
        "body" to "Body",
        "balance" to "Balance",
        "overall" to "Overall",
    )
}
