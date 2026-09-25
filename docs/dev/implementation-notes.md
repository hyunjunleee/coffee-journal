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
- 텍스트 입력: `AppTextField`는 한글 조합이 끊기지 않도록 로컬 `TextFieldValue`를 유지한다(`ImeSafeText`). 숫자만 받기 등 입력 제한은 `onValueChange` 안에서 걸러 되돌리지 말고 `inputFilter = { … }`로 넘긴다(거절 시 null 반환, 바꾼 문자열 반환 시 커서 보정). 소유자 쪽에서 거르면 거절된 글자가 화면에 남는다.
- 날짜 입력: `DateField`(웹 `<input type="date">` 대응, "YYYY-MM-DD" 입출력, 빈 값 안내 `연도-월-일`).
- 글리프 버튼: ×·✕ 같은 기호만 있는 동작은 `GlyphButton(glyph, label, onClick)` — 탭 영역 48dp(`MinTouchTarget`), TalkBack 라벨.
- 키보드: 스크롤 컨테이너에 `Modifier.imeOverlapPadding()`(edge-to-edge라 창이 키보드만큼 줄지 않음; 하단 탭 높이는 제외).
- 폼 상태 보존: 카메라 앱이 앞에 있는 동안 프로세스가 죽어도 입력이 남도록 `SavedFormState`(SavedStateHandle + JSON)로 저장.
- 탭 간 요청: 다른 화면에서 원두 탭의 특정 서브뷰를 열 때는 `BeanViewRequests`(Koin single)에 요청을 넣고 탭을 전환한다(라우트 인자는 탭 전환 시 이전 값으로 복원되므로 쓰지 않음).

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
