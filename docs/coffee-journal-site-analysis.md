# Coffee Journal — Empty Template 사이트 전수 분석

- 대상: https://coffee-journal-empty-template.divine-pear-1472.chatgpt.site/
- 분석일: 2026-09-25
- 방법: 원본 HTML 전량 수집 → CSS/JS/마크업 분리 → 8,374행 JS 전 구간 정독(7개 구간 병렬 분석) → Chromium(Playwright) 실제 렌더링·클릭·다운로드 검증
- 스크린샷: `docs/screenshots/` (홈, 달력, 지도, 품종, 노트, 기타, 보관함, 기록 폼, 커핑 폼, 백업)

---

## 0. 한눈에 보기

| 항목 | 값 |
|---|---|
| 페이지 | 단일 HTML 1개 (모든 경로가 같은 HTML 반환, 6.91 MB) |
| 실제 마크업/코드 | HTML 본문 56 KB, CSS 105 KB(2,084행), JS 532 KB(8,374행 + 보조 3개) |
| 6.2 MB의 정체 | `Ownglyph Yangho` 손글씨 웹폰트 1개가 base64 WOFF2로 인라인 |
| 외부 요청 | `heic2any` 스크립트 1개(cdnjs) + Cloudflare 챌린지 스크립트. 그 외 **모든 데이터는 브라우저 localStorage** |
| 서버 API | 없음. 백엔드·계정·동기화 없음 |
| AI 호출 | `https://api.anthropic.com/v1/messages` 직접 호출 6곳(`claude-sonnet-4-6`), **인증 헤더 없음 → 현 호스팅에서 전부 실패** |
| 메인 탭 | 새로운 추출 / 커피 달력 / 원두 / 기타 (4개) + 원두 탭 하위 9개 뷰 |
| 저장소 키 | 네임스페이스 `coffee-journal-template-clean-v2:` 아래 고정 키 14종 + 접두사 키 4종 |
| 최상위 함수 | 284개, `addEventListener` 213곳, 상수/전역 138개 |
| 언어/테마 | 한국어, 라이트 "아카이브" 테마(다크 변수는 정의 후 덮어써짐), 손글씨 폰트 전면 강제 |

**결론 요약**: 원래 Claude 아티팩트 런타임(저장소 프록시·API 키 자동 주입 환경)용으로 만들어진 개인 커피 저널 앱을, 데이터를 비우고 `localStorage` 래퍼로 갈아 끼워 정적 사이트로 내보낸 "빈 템플릿"이다. 기록·달력·원두 통계·장비·보관함·백업 등 로컬 기능은 정상 동작하지만, 6종의 AI 기능은 호출 코드만 남아 있고 동작하지 않는다. 또한 CSS로 숨겨진 기능(기록 불러오기, 전체 백업/복원, 가루 사진 분석, 별점, 조언 채팅 등)이 상당수 존재한다.

---

## 1. 호스팅·전송 계층

| 확인 항목 | 결과 |
|---|---|
| 서버 | Cloudflare 앞단 (`server: cloudflare`, `cf-cache-status: HIT`), 쿠키 `__cf_bm` (Domain `chatgpt.site`) |
| 라우팅 | `/robots.txt`, `/sitemap.xml`, `/api/`, `/manifest.json`, `/favicon.ico`, 임의 경로 모두 **200 + 동일 HTML** (SPA catch-all, 별도 정적 자산 없음) |
| 캐시 | `cache-control: public, max-age=0, must-revalidate` |
| 문서 | `<html lang="ko">`, `<title>Coffee Journal — Empty Template</title>`, description "개인 기록 없이 시작하는 커피 저널 템플릿", 파비콘은 인라인 SVG data URI(커피콩) |
| HTML 주석 | "빈 템플릿: 원본 계정 데이터와 분리되며 새 기록은 이 브라우저에만 저장됩니다." |
| 하단 삽입 스크립트 | Cloudflare `challenge-platform/scripts/jsd/main.js` 로더(숨은 1×1 iframe) — 앱 기능과 무관 |

---

## 2. 페이지 구성(단일 HTML 해부)

문서 순서대로:

1. `<head>`: 메타, 타이틀, 인라인 SVG 파비콘.
2. `<script src="https://cdnjs.cloudflare.com/ajax/libs/heic2any/0.0.1/index.min.js">` — iPhone HEIC 사진을 JPEG로 바꾸는 용도(없어도 앱은 동작).
3. `<style>` 2,084행 — `@font-face 'Ownglyph Yangho'`(6.2 MB base64) + 전체 스타일. 다크 팔레트(`:root` 11~25행)를 선언한 뒤 1,792행 이후 "Archive light theme" 블록이 같은 변수를 라이트 값으로 덮어씀. 마지막에 `body, body *:not(svg):not(path) { font-family:'Ownglyph Yangho' !important }`로 손글씨 폰트 전면 강제.
4. `<body>` 마크업 928행(아래 4장 화면 트리).
5. `<script>` ① UI 스케일 컨트롤(23행) ② `PRELOADED_ENTRIES=[]` + `templateStorage` localStorage 래퍼(28행) ③ 앱 본체(8,374행) ④ Cloudflare 챌린지.

폰트: CSS는 `Fraunces`, `Noto Serif KR`, `IBM Plex Mono`, `IBM Plex Sans KR`, `Courier New`, `Nanum Gothic Coding`을 참조하지만 **외부 폰트 로드가 없고** 마지막 `!important` 규칙이 전부 Ownglyph Yangho로 덮으므로, 실제로는 SVG를 제외한 모든 텍스트가 손글씨체로 렌더된다(스크린샷으로 확인).

---

## 3. 실행 아키텍처

### 3.1 저장소 (`templateStorage`, script ②)

- 모든 키에 접두사 `coffee-journal-template-clean-v2:`를 붙여 `localStorage`에 저장. `get`은 `{key,value}` 또는 `null`, `list(prefix)`는 `{keys:[...]}` — 원래 원격 저장소 API의 시그니처를 그대로 흉내 낸 async 래퍼.
- 별도 네임스페이스 밖 키: `coffee-journal-ui-scale`(화면 배율).

### 3.2 저장소 키 전수

| 키 | 값 형식 | 용도 |
|---|---|---|
| `entry:<id>` | JSON(entry 1건) | 기록 1건당 1키(현행 방식) |
| `coffee-journal-entries` | JSON 배열 | 레거시 통짜 저장. **읽기 전용**, `persist()`는 호출처 없음 |
| `journal-photo:<entryId>` | data URL 문자열 | 추출 후 가루 사진 |
| `bag-photo:<entryId>` | JSON 배열(최대 2장) 또는 레거시 단일 문자열 | 원두 봉투 사진 |
| `bean-blurb:<coreBeanName>` | `{text, generatedAt, fromFarm?}` | 마시는 중 원두의 설명 캐시 |
| `bean-summaries` | `{[coreBeanName]: {text, generatedAt}}` | 원두 총정리(AI/수동) |
| `bean-best-recipes` | `{[beanName]: entryId}` | 원두별 베스트 레시피 |
| `brew-start-date` | 순수 문자열 `YYYY-MM-DD` | Coffee D-day 시작일 |
| `misc-items` | JSON 배열 | 장비 + 로스터리/수입사/가공/품종/농장 항목(사진 dataURL 인라인 포함) |
| `coffee-books` / `coffee-study-videos` / `coffee-classes` | JSON 배열 | 스터디·클래스 |
| `bean-pantry-items` | JSON 배열 | 원두 보관함 |
| `my-recipes` | JSON 배열 | 내 레시피 |
| `coffee-blends` | JSON 배열 | 직접 만든 블렌드 |
| `roadmap-data` | JSON 배열(phase) | 로드맵 |
| `attr-scale-migrated-v1`, `attr-aroma-split-v1`, `sca-intensity-scale-0-15-v1`, `sca-intensity-scale-1-5-half-v2` | `'1'` | 마이그레이션 완료 플래그 |

