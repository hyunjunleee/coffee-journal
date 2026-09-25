# Coffee Journal 모바일 앱 설계서 (Android 1차, iOS 확장 대비)

- 근거 문서: `docs/coffee-journal-site-analysis.md` (웹 원본 전수 분석)
- 결정 사항(사용자 확인): 1차 범위 = 웹 동등 전체(AI·숨김·죽은 기능 제외), 폰트 = 시스템 폰트, 테마 = 라이트 고정, 기술 스택 = 확장성·유지보수·안정성·데이터 관리·난이도를 고려해 아래와 같이 결정.

---

## 1. 기술 스택 결정

### 1.1 결정: Kotlin Multiplatform(KMP) + Compose Multiplatform(CMP), Android 우선 빌드

| 후보 | 확장(iOS) | 유지보수 | 안정성 | 데이터 관리 | 난이도 | 판단 |
|---|---|---|---|---|---|---|
| **KMP + CMP** | 도메인·데이터·UI를 공유 모듈에 두고 iOS 타깃만 추가 | 코드베이스 1개 | Android에서는 Jetpack Compose 그 자체(네이티브) | Room(KMP)+SQLite, 스키마 1벌 | 중 | **채택** |
| 네이티브 Android 단독 | iOS는 Swift로 전면 재작성 | 코드베이스 2개 | 최상 | 스키마 문서로만 공유 | 하 | 확장 요구와 상충 |
| Flutter | 동시 지원 | 1개 | 자체 렌더러, 플랫폼 관성 낮음 | sqflite/drift | 중 | 네이티브 감각·Kotlin 생태계 대비 이점 적음 |
| React Native | 동시 지원 | 1개 | 브리지 의존 | 서드파티 SQLite | 중 | 성능·안정성 열위 |

채택 이유: Android는 완전한 네이티브 Compose 앱으로 동작하고, 같은 코드가 iOS에서 Compose Multiplatform(UIKit 위 Skia)으로 실행된다. 로컬 DB(Room KMP)와 도메인 규칙(원두 이름 정규화, 달력 범위, 피크 계산, 저장 파이프라인 등)이 플랫폼 코드와 완전히 분리되므로 iOS 확장 시 플랫폼 어댑터(사진 선택·파일 저장)만 추가하면 된다.

### 1.2 버전 조합 (Maven 저장소에서 확인한 안정 버전)

| 구성 요소 | 버전 | 비고 |
|---|---|---|
| Kotlin | 2.2.21 | K2 컴파일러 |
| Compose Multiplatform | 1.9.3 (Material3 1.9.0) | Kotlin 2.2.x 계열 |
| Android Gradle Plugin / Gradle | 8.13.2 / 8.14.3 | 환경에 설치된 Gradle 사용 |
| KSP | 2.3.x (KSP2) | Room 컴파일러 |
| Room (KMP) + sqlite-bundled | 2.8.5 / 2.6.x | `room-gradle-plugin`으로 스키마 내보내기 |
| JetBrains Navigation Compose / Lifecycle ViewModel | 2.9.2 / 2.9.x | 멀티플랫폼 |
| kotlinx-datetime / serialization / coroutines | 0.7.x / 1.9.x / 1.10.x | 날짜·백업 JSON·비동기 |
| Koin | 4.2.x | DI (KMP) |
| Coil 3 | 3.6.x | 사진 표시 (KMP) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 | Android 8.0+ |

정확한 조합은 스캐폴드 빌드(`:androidApp:assembleDebug`)로 검증 후 `gradle/libs.versions.toml`에 고정한다.

---

## 2. 범위

