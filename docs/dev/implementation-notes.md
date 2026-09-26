# 구현 규약 (feature 구현자용)

이 문서는 `shared` 모듈에 기능을 추가하는 모든 구현자가 따라야 하는 계약이다. 설계는 `docs/android-app-design.md`, 웹 원본 동작은 `docs/coffee-journal-site-analysis.md`를 근거로 한다. 웹 원본 소스(문구·동작 확인용): `/tmp/claude-0/-home-user-test/92daf568-f08e-5566-bfa0-6f8e190c8cf6/scratchpad/site/script3.js`, 마크업 `body.compact.html`.

## 빌드·검증
```
export ANDROID_HOME=/opt/android-sdk
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest :androidApp:testDebugUnitTest --no-daemon -q
```
- 커밋 전 반드시 위 명령이 exit 0 이어야 한다. 경고는 허용, 오류는 불가.
- 단위 테스트는 `shared/src/commonTest/kotlin/com/coffeejournal/<패키지>/`에 둔다(kotlin-test). 화면 흐름·스크린샷 테스트는 `androidApp/src/test/kotlin/com/coffeejournal/android/`(Robolectric + Roborazzi, 인메모리 Room)에 둔다.
- git 커밋: `git -c user.name=Claude -c user.email=noreply@anthropic.com commit -m "..."`.
- 의존성 추가·버전 변경 시: ① `./gradlew :androidApp:updateThirdPartyNotices`(앱 내 라이브러리 목록 갱신, 안 하면 빌드가 실패) ② `./gradlew --write-verification-metadata sha256 help :androidApp:assembleRelease :shared:testDebugUnitTest :androidApp:testDebugUnitTest`(체크섬 기록) ③ 새로 생긴 `verification-metadata.xml` 항목의 그룹이 공식 배포처인지 확인 후 커밋. POM에 라이선스가 없거나 목록에 없는 라이선스면 ①이 실패하므로 `gradle/third-party-notices.gradle.kts`의 `spdx()`와 `LicenseTexts`에 추가한다.
- 참고 데이터가 외부 기관·가게의 자료라고 적을 때는 그 공식 페이지와 수치를 대조하고 출처 URL을 남긴다(`CafeRecipes.sourceUrl`, `Credits`). 공식 자료에 없는 값은 지어내지 않고 비운다.

## 패키지 소유 규칙
| 기능 | 패키지 | 소유 파일 |
|---|---|---|
| 기록 폼·상세·내 레시피 | `com.coffeejournal.ui.form` | `RecordFormFeature.kt` + 패키지 내 모든 파일 |
| 홈(새로운 추출)·보관함 | `com.coffeejournal.ui.extract` | `ExtractFeature.kt`, `ExtractTabScreen.kt` + 패키지 |
| 커피 달력·스터디·클래스·로드맵 | `com.coffeejournal.ui.calendar` | `CalendarFeature.kt`, `CalendarTabScreen.kt` + 패키지 |
| 원두 탭 | `com.coffeejournal.ui.bean` | `BeanFeature.kt`, `BeanTabScreen.kt`, `Bean*View.kt`, `BeanData.kt` + 패키지 |
| 기타(장비) | `com.coffeejournal.ui.misc` | `MiscFeature.kt`, `MiscTabScreen.kt` + 패키지 |
| 백업/복원 | `com.coffeejournal.ui.backup`, `com.coffeejournal.data.backup`, `ui/platform/BackupFileIo*` | `BackupFeature.kt` + 패키지 |

- 위 소유 범위 밖의 파일(`domain/*`, `data/db/*`, `data/repo/*`, `ui/theme/*`, `ui/nav/*`)은 수정하지 않는다. 공용 헬퍼가 더 필요하면 자기 패키지 안에 `internal`로 만들고 보고서에 "공용 승격 제안"으로 남긴다.
- 기능 진입점: 자기 `Feature` 객체에 `override val module = module { viewModelOf(::XxxViewModel) ... }`, `override fun NavGraphBuilder.routes(nav)`에 `composable<Route.Xxx> { ... }`를 구현한다. 라우트 정의는 `ui/nav/Routes.kt`에 이미 있다(추가 필요 시 보고).

