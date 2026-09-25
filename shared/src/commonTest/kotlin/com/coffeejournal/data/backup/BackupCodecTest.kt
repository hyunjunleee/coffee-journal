package com.coffeejournal.data.backup

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BeanSummary
import com.coffeejournal.domain.model.BestRecipe
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.model.Video
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupCodecTest {
    private val codec = BackupCodec()

    // ───────────── (a) domain → encode → decode round trip ─────────────

    @Test fun roundTripKeepsEveryCollectionAndPhotoBytes() {
        val brew = Entry(
            id = "e1", createdAt = 1_720_000_000_000L, category = Category.BEAN, beanMode = BeanMode.CUSTOM_BLEND,
            blendComponents = listOf(BlendComponent("A", "10"), BlendComponent("B", "5")),
            name = "에티오피아 벤사 (리브레, 노르딕)", country = "에티오피아", region = "시다마", altitude = "2100", variety = "74158",
            farmProducer = "구지(타데세)", roastery = "리브레", selection = "노르딕", washingStation = "봄베", process = "허니(더블)",
            packageType = PackageType.DRIPBAG, moisture = "10.5", density = "780", score = "87.5", arrival = "2025-06", roastDate = "7. 11",
            roasterDesc = "설명", roast = "라이트", bagWeight = "200", price = "12000", expectedNotes = "베르가못, 복숭아",
            actualNotes = "오렌지, 자스민", dripper = "V60", filter = "아바카", dose = "15", water = "240", temp = "92", grind = "중간",
            waterType = "정수", time = "2:30", notes = "메모", steps = listOf(RecipeStep("0:00", "40", "30", "뜸"), RecipeStep("0:30", "100", "", "1차")),
            recipeRef = RecipeRef("4:6", listOf(RecipeStep("0:00", "60", "45", "1"))),
            attributes = mapOf("flavor" to 8.0, "acidity" to 7.25, "fragranceIntensity" to 3.5), attributeNotes = mapOf("flavor" to "달콤"),
            tags = listOf("아침"), bagPhotos = listOf("a.jpg", "b.jpg"), groundsPhoto = "g.jpg",
            legacyExtra = buildJsonObject { put("beanGuidance", buildJsonObject { put("recipe", "15g/240g") }); put("rating", 4) },
        )
        val cupping = Entry(
            id = "e2", createdAt = 1_720_100_000_000L, category = Category.CUPPING, name = "프릳츠", cuppingType = "홈커핑", cuppingPlace = "프릳츠",
            notes = "전체 경험",
            cuppingBeans = listOf(
                CuppingBean(name = "케냐 AA", country = "케냐", roastery = "프릳츠", process = "워시드", roast = "미디엄", expectedNotes = "자몽",
                    actualNotes = "블랙커런트", evaluation = mapOf("aroma" to "꽃"), evaluationScores = mapOf("aroma" to 8.0, "flavor" to 9.0), memo = "1등", rank = "1"),
                CuppingBean(name = "하우스 블렌드", beanMode = BeanMode.BLEND, blendComponentsText = "브라질 + 콜롬비아", price = "9000"),
            ),
        )
        val cafe = Entry(id = "e3", createdAt = 1_720_200_000_000L, category = Category.CAFE, name = "게이샤", cafeName = "카페 A", actualNotes = "자스민")
        val original = BackupSnapshot(
            entries = listOf(brew, cupping, cafe),
            photos = listOf(
                PhotoBlob("e1", PhotoKind.BAG, 0, byteArrayOf(1, 2, 3)), PhotoBlob("e1", PhotoKind.BAG, 1, byteArrayOf(4, 5, 6)),
                PhotoBlob("e1", PhotoKind.GROUNDS, 0, byteArrayOf(7, 8, 9, 10)),
            ),
            miscItems = listOf(
                MiscItem(id = "m1", type = MiscType.DRIPPER, name = "오리가미", notes = "S", since = "2025-03-01", status = "보유", photos = listOf("p.jpg"), createdAt = 1_710_000_000_000L),
                MiscItem(id = "m2", type = MiscType.SOURCE, name = "프릳츠", scope = "국내", location = "서울", favorite = true, createdAt = 1_710_000_000_001L),
            ),
            miscPhotos = listOf(PhotoBlob("m1", PhotoKind.MISC, 0, byteArrayOf(11, 12))),
            blends = listOf(Blend("bl1", "모닝", "2025-05-05", listOf(BlendComponent("A", "10")), "노트", 1_700_000_000_000L)),
            classes = listOf(CoffeeClass("c1", 1_700_000_000_001L, "핸드드립 기초", ClassType.RECURRING, "", "2025-01-01", "2025-02-01", "")),
            roadmap = listOf(
                RoadmapPhase("starter", 0, "나의 로드맵", "자유롭게 작성", 0, 99999, listOf(RoadmapItem("i1", "V60 연습", true))),
                RoadmapPhase("p2", 1, "2단계", "31~60일", 31, 60),
            ),
            myRecipes = listOf(
                MyRecipe("r1", "아침 레시피", null, "벤사", 5, "15", "240", "92", "V60", "아바카", "중간", "2:30", listOf(RecipeStep("0:00", "40", "30", "뜸")), 1_700_000_000_002L),
                MyRecipe("r2", "from", "e1", "", 0, createdAt = 1_700_000_000_003L),
            ),
            books = listOf(Book("b1", 1_700_000_000_004L, "커피 아틀라스", "호프만", "읽는 중", "2025-01-01", "", 4, "")),
            videos = listOf(Video("v1", 1_700_000_000_005L, "4:6", "Tetsu", "https://youtu.be/x", "")),
            pantryItems = listOf(
                PantryItem(id = "pa1", name = "벤사", roastery = "리브레", weight = "200", status = PantryItem.STATUS_OPENED, openedAt = 1_700_000_000_006L, createdAt = 1_700_000_000_007L, sourceEntryId = "e1"),
                PantryItem(id = "pa2", name = "케냐", packageType = PackageType.SAMPLE, createdAt = 1_700_000_000_008L),
            ),
            beanSummaries = listOf(BeanSummary("에티오피아 벤사", "총정리", 1_720_000_000_000L)),
            bestRecipes = listOf(BestRecipe("에티오피아 벤사", "e1")),
            settings = mapOf("brew-start-date" to "2026-01-01", "ui-flag" to "1"),
            ddayStart = "2026-01-01",
            exportedAt = "2026-09-25T05:23:43.817Z",
        )

        val text = codec.encode(original)
        val decoded = codec.decode(text)

        // File names are not part of the format; everything else (including photo bytes) must survive.
        val expected = original.copy(
            entries = original.entries.map { it.copy(bagPhotos = emptyList(), groundsPhoto = null) },
            miscItems = original.miscItems.map { it.copy(photos = emptyList()) },
        )
        assertEquals(expected, decoded)

        // Web-facing shape spot checks.
        val root = Json.parseToJsonElement(text).jsonObject
        val data = root["data"]!!.jsonObject
        val e1 = data["entries"]!!.jsonArray[0].jsonObject
        assertEquals("dripbag", e1["beanPackageType"]!!.jsonPrimitive.content)
        assertEquals(true, e1["isDripBag"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("리브레", e1["source"]!!.jsonPrimitive.content)
        assertEquals("8", e1["attributes"]!!.jsonObject["flavor"]!!.jsonPrimitive.content)
        assertEquals("4", e1["rating"]!!.jsonPrimitive.content)
        assertEquals(true, e1["hasBagPhoto"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(true, e1["hasPhoto"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(e1.containsKey("cafeName"))
        val e2 = data["entries"]!!.jsonArray[1].jsonObject
        assertEquals("케냐 AA\n하우스 블렌드", e2["cuppingBeans"]!!.jsonPrimitive.content)
        assertEquals(1, e2["cuppingBeanNotes"]!!.jsonArray.size)
        assertEquals("0", e2["rating"]!!.jsonPrimitive.content)
        assertEquals("", e2["price"]!!.jsonPrimitive.content)
        val bag = root["photos"]!!.jsonObject["bag-photo:e1"]!!.jsonPrimitive.content
        assertTrue(bag.startsWith("[\"data:image/jpeg;base64,"))
        assertEquals("data:image/jpeg;base64,BwgJCg==", root["photos"]!!.jsonObject["journal-photo:e1"]!!.jsonPrimitive.content)
        assertEquals("2026-01-01", root["rawData"]!!.jsonObject["ddayStart"]!!.jsonPrimitive.content)
        assertEquals("coffee-journal-mobile", root["app"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val m1 = data["miscItems"]!!.jsonArray[0].jsonObject
        assertEquals("data:image/jpeg;base64,Cww=", m1["photo"]!!.jsonPrimitive.content)
        assertEquals(1, m1["photos"]!!.jsonArray.size)
    }

    // ───────────── (b) web-format file with legacy fields ─────────────

    private val webSample = """
    {
      "exportedAt": "2026-09-20T01:02:03.000Z",
      "data": {
        "entries": [
          {
            "id": "e1", "createdAt": 1720000000000, "category": "원두", "beanMode": "single", "blendComponents": [],
            "name": "에티오피아 벤사 (리브레, 노르딕)", "country": "에티오피아", "isDripBag": true,
            "tastingNotes": "오렌지, 자스민", "farm": "구지", "producer": "타데세",
            "dose": 15, "water": "240", "price": 12000,
            "steps": [{"time": "0:00", "water": "40", "note": "뜸", "wait": "30"}],
            "recipeRef": {"name": "4:6", "steps": []},
            "attributes": {"flavor": 8, "acidity": 7.5}, "attributeNotes": {"flavor": "달콤"},
            "beanGuidance": {"recipe": "15g/240g", "cautions": "", "tips": ""}, "rating": 4, "photoFeedback": null,
            "hasBagPhoto": true, "hasPhoto": true, "tags": ["아침"]
          },
          {
            "id": "e2", "createdAt": "1720100000000", "category": "커핑", "name": "집", "cuppingType": "홈커핑", "cuppingPlace": "집",
            "cuppingBeans": "케냐 AA\n콜롬비아",
            "cuppingBeanDetails": [
              {"name": "케냐 AA", "roastery": "프릳츠", "evaluation": {"aroma": "꽃"}, "evaluationScores": {"aroma": 8, "flavor": 9}, "note": "레거시노트"},
              {"name": "콜롬비아", "actualNotes": "초콜릿", "note": "메모대신", "beanMode": "blend", "blendComponentsText": "A+B"}
            ]
          },
          {
            "id": "e3", "createdAt": 1720200000000, "category": "커핑", "name": "원두A",
            "cuppingBeans": "원두A\n원두B", "cuppingBeanNotes": [{"bean": "원두B", "note": "베리"}]
          }
        ],
        "miscItems": [
          {"id": "m1", "type": "dripper", "name": "오리가미", "notes": "", "since": "2025-03-01", "status": "보유",
           "photo": "data:image/jpeg;base64,AQID", "photos": ["data:image/jpeg;base64,AQID"], "createdAt": 1710000000000},
          {"id": "m2", "type": "source", "name": "프릳츠", "scope": "국내", "location": "", "status": "", "photo": null, "createdAt": 1710000000001}
        ],
        "blends": [{"id": "bl1", "name": "모닝", "date": "2025-05-05", "beans": [{"name": "A", "grams": 10}, {"name": "B", "grams": "5"}], "notes": "", "createdAt": 1700000000001}],
        "classes": null,
        "roadmapData": [{"id": "starter", "title": "나의 로드맵", "range": "자유롭게 작성", "dayStart": 0, "dayEnd": 99999, "items": [{"id": "i1", "text": "V60 연습", "done": true}]}],
        "myRecipes": null,
        "books": [{"id": "b1", "createdAt": 1700000000000, "title": "커피 아틀라스", "author": "제임스 호프만", "status": "읽는 중", "startDate": "", "endDate": "", "rating": "4", "notes": ""}],
        "beanSummaries": {"에티오피아 벤사": {"text": "총정리", "generatedAt": 1720000000000}},
        "bestRecipes": {"에티오피아 벤사": "e1"}
      },
      "rawData": {"ddayStart": "2026-01-01"},
      "photos": {
        "bag-photo:e1": "[\"data:image/jpeg;base64,AQID\",\"data:image/jpeg;base64,BAUG\"]",
        "journal-photo:e1": "data:image/jpeg;base64,BwgJ",
        "bean-blurb:에티오피아 벤사": "{\"text\":\"AI\"}"
      }
    }
    """.trimIndent()

    @Test fun decodesWebFileWithLegacyFields() {
        val s = codec.decode(webSample)
        assertEquals("2026-09-20T01:02:03.000Z", s.exportedAt)
        assertEquals(3, s.entries.size)

        val e1 = s.entries[0]
        assertEquals(PackageType.DRIPBAG, e1.packageType)
        assertEquals("오렌지, 자스민", e1.actualNotes)
        assertEquals("구지(타데세)", e1.farmProducer)
        assertEquals("15", e1.dose)
        assertEquals("12000", e1.price)
        assertEquals(7.5, e1.attributes["acidity"])
        assertEquals("달콤", e1.attributeNotes["flavor"])
        assertEquals("30", e1.steps.single().wait)
        assertEquals("4:6", e1.recipeRef?.name)
        assertEquals(listOf("아침"), e1.tags)
        val extra = e1.legacyExtra!!
        assertEquals("15g/240g", (extra["beanGuidance"] as JsonObject)["recipe"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(4), extra["rating"])
        assertFalse(extra.containsKey("photoFeedback"))
        assertTrue(e1.bagPhotos.isEmpty())

        val e2 = s.entries[1]
        assertEquals(1_720_100_000_000L, e2.createdAt)
        assertEquals("홈커핑", e2.cuppingType)
        assertEquals(2, e2.cuppingBeans.size)
        assertEquals("레거시노트", e2.cuppingBeans[0].actualNotes)
        assertEquals("", e2.cuppingBeans[0].memo)
        assertEquals(8.0, e2.cuppingBeans[0].evaluationScores["aroma"])
        assertEquals("꽃", e2.cuppingBeans[0].evaluation["aroma"])
        assertEquals("초콜릿", e2.cuppingBeans[1].actualNotes)
        assertEquals("메모대신", e2.cuppingBeans[1].memo)
        assertEquals(BeanMode.BLEND, e2.cuppingBeans[1].beanMode)
        assertNull(e2.legacyExtra)

        val e3 = s.entries[2]
        assertEquals(listOf("원두A", "원두B"), e3.cuppingBeans.map { it.name })
        assertEquals("", e3.cuppingBeans[0].actualNotes)
        assertEquals("베리", e3.cuppingBeans[1].actualNotes)

        val bag = s.photosFor("e1", PhotoKind.BAG)
        assertEquals(2, bag.size)
        assertContentEquals(byteArrayOf(1, 2, 3), bag[0].bytes)
        assertContentEquals(byteArrayOf(4, 5, 6), bag[1].bytes)
        assertContentEquals(byteArrayOf(7, 8, 9), s.photosFor("e1", PhotoKind.GROUNDS).single().bytes)
        assertEquals(3, s.photos.size) // bean-blurb ignored

        assertEquals(2, s.miscItems.size)
        assertContentEquals(byteArrayOf(1, 2, 3), s.miscPhotosFor("m1").single().bytes)
        assertEquals("국내", s.miscItems[1].scope)
        assertTrue(s.miscPhotosFor("m2").isEmpty())

        assertEquals(4, s.books.single().rating)
        assertEquals(listOf("10", "5"), s.blends.single().beans.map { it.grams })
        assertEquals(true, s.roadmap.single().items.single().done)
        assertEquals(99999, s.roadmap.single().dayEnd)
        assertEquals("총정리", s.beanSummaries.single().text)
        assertEquals("e1", s.bestRecipes.single().entryId)
        assertEquals("2026-01-01", s.ddayStart)
        assertTrue(BackupKeys.BOOKS in s.present)
        assertTrue(BackupKeys.DDAY in s.present)
        assertFalse(BackupKeys.CLASSES in s.present)
        assertFalse(BackupKeys.VIDEOS in s.present)
        assertEquals(4, s.photoCount)
        assertTrue(s.summaryLine().startsWith("entries: 3개, miscItems: 2개"))
    }

    // ───────────── (c) empty web backup (shots/backup.json) ─────────────

    private val emptyWebBackup = """
    {
      "exportedAt": "2026-09-25T05:23:43.817Z",
      "data": {
        "entries": [],
        "miscItems": null,
        "blends": null,
        "classes": null,
        "roadmapData": [
          {
            "id": "starter",
            "title": "나의 로드맵",
            "range": "자유롭게 작성",
            "dayStart": 0,
            "dayEnd": 99999,
            "items": []
          }
        ],
        "myRecipes": null,
        "books": null,
        "beanSummaries": null,
        "bestRecipes": null
      },
      "rawData": {
        "ddayStart": "2026-01-01"
      },
      "photos": {}
    }
    """.trimIndent()

    @Test fun emptyWebBackupParses() {
        val s = codec.decode(emptyWebBackup)
        assertTrue(s.entries.isEmpty())
        assertTrue(s.miscItems.isEmpty())
        assertEquals(1, s.roadmap.size)
        assertEquals("나의 로드맵", s.roadmap[0].title)
        assertEquals("2026-01-01", s.ddayStart)
        assertEquals(setOf(BackupKeys.ENTRIES, BackupKeys.ROADMAP, BackupKeys.DDAY), s.present)
        assertEquals(0, s.photoCount)
        assertEquals("2026-09-25T05:23:43.817Z", s.exportedAt)
    }

    @Test fun rejectsFilesWithoutDataField() {
        val e = assertFailsWith<BackupFormatException> { codec.decode("""{"exportedAt": "x"}""") }
        assertEquals("백업 파일 형식이 아니에요. (data 필드가 없어요)", e.message)
        assertFailsWith<BackupFormatException> { codec.decode("not json at all") }
    }

    @Test fun dataUrlDecodingIsLenient() {
        assertContentEquals(byteArrayOf(1, 2, 3), codec.decodeDataUrl("data:image/png;base64,AQID"))
        assertContentEquals(byteArrayOf(1, 2, 3), codec.decodeDataUrl("AQID"))
        assertContentEquals(byteArrayOf(7, 8, 9, 10), codec.decodeDataUrl("data:image/jpeg;base64,BwgJ\nCg=="))
        assertNull(codec.decodeDataUrl(""))
        assertNull(codec.decodeDataUrl("data:image/jpeg;base64"))
    }
    /** Backup data is untrusted: "NaN" / "Infinity" / "1e999" numbers count as missing instead of crashing later. */
    @Test fun nonFiniteNumbersInABackupCountAsMissing() {
        val s = codec.decode(
            """
            {"data": {
              "entries": [{"id": "n1", "createdAt": "1e999", "name": "x", "attributes": {"flavor": "NaN", "acidity": "Infinity", "body": 8, "balance": "-0"},
                "cuppingBeanDetails": [], "rating": "NaN"},
                {"id": "n2", "createdAt": 9223372036854775807, "category": "커핑",
                "cuppingBeanDetails": [{"name": "A", "evaluationScores": {"acidity": "1e999", "flavor": 9}}]}],
              "books": [{"id": "b", "createdAt": 1, "title": "t", "rating": "NaN"}],
              "roadmapData": [{"id": "p", "title": "t", "dayStart": "Infinity", "dayEnd": 1e999, "items": []}],
              "pantryItems": [{"id": "p1", "name": "x", "openedAt": "NaN", "createdAt": 5}]
            }}
            """.trimIndent(),
        )
        val (n1, n2) = s.entries
        assertEquals(mapOf("body" to 8.0, "balance" to -0.0), n1.attributes)
        assertTrue(n1.createdAt in 1_700_000_000_000L..4_000_000_000_000L, "createdAt falls back to now: ${n1.createdAt}")
        assertTrue(n2.createdAt in 1_700_000_000_000L..4_000_000_000_000L, "an out-of-range timestamp falls back to now")
        assertEquals(mapOf("flavor" to 9.0), n2.cuppingBeans.single().evaluationScores)
        assertEquals(0, s.books.single().rating)
        assertEquals(0 to 0, s.roadmap.single().dayStart to s.roadmap.single().dayEnd)
        assertNull(s.pantryItems.single().openedAt)
    }

    /** web loadMiscItems (script3.js 5177-5178): legacy type 'equipment' is shown as a kettle. */
    @Test fun legacyEquipmentTypeBecomesKettle() {
        val s = codec.decode("""{"data": {"miscItems": [{"id": "old", "type": "equipment", "name": "하리오 부오노"}]}}""")
        assertEquals(MiscType.KETTLE, s.miscItems.single().type)
    }
}