### 3.3 안정성 계층(원격 저장소 시절의 유물, 지금도 작동)

| 함수 | 파라미터 | 의미 |
|---|---|---|
| `enqueueStorageWrite` | `MIN_GAP_MS = 2000` | **모든 쓰기를 단일 큐로 직렬화하고 호출 간 최소 2초 간격** 강제 |
| `storageSetWithRetry` | 최대 60회, 대기 `min(1500·n, 10000)`ms | "rate limit" 문자열 오류에만 재시도(최악 8~9분) |
| `getWithTimeout` | 8초 타임아웃, 최대 20회 | 읽기 |
| `listWithRetry` | 8초, 최대 20회 | `entry:` 접두사 나열 |
| `getQuick` | 3초, 재시도 없음 | 백업 순회용 |
| `*LoadFailed` 플래그 9종 | — | 읽기 실패 시 해당 키 저장을 alert로 차단(빈 데이터로 덮어쓰기 방지) |

localStorage는 rate limit을 던지지 않으므로 재시도 경로는 실행되지 않지만, **2초 간격 직렬화는 그대로 적용**된다. 실측: 첫 로드 후 3초 시점에 마이그레이션 플래그 2개, 12초 이후에야 4개가 기록됨. 기록 1건 저장(entry + 봉투사진 + misc 자동등록 + 보관함 동기화)도 각 2초씩 순차 지연된다. 화면 렌더는 이 쓰기를 기다리지 않는다(`await` 없음).

### 3.4 `loadEntries` 마이그레이션 순서

1. 레거시 통짜 키 읽기 → 2. `entry:*` 나열 후 병렬 읽기 → 3. id 기준 병합(개별 키 우선) → 4. "유어홈" 레시피 수치 보정(18g/300g·2차 140g → 15g/240g·190g) → 5. `farm`/`producer` → `farmProducer` 병합 → 6. SCA 1~5점 → 6~10점 변환(`attr-scale-migrated-v1`) → 7. `aroma` → `aromaIntensity` 이동(`attr-aroma-split-v1`) → 8. 강도 1~5 → 0~15(`…0-15-v1`) → 9. 0~15 → 1~5(0.5 단위)(`…1-5-half-v2`) → 10. 변경분 백그라운드 개별 저장 후 `render()`.
(신규 설치에서는 8→9가 왕복 변환이며, 7의 `aromaIntensity`는 현행 키 `fragranceIntensity`와 달라 고아 필드가 된다.)

### 3.5 부트스트랩(파일 끝)

동기: `buildAttrGrid()` → `populateRegionDatalist()` → `renderRecipeRefSteps()`.
비동기: `await Promise.all([loadEntries, loadMiscItems, loadBeanPantry])` → `render()` → (대기 없이) `loadDday`, `loadBlends`, `loadRoadmapData`, `loadMyRecipes`, `loadBeanSummaries`, `loadBestRecipes` 병렬 → 그 뒤 `loadBooks`, `loadVideos`, `loadClasses` → misc 항목 정규화·백필(농장 이름 보정, 품종/가공 대분류 통합, 기록의 로스터리·수입사·품종·농장 자동 등록) → `render()`.
초기 탭 `extract`, 원두 탭 초기 뷰 `map`. 전역 이벤트 위임: `document` click 2곳(총정리 버튼, 조언 채팅), `document` keydown 1곳, `window` beforeunload(저장 중 이탈 경고).

### 3.6 주요 전역 상태

`entries`, `beanSummaries`, `bestRecipes`, `beanPantryItems`, `miscItems`, `books`, `videos`, `classes`, `myRecipes`, `blends`, `roadmapData`, `ddayStartDateStr`, 폼 상태(`editingId`, `rating`, `attrValues`, `attrNoteValues`, `photoData`, `photoFeedback`, `bagPhotoData[2]`, `editKeepPhoto`, `editKeepBagPhoto[2]`, `appliedRecipeRef`, `beanGuidanceResult`, `noteImportImages`), 뷰 상태(`currentTab`, `currentBeanView`, `calDate`, `calFilter`, `cuppingTypeFilter`, `cafeCuppingListScope`, `extractFilterMode`, `smallPackFilterMode`, `extractBeanSearchQuery`, `selectedRoastFamily`, `roastSortMode`, `darkRoastFilter`, `sourceScopeFilter`, `blendViewFilter`, `classViewFilter`, `miscCurrentType`, `miscAllSortMode`, `beanPantrySortMode`, `noteCloudMode`, `noteCloudSortMode`, `selectedNoteKey`, `selectedProcess*`, `selectedVariety*`, `roadmapSidebarView`, `roadmapOpenPhase`), 캐시(`weeklyBeanCache`, `lastWeeklyBeanKey`, `mapCountryStats`), `pendingSaveCount`, `adviceChatLoading`.

---

## 4. 화면 트리 (모든 인스턴스)

```
header.top  "coffee_journal / 2026" · "[ personal coffee archive ]" · "#entry-count (N entries)"
#main-tabs  [새로운 추출] [커피 달력] [원두] [기타]
├─ #tab-extract
│  ├─ #dday-bar            Coffee D-day 설정/표시
│  ├─ #weekly-bean         "마시는 중" 카드(보관함 개봉 원두 또는 최근 원두)
│  ├─ 버튼: #toggle-form(+ 새 기록 추가) #bean-pantry-btn(원두 보관함)
│  │        #champions-btn #cafe-btn #my-recipes-btn → JS가 폼 내부 #form-recipe-launchers로 이동
│  │        #import-note-btn #backup-export-btn #backup-restore-btn → CSS display:none!important (숨김)
│  ├─ #backup-result-panel / #champions-panel / #cafe-panel / #my-recipes-panel
│  ├─ #bean-pantry-panel   보관함(정렬 토글, 추가 폼 14필드, 미개봉 목록)
│  ├─ #note-import-panel   기록 불러오기(스크린샷 다중 업로드) — 진입 버튼 숨김
│  ├─ #extract-filter-tabs [전체][드립백 / 소량] + #smallpack-filter-tabs [전체][드립백][소량]
│  ├─ #extract-bean-search 원두 이름 검색
│  └─ #entries-container   원두별 그룹 목록 / 검색 결과
├─ #tab-calendar
│  ├─ #cal-subtabs [전체][스터디][클래스][커핑][카페][원두]
│  ├─ #calendar-view
│  │  ├─ 달력(제목, 오늘/‹/›, #calendar-grid, #calendar-day-panel, #calendar-today-bean, #calendar-bean-legend)
│  │  ├─ #cafe-cupping-section (카페/커핑 필터 시) + 기록 추가 버튼, #cupping-type-filter [퍼블릭][홈커핑][수업], 월별/전체 토글, 목록
│  │  ├─ aside #calendar-brew-section (원두 필터 시) 이 달 추출 기록
│  │  └─ aside #calendar-roadmap-embed (전체 필터 시) [로드맵][먹어볼 원두]
│  ├─ #study-view  [책][동영상] 각 추가 폼 + 목록
│  └─ #classes-view 추가 폼 + [전체][원데이][주/월별] 필터 + 목록
├─ #tab-bean
│  ├─ #bean-subtabs [커피 노트][가공 방식][배전도][품종][블렌드][로스터리][생두 수입사][커피 지도 + 농장(생산자)★기본][✦ Competition Lots]
│  ├─ #bean-view-notes / process / roast / variety / source / selection / map(+farm) / specialty
│  └─ (블렌드 선택 시) #tab-practice 블렌드 실험실
├─ #tab-misc  [장비] → [전체][드리퍼][필터][케틀][온도계][저울][물], 추가 폼(사진 2장), 정렬 토글, 목록
├─ #form-panel  기록 입력 폼(원두/카페/커핑 공용, 아래 5.1.6)
├─ button.fab "+" (모바일 ≤560px 전용)
└─ .scale-control  화면 크기 100~140%(데스크톱 ≥900px 전용, localStorage 저장)
```

