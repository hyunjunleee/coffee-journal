// Generated from the web app reference data by scratchpad/site/gen_refs.py. Do not edit by hand.
package com.coffeejournal.domain.reference

object FlavorWheel {
    data class Group(val name: String, val terms: List<String>)
    data class Category(val name: String, val colorHex: String, val groups: List<Group>)

    val categories: List<Category> = listOf(
        Category("Floral", "#a969a8", listOf(
            Group("Floral", listOf("Black Tea", "Floral", "Chamomile", "Rose", "Jasmine")),
        )),
        Category("Fruity", "#d85872", listOf(
            Group("Berry", listOf("Blackberry", "Raspberry", "Blueberry", "Strawberry")),
            Group("Dried Fruit", listOf("Raisin", "Prune")),
            Group("Other Fruit", listOf("Coconut", "Cherry", "Pomegranate", "Pineapple", "Grape", "Apple", "Peach", "Pear")),
            Group("Citrus Fruit", listOf("Grapefruit", "Orange", "Lemon", "Lime")),
        )),
        Category("Sour / Fermented", "#d69055", listOf(
            Group("Sour", listOf("Sour Aromatics", "Acetic Acid", "Butyric Acid", "Isovaleric Acid", "Citric Acid", "Malic Acid")),
            Group("Alcohol / Fermented", listOf("Winey", "Whiskey", "Fermented", "Overripe")),
        )),
        Category("Green / Vegetative", "#769354", listOf(
            Group("Green", listOf("Olive Oil", "Raw", "Under-ripe", "Peapod", "Fresh", "Dark Green", "Vegetative", "Hay-like", "Herb-like", "Beany")),
        )),
        Category("Other", "#817b72", listOf(
            Group("Papery / Musty", listOf("Stale", "Cardboard", "Papery", "Woody", "Moldy / Damp", "Musty / Dusty", "Musty / Earthy", "Animalic", "Meaty / Brothy", "Phenolic")),
            Group("Chemical", listOf("Bitter", "Salty", "Medicinal", "Petroleum", "Skunky", "Rubber")),
        )),
        Category("Roasted", "#795349", listOf(
            Group("Roasted", listOf("Pipe Tobacco", "Tobacco", "Burnt", "Acrid", "Ashy", "Smoky", "Brown Roast")),
            Group("Cereal", listOf("Grain", "Malt")),
        )),
        Category("Spices", "#b65d3d", listOf(
            Group("Pungent", listOf("Pungent")),
            Group("Pepper", listOf("Pepper")),
            Group("Brown Spice", listOf("Anise", "Nutmeg", "Cinnamon", "Clove")),
        )),
        Category("Nutty / Cocoa", "#8b603d", listOf(
            Group("Nutty", listOf("Nutty", "Peanuts", "Hazelnut", "Almond")),
            Group("Cocoa", listOf("Cocoa", "Chocolate", "Dark Chocolate")),
        )),
        Category("Sweet", "#d8a447", listOf(
            Group("Brown Sugar", listOf("Molasses", "Maple Syrup", "Caramelized", "Honey")),
            Group("Vanilla", listOf("Vanilla", "Vanillin")),
            Group("Sweet", listOf("Overall Sweet", "Sweet Aromatics")),
        )),
    )

    val allTerms: List<String> get() = categories.flatMap { c -> c.groups.flatMap { it.terms } }
}