## 기술 스택 사용법
- ViewModel: `androidx.lifecycle.ViewModel` + `viewModelScope`, 상태는 `StateFlow<UiState>`. 화면에서는 `org.koin.compose.viewmodel.koinViewModel<XxxViewModel>()` (파라미터는 `koinViewModel { parametersOf(id) }`, 모듈에서는 `viewModel { (id: String?) -> XxxViewModel(id, get(), ...) }`).
- 저장소(`data/repo/Repositories.kt`): `EntryRepository`(observeAll/getAll/getById/upsert/delete), `PantryRepository`, `MiscRepository`, `StudyRepository`(books/videos/classes), `BlendRepository`, `MyRecipeRepository`, `RoadmapRepository`(ensureSeeded), `BeanMetaRepository`(observeSummaries/putSummary/observeBest/setBest), `SettingsRepository`(observeDdayStart/setDdayStart). 기록 저장은 반드시 `SaveEntryPipeline.save(entry, isNew)`를 통한다(형제 전파·자동 등록·보관함 동기화 포함).
- 사진: `PhotoStore`(Koin `get<PhotoStore>()`), 선택은 `ui/platform/ImagePicker.kt`의 `rememberImagePicker(maxItems) { bytes -> }` / `rememberCameraCapture { bytes -> }`. 저장: `photoStore.save(bytes)` → 파일명. 표시: `coil3.compose.AsyncImage(model = "file://" + photoStore.pathFor(name), contentDescription = …)`.
- 날짜: `domain/rules/Dates.kt`만 사용(Instant 직접 사용 금지). id: `Ids.newId()`.
- 도메인 규칙은 `domain/rules/*`(BeanNames, Packages, BeanRecords, CalendarRanges, DdayRules, PantryRules, Prices, ScaScoring, RoastFamily, NoteCanon, CountryLookup, RegionHierarchy, RecipeSteps)를 재사용하고 중복 구현하지 않는다.
- 참조 데이터는 `domain/reference/*`(FlavorWheel, NoteCategories, NoteSynonyms, Processes, Varieties, CoffeeCountries, WorldMapData, Champions, CafeRecipes, GenericSteps, BeanRangeColors, EquipmentTypes, RoastLevels, ScoreTiers, RoadmapDefaults, RoasteryMapPoints, ScaForm).

## 공용 헬퍼 (감사 후 추가, 반드시 재사용)
- 숫자: `Numbers.parse(text)` — `NaN`·`Infinity`·범위 초과를 "값 없음"(null)으로 돌린다. `toDoubleOrNull()` 직접 사용 금지.
- 오늘 날짜: 화면이 오늘에 의존하면 `Dates.todayFlow()`를 `combine`해 자정에 갱신한다. 특정 날짜의 원두 범위는 `CalendarRanges.rangesOn(ranges, date)`.
- 트랜잭션: 여러 저장소에 걸친 쓰기는 `TransactionRunner.write { … }` 한 블록으로(같은 코루틴 안의 DAO 호출이 모두 합류). 기록 저장은 여전히 `SaveEntryPipeline.save`.
- 복원: 화면은 `RestoreRunner`(앱 스코프)를 통해서만 `BackupService.import`를 호출한다. 화면을 떠나도 복원이 중간에 취소되지 않는다.
- 텍스트 입력: `AppTextField`는 한글 조합이 끊기지 않도록 로컬 `TextFieldValue`를 유지한다(`ImeSafeText`). `FormTextField`·`CompactField`도 같은 방식이다. 숫자만 받기 등 입력 제한은 `onValueChange` 안에서 걸러 되돌리지 말고 `inputFilter = { … }`로 넘긴다(거절 시 null 반환, 바꾼 문자열 반환 시 커서 보정). 소유자 쪽에서 거르면 거절된 글자가 화면에 남는다. 웹의 `type=number` 필드는 `InputFilters.decimal`(음이 아닌 소수, 쉼표는 소수점으로). 한 줄 필드가 포커스를 잃으면 `shownWhen`으로 글 앞부분을 보인다.
- 날짜 입력: `DateField`(웹 `<input type="date">` 대응, "YYYY-MM-DD" 입출력, 빈 값 안내 `연도-월-일`).
- 글리프 버튼: ×·✕ 같은 기호만 있는 동작은 `GlyphButton(glyph, label, onClick)` — 탭 영역 48dp(`MinTouchTarget`), TalkBack 라벨.
- 키보드: 스크롤 컨테이너에 `Modifier.imeOverlapPadding()`(`ui/theme/ImeInsets.kt`)(edge-to-edge라 창이 키보드만큼 줄지 않음; 하단 탭 높이는 제외).
- 폼 상태 보존: 카메라 앱이 앞에 있는 동안 프로세스가 죽어도 입력이 남도록 `SavedFormState`(`ui/theme`, SavedStateHandle + JSON)로 저장.
- 저장 중 뒤로 가기: 폼은 `BlockBackWhile(saving)`으로 시스템 뒤로 가기를 막고, 제목줄 ←·취소도 저장 중에는 무시한다.
- 파생 계산: 기록 전체를 다시 묶는 계산(그룹핑·통계·추천)은 `Flow.deriveOffMain { … }`으로 `Dispatchers.Default`에서, 최신 입력만 반영해 수행한다. 메인 스레드에서 직접 계산하지 않는다(기록 1,000건에서 110–150ms).
- 큰 글자·좁은 화면: 한 줄이어야 하는 짧은 라벨은 `FitText`(줄바꿈 대신 축소), 고정 폭 숫자 열은 `N.dp.fontScaled()`(글자 배율만큼, 최대 1.6배 확장). `Seg`는 웹 flex-wrap처럼 넘치면 다음 줄로 옮긴다.
- 동시 수정: 읽고-고쳐-쓰는 저장(로드맵, 보관함 편집, 즐겨찾기)은 `Mutex` 안에서 행을 다시 읽은 뒤 쓴다. 빠른 연속 탭이나 다른 화면의 변경이 덮이지 않게 한다.
- 2차 기록·분석 헬퍼(feature-plan-v2 §2):
  - `BrewMath`(`domain/rules`): 비율 `ratio`/`ratioText`("1:16.7"), `extractionYield`(EY% = TDS% × 추출액 ÷ 원두량), 계산기 `solve`, SCA 고전 추출 차트 구역 `ClassicChart`(18–22%, 1.15–1.35%, 출처 주석)와 `eyVerdict`/`tdsVerdict`.
  - `CvaForm`(`domain/reference`, SCA-103/104와 한국어 양식 대조)·`CvaScoring`·`CvaAssessment`(`domain/rules`): CVA 점수(`affectiveScore`, §5.5 식, 결점 종류 규칙), 저장은 `toScores`/`toTexts` → 기존 맵의 `cva.` 키, 읽기는 `fromMaps`. 점수 표시는 `CvaScoring.scoreText(entry)`("83.50 / 100" 또는 "CVA 84.25 / 100")를 쓰고, 결점 종류가 텍스트 맵에 있으므로 숫자 맵만으로 CVA 점수를 계산하지 않는다. SCA 2004 합계(`ScaScoring`)는 `cva.` 키를 보지 않는다.
  - 타이머: `BrewClock`(단조 `monotonicMs` + 프로세스 복원용 `wallMs`, Koin `single`, 테스트는 가짜 시계로 교체), 순수 규칙 `BrewTimerEngine`(행 → `RecipeStep`, 레시피 안내, 진동 경계), 결과 전달 `BrewTimerResult`(목적지 SavedStateHandle 키). 플랫폼 경계 `KeepScreenOn(enabled)`, `rememberStepBuzz()`(`ui/platform/BrewTimerPlatform*`, Android VIBRATE 권한).
  - `SliderRow(description = …)`: 같은 이름의 행이 둘일 때(CVA 강도/품질 인상) TalkBack 이름을 따로 준다.
  - 통계 `StatsCalc`(기간, 막대, 지출 단가 `unitPrice`, 상위 목록), 비교 `BrewCompare`(기준 선택, 셀 차이) — 둘 다 순수 함수, 화면은 `deriveOffMain`.
  - 차트는 Compose Canvas(`ui/extract/stats/StatsCharts.kt`): 헤어라인, 잉크 막대, `rememberTextMeasurer`로 모노 숫자. 차트 노드는 `clearAndSetSemantics { contentDescription = 요약 }`.
  - Robolectric 흐름 테스트에서 `AlertDialog` 안에 텍스트 필드를 두면 대화상자가 열린 동안 Compose가 idle이 되지 않았다(타이머 물량 입력). 입력은 화면 안의 패널로 두었다.
