// Generated from the web app reference data by scratchpad/site/gen_refs.py, then checked by hand (2026-09-29) against the
// SCA/WCR wheel itself: https://atlanticspecialtycoffee.com/wp-content/uploads/SCA_TasterWheel_English_8.5x11.pdf
// (SCA poster "© 2016 SCA and WCR", V.2) and its V.3 ("© 2021 SCA and WCR", same descriptors) printed in SCA Standard
// 103-2024 Appendix 8.3, https://sca.coffee/s/AW_SCA-103_Descriptive-Assessment_Sept2024_Secured.pdf. Every outer-tier
// descriptor was already here; the middle-tier labels the web app had only as group headings ("Berry", "Brown Sugar",
// "Cereal" …) were added by hand as the first term of their group, so every label on the wheel can be picked
// (SCA-103 §4.2: "each descriptor in the inner or middle circle of the Flavor Wheel also represents a 'flavor category'").
// "Brown, Roast" is kept as "Brown Roast": notes are stored comma-separated, so a comma would split it in two.
// Keep these additions if the file is ever regenerated. Notes that are NOT on the wheel live in FlavorWheelExtras.
package com.coffeejournal.domain.reference

object FlavorWheel {
    data class Group(val name: String, val terms: List<String>)
    data class Category(val name: String, val colorHex: String, val groups: List<Group>)

    val categories: List<Category> = listOf(
        Category("Floral", "#a969a8", listOf(
            Group("Floral", listOf("Black Tea", "Floral", "Chamomile", "Rose", "Jasmine")),
        )),
        Category("Fruity", "#d85872", listOf(
            Group("Berry", listOf("Berry", "Blackberry", "Raspberry", "Blueberry", "Strawberry")),
            Group("Dried Fruit", listOf("Dried Fruit", "Raisin", "Prune")),
            Group("Other Fruit", listOf("Other Fruit", "Coconut", "Cherry", "Pomegranate", "Pineapple", "Grape", "Apple", "Peach", "Pear")),
            Group("Citrus Fruit", listOf("Citrus Fruit", "Grapefruit", "Orange", "Lemon", "Lime")),
        )),
        Category("Sour / Fermented", "#d69055", listOf(
            Group("Sour", listOf("Sour", "Sour Aromatics", "Acetic Acid", "Butyric Acid", "Isovaleric Acid", "Citric Acid", "Malic Acid")),
            Group("Alcohol / Fermented", listOf("Alcohol / Fermented", "Winey", "Whiskey", "Fermented", "Overripe")),
        )),
        Category("Green / Vegetative", "#769354", listOf(
            Group("Green", listOf("Olive Oil", "Raw", "Green / Vegetative", "Under-ripe", "Peapod", "Fresh", "Dark Green", "Vegetative", "Hay-like", "Herb-like", "Beany")),
        )),
        Category("Other", "#817b72", listOf(
            Group("Papery / Musty", listOf("Papery / Musty", "Stale", "Cardboard", "Papery", "Woody", "Moldy / Damp", "Musty / Dusty", "Musty / Earthy", "Animalic", "Meaty / Brothy", "Phenolic")),
            Group("Chemical", listOf("Chemical", "Bitter", "Salty", "Medicinal", "Petroleum", "Skunky", "Rubber")),
        )),
        Category("Roasted", "#795349", listOf(
            Group("Roasted", listOf("Pipe Tobacco", "Tobacco", "Burnt", "Acrid", "Ashy", "Smoky", "Brown Roast")),
            Group("Cereal", listOf("Cereal", "Grain", "Malt")),
        )),
        Category("Spices", "#b65d3d", listOf(
            Group("Pungent", listOf("Pungent")),
            Group("Pepper", listOf("Pepper")),
            Group("Brown Spice", listOf("Brown Spice", "Anise", "Nutmeg", "Cinnamon", "Clove")),
        )),
        Category("Nutty / Cocoa", "#8b603d", listOf(
            Group("Nutty", listOf("Nutty", "Peanuts", "Hazelnut", "Almond")),
            Group("Cocoa", listOf("Cocoa", "Chocolate", "Dark Chocolate")),
        )),
        Category("Sweet", "#d8a447", listOf(
            Group("Brown Sugar", listOf("Brown Sugar", "Molasses", "Maple Syrup", "Caramelized", "Honey")),
            Group("Vanilla", listOf("Vanilla", "Vanillin")),
            Group("Sweet", listOf("Overall Sweet", "Sweet Aromatics")),
        )),
    )

    /** The groups' terms, in wheel order (the AI helper's list, tools/ai-eval/eval.py wheel_terms). */
    val allTerms: List<String> get() = categories.flatMap { c -> c.groups.flatMap { it.terms } }

    /**
     * The inner tier's labels as descriptors ("Start at the center"): each category's name, unless one of its groups
     * already has it as a term (Floral, Green / Vegetative).
     */
    fun innerTerm(category: Category): String? =
        category.name.takeIf { name -> category.groups.none { g -> g.terms.any { it.equals(name, ignoreCase = true) } } }

    /** Every descriptor printed on the wheel: the inner-tier labels and the groups' terms. */
    val allDescriptors: List<String> get() = categories.flatMap { c -> listOfNotNull(innerTerm(c)) + c.groups.flatMap { it.terms } }
}
