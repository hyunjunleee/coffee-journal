package com.coffeejournal.domain.reference

/**
 * The app's own additions to the flavor wheel: tasting notes people use a lot that are NOT on the SCA/WCR Coffee
 * Taster's Flavor Wheel ([FlavorWheel]), filed under the wheel category they read closest to. They are not SCA or WCR
 * content and are shown apart from the wheel (grey, marked 비공식), so the wheel itself stays unaltered
 * (CC BY-NC-ND 4.0). None of them repeats a wheel descriptor, and the WCR Sensory Lexicon 2.0 (2017,
 * https://worldcoffeeresearch.org/resources/sensory-lexicon) has none of them as an attribute (it names apricot only
 * inside its definition of Almond, and no tartaric acid at all).
 *
 * Chosen 2026-09-29; each list says where its terms come from:
 * - CCC: Counter Culture Coffee Taster's Flavor Wheel (© 2013),
 *   https://counterculturecoffee.com/blogs/counter-culture-coffee/flavor-wheel
 *   (poster PDF: https://www.javalush.com/wp-content/uploads/2015/02/CCC_Tasters_Wheel_85x11.pdf). Written as a single
 *   note where the poster has a variant ("Cacao Nibs" → Cacao Nib, "Dried Fig" → Fig, "Sugar Cane" → Cane Sugar).
 * - SCA: on the CVA forms but not on the wheel — Umami (a main taste) and Metallic (mouthfeel) in SCA Standard
 *   103-2024, https://sca.coffee/s/AW_SCA-103_Descriptive-Assessment_Sept2024_Secured.pdf; the potato defect in SCA
 *   Standard 104-2024, https://sca.coffee/s/AW_SCA-104_Affective-Assessment_Sept2024_Secured.pdf.
 * - Acids: Olympia Coffee Roasting, "Understanding Acids in Coffee", https://www.olympiacoffee.com/blogs/blog/acids-in-coffee
 *   (tartaric, phosphoric, lactic acid; lactic "yogurt"); Rune et al., "Acids in brewed coffees: Chemical composition and
 *   sensory threshold", Curr. Res. Food Sci. 6 (2023) 100485, https://doi.org/10.1016/j.crfs.2023.100485 (phosphoric
 *   and lactic acid measured in brewed specialty coffee). The wheel has only acetic, butyric, isovaleric, citric and
 *   malic acid.
 * - Roasters: common on specialty roasters' bags and cupping notes, but on neither wheel.
 */
object FlavorWheelExtras {
    /** Official [FlavorWheel.Category.name] → the extra notes, in wheel order. */
    val byCategory: Map<String, List<String>> = linkedMapOf(
        "Floral" to listOf(
            // CCC
            "Lavender", "Hibiscus", "Orange Blossom", "Bergamot", "Green Tea", "Honeysuckle", "Magnolia", "Rose Hips", "Lemongrass",
            // roasters
            "Earl Grey", "Oolong", "Elderflower", "Lilac", "Violet", "Acacia", "Coffee Blossom",
        ),
        "Fruity" to listOf(
            // CCC
            "Apricot", "Nectarine", "Plum", "Stone Fruit", "Mango", "Passion Fruit", "Papaya", "Lychee", "Banana", "Kiwi",
            "Tropical Fruit", "Melon", "Watermelon", "Green Apple", "Red Apple", "Cranberry", "Black Currant", "Red Currant",
            "Tangerine", "Mandarin", "Fig", "Date",
            // roasters
            "Red Fruit", "Yuzu", "Blood Orange",
        ),
        "Sour / Fermented" to listOf(
            // acids
            "Tartaric Acid", "Phosphoric Acid", "Lactic Acid", "Yogurt",
            // CCC ("Wine")
            "Red Wine", "White Wine",
            // roasters
            "Kombucha", "Cider", "Rum", "Brandy", "Bourbon", "Liqueur", "Boozy",
        ),
        "Green / Vegetative" to listOf(
            // CCC
            "Tomato", "Green Pepper", "Snow Pea", "Grassy", "Leafy Greens", "Mint", "Sage", "Dill", "Hops", "Olive",
            // roasters
            "Rhubarb", "Thyme", "Basil",
        ),
        "Other" to listOf(
            // CCC ("Soil", "Leathery")
            "Earthy", "Mushroom", "Leather", "Cedar", "Soy Sauce", "Savory",
            // SCA
            "Umami", "Metallic", "Potato",
            // roasters
            "Mineral",
        ),
        "Roasted" to listOf(
            // CCC ("Sweet Bread Pastry")
            "Toast", "Burnt Sugar", "Graham Cracker", "Fresh Bread", "Pastry", "Barley", "Rye", "Granola",
            // roasters
            "Biscuit",
        ),
        "Spices" to listOf(
            // CCC ("Licorice-Anise")
            "Ginger", "Licorice", "Black Pepper", "Coriander",
            // roasters
            "Cardamom", "Pink Peppercorn", "Allspice", "Star Anise", "Baking Spice", "Chai",
        ),
        "Nutty / Cocoa" to listOf(
            // CCC
            "Milk Chocolate", "Cacao Nib", "Bittersweet Chocolate", "Walnut", "Pecan", "Cashew",
            // roasters
            "White Chocolate", "Brownie", "Macadamia", "Pistachio", "Chestnut", "Praline", "Marzipan", "Peanut Butter", "Sesame",
        ),
        "Sweet" to listOf(
            // CCC
            "Caramel", "Nougat", "Marshmallow", "Cane Sugar", "Cola",
            // roasters
            "Toffee", "Butterscotch", "Panela", "Muscovado", "Cotton Candy", "Dulce de Leche", "Golden Syrup",
        ),
    )

    /** The extras of the category named [category]; empty for an unknown name. */
    fun terms(category: String): List<String> = byCategory[category].orEmpty()

    val allTerms: List<String> get() = byCategory.values.flatten()
}