### 2.1 포함 (웹 동등)
- **새로운 추출**: Coffee D-day, "마시는 중" 카드(보관함 개봉 원두/최근 원두, 잔여량, 100g 환산가, 예상 피크), 원두 보관함(추가·수정·개봉·정렬), 기록 목록(원두별 그룹, 검색, 전체/드립백·소량 필터, 최고 점수, 총정리(수동), 베스트 레시피 지정), 기록 카드 전체 항목, 기록 입력 폼 3모드(원두/카페/커핑)의 모든 필드, SCA 커핑 폼(10항목+강도 3항목), SCA 플레이버 휠 85용어 선택, 예상/실제 노트 칩과 "봉투 노트에서 추천", 추출 단계 로그(요약·레시피 대비 차이), 레시피 런처(WBrC 챔피언 9건, 카페 레시피 4건, 내 레시피), 봉투 사진 2장, 저장 파이프라인(형제 기록 전파, 자동 등록, 보관함 동기화).
- **커피 달력**: 월 그리드(카테고리 색 바, N잔 배지, D-day 마일스톤, 점, 원두 범위 띠), 날짜 패널, 오늘의 원두, 범례, 카페·커핑 목록(월별/전체, 커핑 유형 필터), 이 달 추출 기록, 로드맵(항목 추가·체크·편집·삭제)과 "커핑 리뷰 모음"(웹의 "먹어볼 원두"), 스터디(책·동영상), 클래스.
- **원두**: 커피 노트(예상/실제, 향미 분류 9종, 노트 클라우드, 조합 상세), 가공 방식(검색, 4+4, 허니 세부, 상세, 내 목록), 배전도, 품종(계보 탐색기 21종, 국가별), 블렌드(직접/카페/커핑), 로스터리(국내·해외 지도식 배치, 즐겨찾기), 생두 수입사, 커피 지도(45개국·60산지 SVG, 국가/산지 상세, 대륙별 목록)+농장(생산자), Competition Lots.
- **기타**: 장비 6종(사진 2장, 보유/궁금, 정렬).
- **백업/복원**: 웹 백업 JSON과 상호 호환(가져오기·내보내기) + 앱 확장 키. 로컬 파일로 저장/공유.

### 2.2 제외 (사용자 결정)
- AI 6종: 원두 배경 설명 생성, 원두 총정리 AI, 분쇄·추출 참고사항, 가루 사진 분석, 스크린샷 기록 불러오기, 조언 채팅. (관련 데이터 필드 `beanGuidance`, `photoFeedback`, `adviceChat` 등은 **백업 호환을 위해 보존만** 하고 UI에 노출하지 않는다.)
- 웹에서 숨겨졌거나 죽은 기능: 별점, 커핑용 SCA 그리드, 품종 참조표·품종 수동 추가 폼, 인라인 내 레시피 버튼, 화면 배율 컨트롤, 레거시 태그.

