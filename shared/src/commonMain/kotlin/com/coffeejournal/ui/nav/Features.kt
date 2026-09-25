package com.coffeejournal.ui.nav

import com.coffeejournal.ui.backup.BackupFeature
import com.coffeejournal.ui.bean.BeanFeature
import com.coffeejournal.ui.calendar.CalendarFeature
import com.coffeejournal.ui.extract.ExtractFeature
import com.coffeejournal.ui.form.RecordFormFeature
import com.coffeejournal.ui.misc.MiscFeature

/** Single registry of feature packages; each feature owns its object in its own package. */
object Features {
    val all: List<Feature> = listOf(ExtractFeature, RecordFormFeature, CalendarFeature, BeanFeature, MiscFeature, BackupFeature)
}
