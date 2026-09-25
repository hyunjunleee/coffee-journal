package com.coffeejournal.data.repo

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.domain.rules.Packages

/**
 * What happens after a record is saved (web save-entry handler): fill sibling records of the same bean,
 * auto-register roastery / process / farm / importer / variety items, and sync the pantry.
 */
class SaveEntryPipeline(
    private val entries: EntryRepository,
    private val misc: MiscRepository,
    private val pantry: PantryRepository,
) {
    suspend fun save(entry: Entry, isNew: Boolean): Entry {
        entries.upsert(entry)
        if (!entry.isCupping && entry.name.isNotBlank()) propagateToSiblings(entry)
        autoRegister(entry)
        if (entry.isBrew) syncPantry(entry, isNew)
        return entry
    }

    /** Siblings (same core name, not cupping) only receive values they are missing. */
    private suspend fun propagateToSiblings(entry: Entry) {
        val key = BeanNames.coreBeanName(entry.name)
        if (key.isBlank()) return
        val parens = BeanNames.parseNameParens(entry.name)
        val roasteryVal = entry.roastery.ifBlank { parens?.roastery ?: "" }
        val selectionVal = entry.selection.ifBlank { parens?.source ?: "" }
        val all = entries.getAll()
        for (sib in all) {
            if (sib.id == entry.id || sib.isCupping || BeanNames.coreBeanName(sib.name) != key) continue
            var changed = sib
            fun fill(current: String, value: String, apply: (String) -> Entry) { if (current.isBlank() && value.isNotBlank()) changed = apply(value) }
            fill(changed.expectedNotes, entry.expectedNotes) { changed.copy(expectedNotes = it) }
            fill(changed.variety, entry.variety) { changed.copy(variety = it) }
            fill(changed.roastery, roasteryVal) { changed.copy(roastery = it) }
            fill(changed.selection, selectionVal) { changed.copy(selection = it) }
            fill(changed.farmProducer, entry.farmProducer) { changed.copy(farmProducer = it) }
            fill(changed.washingStation, entry.washingStation) { changed.copy(washingStation = it) }
            fill(changed.region, entry.region) { changed.copy(region = it) }
            fill(changed.country, entry.country) { changed.copy(country = it) }
            if (changed != sib) entries.upsert(changed)
        }
    }

    private suspend fun autoRegister(entry: Entry) {
        val pairs = mutableListOf<Pair<String, String>>()
        fun processMain(process: String, other: String): String =
            if (process == "기타") other.ifBlank { "기타" } else BeanNames.parseFarmProducer(process).farm.ifBlank { process }
        if (entry.isCupping) {
            for (b in entry.cuppingBeans) {
                pairs += MiscType.SOURCE to b.roastery
                pairs += MiscType.PROCESS to processMain(b.process, "")
                pairs += MiscType.FARM to b.farmProducer
                BeanNames.splitVarietyValues(b.variety).forEach { pairs += MiscType.VARIETY to BeanNames.varietyPrimary(it) }
            }
        } else {
            pairs += MiscType.SOURCE to entry.roastery
            pairs += MiscType.PROCESS to processMain(entry.process, entry.processOther)
            pairs += MiscType.FARM to entry.farmProducer
            pairs += MiscType.SELECTION to BeanNames.entrySelection(entry)
            BeanNames.splitVarietyValues(entry.variety).forEach { pairs += MiscType.VARIETY to BeanNames.varietyPrimary(it) }
        }
        registerMany(pairs)
    }

    suspend fun registerMany(pairs: List<Pair<String, String>>) {
        val existing = misc.getAll().toMutableList()
        val toAdd = mutableListOf<MiscItem>()
        for ((type, raw) in pairs) {
            val value = raw.trim()
            if (value.isEmpty()) continue
            val norm = value.lowercase().replace(Regex("\\s+"), "")
            val dup = (existing + toAdd).any { it.type == type && it.name.trim().lowercase().replace(Regex("\\s+"), "") == norm }
            if (dup) continue
            val now = Dates.nowMillis()
            toAdd += MiscItem(
                id = Ids.newId(now) + Ids.newCustomId("", now).takeLast(3),
                type = type, name = value, createdAt = now,
                scope = if (type == MiscType.SOURCE) (if (Regex("[가-힣]").containsMatchIn(value)) Scope.DOMESTIC else Scope.OVERSEAS) else "",
            )
        }
        if (toAdd.isNotEmpty()) misc.upsertAll(toAdd)
    }

    /** Web syncBrewEntryToOpenedPantry, but only a *new* record opens an unopened bag automatically. */
    private suspend fun syncPantry(entry: Entry, isNew: Boolean) {
        if (Packages.isBlend(entry) || entry.name.isBlank()) return
        val key = BeanNames.coreBeanName(entry.name)
        if (key.isBlank()) return
        val items = pantry.getAll()
        val match = items.firstOrNull { BeanNames.coreBeanName(it.name) == key }
        val now = Dates.nowMillis()
        if (match != null) {
            val shouldOpen = isNew || match.isOpened
            val updated = match.copy(
                roastery = match.roastery.ifBlank { entry.roastery },
                packageType = Packages.pantryPackageType(match),
                weight = match.weight.ifBlank { entry.bagWeight },
                price = match.price.ifBlank { entry.price },
                roastLevel = match.roastLevel.ifBlank { entry.roast },
                roastDate = match.roastDate.ifBlank { entry.roastDate },
                expectedNotes = match.expectedNotes.ifBlank { entry.expectedNotes },
                sourceEntryId = match.sourceEntryId.ifBlank { entry.id },
                status = if (shouldOpen) PantryItem.STATUS_OPENED else match.status,
                openedAt = if (shouldOpen) (match.openedAt ?: now) else match.openedAt,
            )
            if (updated != match) pantry.upsert(updated)
        } else if (isNew) {
            pantry.upsert(
                PantryItem(
                    id = Ids.newId(now), name = entry.name, roastery = entry.roastery,
                    packageType = Packages.entryPackageType(entry), weight = entry.bagWeight, price = entry.price,
                    roastLevel = entry.roast, roastDate = entry.roastDate, expectedNotes = entry.expectedNotes,
                    status = PantryItem.STATUS_OPENED, openedAt = now, createdAt = entry.createdAt.takeIf { it > 0 } ?: now,
                    sourceEntryId = entry.id,
                )
            )
        }
    }
}