### 4.1 화면에 보이지 않는(숨김/도달 불가) 요소

| 요소 | 숨김 방식 | 상태 |
|---|---|---|
| 기록 불러오기, 전체 데이터 백업, 백업 파일에서 복원 버튼 | CSS `display:none!important`(style 497행) | 코드 완전 구현. DOM 클릭으로 동작 확인(백업 JSON 다운로드 성공) |
| 추출 후 가루 사진 섹션(`#photo-section-*`) | 인라인 `display:none !important` | 업로드→AI 분석 코드 존재하나 UI 도달 불가 |
| 별점 `#rating-row` | 필드 `display:none` | 리스너·저장(`rating`)은 살아 있음 |
| `#inline-my-recipe-btn` (폼 내 내 레시피 불러오기) | `display:none` | 대체 경로(폼 상단 런처)가 있음 |
| 품종 참조표 `#variety-ref-table`, 품종 `+ 추가` 폼, `#variety-list-container` | 래퍼 `display:none aria-hidden` | 여전히 렌더 계산은 실행 |
| 커핑용 SCA 그리드 `#attr-grid-cupping` | `display:none` | `buildAttrGrid`가 항상 채움(총점 id 중복 생성) |
| 조언 채팅(`adviceSlotHtml`/`renderAdviceChat`) | `#advice-slot-<id>`를 만드는 코드가 없음 | 시작 버튼이 렌더될 수 없어 **도달 불가** |
| `#tab-practice`의 독립 탭, `#bean-view-farm` 서브탭 | 메인/서브탭에 항목 없음 | 각각 블렌드 뷰, 지도 뷰에 붙어서만 표시 |
| `#misc-main-subtabs`(장비 1개) | 리스너 없음 | 과거 비장비 그룹의 잔재 |
| `#toggle-form` | ≤560px에서 숨김(FAB로 대체) | 반응형 |
| `.scale-control`, `.tagline-inline` | ≤899px / 소형에서 숨김 | 반응형 |

---

## 5. 기능 상세

### 5.1 새로운 추출 탭

#### 5.1.1 Coffee D-day (`#dday-bar`)
- 미설정: 안내문 + 날짜 입력 + 저장 → `brew-start-date`에 `YYYY-MM-DD` 원문 저장.
- 설정: 알약 UI `Coffee D-{n}` (`n = floor((오늘0시 − 시작일)/1일) + 1`, 시작일 당일이 D-1) + `YYYY.MM.DD 첫 추출`. 100의 배수면 큰 `N일 기념 ✦`, 30의 배수면 `N일 기념`. 검증 실측: 2026-01-01 저장 → `Coffee D-268`.
- 읽기 실패 시 새 입력을 막고 "다시 불러오기" 버튼만 노출(덮어쓰기 방지).

#### 5.1.2 "마시는 중" 카드 (`#weekly-bean`)
- 우선 보관함에서 **개봉(opened)·일반 포장·비블렌드·비디카페인** 항목이 있으면 그 항목마다 카드(피크 시작 빠른 순): `마시는 중 · 개봉일~`, 이름, 로스터리/로스팅일/용량, `잔여량 Xg/Yg`, 가격·100g 환산가, 예상 피크, `수정`.
- 없으면 가장 최근 일반 원두 기록 1장: `N일째(시작일(요일)~)`, 잔여량(봉투 용량 미기재 시 100g 가정, 같은 이름 기록의 dose 합 + 블렌드 사용량 차감), 그리고 **AI 배경 설명**(순서: `BEAN_BLURBS`(비어 있음) → `bean-blurb:` 캐시 → 농장 항목 메모 → Anthropic API 직접 fetch).

#### 5.1.3 원두 보관함 (`#bean-pantry-panel`)
- 폼 14필드: 이름(필수), 로스터리, 원두 형태(일반/드립백/소량), 봉투 용량 g, 구매 가격(콤마 포맷), 배전도(5단계 select), 로스팅 날짜, 구매 날짜, 예상 피크 시작/종료(선택), 예상 노트(칩), 메모. 용량·가격 입력 시 `100g 환산가 · N원` 즉시 계산.
- 음용 적기: 수동 피크 우선, 없으면 로스팅일 + 배전도별 오프셋(라이트 14~45일, 미디엄 7~30일, 다크 4~21일; `미디엄 라이트`는 미디엄으로 분류됨).
- 정렬 `등록순 / 예상 피크 빠른 순`. 카운트 `N봉`(미개봉만). 카드: 메타, 피크, Expected Notes, 메모, `수정`, `개봉함`(confirm → `status:'opened'`, 목록에서 사라지고 "마시는 중" 카드·원두 이름 자동완성으로 이동).
- 추출 기록 저장 시 `syncBrewEntryToOpenedPantry`: 같은 핵심 이름 항목을 찾아 빈 값 보강 + **무조건 opened로** 전환, 없으면 개봉 항목 자동 생성.
- 삭제·개봉 취소 UI는 없음(파일 전체에 pantry 삭제 코드 없음).

#### 5.1.4 기록 목록 (`render`)
- 헤더 `N entries`(전 카테고리 합).
- 검색어가 있으면 목록 대신 전체 기록 검색(이름/로스터리/출처/수입사 부분일치, 커핑 내 개별 원두 포함) 결과와 `기록 보기` 버튼.
- 필터 `전체`(일반 포장 원두 기록) / `드립백 / 소량`(하위: 전체·드립백·소량 + 보관함의 개봉 소량 카드).
- 원두 그룹(`coreBeanName` = 이름 끝 괄호 제거·소문자 기준): 봉투 썸네일, 이름, 로스터리/수입사/농장/워싱스테이션, 마신 기간, `N개 기록`, `최고 {SCA 총점}`. 직접 블렌드는 구성 원두 그룹마다 중복 표시. 원두 1개짜리 홈커핑 기록은 해당 원두 그룹에 투영.
- 그룹 펼침 내용: `☕ 이 원두 총정리`(수동 편집 textarea / 기록 5개↑이면 `🤖 AI로 정리하기`), `⭐ 베스트 레시피`(기록 5개↑이면 피커), 기록 카드들.
- 카드 공통 동작: 클릭 토글(첫 펼침 시 사진 지연 로드), `자세히 보기/간단히 보기`(메모에 "Final Evaluation"/"최종 평가" 마커가 있을 때), `내 레시피로 저장`(단계 있는 기록), `수정`, `삭제`(confirm; `journal-photo:`는 지우지만 `bag-photo:`는 남김), 저장 실패 배너의 `다시 저장`.