### 2.3 웹 대비 의도적 개선 (버그·UX)
1. 모든 삭제에 확인 대화상자, 책·동영상·클래스 편집 지원, 보관함 항목 삭제·개봉 취소 지원.
2. 원두 키 정규화(`coreBeanName`) 단일화 → 베스트 레시피·총정리 키 불일치 버그 제거.
3. SCA 기본값(Uniformity/Clean Cup/Sweetness = 10)은 유지하되, 나머지 7항목 중 하나라도 입력했을 때만 총점을 저장·표시.
4. 기록 삭제 시 모든 사진 파일 삭제. 백업에 동영상·보관함·설정 포함.
5. 달력 날짜 패널의 블렌드 항목 → 블렌드 뷰로 정상 이동.
6. 형제 기록 전파는 "빈 값만 채움"으로 통일(예상 노트·품종 덮어쓰기 제거), 보관함 동기화는 신규 저장 시에만 자동 개봉(수정 시에는 빈 값 보강만).
7. 저장은 트랜잭션 1회(웹의 2초 직렬 큐 제거).
8. 복원은 전부-또는-전무(atomic): 사진 파일을 먼저 쓰고 DB 변경 전체를 트랜잭션 1회로 적용, 실패 시 롤백하고 이번 복원이 쓴 파일만 지운다. 복원은 앱 전역 스코프에서 실행되어 화면을 떠나도 중간에 끊기지 않는다.
9. 기록(`entries`)은 "교체"를 골라도 항상 id 기준 병합한다(사용자 결정: 백업에 없는 기존 기록을 지우지 않음). "교체"는 나머지 컬렉션에만 적용된다.
10. 숫자 입력은 `Numbers.parse`로 통일: `NaN`·`Infinity`·`1e999` 같은 값은 웹의 `parseFloat(x) || 0` 가드처럼 "값 없음"으로 취급한다.
11. 날짜가 바뀌면(자정) D-day·오늘의 원두·달력 오늘 표시가 앱을 다시 열지 않아도 갱신된다(`Dates.todayFlow()`).
12. 입력 중 한글 조합(IME)이 끊기지 않도록 모든 텍스트 필드는 로컬 편집 상태를 유지하고, 입력 제한(숫자만 등)은 필드 자체의 `inputFilter`로 적용한다.
13. 가공 방식 국가별 분포 등 일부 목록은 국가명을 "한국어 (English)" 병기로 표시한다(웹은 입력 원문 그대로). 같은 나라가 한·영 두 줄로 갈라지지 않게 하기 위한 의도적 차이.
14. 커피 지도에서 "마셔본 나라"는 `#A87B58`(갈색)로 칠한다. 웹의 잉크색은 생산국 채움색과 대비가 약 1.4:1이라 구분되지 않았다.
15. 백업 파일 크기 상한: 읽기·파싱에 파일 크기의 수 배 메모리가 필요하므로, 앱 힙의 약 1/5(16–256MB로 제한)보다 큰 파일은 메모리 부족으로 중간에 죽는 대신 안내 문구와 함께 거절한다.
16. 원두 이름 입력 후 자동 채움(첫 등록 기록의 봉투 정보)은 빈 칸만 채운다. 웹은 이미 입력한 값까지 덮어써 사용자가 친 내용이 사라졌다(#6과 같은 원칙).
17. 웹 데이터 이전은 웹처럼 "한 번만" 플래그로 막을 수 없어(백업마다 이전 전·후 데이터가 섞일 수 있음) 복원 때마다 멱등하게 적용한다. 그래서 1–5점 → 6–10점 변환은 7개 품질 항목에만 적용하고, 0–10점(2점 단위)인 Uniformity·Clean Cup·Sweetness의 2·4점은 실제 점수로 보고 그대로 둔다.
18. 노트 칩 입력은 같은 노트를 중복으로 넣지 않는다(웹 "내가 느낀 노트"는 중복 허용).

---

## 3. 모듈·아키텍처

```
coffee-journal/
├─ gradle/libs.versions.toml
├─ settings.gradle.kts, build.gradle.kts, gradle.properties
├─ shared/                       # KMP 라이브러리 (android + ios 타깃, iOS는 macOS 호스트에서만 활성화)
│  └─ src/
│     ├─ commonMain/kotlin/com/coffeejournal/
│     │  ├─ domain/model/        # Entry, CuppingBean, PantryItem, MiscItem, Book, Video, CoffeeClass, Blend, MyRecipe, RoadmapPhase …
│     │  ├─ domain/rules/        # BeanNames, BeanRecords, CalendarRanges, PantryRules, NoteCanon, CountryLookup, RoastFamily, ScaScoring, RecipeSteps, RegionHierarchy
│     │  ├─ domain/reference/    # FlavorWheel, NoteCategories, Processes, Varieties, CoffeeCountries, WorldMap, Champions, CafeRecipes, RoasteryMapPoints
│     │  ├─ data/db/             # Room: AppDatabase, entities, DAOs, converters, migrations
│     │  ├─ data/repo/           # EntryRepository, PantryRepository, MiscRepository, StudyRepository, BlendRepository, RoadmapRepository, SettingsRepository
│     │  ├─ data/backup/         # BackupCodec (웹 호환 JSON), BackupService
│     │  ├─ data/photo/          # PhotoStore(expect), ImagePicker(expect), ImageResizer(expect)
│     │  ├─ ui/theme/            # 색·타이포·형태 토큰, 컴포넌트
│     │  ├─ ui/nav/              # AppNav, Routes(type-safe), BottomBar
│     │  ├─ ui/extract/ ui/form/ ui/calendar/ ui/bean/ ui/misc/ ui/backup/  # 화면 + ViewModel
│     │  └─ di/                  # Koin 모듈
│     ├─ androidMain/kotlin/     # Room 드라이버/DB 빌더, PhotoStore·ImagePicker·ImageResizer·BackupFileIo 실제 구현
│     ├─ iosMain/kotlin/         # 동일 expect의 iOS 실제 구현 자리(1차: 파일 저장·리사이즈 스텁, 선택기 TODO)
│     └─ commonTest/kotlin/      # 도메인 규칙·백업 코덱·DB 마이그레이션 테스트
├─ androidApp/                   # Android 애플리케이션 (MainActivity → App())
└─ iosApp/                       # SwiftUI 진입점 스켈레톤(이 환경에서는 미빌드)
```

- 패턴: 단방향 데이터 흐름(UDF). 화면당 `ViewModel`(공유 모듈, `lifecycle-viewmodel`)이 `StateFlow<UiState>`를 노출하고 Repository(Flow)를 구독.
- 데이터 접근은 Repository만 통과. 도메인 규칙은 순수 Kotlin 함수(테스트 대상).
- DI: Koin. `initKoin(platformModule)`를 각 플랫폼 진입점에서 호출.
- iOS 타깃 게이팅: `gradle.properties`의 `coffeejournal.enableIos`가 true이고 호스트가 macOS일 때만 `iosArm64/iosSimulatorArm64` 타깃을 선언 → Linux CI/이 환경에서는 Android만 빌드.

### 3.1 플랫폼 경계(expect/actual)
| 인터페이스 | Android | iOS(추후) |
|---|---|---|
| `DatabaseBuilder` | `Room.databaseBuilder(context, path)` + BundledSQLiteDriver | `NSDocumentDirectory` 경로 + BundledSQLiteDriver |
| `PhotoStore` | `filesDir/photos/*.jpg` | Documents/photos |
| `ImagePicker` | Photo Picker(`PickVisualMedia`) + 카메라(TakePicture) | PHPickerViewController / UIImagePickerController |
| `ImageResizer` | Bitmap 디코드·EXIF 회전·긴 변 1280px·JPEG 82 | UIImage |
| `BackupFileIo` | SAF `CreateDocument`/`OpenDocument` + 공유 시트 | UIDocumentPicker / ShareSheet |
| `Clock/Locale` | kotlinx-datetime 공통 | 공통 |

---

## 4. 데이터 설계 (Room, SQLite)

식별자는 웹과 동일한 문자열 id(base36 시각+랜덤)를 유지해 백업이 왕복 가능하게 한다. 목록형 부속 데이터는 JSON 컬럼(kotlinx-serialization TypeConverter)으로 저장하되, 통계 질의가 필요한 커핑 원두는 별도 테이블로 정규화한다.

| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| `entries` | id PK, created_at, category('원두'/'카페'/'커핑'), bean_mode, name, country, region, altitude, variety, farm_producer, roastery, selection, washing_station, process, process_other, package_type, moisture, density, score, arrival, roast_date, roaster_desc, roast, bag_weight, price, cafe_name, expected_notes, actual_notes, dripper, filter, dose, water, temp, grind, water_type, time, notes, cupping_type, cupping_place, steps(JSON), recipe_ref(JSON), blend_components(JSON), attributes(JSON), attribute_notes(JSON), tags(JSON), legacy_extra(JSON: beanGuidance·photoFeedback·adviceChat·consultation·practice·noteChat 보존) | 인덱스: created_at, category, lower(name) |
| `cupping_beans` | id PK, entry_id FK(CASCADE), position, name, country, region, roastery, farm_producer, altitude, variety, price, rank, process, roast, expected_notes, actual_notes, evaluation(JSON), evaluation_scores(JSON), memo, bean_mode, blend_components_text | |
| (사진) | `entries.bag_photos`(JSON 파일명 목록, 최대 2) · `entries.grounds_photo` | 구현 시 별도 테이블 대신 컬럼으로 단순화. 파일은 PhotoStore |
| `pantry_items` | id, name, roastery, package_type, weight, price, roast_level, roast_date, purchase_date, peak_start, peak_end, expected_notes, notes, status('unopened'/'opened'), opened_at, created_at, source_entry_id | |
| `misc_items` | id, type, name, notes, since, status, scope, location, favorite, photos(JSON 파일명 목록), created_at | type: dripper, filter, kettle, thermometer, scale, water, source, selection, process, variety, farm |
| `books` | id, created_at, title, author, status, start_date, end_date, rating, notes | |
| `videos` | id, created_at, title, channel, url, notes | |
| `classes` | id, created_at, title, class_type, date, start_date, end_date, notes | |
| `blends` | id, name, date, beans(JSON), notes, created_at | |
| `my_recipes` | id, name, from_entry_id, bean_name, rating, dose, water, temp, dripper, filter, grind, time, steps(JSON), created_at | |
| `roadmap_phases` | id, position, title, range_label, day_start, day_end, items(JSON) | 기본 1단계 시드 |
| `bean_summaries` | bean_key PK, text, generated_at | 수동 총정리 |
| `best_recipes` | bean_key PK, entry_id | |
| `settings` | key PK, value | `brew-start-date`, 마지막 필터 등 |

- 마이그레이션: `room-gradle-plugin` 스키마 export(`shared/schemas`) + `autoMigrations`.
- 커핑 원두 이외의 목록형 필드(steps, recipeRef, blendComponents, attributes, attributeNotes, tags, 사진 파일명)와 웹 전용 필드(`legacy_extra`: beanGuidance·photoFeedback·adviceChat 등)는 kotlinx-serialization JSON 컬럼으로 저장한다.
- 사진: `PhotoStore`가 `photos/<uuid>.jpg`로 저장, DB에는 파일명만. 삭제 시 파일도 삭제.
- 성능 가정: 개인 저널(수천 건 이하) → 기록 전체를 메모리에 로드해 웹과 같은 방식으로 파생 통계를 계산(Flow combine).

### 4.1 백업 JSON (웹 호환)
```
{ "exportedAt": ISO,
  "data": { "entries": [...웹 스키마 그대로, cuppingBeanDetails 포함...], "miscItems", "blends", "classes", "roadmapData", "myRecipes", "books", "beanSummaries", "bestRecipes",
            "videos": [...], "pantryItems": [...], "settings": {...} },          // 앱 확장 키
  "rawData": { "ddayStart": "YYYY-MM-DD" },
  "photos": { "bag-photo:<id>": "[dataURL,...]", "journal-photo:<id>": "dataURL" },
  "app": { "name": "coffee-journal-mobile", "schema": 1 } }
```
- 가져오기: 웹 파일(확장 키 없음)도 그대로 수용하며, 웹이 앱 시작 시 수행하던 데이터 이전(`WebMigrations`: SCA 1–5점 척도 → 6–10점, 옛 `aroma` → `aromaIntensity`, 0–15 강도 → 1–5 0.5단위)을 복원 시 동일하게 적용하고, 손상된 항목은 건너뛰고 항목별 실패 수로 보고한다. `entries`는 모드와 무관하게 항상 id 기준 upsert(병합), 나머지 컬렉션은 "교체"/"병합" 중 선택(기본 병합). 사진 data URL은 파일로 복원(이미 규격(긴 변 1280px 이하·정방향 JPEG) 안인 사진은 재인코딩 없이 그대로 저장해 백업↔복원 왕복에도 화질이 떨어지지 않는다).
- 원자성: 사진 파일 → DB 트랜잭션 1회 → 커밋 후 참조가 끊긴 옛 사진 파일 삭제. 도중 실패 시 롤백 + 이번 복원이 쓴 파일 삭제로 복원 전 상태를 그대로 유지한다. 결과는 웹과 같은 문구의 항목별 줄(`entries: ✓ 3개 복원됨` 등)로 보고한다.
- 파일 크기 상한: `BackupFileLimits.maxFileBytes(힙)` = 힙/5, 16–256MB로 제한. 일반 기기(힙 256–512MB)에서 50–100MB 정도가 한계이며, 더 큰 웹 백업을 받으려면 `android:largeHeap` 사용 여부를 결정해야 한다(현재 미사용).
- 기기 백업(Android Auto Backup / 기기 간 전송)은 DB·WAL·사진 폴더만 포함한다. 클라우드 백업은 앱당 25MB 할당량을 넘으면 Android가 통째로 건너뛰므로, 앱 내 JSON 백업이 기본 이전 수단이다.
- 내보내기: 사진을 data URL로 포함(웹과 동일)하되 용량 안내 표시. 파일명 `커피일지-백업-YYYY-MM-DD.json`.

---

## 5. 화면 설계

### 5.1 내비게이션
- 하단 탭 4개: 새로운 추출 · 커피 달력 · 원두 · 기타 (웹 메인 탭 순서 유지, 탭별 백스택 보존).
- 전체 화면 라우트: `RecordForm(entryId?, mode)`, `EntryDetail(id)`, `PantryEditor(id?)`, `BlendForm(id?)`, `BookForm(id?)`, `VideoForm(id?)`, `ClassForm(id?)`, `MiscForm(type, id?)`, `FlatItemForm(type, id?)`(로스터리·수입사·농장·가공), `RecipeLauncher(kind)`, `BackupRestore`, `CountryDetail(en)`, `NoteDetail(key)`, `VarietyDetail(key)`, `ProcessDetail(name)`.
- 웹의 "펼침 카드"는 모바일에서 `EntryDetail` 화면 + 목록의 접이식 요약으로 대체.

### 5.2 탭별 화면
**새로운 추출(Home)**: 상단 헤더 `coffee_journal / 2026`·`N entries` → D-day 알약(미설정 시 날짜 입력 행) → 마시는 중 카드(가로 스크롤) → 액션 행(새 기록, 원두 보관함, 백업) → 필터 칩(전체 / 드립백·소량 → 전체·드립백·소량) → 검색창 → 원두 그룹 목록(헤더: 썸네일·이름·로스터리/수입사/농장·기간·N개 기록·최고 점수; 펼치면 총정리·베스트 레시피·기록 요약 행). FAB `+`.

**기록 폼**: 상단 세그(원두/카페/커핑, 진입 모드에 따라 고정) → 섹션 카드 순서: 레시피로 시작(원두) · 기본(날짜시간, 원두 형태) · 원두 정보(이름 자동완성·가격·로스터리·수입사·국가·지역·농장·워싱스테이션·고도·품종·수분율·밀도·CoE·가공(세그+기타+세부)·로스팅·총량·입고·로스팅일·예상 노트 칩·가게 설명·봉투 사진 2) · 레시피(드리퍼·필터·분쇄도·원두량·물량·온도·총시간(자동)·물) · 추출 예시(읽기 전용) · 단계 로그(행 편집, 요약, 차이) · 테이스팅(SCA 슬라이더 13행+메모, 플레이버 휠 시트, 내가 느낀 노트 칩+추천 칩) · 메모 · 저장/취소(하단 고정). 커핑 모드는 유형·장소·원두 카드 목록(카드마다 웹과 동일 필드)·전체 경험. 카페 모드는 `.cafe-hide` 대응 필드 숨김.

**커피 달력**: 상단 칩(전체/스터디/클래스/커핑/카페/원두) → 월 헤더(‹ 오늘 ›) → 그리드 → 날짜 패널(바텀시트) → Today 원두 → 범례 → 필터별 하단 섹션(카페·커핑 목록 / 이 달 추출 기록 / 커피 공부(로드맵·커핑 리뷰 모음)). 스터디·클래스는 같은 탭의 별도 뷰.

**원두**: 상단 가로 스크롤 서브탭 9개(기본 커피 지도). 각 뷰는 웹 구성을 세로 스크롤 화면으로 재배치. 커피 지도는 Canvas에 SVG 폴리곤(M/L/Z만 사용, 175개국)을 그리고 탭 좌표를 point-in-polygon으로 판정, 핀치 줌 지원. 로스터리 지도는 웹처럼 % 좌표 핀 배치.

**기타(장비)**: 유형 칩 → 정렬 토글 → 카드(사진·이름·시작일·메모) → FAB 추가.

### 5.3 디자인 시스템 (웹 "아카이브 라이트" 테마 이식)
- 색: `bg #F5F4EF`, `surface #FFFFFF`, `surfaceRaised #ECEBE5`, `line #C8C6BD`, `text #191916`, `textMuted #5D5B54`, `textFaint #858177`, `accent(ink) #20201D`, `accentSoft #20201D12`, `bad #9D3026`, `good #4D684D`, `cupping #6E5F8B`, `cafe #8B5C35`, `book #5D6F79`, 범위 띠 6색 `#B58968 #8A6A52 #C9A47E #9C7355 #D9BD9C #6B543E`.
- 형태: 모서리 반경 0, 0.5dp 헤어라인, 헤더 하단 2dp 잉크 실선, 좌측 세로 가이드선, 그림자 대신 오프셋 실선(8dp 8dp 0 #20201D24).
- 타이포: 시스템 산세리프(제목 600, 본문 400) + `FontFamily.Monospace`(섹션 라벨 10.5sp·자간 0.05em, 숫자·카운트·날짜, 태그라인 `[ personal coffee archive ]`). 헤더 타이틀은 소문자 모노 감성 `coffee_journal / 2026`.
- 컴포넌트: `SectionLabel`, `SubTabs`(선택 시 잉크 채움), `Seg`(사각 세그먼트, 재탭 해제), `ChipInput`(노트 칩 + 입력), `SliderRow`(SCA), `HairlineCard`, `PrimaryButton`(잉크 배경)/`GhostButton`(헤어라인), `CatDot`(카테고리 색점), `EmptyNote`(점선 박스 안내), `KeyValueRow`, `StepRow`, `PhotoSlot`.
- 한국어 문구는 웹 원문을 그대로 재사용(안내문·빈 상태 문구 포함).
- 사용자 결정으로 유지한 앱 고유 스타일(웹과 다름): 홈 목록은 괘선 원장 행 대신 흰 박스 카드, 선택된 서브탭은 밑줄 대신 잉크 채움, D-day 알약·마일스톤 배지는 현재 배색, SCA 슬라이더는 캡슐형, 섹션 라벨은 현재 톤(괘선 없음). 폰트는 손글씨 없이 시스템 폰트, 테마는 라이트 고정.
- 터치 영역: 글리프만 있는 동작(칩 ×, 사진 ✕ 등)은 `GlyphButton`으로 레이아웃은 그대로 두고 탭 영역만 48dp로 넓히며, TalkBack에는 "오렌지 삭제"처럼 동작 이름을 읽힌다.

---

## 6. 핵심 도메인 규칙 (웹 로직 이식 명세)
| 규칙 | 정의 |
|---|---|
| `coreBeanName(name)` | 끝 괄호 제거 → trim → 소문자. 그룹·보관함·베스트·총정리의 유일 키 |
| `parseNameParens(name)` | 끝 괄호 `(로스터리, 출처, 농장, 생산자)` 분해(레거시 입력 힌트용) |
| `entryPackageType` | sample > dripbag(or legacy isDripBag) > standard |
| `getAllBeanRecords()` | 원두·카페 기록 + 커핑 원두를 개별 레코드로 펼침(`parentEntryId`) |
| 달력 범위 | 핵심 이름별 시간순, 직전 기록과 10일 초과 간격이면 새 범위, 6색 순환 |
| D-day | `floor((오늘0시 − 시작일)/1일)+1`, 100배수 강조·30배수 표시, 달력은 10일 단위 배지 |
| 보관함 피크 | 수동 피크 우선, 없으면 로스팅일 + (라이트 14~45 / 미디엄 7~30 / 다크 4~21일); `미디엄 라이트`는 라이트로 분류(웹 오분류 수정) |
| 잔여량 | 봉투 용량(기본 100g) − 같은 원두 dose 합 − 블렌드 사용 g |
| SCA 총점 | 10항목 합(강도 제외), 소수 2자리; 7개 채점 항목 중 입력이 있을 때만 유효 |
| 배전 계열 | dark → light → medium 순 정규식(`미디엄 다크`=dark) |
| 노트 정규화 | 끝 문장부호 제거, 공백 제거 키로 동의어 45개 매핑 |
| 국가 판별 | 동의어 10개 치환 후 45개국 영문/한글 부분 일치; 이름 폴백 |
| 지역 계층 | 쉼표 분리, 첫 조각은 산지 동의어 25개 정규화 |
| 단계 요약 | 물량 합, 마지막 행 시각+대기 = 총 시간, 푸어 횟수; 레시피와 물량 2g/시간 10초 초과 차이 경고, 단계별 물량·대기 정확 비교 |
| 저장 파이프라인 | 검증 → entry upsert → 커핑 원두 교체 → 사진 저장/삭제 → 형제 기록 빈 값 보강 → misc 자동 등록(로스터리·가공 대분류·농장·수입사·품종) → 보관함 동기화 → 화면 이동 |

---

## 7. 내장 참조 데이터 (공유 모듈 상수, 웹에서 그대로 이식)
플레이버 휠 9/85, 향미 분류 9/43, 노트 동의어 45, 가공 4+4(허니 세부 5), 품종 참조 26·계보 21·표시명·한국어명 39, 커피 생산국 45/산지 60(+좌표), 지역 동의어 25, 국가 동의어 10, 대륙 매핑, 세계지도 SVG 175 폴리곤(viewBox 138 100 788 283, 회귀선 y 204.7/330.0), 로스터리 배치 좌표 국내 11/해외 15, WBrC 챔피언 9, 카페 레시피 4(단계 포함), 일반 단계 예시 6, 배전도 5단계, 장비 유형 6, 점수 티어 4.

---

## 8. 테스트·품질
- `commonTest`: 도메인 규칙(이름 정규화, 범위 계산, 피크, SCA, 단계 요약·차이, 노트 정규화, 국가 판별), 백업 코덱 왕복(웹 샘플 JSON 포함), 저장 파이프라인.
- Android 계측: Room 마이그레이션·DAO 스모크(선택).
- 빌드 게이트: `:shared:testDebugUnitTest` + `:androidApp:testDebugUnitTest` + `:androidApp:assembleDebug`.
- 화면 검증: 에뮬레이터 없이 Robolectric + Roborazzi로 실제 Compose 화면을 JVM에서 렌더해 PNG로 남긴다(`./gradlew :androidApp:recordRoborazziDebug` → `androidApp/screenshots/`). 테스트는 인메모리 Room(프레임워크 SQLite 드라이버)과 샘플 데이터(`SampleData`)를 주입한다.
- 흐름 테스트: 같은 환경에서 실제 `App()`을 띄워 탭·입력으로 사용자 흐름 전체를 수행하고, 화면 문구와 저장된 데이터를 웹 원본 핸들러 기준으로 함께 검증한다(백업 왕복, 원자적 복원, 한글 IME 조합, 자정 전환, 연속 탭 경합, 저장 중 뒤로 가기 포함).
- 렌더 매트릭스: 모든 라우트를 기본·320dp 폭·글자 1.3/2.0배로 렌더해 줄바꿈·잘림·겹침을 확인한다(`ScreenshotMatrixTest`).
- 현재 규모: `shared` 단위 179개, `androidApp` 흐름·스크린샷 232개, 모두 통과(건너뜀 0).
- 접근성: 최소 터치 48dp, 대비 4.5:1(잉크/아이보리), 콘텐츠 설명, 토글·펼침 상태 노출.

## 9. iOS 확장 경로 (2차)
1. macOS에서 `coffeejournal.enableIos=true`로 iOS 타깃 활성화 → `shared` 프레임워크 생성.
2. `iosMain` actual 구현(PHPicker, UIImage 리사이즈, Documents 경로, UIDocumentPicker/ShareSheet).
3. `iosApp/` SwiftUI 앱에서 `MainViewController()` 호스팅. 하단 탭·내비게이션은 Compose 공용 코드 그대로.

## 10. 구현 마일스톤
| 단계 | 산출물 | 검증 |
|---|---|---|
| M0 | Gradle 스캐폴드, 버전 고정, 빈 앱 빌드 | assembleDebug |
| M1 | 디자인 토큰·컴포넌트, 내비게이션, Room 스키마, 참조 데이터 | 단위 테스트 |
| M2 | 기록 폼 3모드, 저장 파이프라인, 홈 목록·카드·검색·필터, 보관함, D-day | 테스트+빌드 |
| M3 | 커피 달력(그리드·범위·패널·목록), 스터디·클래스·로드맵 | 빌드 |
| M4 | 원두 탭 9뷰(지도 포함), 장비 탭 | 빌드 |
| M5 | 백업/복원(웹 호환), 사진 파이프라인 | 코덱 테스트 |
| M6 | 마무리(빈 상태·접근성·성능), 문서 갱신 | 전체 테스트 |

진행 상황: M0–M6 완료. 이후 웹 원본 대비 전수 감사(확정 결함 105건)를 거쳐, 사용자 스타일 결정으로 제외한 5건을 뺀 100건을 수정하고 흐름 테스트로 고정했다. iOS(§9)는 공유 코드·`iosMain` 구현까지 준비되어 있고 macOS에서의 빌드·실행 확인이 남았다.
