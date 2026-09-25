package com.coffeejournal.data.db

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import kotlin.test.Test
import kotlin.test.assertEquals

/** form-1 / data-1: cupping bean rows are keyed by position, so editing a session never makes two beans share an id. */
class MappersTest {
    private fun session(vararg beans: CuppingBean) = Entry(id = "E", createdAt = 1, category = Category.CUPPING, cuppingBeans = beans.toList())

    /** What the form gets back after a save: the stored rows mapped to domain beans (ids included). */
    private fun reload(entry: Entry): List<CuppingBean> = entry.toBeanEntities().sortedBy { it.position }.map { it.toDomain() }

    @Test fun removeFirstThenAddKeepsThreeDistinctBeans() {
        val saved = reload(session(CuppingBean(name = "A", memo = "a"), CuppingBean(name = "B", memo = "b"), CuppingBean(name = "C", memo = "c")))
        assertEquals(listOf("E-0", "E-1", "E-2"), saved.map { it.id })

        // the form removes A and appends a new card, whose id is blank: [B:E-1, C:E-2, D:""]
        val edited = session(*(saved.drop(1) + CuppingBean(name = "D", memo = "d")).toTypedArray())
        val rows = edited.toBeanEntities()
        assertEquals(3, rows.map { it.id }.toSet().size, "three distinct primary keys")
        assertEquals(listOf("E-0" to "B", "E-1" to "C", "E-2" to "D"), rows.map { it.id to it.name })
        assertEquals(listOf("b", "c", "d"), rows.map { it.memo }, "C keeps its memo")
    }

    @Test fun removeMiddleThenAddAcrossTwoEditsKeepsEveryBean() {
        val first = reload(session(CuppingBean(name = "A"), CuppingBean(name = "B"), CuppingBean(name = "C")))
        val afterRemove = reload(session(first[0], first[2]))
        val rows = session(*(afterRemove + CuppingBean(name = "D")).toTypedArray()).toBeanEntities()
        assertEquals(listOf("A", "C", "D"), rows.map { it.name })
        assertEquals(3, rows.map { it.id }.toSet().size)
    }

    @Test fun nonFiniteScoresNeverReachTheJsonColumns() {
        val e = Entry(id = "x", createdAt = 1, attributes = mapOf("flavor" to Double.NaN, "body" to 8.0),
            cuppingBeans = listOf(CuppingBean(name = "A", evaluationScores = mapOf("acidity" to Double.POSITIVE_INFINITY, "flavor" to 9.0))))
        assertEquals("""{"body":8.0}""", e.toEntity().attributesJson)
        assertEquals("""{"flavor":9.0}""", e.toBeanEntities().single().evaluationScoresJson)
    }
}
