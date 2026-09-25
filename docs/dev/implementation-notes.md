# 구현 규약 (feature 구현자용)

이 문서는 `shared` 모듈에 기능을 추가하는 모든 구현자가 따라야 하는 계약이다. 설계는 `docs/android-app-design.md`, 웹 원본 동작은 `docs/coffee-journal-site-analysis.md`를 근거로 한다. 웹 원본 소스(문구·동작 확인용): `/tmp/claude-0/-home-user-test/92daf568-f08e-5566-bfa0-6f8e190c8cf6/scratchpad/site/script3.js`, 마크업 `body.compact.html`.

## 빌드·검증
```
export ANDROID_HOME=/opt/android-sdk
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest --no-daemon -q
```
- 커밋 전 반드시 위 명령이 exit 0 이어야 한다. 경고는 허용, 오류는 불가.
- 단위 테스트는 `shared/src/commonTest/kotlin/com/coffeejournal/<패키지>/`에 둔다(kotlin-test).
- git 커밋: `git -c user.name=Claude -c user.email=noreply@anthropic.com commit -m "..."`.

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

## 디자인 규약 (`ui/theme`)
- 색·타이포: `Ink.*`, `AppType.*`, 간격 `Dimens.*`. 모서리 반경 0, 헤어라인 0.5dp, 그림자 없음.
- 컴포넌트: `TopHeader`, `ScreenTitleBar`, `SectionLabel`, `FieldLabel`, `Hairline`, `PrimaryButton`, `GhostButton`, `SubTabs`, `Seg`, `HairlineCard`, `EmptyNote`, `HintText`, `CatDot`, `Badge`, `KeyValueRow`, `AppTextField`, `ChipInput`, `Chip`, 아이콘 `AppIcons.*`(material-icons 라이브러리는 없다).
- 문구는 웹 원문을 그대로 쓴다(빈 상태 안내, 라벨, placeholder). 한국어.
- 삭제는 항상 확인 대화상자(`AlertDialog`). 목록 정렬·필터 기본값은 웹과 동일.
- 세그먼트는 같은 옵션 재탭 시 해제(`Seg(allowClear = true)`), 단 카테고리 같은 필수 세그는 `allowClear = false`.
- 스크롤 화면은 `LazyColumn` 또는 `verticalScroll`, 하단 여백 96dp(FAB/하단 탭 고려).

## 웹 동작 이식 원칙
- 데이터 필드명·값(카테고리 '원두/카페/커핑', 패키지 standard/dripbag/sample, 커핑 유형 퍼블릭/홈커핑/수업 등)은 웹과 동일하게 유지한다(백업 호환).
- 웹의 버그로 확인된 항목(설계서 §2.3)은 고쳐서 구현한다.
- AI 기능·숨김 기능은 구현하지 않는다.