#### 5.1.5 기록 카드(`entryHtml`) 표시 순서
색점+이름(+`드립백`/`소량`/`⭐ 베스트` 배지) → 저장 실패 배너 → 로스터리/수입사/농장/워싱스테이션 줄 → `YYYY.MM.DD · 카테고리 · 커핑유형 · 카페명 · 지역 · 드리퍼` → 우측 `NN.NN / 100`(SCA 총점) → 태그(레거시) → [상세] FINAL EVALUATION → 봉투 사진(≤2) → 정보 그리드(카페, 가격, 장소, 커핑 원두 아코디언(순위·지역·농장·고도·배전·가격·예상/실제 노트·항목별 평가·메모), 가공, 고도, 품종, 로스팅, 수분율, 밀도, CoE 점수, 입고 시기, 로스팅 날짜, 비율, 물 온도, 분쇄도, 사용한 물, 총 시간, 필터, 예상 노트, 내가 느낀 노트) → 가게 설명 → 첫 등록일 가이던스 카드(추천 레시피/주의점/참고점) → SCA 항목별 점수+강도+메모+TOTAL → 단계 로그 타임라인 + 요약 + 레시피 대비 차이 → 메모 → 가루 사진 → 사진 분석 → 액션 버튼.

#### 5.1.6 기록 입력 폼 (`#form-panel`)
- 진입: `+ 새 기록 추가`/FAB → 원두 모드(카테고리 선택 숨김). 달력 탭의 `+ 카페/커핑 기록 추가` → 카페/커핑 모드. 카드 `수정` → 편집 모드(`수정 저장`).
- 상단 "레시피로 시작": **챔피언 레시피**(WBrC 우승자 9건, 비율·온도·드리퍼만 적용) / **카페 레시피**(4건, 단계까지 적용 + `recipeRef` 기록) / **내 레시피**(저장본 목록·적용·삭제·새로 만들기).
- 기록 종류 세그: 원두(집에서 내림) / 카페 / 커핑. 원두일 때만 `원두 형태`(일반/드립백/소량). 기록 날짜/시간(datetime-local, 기본 현재).
- **커핑 모드** 필드: 커핑 유형(퍼블릭/홈커핑/수업; 홈커핑이면 장소 숨김), 장소, 원두 카드 N개(이름, ✕, 단일/블렌드, 블렌드 구성 텍스트, 국가, 지역, 로스터리, 농장(생산자), 고도, 품종, 가격, 나의 순위, 가공(5종+기타+세부), 배전도(5단계), 예상 노트 칩, 내가 마신 노트 칩, 항목별 평가 8종(6~10점 슬라이더 + 메모), 메모), 전체적인 경험.
- **원두/카페 모드** 필드: 원두 구성(단일/카페 블렌드/직접 블렌드 → 구성 원두+g 행), 카페 이름(카페), 원두 이름(datalist = 개봉 보관함), 가격(카페: 한 잔 가격), 로스터리·생두 수입사·국가·지역(국가 입력 시 산지 datalist)·농장(생산자)·워싱 스테이션·재배 고도·품종·수분율·밀도·CoE 컵 점수·가공 방식(내추럴/워시드/허니/아나로빅/기타 + 기타 텍스트 + 세부 종류)·로스팅 정도(5단계)·원두 총량(기본 100)·입고 시기·로스팅 날짜, 예상 노트 칩, 가게 설명, 봉투 사진 2장, `🔍 이 원두의 분쇄 및 추출 참고사항 받기`(AI), 레시피(드리퍼·필터·분쇄도·원두량·물량·물 온도·총 추출시간(자동 계산)·사용한 물), 추출 예시(참고, 읽기 전용), 추출 단계 로그(시점/대기초/물량/메모, 물방울 토글, 마지막 행 = 드로우다운), 단계 요약(총 N차 · 합계 물량 · 총 시간, 레시피와 물량 2g/시간 10초 초과 차이 경고, 참고 레시피 단계별 차이), 테이스팅(SCA 커핑 폼 10항목 + 강도 3항목 슬라이더·항목 메모·TOTAL, SCA 플레이버 휠 `<details>` 9카테고리 85용어 클릭 추가, 내가 느낀 노트 칩 + "봉투 노트에서 추천" 원탭 칩), 추출 관련 메모.
- 카페 모드는 `.cafe-hide`로 원두 구성·수분율/밀도/점수·총량/입고/로스팅일·봉투 사진·가이던스·분쇄도~사용한 물·단계 로그·가루 사진을 숨김.
- 이름 규칙: 끝 괄호 `(로스터리, 출처, 농장, 생산자)`를 파싱해 힌트 표시·농장 자동 채움. blur 시 같은 핵심 이름의 기존 기록이 있으면 원두 정보 전 필드를 최초 기록 값으로 채우고 잠금 배너(입력 자체는 편집 가능) + 가이던스 버튼·봉투 사진 섹션 숨김.
- SCA 기본값: 아무것도 만지지 않아도 Uniformity/Clean Cup/Sweetness = 10 → **신규 폼은 TOTAL 30.00부터 시작**(카드에 30.00 / 100으로 저장·표시됨).
- 사진: HEIC→JPEG 변환, 긴 변 700px 축소, JPEG 0.7로 dataURL 저장.
- 저장(`save-entry`) 절차: 입력 중 칩 확정 → 이름/구성 검증(실패 시 포커스만) → entry 객체 생성(7장 스키마) → 메모리 반영 → `entry:<id>` 저장(버튼 "저장 중…", 이탈 경고 카운터) → 실패 시 `_syncFailed` 배너 → 성공 시 보관함 동기화 → 같은 원두 형제 기록에 정보 전파(예상 노트·품종은 덮어씀, 나머지는 빈 값만) → 로스터리/가공/농장/수입사/품종을 `misc-items`에 자동 등록 → 사진 키 저장/삭제 → 폼 닫기 → 원두면 추출 탭에서 해당 기록 펼침, 카페/커핑이면 달력 탭 해당 필터로 이동.

#### 5.1.7 기록 불러오기 (`#note-import-panel`, 버튼 숨김)
여러 장의 노트 스크린샷을 700px/JPEG 0.6으로 축소해 **한 번의 API 호출**에 전부 첨부(안내문의 "2장씩 나눠 처리"는 구현과 다름) → 모델이 반환한 JSON(name, country, region, process, altitude, variety, roast, expectedNotes, dripper, filter, dose, water, temp, grind, time, steps[], rating, attributes{10}, notes, dateGuess)으로 새 기록 폼을 채움(저장은 사용자가). 파일 로드가 비동기라 첨부 순서가 뒤바뀔 수 있음.

