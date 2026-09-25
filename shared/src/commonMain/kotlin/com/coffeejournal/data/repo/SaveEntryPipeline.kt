package com.coffeejournal.data.repo

import com.coffeejournal.data.db.TransactionRunner
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BeanRecords
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
    private val tx: TransactionRunner,
) {
    /**
     * One write transaction (design §2.3 #7): the record, its siblings, the registrations and the pantry bag are
     * committed together or not at all. Every step is an upsert or fills blanks only, so calling it again with the
     * same entry (a retry after a failure) leaves exactly one record and one bag.
     */
    suspend fun save(entry: Entry, isNew: Boolean): Entry = tx.write {
        entries.upsert(entry)
        if (!entry.isCupping && entry.name.isNotBlank()) propagateToSiblings(entry)
        autoRegister(entry)
        if (entry.isBrew) syncPantry(entry, isNew)
        entry
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
            // web save-entry (script3.js 7110-7116): source, process, farm and ['selection', getEntrySelection(b)] per bean
            for (b in entry.cuppingBeans) {
                pairs += MiscType.SOURCE to b.roastery
                pairs += MiscType.PROCESS to processMain(b.process, "")
                pairs += MiscType.FARM to b.farmProducer
                pairs += MiscType.SELECTION to BeanNames.selectionShortName(BeanNames.parseNameParens(b.name)?.source)
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

    /**
     * Web load-time misc backfill (script3.js 8238-8373): every bean record's farm, variety (primary name),
     * roastery (`en.roastery || en.source`, source = the name's parenthesised roastery) and importer
     * (getEntrySelection) is registered when it is missing. A restore runs it, because restored records do not go
     * through [save].
     */
    suspend fun backfill(records: List<Entry>) {
        val pairs = mutableListOf<Pair<String, String>>()
        for (r in BeanRecords.flatten(records)) {
            pairs += MiscType.FARM to r.farmProducer
            BeanNames.splitVarietyValues(r.variety).forEach { pairs += MiscType.VARIETY to BeanNames.varietyPrimary(it) }
            pairs += MiscType.SOURCE to r.roastery.ifBlank { if (r.parentEntryId == null) BeanNames.parseNameRoastery(r.name) else "" }
            pairs += MiscType.SELECTION to r.selection
        }
        registerMany(pairs)
    }

    /** Web autoRegisterMany: adds (type, value) items that are not registered yet (case- and whitespace-insensitive). */
    suspend fun registerMany(pairs: List<Pair<String, String>>) {
        val known = misc.getAll().mapTo(HashSet()) { miscNameKey(it.type, it.name) }
        val toAdd = mutableListOf<MiscItem>()
        for ((type, raw) in pairs) {
            val value = raw.trim()
            if (value.isEmpty()) continue
            if (!known.add(miscNameKey(type, value))) continue
            val now = Dates.nowMillis()
            toAdd += MiscItem(
                id = Ids.newId(now) + Ids.newCustomId("", now).takeLast(3),
                type = type, name = value, createdAt = now,
                scope = if (type == MiscType.SOURCE) (if (Regex("[가-힣]").containsMatchIn(value)) Scope.DOMESTIC else Scope.OVERSEAS) else "",
            )
        }
        if (toAdd.isNotEmpty()) misc.upsertAll(toAdd)
    }

    /**
     * Web syncBrewEntryToOpenedPantry (script3.js 5896-5935), but only a *new* record opens an unopened bag
     * automatically. The roastery falls back to the name's parenthesised roastery (web `entry.roastery || entry.source`).
     */
    private suspend fun syncPantry(entry: Entry, isNew: Boolean) {
        if (Packages.isBlend(entry) || entry.name.isBlank()) return
        val key = BeanNames.coreBeanName(entry.name)
        if (key.isBlank()) return
        val roasteryVal = entry.roastery.ifBlank { BeanNames.parseNameRoastery(entry.name) }
        val items = pantry.getAll()
        val match = items.firstOrNull { BeanNames.coreBeanName(it.name) == key }
        val now = Dates.nowMillis()
        if (match != null) {
            val shouldOpen = isNew || match.isOpened
            val updated = match.copy(
                roastery = match.roastery.ifBlank { roasteryVal },
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
                    id = Ids.newId(now), name = entry.name, roastery = roasteryVal,
                    packageType = Packages.entryPackageType(entry), weight = entry.bagWeight, price = entry.price,
                    roastLevel = entry.roast, roastDate = entry.roastDate, expectedNotes = entry.expectedNotes,
                    status = PantryItem.STATUS_OPENED, openedAt = now, createdAt = entry.createdAt.takeIf { it > 0 } ?: now,
                    sourceEntryId = entry.id,
                )
            )
        }
    }
}

private val whitespace = Regex("\\s+")

/** Identity of a misc item for de-duplication: type plus lower-case name without whitespace (web addMiscValueIfMissing). */
internal fun miscNameKey(type: String, name: String): String = type + "\u0000" + name.trim().lowercase().replace(whitespace, "")
