package com.coffeejournal.android

import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.BookStatus
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.model.Video
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.koin.core.context.GlobalContext

/** Seeds a realistic little journal so screenshots show populated screens. */
object SampleData {
    private fun at(date: LocalDate, hour: Int = 8, minute: Int = 30): Long = Dates.toMillis(date, hour, minute)

    private val steps = listOf(
        RecipeStep("0:00", "50", "10", "1차 푸어"), RecipeStep("0:10", "", "40", "스월, 대기"),
        RecipeStep("0:50", "190", "30", "2차 푸어"), RecipeStep("1:20", "", "70", "드로우다운"),
    )

    fun seed() = runBlocking {
        val koin = GlobalContext.get()
        val entries: EntryRepository = koin.get()
        val pantry: PantryRepository = koin.get()
        val misc: MiscRepository = koin.get()
        val study: StudyRepository = koin.get()
        val blends: BlendRepository = koin.get()
        val meta: BeanMetaRepository = koin.get()
        val settings: SettingsRepository = koin.get()
        val roadmap: RoadmapRepository = koin.get()

        settings.setDdayStart(LocalDate(2026, 1, 1))
        roadmap.ensureSeeded()

        val sca = ScaScoring.defaultAttributes() + mapOf(
            "fragranceAroma" to 8.0, "flavor" to 8.25, "aftertaste" to 7.75, "acidity" to 8.25, "body" to 7.5,
            "balance" to 8.0, "overall" to 8.25, "fragranceIntensity" to 3.5, "acidityIntensity" to 4.0, "bodyIntensity" to 3.0,
        )
        val yirg = Entry(
            id = "e1", createdAt = at(LocalDate(2026, 9, 21)), category = Category.BEAN,
            name = "에티오피아 예가체프 워카 첼베사", country = "에티오피아", region = "Yirgacheffe, Gedeb, Worka Chelbesa",
            altitude = "2,000~2,200m", variety = "Heirloom(74158)", farmProducer = "워카 첼베사(SNAP)", roastery = "커피 리브레",
            selection = "Nordic Approach", washingStation = "첼베사", process = "워시드", roast = "라이트", bagWeight = "200", price = "18000",
            expectedNotes = "자스민, 얼그레이, 복숭아", actualNotes = "자스민, 복숭아, 꿀", dripper = "V60", filter = "V60 표백",
            dose = "15", water = "240", temp = "92", grind = "C40 24클릭", waterType = "정수기 물", time = "2:30",
            notes = "첫 잔은 밝고 깨끗했다. 다음엔 온도를 1도 내려볼 것.", steps = steps, recipeRef = RecipeRef("유어홈 (Your Home)", steps),
            attributes = sca, attributeNotes = mapOf("acidity" to "복숭아 산미가 또렷"),
        )
        entries.upsert(yirg)
        entries.upsert(yirg.copy(id = "e2", createdAt = at(LocalDate(2026, 9, 23), 9, 5), temp = "91", actualNotes = "자스민, 홍차, 살구", attributes = sca + mapOf("flavor" to 8.5, "overall" to 8.5), notes = "91도가 더 부드럽다.", recipeRef = null))
        entries.upsert(
            Entry(
                id = "e3", createdAt = at(LocalDate(2026, 9, 12)), category = Category.BEAN, name = "콜롬비아 라 플라타 게이샤 워시드",
                country = "콜롬비아", region = "Huila", variety = "Geisha", farmProducer = "라 플라타(호세)", roastery = "프릳츠", process = "워시드",
                roast = "미디엄 라이트", bagWeight = "100", price = "32000", expectedNotes = "오렌지 블라썸, 라임", actualNotes = "라임, 꽃",
                dripper = "오리가미", dose = "15", water = "250", temp = "90", steps = steps, attributes = ScaScoring.defaultAttributes(),
            )
        )
        entries.upsert(
            Entry(
                id = "e4", createdAt = at(LocalDate(2026, 9, 18), 15, 0), category = Category.CAFE, name = "브라질 세하도 내추럴", cafeName = "FELT 청계천",
                country = "브라질", region = "Cerrado", process = "내추럴", roast = "미디엄", price = "6500", dripper = "칼리타 웨이브",
                actualNotes = "헤이즐넛, 초콜릿", notes = "고소하고 편안한 데일리.", attributes = ScaScoring.defaultAttributes(),
            )
        )
        entries.upsert(
            Entry(
                id = "e5", createdAt = at(LocalDate(2026, 9, 6), 14, 0), category = Category.CUPPING, name = "커피플랜트 퍼블릭 커핑",
                cuppingType = CuppingType.PUBLIC, cuppingPlace = "커피플랜트 성수", notes = "케냐가 압도적이었다. 파나마 게이샤는 향은 좋으나 바디가 얇았다.\n\nFinal Evaluation\n케냐 1위, 파나마 2위, 브라질 3위.",
                cuppingBeans = listOf(
                    CuppingBean(name = "케냐 키리냐가 AA", country = "케냐", region = "Kirinyaga", variety = "SL28, SL34", process = "워시드", roast = "라이트", rank = "1", actualNotes = "블랙커런트, 토마토", evaluationScores = mapOf("acidity" to 9.0, "flavor" to 8.75)),
                    CuppingBean(name = "파나마 보케테 게이샤", country = "파나마", region = "Boquete", variety = "Geisha", process = "내추럴", roast = "라이트", rank = "2", actualNotes = "자스민, 베르가못"),
                    CuppingBean(name = "하우스 블렌드", country = "브라질", beanMode = "blend", blendComponentsText = "브라질 60 · 콜롬비아 40", process = "내추럴", roast = "미디엄 다크", rank = "3"),
                ),
            )
        )
        pantry.upsertAll(
            listOf(
                PantryItem(id = "p1", name = "에티오피아 예가체프 워카 첼베사", roastery = "커피 리브레", weight = "200", price = "18000", roastLevel = "라이트", roastDate = "2026-09-10", status = PantryItem.STATUS_OPENED, openedAt = at(LocalDate(2026, 9, 21)), createdAt = at(LocalDate(2026, 9, 15)), sourceEntryId = "e1"),
                PantryItem(id = "p2", name = "과테말라 안티구아 부르봉", roastery = "모모스", weight = "200", price = "16000", roastLevel = "미디엄", roastDate = "2026-09-20", purchaseDate = "2026-09-22", expectedNotes = "캐러멜, 오렌지", createdAt = at(LocalDate(2026, 9, 22))),
            )
        )
        misc.upsertAll(
            listOf(
                MiscItem(id = "m1", type = MiscType.DRIPPER, name = "오리가미 드리퍼 S", notes = "세라믹, 웨이브 필터 사용", since = "2026-03-01", status = MiscStatus.OWNED, createdAt = at(LocalDate(2026, 3, 1))),
                MiscItem(id = "m2", type = MiscType.FILTER, name = "칼리타 웨이브 필터 155", status = MiscStatus.OWNED, createdAt = at(LocalDate(2026, 3, 2))),
                MiscItem(id = "m3", type = MiscType.KETTLE, name = "펠로우 스타그 EKG", status = MiscStatus.CURIOUS, createdAt = at(LocalDate(2026, 4, 1))),
                MiscItem(id = "m4", type = MiscType.SOURCE, name = "커피 리브레", scope = Scope.DOMESTIC, location = "대한민국 서울특별시", favorite = true, createdAt = at(LocalDate(2026, 5, 1))),
                MiscItem(id = "m5", type = MiscType.SOURCE, name = "Kurasu Kyoto", scope = Scope.OVERSEAS, location = "교토, 일본", createdAt = at(LocalDate(2026, 5, 2))),
                MiscItem(id = "m6", type = MiscType.FARM, name = "워카 첼베사(SNAP)", notes = "게데브 지역 워싱스테이션", createdAt = at(LocalDate(2026, 5, 3))),
                MiscItem(id = "m7", type = MiscType.SELECTION, name = "Nordic Approach", createdAt = at(LocalDate(2026, 5, 4))),
            )
        )
        study.upsertBook(Book(id = "b1", createdAt = at(LocalDate(2026, 8, 1)), title = "커핑 바이블", author = "홍길동", status = BookStatus.READING, startDate = "2026-08-01", rating = 4, notes = "센서리 훈련 파트가 좋다."))
        study.upsertVideo(Video(id = "v1", createdAt = at(LocalDate(2026, 8, 5)), title = "추출 변수와 맛의 관계", channel = "James Hoffmann", url = "https://youtube.com", notes = "온도 파트 다시 보기"))
        study.upsertClass(CoffeeClass(id = "c1", createdAt = at(LocalDate(2026, 7, 10)), title = "홈카페 원데이 클래스", classType = ClassType.ONEDAY, date = "2026-07-12", notes = "뜸 들이기의 의미"))
        blends.upsert(Blend(id = "bl1", name = "에티오피아+콜롬비아", date = "2026-09-20", beans = listOf(BlendComponent("에티오피아 예가체프 워카 첼베사", "10"), BlendComponent("콜롬비아 라 플라타 게이샤 워시드", "5")), notes = "꽃향이 더 살아남", createdAt = at(LocalDate(2026, 9, 20))))
        meta.putSummary("에티오피아 예가체프 워카 첼베사", "92도보다 91도가 안정적. 2차 푸어를 천천히.")
        meta.setBest("에티오피아 예가체프 워카 첼베사", "e2")
    }
}
