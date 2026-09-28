package com.coffeejournal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Thin 24-unit stroke icons matching the web tab icons (Lucide-style paths copied from the site). */
object AppIcons {
    val cup: ImageVector by lazy { build("cup", "M10 2v2", "M14 2v2", "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1", "M6 2v2") }
    val calendar: ImageVector by lazy { build("calendar", "M8 2v4", "M16 2v4", "M3 6h18v16H3z", "M3 10h18", "M8 14h.01", "M12 14h.01", "M16 14h.01", "M8 18h.01", "M12 18h.01", "M16 18h.01") }
    val bean: ImageVector by lazy { build("bean", "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z", "M12 22V12", "M3.29 7L12 12L20.71 7", "M7.5 4.27l9 5.15") }
    val misc: ImageVector by lazy { build("misc", "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M2 14h4", "M10 8h4", "M18 16h4") }
    val plus: ImageVector by lazy { build("plus", "M12 5v14", "M5 12h14") }
    val star: ImageVector by lazy { build("star", "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z") }
    val camera: ImageVector by lazy { build("camera", "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z", "M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z") }
    val map: ImageVector by lazy { build("map", "M9 3L3 5v16l6-2 6 2 6-2V3l-6 2-6-2z", "M9 3v16", "M15 5v16") }
    val search: ImageVector by lazy { build("search", "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M21 21l-4.3-4.3") }
    val chevronLeft: ImageVector by lazy { build("chevron-left", "M15 18l-6-6 6-6") }
    val chevronRight: ImageVector by lazy { build("chevron-right", "M9 18l6-6-6-6") }
    val chevronDown: ImageVector by lazy { build("chevron-down", "M6 9l6 6 6-6") }
    val close: ImageVector by lazy { build("close", "M18 6L6 18", "M6 6l12 12") }
    val trash: ImageVector by lazy { build("trash", "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6", "M10 11v6", "M14 11v6") }
    val edit: ImageVector by lazy { build("edit", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z") }
    val check: ImageVector by lazy { build("check", "M20 6L9 17l-5-5") }
    val drop: ImageVector by lazy { build("drop", "M12 2.7l5.7 7.1a7 7 0 1 1-11.4 0L12 2.7z") }
    val backup: ImageVector by lazy { build("backup", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }
    val restore: ImageVector by lazy { build("restore", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M17 8l-5-5-5 5", "M12 3v12") }
    // lucide-static 1.48.0 icons/settings.svg (its circle r=3 written as two arcs)
    val settings: ImageVector by lazy {
        build(
            "settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
        )
    }
    val box: ImageVector by lazy { build("box", "M3 7l9-4 9 4v10l-9 4-9-4z", "M3 7l9 4 9-4", "M12 11v10") }
    /** Lucide locate-fixed: the location picker's "현재 위치". */
    val locate: ImageVector by lazy {
        build("locate", "M2 12h3", "M19 12h3", "M12 2v3", "M12 19v3", "M12 19a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    }

    private fun build(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            b.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.25f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter,
            )
        }
        return b.build()
    }
}