#### 5.1.8 전체 데이터 백업/복원 (버튼 숨김, DOM 호출로 동작 확인)
- 백업 JSON: `{exportedAt, data:{entries(메모리), miscItems, blends, classes, roadmapData, myRecipes, books, beanSummaries, bestRecipes}, rawData:{ddayStart}, photos:{'journal-photo:<id>', 'bag-photo:<id>', 'bean-blurb:<name>'}}`. 파일명 `커피일지-백업-YYYY-MM-DD.json`. 결과 패널에 항목 수 표·키별 용량(5MB 초과 경고)·전문 textarea·복사 버튼.
- **백업에서 빠지는 것**: `coffee-study-videos`(동영상), `bean-pantry-items`(보관함), 레거시 통짜 키, 기록에 연결되지 않은 고아 사진, UI 배율.
- 복원: 파일 선택 → confirm → entries는 id 단위 키 덮어쓰기(사실상 병합), 나머지 8키·D-day·사진은 키 단위 교체. 메모리 갱신/자동 새로고침 없음(안내문으로 새로고침 요청).

### 5.2 커피 달력 탭

- **월 그리드**: 요일 헤더, 칸마다 카테고리 색 바(원두=accent, 카페=`--cafe`, 커핑=`--cupping`), 하루 2잔↑ `N잔` 배지, D-day 기준 10일 단위 마일스톤 배지(100 단위 강조), 최대 3개 색 점(실험실 블렌드 포함), 오늘 강조.
- **원두 범위 띠**: 같은 핵심 이름 원두를 연속으로 마신 기간을 캘린더 이벤트처럼 띠로 표시(6색 순환, 10일 초과 공백이면 새 봉지로 분리, 주 경계·범위 끝에서만 둥근 캡). 하루에 여러 범위가 겹치면 첫 번째만 그림. 필터와 무관하게 항상 원두 기록 전체 기준.
- 날짜 클릭: 기록 1건이면 바로 이동, 여러 건이면 아래 패널에 목록(색점·이름·카테고리·SCA 점수). 블렌드 항목 클릭은 `switchTab('practice')` 호출로 **화면이 비는 버그**.
- `Today 원두이름`(최신 원두 기록), 범례(`■ 이름 (N잔)`).
- 서브탭 `카페`/`커핑`: `+ 카페/커핑 기록 추가`, `YYYY년 M월 {카테고리} 기록`(월별/전체 토글), 커핑은 `퍼블릭/홈커핑/수업` 필터(유형 미지정 기록은 이름·장소·메모 텍스트로 추정). 서브탭 `원두`: 사이드바에 이 달 추출 기록(원두명·레시피명·시각). 서브탭 `전체`: 사이드바에 커피 공부.
- **로드맵**: 기본 데이터는 `starter`("나의 로드맵 · 자유롭게 작성", day 0~99999) 1단계, 항목 0개. 항목 추가(Enter)/체크/인라인 편집(contentEditable)/삭제. D-day가 있으면 `D-{n} 지금은 "나의 로드맵" 단계예요`. 새 단계 추가 UI는 없음.
- **먹어볼 원두**: 이름과 달리 "전체 리뷰(notes)가 적힌 커핑 기록" 목록(날짜, 장소, 115자 발췌, 원두 칩) → 클릭 시 해당 커핑으로 이동.
- **스터디 · 책**: 제목(필수)/저자/상태(읽고 싶음·읽는 중·완료)/읽은 기간(읽는 중·완료일 때, 읽은 날짜는 완료일 때만)/별점(정수)/메모. 카드에 상태 배지·별·날짜. 편집 없음, 삭제 confirm 없음.
- **스터디 · 동영상**: 제목/채널/링크(새 탭)/메모. 편집 없음.
- **클래스**: 클래스명/유형(원데이·주/월별 → 날짜 또는 기간)/메모, 필터 전체·원데이·주/월별, 정렬 날짜 내림차순. 편집 없음.
- 책·동영상·클래스는 달력 그리드에 표시되지 않는다(같은 탭의 별도 뷰).

### 5.3 원두 탭 (통계 소스 = 직접 내림 + 카페 + 커핑 내 개별 원두를 펼친 `getAllBeanRecords()`)

| 뷰 | 내용 |
|---|---|
| **커피 노트** | 예상 노트/내가 느낀 노트 토글, 정적 플레이버 휠 범례, 향미 분류 9카드(클릭 시 세부 목록), 노트 클라우드(`라벨 N` = 고유 원두 수; 검색, 많이 나온 순/가나다순), 노트 선택 시 우측 패널에 "함께 기록된 노트 조합"별 기록 카드 + `원래 기록 보기`(필터까지 맞춰 이동). 동의어 45개로 표기 통합 |
| **가공 방식** | 검색(기록의 가공 라벨별 그룹), 4대 카드(워시드·내추럴·허니·아나로빅, 마셔봄 ✓), `기타 더보기` 표(카보닉 마세레이션·웻헐드·디카페인·기타), 상세 패널(허니는 블랙/레드/옐로/화이트/골든 세부 목록, 그 외 국가별·품종별·세부별·장소별·직접 내린 원두), 하단 `내가 마셔본 가공 방식` 카드 목록(+추가/수정/삭제) |
| **배전도** | 라이트계/중간/다크계 세그(건수), 다크계에서 `전체/블렌드` 필터, 최신순/오래된 순. 행 클릭 → 원 기록 |
| **품종** | 품종별로 보기(검색, **계보 탐색기**: 종 탭 아라비카·리베리카·로부스타, 알파벳순/계보순, 마셔봄 ●/○ 색인 21+기록 품종, 프로필(type·N cups·부모1 →/× 부모2 → 현재·설명·특성 칩·단일/복수 품종 기록·Heirloom은 74xxx 번호별)) / 국가별로 보기(나라별 품종·횟수·장소 배지) |
| **블렌드** (`#tab-practice`) | 필터 전체/커핑한 블렌드/카페 블렌드/내가 만든 블렌드. 소스: 직접 만든 블렌드(폼: 이름·날짜·원두+g 행·메모, 수정/삭제), 기록의 customBlend/commercialBlend, 커핑 원두 중 블렌드. 카드에 원두별 g·비율 |
| **로스터리** | 한국/해외 스코프, "지도"(실제 지도가 아닌 CSS 블롭 위 % 좌표 핀: 국내 11지역, 해외 15지역 정규식 매핑, 카운트 `N곳 · M잔`), 핀 클릭 상세, 위치 미입력 목록, 추가 폼(이름·마셔봄/궁금함·국내/해외·지역·메모), 목록(★ 즐겨찾기, 여기서 산 원두, 궁금한 로스터리) |
| **생두 수입사** | 추가 폼(이름·상태·메모), 목록 카드(국가별 횟수·원두 목록·장소) |
| **커피 지도 + 농장(생산자)** (기본) | 인라인 SVG 세계지도(175개국 path, 회귀선 2개), 커피 생산국 45개 진초록, 마셔본 나라 강조, 주요 산지 60개 점(r 2.2), 나라/점 클릭 → 패널(국기·이름·`N지역, Mcup`·주요 산지·지역→농장별 방문 내역·카테고리 비율), `총 N개국`, 총 잔 수 바(직접내림·카페·커핑 합), 대륙별 국가 아코디언(`경험해본 산지 n / 45 countries · N coffees`)과 `경험할 생산국` 버튼. 아래에 농장(생산자) 카드 목록(+추가, 검색, 국가별 횟수 → 지도 점프) |
| **✦ Competition Lots** | `score ≥ 80` 원두를 점수순 카드로(티어: ≥90 최상급 경매급·명품급 / ≥87 마이크로랏급 / ≥85 고품질 스페셜티 / ≥80 스페셜티 기준), 예상 노트·가게 설명 표시 |