- 탭 간 요청: 다른 화면에서 원두 탭의 특정 서브뷰를 열 때는 `BeanViewRequests`(Koin single)에 요청을 넣고 탭을 전환한다(라우트 인자는 탭 전환 시 이전 값으로 복원되므로 쓰지 않음).

## 디자인 규약 (`ui/theme`)
- 접근성: 글리프·체크박스·지도·휠처럼 그림만 있는 요소는 동작 이름이나 아래 목록을 가리키는 content description을 달고, 접이식 헤더·토글은 펼침/선택 상태를 노출한다. 달력 칸은 "9월 21일, 오늘, 기록 2개"처럼 읽힌다.
- 색·타이포: `Ink.*`, `AppType.*`, 간격 `Dimens.*`. 입력 상자 안의 글(값·placeholder)은 `AppType.input` / `AppType.inputSmall`(줄 상자를 자르지 않아 한글·영문 필드 높이가 같음). 모서리 반경 0, 헤어라인 0.5dp, 그림자 없음.
- 컴포넌트: `TopHeader`, `ScreenTitleBar`, `SectionLabel`, `FieldLabel`, `Hairline`, `PrimaryButton`, `GhostButton`, `SubTabs`(선택 칩으로 자동 스크롤, 강조 칩 옵션), `Seg`, `HairlineCard`, `EmptyNote`, `HintText`, `CatDot`, `Badge`, `KeyValueRow`, `AppTextField`, `ChipInput`, `Chip`, 아이콘 `AppIcons.*`(material-icons 라이브러리는 없다).
- 문구는 웹 원문을 그대로 쓴다(빈 상태 안내, 라벨, placeholder). 한국어.
- 삭제는 항상 확인 대화상자(`AlertDialog`). 목록 정렬·필터 기본값은 웹과 동일.
- 세그먼트는 같은 옵션 재탭 시 해제(`Seg(allowClear = true)`), 단 카테고리 같은 필수 세그는 `allowClear = false`.
- 스크롤 화면은 `LazyColumn` 또는 `verticalScroll`, 하단 여백 96dp(FAB/하단 탭 고려).

## 웹 동작 이식 원칙
- 데이터 필드명·값(카테고리 '원두/카페/커핑', 패키지 standard/dripbag/sample, 커핑 유형 퍼블릭/홈커핑/수업 등)은 웹과 동일하게 유지한다(백업 호환).
- 웹의 버그로 확인된 항목(설계서 §2.3)은 고쳐서 구현한다.
- AI 기능·숨김 기능은 구현하지 않는다.