### 5.4 기타 탭 (장비)
- 유형 6종(드리퍼·필터·케틀·온도계·저울·물). 폼: 상태(보유/궁금한 장비), 이름, 메모, 사용 시작일, 사진 2장(600px/JPEG 0.7, `misc-items` JSON 안에 dataURL로 인라인, 첫 장은 `photo`에도 중복 저장).
- 전체 보기: 지정순(유형 그룹) / 최신순. 개별 유형: 보유 → 궁금한 장비 섹션. 수정/삭제(confirm 없음).
- 드리퍼·필터·물 항목은 기록 폼 datalist에 공급됨.

### 5.5 공통 UI
- 화면 크기 컨트롤(우측 고정): 100~140%, 5% 단위, `--journal-scale`로 `.wrap, .fab`에 `zoom` 적용(≥900px), 기본 125%, `coffee-journal-ui-scale`에 저장.
- FAB `+`(≤560px), 저장 진행 중 `beforeunload` 경고, 세그먼트 컨트롤 12그룹은 같은 옵션 재클릭 시 해제(토글).

---

## 6. AI 기능 6종 (모두 `POST https://api.anthropic.com/v1/messages`, 헤더 `Content-Type`만)

| # | 기능 | 진입 | 모델 / max_tokens | 입력 | 출력 처리 |
|---|---|---|---|---|---|
| 1 | 마시는 중 원두 배경 설명 | 자동(`renderWeeklyBean`, 캐시 미스 시) | claude-sonnet-4-6 / 700 | 이름·지역·가공·고도·품종(국가 미포함) | 텍스트를 `innerHTML`로 표시, `bean-blurb:` 캐시 |
| 2 | 원두 총정리 | 그룹의 `🤖 AI로 정리하기`(기록 5개↑) | 700 | 같은 원두 기록 타임라인 JSON | `{"summary"}` JSON 파싱 → `bean-summaries` |
| 3 | 분쇄·추출 참고사항 | 폼 `🔍 … 받기` 버튼 | 900 | 폼의 원두 정보 15항목 | `{recipe{dose,water,temp,grind},cautions[],tips[]}` → 카드 + `이 추천값 적용`, 기록의 `beanGuidance`로 저장 |
| 4 | 가루 사진 분석 | 가루 사진 업로드(**UI 숨김**) | 400 | JPEG base64 + 프롬프트 | 3~4문장 텍스트 → `photoFeedback` |
| 5 | 기록 불러오기 | 패널 `가져오기`(**버튼 숨김**) | 3000 | 스크린샷 N장 + 스키마 프롬프트 | JSON → 폼 채움 |
| 6 | 다음 추출 조언 채팅 | `.gen-advice-btn`(**렌더 경로 없음**) | 500 | 기록 + 최근 5건 JSON, 이후 대화 | 문단별 말풍선, `entry.adviceChat` 저장 |

- `callClaudeAPI` 래퍼: 빈 응답/JSON 파싱 실패 시 최대 3회 재시도, 마지막엔 잘린 응답에서 `"text"` 값을 정규식으로 복구("사파리 iframe 환경 제약" 안내). 네트워크 예외는 그대로 전파.
- **현 상태**: 코드에 `x-api-key`, `anthropic-version`, `anthropic-dangerous-direct-browser-access` 헤더와 키 입력 UI가 전혀 없다. 일반 브라우저에서는 CORS 차단 또는 401로 실패하며, 실측 시 버튼 3 클릭 결과가 "추천을 만드는 중 오류가 발생했어요: Failed to fetch"로 표시됐다(테스트 환경의 네트워크 정책 때문에 실패 원인 문자열은 실제 브라우저와 다를 수 있으나, 인증 부재로 인한 실패 자체는 동일). 원격 저장소용 rate-limit 재시도, "Anthropic 지원팀 권장 간격" 주석, `templateStorage` 명칭으로 보아 원본은 Claude 아티팩트 환경에서 실행되던 앱이다.

---

## 7. 데이터 스키마 전수

### 7.1 entry (`entry:<id>`)
`id`(base36 시각+랜덤5) · `createdAt`(ms) · `category` '원두'|'카페'|'커핑' · `beanMode` 'single'|'commercialBlend'|'customBlend' · `blendComponents[{name,grams}]` · `name` · `country` · `region` · `altitude` · `variety` · `farmProducer`("농장(생산자)") · `roastery` · `selection`(생두 수입사) · `washingStation` · `process`(4대 가공은 `"허니(세부)"` 형식 가능) · `processOther` · `beanPackageType` 'standard'|'dripbag'|'sample' · `isDripBag`(레거시) · `moisture` · `density` · `score`(CoE) · `arrival` · `roastDate` · `roasterDesc` · `roast`(5단계) · `bagWeight` · `price`(숫자 문자열) · `source`(이름 괄호의 로스터리, 레거시) · `cafeName` · `expectedNotes`/`actualNotes`(", " 구분) · `dripper` · `filter` · `dose` · `water` · `temp` · `grind` · `waterType` · `time` · `hasPhoto` · `photoFeedback` · `hasBagPhoto` · `steps[{time,wait,water,note}]` · `recipeRef{name,steps[]}` · `beanGuidance{recipe,cautions,tips}` · `consultation`/`practice`/`adviceChat[{role,text,hidden?}]`/`noteChat`(편집 시 보존) · `rating`(0~5, 0.5단위) · `attributes{fragranceAroma, flavor, aftertaste, acidity, body, balance, uniformity, cleancup, sweetness, overall, fragranceIntensity, acidityIntensity, bodyIntensity}` · `attributeNotes{key:text}` · `tags[]`(레거시) · `notes` · `cuppingType` · `cuppingPlace` · `cuppingBeans`(줄바꿈 결합, 레거시) · `cuppingBeanNotes[{bean,note}]`(레거시) · `cuppingBeanDetails[]` · 런타임 `_syncFailed`, `_homeCuppingProjection`, `_fromPantry`.

`cuppingBeanDetails[]`: `name, country, region, roastery, farmProducer, altitude, variety, price, rank, process, roast, expectedNotes, actualNotes, evaluation{aroma,flavor,aftertaste,acidity,sweetness,body,balance,overall: text}, evaluationScores{같은 키: 6~10}, memo, beanMode 'single'|'blend', blendComponentsText` (+레거시 `note`).

### 7.2 기타 컬렉션
| 컬렉션 | 필드 |
|---|---|
| pantry item | `id, name, roastery, packageType, weight, price, roastLevel, roastDate, purchaseDate, peakStart, peakEnd, expectedNotes, notes, status 'unopened'|'opened', openedAt, createdAt, sourceEntryId` |
| misc item | `id, type(dripper·filter·kettle·thermometer·scale·water·source·selection·process·variety·farm), name, notes, since, photo, photos[], status, createdAt` + source: `scope '국내'|'해외', location, favorite` |
| book | `id, createdAt, title, author, status, startDate, endDate, rating, notes` |
| video | `id, createdAt, title, channel, url, notes` |
| class | `id, createdAt, title, classType 'oneday'|'recurring', date, startDate, endDate, notes` |
| blend | `id, name, date, beans[{name,grams}], notes, createdAt` |
| my recipe | `id, name, fromEntryId, beanName, rating, dose, water, temp, dripper, filter, grind, time, steps[], createdAt` |
| roadmap phase | `id, title, range, dayStart, dayEnd, items[{id,text,done}], tables?[]` |
| bean summary | `{text, generatedAt}` / best recipe: `entryId` / blurb: `{text, generatedAt, fromFarm?}` |
| 백업 파일 | 5.1.8 참조 |

---

## 8. 내장 참조 데이터셋 전수

| 데이터 | 규모 | 내용 |
|---|---|---|
| `ATTRS` (SCA 2004 커핑 폼) | 10 | Fragrance/Aroma, Flavor, Aftertaste, Acidity, Body, Balance(6~10, 0.25), Uniformity, Clean Cup, Sweetness(0~10, 2), Overall(6~10) |
| `SCA_INTENSITIES` | 3 | Aroma/Acidity/Body Intensity (1~5, 0.5) |
| `CUPPING_EVALUATION_FIELDS` | 8 | aroma, flavor, aftertaste, acidity, sweetness, body, balance, overall |
| `FLAVOR_INDEX` (2016 SCA/WCR 휠) | 9 카테고리 / 85 용어 | Floral·Fruity(Berry/Dried/Other/Citrus)·Sour/Fermented·Green/Vegetative·Other(Papery/Musty, Chemical)·Roasted·Spices·Nutty/Cocoa·Sweet |
| `NOTE_CATEGORIES` | 9 / 세부 43 | 과일(베리류·감귤류·열대과일·씨과일·건과일), 꽃(자스민·장미·카모마일·홍차 같은·라벤더), 단맛(바닐라·꿀·흑설탕·메이플·캐러멜), 견과·코코아(7), 향신료(4), 로스팅(4), 발효·신맛(5), 풋내·식물(4), 기타(종이·케미컬·흙·고무) — `keywords` 필드는 정의만 있고 미사용 |
| `NOTE_SYNONYMS` | 45 | 갈색설탕→브라운슈거, 재스민→자스민, 카라멜→캐러멜, jasmine→자스민, brown sugar→브라운슈거 등 |
| `PROCESS_MAIN4` / `PROCESS_ETC` | 4 / 4 | 워시드·내추럴·허니·아나로빅 / 카보닉 마세레이션·웻헐드(Giling Basah)·디카페인·기타. 허니 세부 정규식 5종 |
| `VARIETY_REFERENCE` | 26 | 티피카, 버번, 레드/핑크/옐로 버번, 카투라, 카투아이, 게이샤, 파카마라, SL28, SL34, 문도노보, 파카스, 마라고지페, 카스티요, 빌라사르치, 루이루 11, 바티안, 카티모르, 자바, 파파요, 옴블리곤, 치로소, 라우리나, 치초 가요, Heirloom (+피베리 특이사항) |
| `VARIETY_LINEAGE` | 21 (+alias geisha→gesha) | ethiopian heirloom, typica, bourbon, red/yellow/pink bourbon, villa sarchi, pacas, maragogype, catimor, sarchimor, castillo, caturra, gesha, mundo novo, catuai, pacamara, ruiru 11, batian, robusta, liberica — 각 type·부모 2·기호(→/×)·설명·특성 |
| `VARIETY_KOREAN_NAMES` | 39 | maracaturra→마라카투라 … wush wush→우쉬우쉬, 74xxx 번호는 Heirloom 하위로 처리 |
| `COFFEE_COUNTRIES` | 45개국 / 60산지 | 아메리카 19(브라질 Cerrado·Sul de Minas·Mogiana, 콜롬비아 Huila·Nariño·Antioquia, 페루, 에콰도르, 볼리비아, 멕시코, 과테말라 Antigua·Huehuetenango, 온두라스, 엘살바도르, 니카라과, 코스타리카, 파나마 Boquete, 자메이카, 도미니카공화국, 쿠바, 아이티, 베네수엘라, 푸에르토리코, 가이아나) / 아프리카 14(에티오피아 Yirgacheffe·Sidamo·Guji·Harrar, 케냐 Nyeri·Kirinyaga, 르완다, 부룬디, 탄자니아 Kilimanjaro·Mbeya, 우간다, 콩고민주공화국, 잠비아, 말라위, 짐바브웨, 카메룬, 코트디부아르, 마다가스카르, 기니) / 아시아·오세아니아 12(인도네시아 Sumatra·Java·Sulawesi·Bali·Flores, 베트남, 인도 Chikmagalur·Coorg, 중국 Yunnan, 태국, 미얀마, 라오스, 파푸아뉴기니, 필리핀, 대만 Alishan, 동티모르, 예멘 Haraz) |
| `REGION_SYNONYMS` / `COUNTRY_SYNONYMS` | 25 / 10 | 예가체프→Yirgacheffe 등 / columbia→colombia, drc→dem. rep. congo 등 |
| `COUNTRY_ZONE` / `MAJOR_PRODUCERS_BY_ZONE` | 45 / 15 | 대륙 매핑 / 스페셜티 명성 기준 목록(데드 코드에서만 사용) |
| `WORLD_MAP_SVG` | 175 path + 회귀선 2 | viewBox `138 100 788 283` |
| 로스터리 지도 좌표 | 국내 11 / 해외 15 | % 좌표 + 7슬롯 지터 |
| `CHAMPIONS` (WBrC) | 9 | 2016 Tetsu Kasuya 20:300·92 V60 / 2017 Chad Wang 15:250·92 / 2018 Emi Fukahori 17:220·88 GINA / 2019 Du Jianing 16:240·94 오리가미 / 2021 Matt Winton 20:300·93 / 2022 Sherry Hsu 14:200·70 Orea V3 / 2023 Carlos Medina 15.5:250·91(highlight) / 2024 Martin Wölfl 17:270·93 Orea v4 / 2025 George Peng 15:210·96 SOLO |
| `CAFE_RECIPES` | 4 (단계 포함) | 유어홈 15:240(50+190) / 글리치 15:240·86°C(40·100·100) / 큐라스 V60 13:200·91°C(30·100·70) / 큐라스 오리가미 웨이브 15:250(40·110·100) |
| `GENERIC_STEPS_EXAMPLE` | 6단계 | 0:00 30g → 0:10 뜸 → 0:40 100g → 0:55 → 1:20 80g → 1:35 마무리 (210g, 2:10) |
| `ROADMAP_PHASES` | 1 | starter "나의 로드맵" |
| `BEAN_RANGE_COLORS` | 6 | 갈색 계열 |
| `MISC_LABELS` / `MISC_TYPE_ORDER` | 6 | 장비 유형 |
| 빈 스텁 | — | `PRELOADED_ENTRIES=[]`, `BEAN_BLURBS={}`, `DEFAULT_OWNED_EQUIPMENT=[]`, 특정 커핑 이벤트 상수 4개(`COFFEEPLANT_NEWWAVE`, `RYANS`, `LIBRE`, `HANKOOK_COFFEE`)는 빈 껍데기, `isLibreTimesSquareCupping()`은 항상 false |

---

## 9. CSS / 디자인 시스템

- 변수 2세트: 다크(`--bg #15100c`, `--accent #b58968`, `--cupping #c9a47e`, `--cafe #9c7355`, `--book #8a7560`, radius 3px) → 라이트 아카이브 테마가 덮어씀(`--bg #f5f4ef`, `--surface #fff`, `--accent #20201d`, `--cupping #6e5f8b`, `--cafe #8b5c35`, `--book #5d6f79`, radius 0). 실제 표시는 항상 라이트.
- 레이아웃: `.wrap` 최대 920px, 헤더 하단 2px 실선, 좌측 세로 가이드선(그라디언트), 모노스페이스 라벨, 손글씨 본문.
- 반응형 브레이크포인트: 560 / 600 / 640 / 700 / 760 / 899·900px. 모바일에서 FAB·sticky 저장 버튼, 노트 상세 패널 전체화면 고정.
- 선택자 937개, 고유 클래스 597개, `@media` 15개, 애니메이션 없음.

---

## 10. 이벤트 배선 요약

- `addEventListener` 213곳 + 인라인 `onclick` 2곳(`jumpToMapCountry`, 백업 textarea select).
- 로드 시 고정 배선 약 90개 id(폼/버튼/토글) + `document` click 위임 2, keydown 1, `window` beforeunload 1.
- 렌더마다 재배선되는 동적 리스너: 기록 카드(토글·수정·삭제·상세·재저장·레시피 저장), 그룹 헤더, 총정리/베스트 피커, 달력 칸·패널, 지도 국가/산지/아코디언, 품종 탐색기, 로스터리 핀, 노트 클라우드, 각 목록의 수정/삭제, 단계 행, 칩 삭제 등.

---

## 11. 레거시 · 스텁 · 데드 코드 · 버그 목록

**데드/도달 불가**: `persist()`, `renderWeeklyBeanGeneric`, `renderMajorProducersTable`(#major-producers-table 없음), `renderCountryFarmFocus`(#country-focus-panel 없음), `autoRegisterMiscValue`+래퍼 6개, 조언 채팅 전체(슬롯 미렌더), 가루 사진 분석(섹션 숨김), 기록 불러오기·백업·복원(버튼 숨김), `renderBeanView('farm')`, `#misc-main-subtabs`, `roastery-datalist` 참조, `#f-bean-mode`의 `selected` 클래스, `NOTE_CATEGORIES.keywords`, `openForm`의 `appliedRecipeRef` 시드 분기.

**HTML–코드 불일치**: 기록 불러오기 "2장씩 처리"(실제 1회 호출), "콘솔에 로그 남김"(제거됨), Competition Lots 안내 "CoE 80점"(코드 주석은 SCA), 가공 방식 주석의 4종 목록(실제 아나로빅 포함 5종), `#misc-list-title` 초기값 "원두 구입처".

**버그로 판단되는 지점**
1. 달력 날짜 패널에서 블렌드 항목 클릭 → 존재하지 않는 `practice` 탭으로 전환되어 본문이 비고 탭 강조가 사라짐.
2. 베스트 레시피 피커/총정리 저장 키 불일치: `dataset.bean` 원본 이름 vs `coreBeanName` 비교라 이름에 대문자·끝 괄호가 있으면 피커가 열리지 않거나 저장한 총정리가 표시되지 않음.
3. 카페·커핑 목록의 "다시 저장" 버튼은 리스너가 없어 카드 토글만 됨.
4. 재시도 저장 성공 후 `_syncFailed:true`가 저장본에 남을 수 있음.
5. 이름 blur 자동 채움이 최초 기록의 빈 값으로 기존 입력(총량 100 등)을 덮어씀; 가이던스 결과는 이름을 바꿔도 전역에 남아 저장될 수 있음.
6. 기록 저장 시 형제 기록의 예상 노트·품종을 값이 달라도 덮어씀(카페 기록 포함).
7. 보관함 동기화가 미개봉 항목까지 강제 개봉하고, 오래된 기록을 수정만 해도 재개봉·생성됨; 폼 수정 시 `sourceEntryId` 유실.
8. 삭제 시 `bag-photo:`가 남음(고아 키). 백업에 동영상·보관함 누락.
9. `stripOrdinalPrefix` 정규식이 소수("16.5g")를 서수로 오인해 앞자리를 잘라낼 수 있음.
10. `renderNoteDetail`이 필터 상태를 바꾸지만 재렌더하지 않음; `renderSpecialtyList`만 커핑 원두를 집계하지 않음(데이터 소스 불일치).
11. `lookupCountry` 부분 문자열 매칭: "Dominican Republic", "콩고" 등은 실패, "Guinea-Bissau"는 기니로 오매칭 가능.
12. SCA 기본값 30점이 사용자가 만지지 않아도 저장·표시됨.
13. AI 응답을 `innerHTML`에 이스케이프 없이 삽입(1번 기능), 오류 문구가 `bean-summaries`/`adviceChat`에 영구 저장됨.
14. 모든 삭제(장비·책·동영상·클래스·블렌드·로드맵 항목·내 레시피·misc 카드)에 확인창 없음; 책·동영상·클래스는 편집 불가.
15. `roadmap-data`가 배열이 아니면 로드 실패 플래그가 켜져 새로고침 전까지 로드맵 저장이 영구 차단.

---

## 12. 검증 방법과 산출물

1. `curl`로 HTML·헤더·다른 경로 응답 수집(모두 동일 SPA).
2. Python으로 base64 제거 후 CSS/JS 4개/본문 HTML 분리, 함수·상수·저장소 키·리스너 grep 색인.
3. JS 8,374행을 7구간으로 나눠 전 구간 정독, 구간 간 참조는 grep으로 교차 확인.
4. Playwright + Chromium으로 다운로드한 HTML을 로컬 서빙해 실제 렌더: 4개 메인 탭·6개 달력 서브탭·9개 원두 뷰·7개 장비 필터·5개 패널·폼 3모드·가이던스 버튼·D-day 저장·백업 내보내기까지 DOM 클릭으로 실행하고 31장 스크린샷과 상태 스냅샷(`runtime.json`) 확보. 확인된 사실: 초기 `0 entries`·빈 상태 문구, 배율 125%, 손글씨 폰트 적용, 홈에 노출되는 버튼은 2개뿐, 챔피언 9건·카페 레시피 4건 패널 내용, SCA 그리드 초기 TOTAL 30.00, 커핑 원두 카드 필드, 가이던스 호출 실패 문구, 백업 파일 구조(`entries:[]`, `roadmapData` 1단계, `rawData.ddayStart`), localStorage 키 생성 순서(2초 간격).
5. 스크린샷 10장을 `docs/screenshots/`에 보관.
