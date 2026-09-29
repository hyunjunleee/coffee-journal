# Coffee Journal 모바일 앱 설계서 (Android · iOS)

- 근거 문서: `docs/coffee-journal-site-analysis.md` (웹 원본 전수 분석)
- 현재 상태(2026-09-29, v1.3.0): Android와 iOS 앱이 같은 공유 코드로 동작하고 GitHub 릴리스로 배포된다(설치는 루트 `README.md`, iOS 빌드·플랫폼 구현은 `iosApp/README.md`). 1차는 Android로 시작했고(§1.1), iOS는 §9대로 이어 붙였다. iOS에 없는 것은 홈 화면 위젯뿐이다.
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
| Kotlin | 2.3.20 | K2 컴파일러. Koin 4.2의 iOS 라이브러리가 2.3.20으로 빌드되어 맞춤(2.2 컴파일러는 새 klib ABI를 읽지 못함) |
| Compose Multiplatform | 1.9.3 (Material3 1.9.0) | Kotlin 2.2 이상 |
| Android Gradle Plugin / Gradle | 8.13.2 / 8.14.3 | 환경에 설치된 Gradle 사용 |
| KSP | 2.3.x (KSP2) | Room 컴파일러 |
| Room (KMP) + sqlite-bundled | 2.8.5 / 2.6.x | `room-gradle-plugin`으로 스키마 내보내기 |
| JetBrains Navigation Compose / Lifecycle ViewModel | 2.9.2 / 2.9.x | 멀티플랫폼 |
| kotlinx-datetime / serialization / coroutines | 0.7.x / 1.9.x / 1.10.x | 날짜·백업 JSON·비동기 |
| Koin | 4.2.x | DI (KMP) |
| Coil 3 | 3.6.x | 사진 표시 (KMP) |
| WorkManager / Jetpack Glance | 2.12.0 / 1.2.0 | 하루 한 번 알림 점검, 홈 화면 위젯 (Android 전용, 2차 §3) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 | Android 8.0+ |
| MapLibre Compose / MapLibre Native Android / MapLibre Native iOS | 0.12.1 / 12.0.1 / 6.17.1 | 상세 지도(2차 §1.7). MapLibre Compose는 공용(`commonMain`) 의존성, iOS의 MapLibre 프레임워크는 Gradle이 아니라 Xcode 프로젝트가 공식 Swift 패키지로 링크(`iosApp/project.yml`) |

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
- AI 6종: 원두 배경 설명 생성, 원두 총정리 AI, 분쇄·추출 참고사항, 가루 사진 분석, 스크린샷 기록 불러오기, 조언 채팅. (관련 데이터 필드 `beanGuidance`, `photoFeedback`, `adviceChat` 등은 **백업 호환을 위해 보존만** 하고 UI에 노출하지 않는다.) 이와 별개로 앱 고유의 "AI 노트 도우미"를 사용자 요청으로 더했다(§2.3 29).
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
19. 카페 레시피 중 "공식"이라 적힌 3건(글리치 오리가미 핫, 큐라스 V60, 큐라스 오리가미)을 각 카페가 공개한 가이드 수치로 고쳤고(2026-09 확인), 카드에 출처 링크를 단다. 가이드에 없는 값(붓는 시간·분쇄도·전체 추출시간)은 비운다. 챔피언 레시피에는 "수치는 공개 자료 정리, 대회 공식 자료 아님" 안내를 붙인다.
20. "출처 · 라이선스" 화면을 둔다(처음에는 기타 탭 맨 아래, 이제 설정 › 정보에서 연다): 데이터·디자인 출처와 조건(SCA·WCR 플레이버 휠 CC BY-NC-ND 4.0 표기 포함), 아이콘(Lucide ISC·Feather MIT) 전문, APK에 든 오픈소스 라이브러리 전체(빌드 시 생성). 플레이버 휠 아래에도 저작권 표기를 둔다.
21. 로스터리 지도는 웹의 % 좌표 자리 표시(KOREA/WORLD 도형) 대신 실제 지도를 쓴다(2차 설계 §1): 국내는 통계청 SGIS 경계 기반 한국 지도(시·도 → 시·군·구 확대), 해외는 Natural Earth 세계지도. 로스터리·카페 위치를 지도에 직접 찍어 저장하고 네이버 지도·카카오맵·Google 지도로 연다. 좌표는 앱 전용 값이라 웹 백업에는 선택 키(`lat`, `lng`, `cafePlaces`)로만 더한다. 카페·로스터리는 그것이 보이는 화면마다 그 자리에서 더한다(사용자 요청 2026-09-28): 카페 지도·달력 카페 목록·상세 지도의 "+ 카페 추가", 로스터리 지도(한국·해외)·상세 지도의 "+ 로스터리 추가"(보던 탭의 국내/해외로 시작), 지도가 못 놓은 로스터리의 "위치 입력/수정". 방문 기록이 없는 카페도 등록해 두고 위치를 정할 수 있고, 모든 목록과 지도는 같은 Room 흐름을 읽어 어디서 더하든 바로 함께 바뀐다.
22. 추출 타이머(2차 설계 §2.1): 기록 폼 단계 로그의 "⏱ 타이머로 기록"이 전체 화면 타이머(`Route.BrewTimer`)를 연다. 붓기 시작·끝(붓기 끝이면 그 푸어에 어림 물량이 바로 들어간다: 레시피의 같은 차수 목표 물량, 없으면 붓는 시간 × 초당 6g을 10g 단위로 반올림(최소 10g). 다음 붓기와 추출 끝은 물량을 기다리지 않는다. 버튼 아래 인라인 패널에 어림값이 옅게 미리 채워져 누르면 바로 실제 물량으로 덮어쓰고(입력하는 대로 반영, 비우면 어림값), 기록·"부은 물" 합계에는 확인하거나 고치기 전까지 "≈ 60g"으로 흐리게 보인다. 기록이나 끝난 뒤 미리보기의 푸어를 누르면 같은 패널에서 고친다. 단계 로그·저장된 기록에는 어림 표시 없이 숫자만 간다. 잘못 누른 붓기는 기록의 푸어 행 옆 옅은 ✕(TalkBack "2차 푸어 삭제", 사용자 요청 2026-09-28)로 지운다: 그 시간은 앞뒤 대기에 합쳐지고(이름 없는 대기는 앞 대기에 붙고, 맨 앞에 남은 대기는 버려 "시작"으로 시작한 것과 같아짐), 푸어에 붙인 메모(스월)는 그 시간에 남고, 붓는 중인 푸어는 취소되며, 시계는 그대로 간다. 뒤 푸어들은 차수가 하나씩 당겨져 어림값을 다시 매기고(레시피 2차 → 1차 목표), 입력·확인한 물량은 그대로다. 지운 자리에 "2차 푸어를 지웠어요 · 되돌리기"가 기록이 다시 바뀔 때까지 남는다(대화상자 없음). 규칙은 순수 함수 `BrewTimerEngine.removePour`/`undoRemove`), 뜸·스월·드로우다운 메모, 일시정지·초기화(확인). 레시피가 적용돼 있으면 다음 단계까지 남은 초와 목표·누적 물량을 보여주고 단계가 바뀔 때마다 진동한다. 도는 동안 화면을 켜 둔다(`KeepScreenOn`, Android FLAG_KEEP_SCREEN_ON / iOS idleTimerDisabled). "추출 끝 → 단계 로그로 옮기기"는 기존 `RecipeStep`(시점·물량·대기·메모) 행으로 바꿔 넣고, 폼에 직접 쓴 로그(빈 로그·새 폼의 예시 제외)가 있으면 바꾸기 전에 묻는다. 시간은 단조 시계(`BrewClock`)로 재고, 상태는 목적지 SavedStateHandle에 저장되어 프로세스가 죽어도 벽시계로 흐른 시간을 더해 이어진다. 물량 입력을 대화상자가 아닌 인라인 패널로 둔 것은 타이머·카운트다운을 가리지 않기 위해서다.
23. 추출 비교·계산기(§2.2): 홈 원두 그룹을 펼치면 기록 2개 이상일 때 "📊 추출 비교"(`Route.BrewCompare`). 기준 열(베스트 레시피, 없으면 SCA 2004 최고 점수, 그것도 없으면 CVA 최고 점수)과 다른 값을 굵게·바탕색으로 표시하고 차이(+1g, −1°C, +10s)를 적는다. 행 이름은 고정, 기록 열은 하나의 스크롤 상태로 함께 옆으로 밀린다(320dp에서 2열). 계산기는 기록 폼 레시피 영역의 접이식 "🧮 비율 · 추출수율 계산기": 원두량·비율 1:x·물량 중 둘로 나머지를 계산하고, EY% = TDS% × 추출액 g ÷ 원두량 g. 비교 기준은 SCA가 25 매거진 13호에 실은 고전 Coffee Brewing Control Chart의 IDEAL OPTIMUM BALANCE(추출수율 18–22%, TDS 1.15–1.35%, 2026-09 대조)이며 참고 범위로만 표시한다. "메모에 추가"는 한 줄 요약을 추출 관련 메모에 덧붙인다(계산기 값 자체는 저장하지 않음).
24. SCA CVA(§2.3): 테이스팅과 커핑 원두마다 "SCA 2004 | CVA". CVA는 SCA-103(묘사: 섹션 강도 0–15, 향·맛 CATA 최대 5, 주요 맛 최대 2, 마우스필 최대 2)과 SCA-104(정동: 8개 섹션 품질 인상 1–9, 5컵 중 균일하지 않은 컵·결점 컵, 결점 종류)의 결합 양식 배치를 따르고, 점수는 S = 0.65625·Σh + 52.75 − 2u − 4d를 0.25점 단위(반올림 half-up, 표준 §7.1 표와 일치)로. 결점 컵은 결점 종류를 함께 골라야 계산된다(SCA-104 §5.4.1). 한 기록에는 고른 양식 하나만 저장한다. 저장은 스키마 변경 없이 기존 JSON 맵(`attributes`/`attributeNotes`, 커핑 원두 `evaluationScores`/`evaluation`)의 `cva.` 접두 키. SCA 2004 합계·최고 점수·베스트 로직은 `cva.` 키를 보지 않고, CVA 점수는 "CVA 84.25 / 100", "CVA 최고 84.25"처럼 따로 표시한다. 웹은 모르는 키를 무시하며 웹 편집 시에도 `attributes`는 통째로 복사되어 보존된다(커핑 원두 평가는 웹에서 편집하면 CVA 키가 빠진다).
25. 통계(§2.4): 홈 액션 줄의 "통계"(`Route.Stats`). 이번 달(일별)/3개월/올해/전체(월별) 잔 수 막대(집 추출·카페 누적), 원두 사용량, 지출(봉투 가격 ÷ 용량 × 원두량 — 기록에 없으면 같은 원두의 첫 기록, 보관함 봉투 순; 직접 블렌드는 구성 원두별; 카페는 한 잔 가격), 산지·가공·품종·로스터리 상위 5, 점수 추이(SCA 2004는 선으로 잇고 CVA는 속 빈 사각형), 비율·온도와 점수 산점도. 모두 기기에서 `deriveOffMain`으로 계산해 Compose Canvas로 그리고, 차트마다 요약 문장을 TalkBack 설명으로 단다.
26. 앱 전용 알림·홈 화면 위젯(2차 §3, 웹에는 없음): 하루 한 번 보관함 피크 시작·원두 소진 임박·D-day 마일스톤을 알리고(설정의 "알림" 절; 처음에는 기타 탭 하단 "알림 설정" 화면이었다), 위젯은 D-day·마시는 중 원두·잔여량과 "+ 새 기록"을 보여준다. 문구와 계산은 홈 화면(D-day 알약, 마시는 중 카드)과 같은 규칙을 쓴다. 알림 설정은 기기 설정이라 백업에 넣지 않는다.
27. 상세 지도(2차 설계 §1.7, 하이브리드): SGIS 지도는 오프라인 기본 지도로 그대로 두고, 도로·하천·건물·지명이 보이는 OpenStreetMap 지도(`Route.DetailMap`)를 더했다. 시·도/시·군·구 지도의 "상세 지도" 버튼(보이는 범위로 맞춤)이나 로스터리·카페 패널의 "상세 지도에서 보기"(그 핀으로, 선택된 채)로 열고, 위치 지정에서는 "상세 지도에서 정확히"로 가운데 십자를 맞춰 "이 위치로 지정". 타일은 OpenFreeMap(키 없음, OpenMapTiles 스키마)에서 보이는 지역만 받고, 지도 위에 "© OpenMapTiles © OpenStreetMap contributors"를 항상 표시한다. 네트워크가 없거나 렌더러가 실패하면 안내와 "한국 지도로 돌아가기". 웹에는 없는 기능이다(앱이 네트워크를 쓰는 곳은 §8 네트워크 항목).
28. 설정(웹에는 없음, 사용자 요청): 각 탭 머리글의 작은 톱니바퀴(44dp 터치, TalkBack "설정")로 여는 `Route.Settings` 한 페이지. 하단 탭은 네 개 그대로. 화면 절: 글꼴(고딕 `FontFamily.Default` / 명조 `FontFamily.Serif`), 제목·숫자 글꼴(고정폭 / 본문과 같게), 글자 크기(0.9 / 1 / 1.15 / 1.3배: `LocalDensity`의 fontScale에 곱해 플랫폼 변환(Android 14+ 비선형)을 그대로 따름), 화면 전환(NavHost 페이드 0 / 200 / 400 / 700 ms, 기본 400 ms; Navigation 기본 700 ms가 느리게 흐려 보인다는 요청), 미리보기 카드. 이어서 알림 절(이전 알림 설정 화면의 내용), 정보 절(출처 · 오픈소스 라이선스 →). 값은 `device.display.*` 키(백업 제외)이고, 앱은 저장된 값을 읽기 전까지 배경만 그려 기본 글꼴이 먼저 보였다가 바뀌지 않게 한다. 글꼴은 기기 글꼴만 쓰므로(번들·다운로드 없음) 명조 한글 글꼴이 없는 기기에서는 한글이 고딕으로 보인다(Robolectric 렌더에도 CJK 명조가 없어 스크린샷의 한글은 고딕).
29. AI 노트 도우미(웹에는 없음, 사용자 요청 2026-09-26; `docs/ai-note-helper-plan.md` 12장): 노트 상세의 "✦ 출처로 알아보기"(모드 A, 노트 설명)와 기록 폼 "내가 느낀 노트" 옆의 "✦ AI에게 묻기"(모드 B, 맛 묘사 → 앱의 휠 용어·노트 분류 후보; 고른 후보는 폼 목적지의 SavedStateHandle로 돌아가 중복 없이 붙는다)가 `Route.NoteHelper`를 연다. 방식은 설정에서 고른다: Gemini 무료 + Tavily(기본: Gemini가 질문을 짧은 영어 검색어 하나로 바꾸고(JSON, 실패하면 고정 틀), Tavily가 찾은 결과(검색마다 최대 5개, 모두 최대 15개; 설정 "검색": 기본 = basic 1크레딧, 정밀 = advanced 2크레딧(기본값), 정밀+기본 = 같은 검색어를 basic 다음 advanced 3크레딧; "사람들 의견" 켬(기본값) = 한국어 검색어로 네이버 블로그·티스토리·브런치·네이버 카페만 basic 한 번 더(+1크레딧), 결과는 맨 뒤, 질문에 "사람들의 느낌" 줄, 실패하면 본 결과로 답하고 안내)를 번호를 붙여 Gemini에 넘기고, Gemini가 붙인 [n]을 앱이 해석하며, 인용된 표현을 그 페이지 원문과 대조해 "✓ 원문 확인"/"원문에서 찾지 못함"), Gemini + Google 검색(결제한 프로젝트만; 그라운딩 메타데이터로 번호를 붙이고 Google 검색 제안을 함께 보임), GPT(OpenAI Responses 웹 검색, url_citation), Claude(Anthropic Messages 웹 검색, 인용 블록 끝에 번호, cited_text로 ✓). 답 아래에는 보낸 검색어를 모두("검색어: …") 보이고, Tavily 방식은 화면 안 입력칸에서 영어 검색어를 고쳐 다시 물을 수 있다. 답의 글은 고치지 않고 번호만 붙이며, 출처와 연결되지 않은 문장은 지우지 않고 회색으로 보인다. 출처와 연결된 문장이 하나도 없으면 답 대신 "출처를 찾지 못했어요". 앱·저장소·CI 빌드에는 키가 없다: 사용자가 설정에서 자기 키를 붙여 넣고(마지막 4자리만 표시, 키 확인, 지우기), 키는 Android Keystore의 내보낼 수 없는 AES-256/GCM 키로 암호화해 `noBackupFilesDir/ai-keys`에만 둔다(DB·JSON 백업·클라우드 백업 제외). 방식·모델·안내 확인·검색 설정은 `device.ai.*` 키. 방식마다 처음 물을 때 무엇을(질문 글만) 어디로 보내는지 확인을 받는다. HTTP는 `HttpURLConnection`(새 의존성 없음). 답과 출처는 화면을 닫으면 남지 않는다(기록·캐시 없음). 폼의 묘사 입력은 대화상자 대신 노트 칸 아래 패널이다(대화상자 속 입력칸은 Robolectric 흐름 테스트에서 Compose가 idle이 되지 않음).
30. 원두 정보의 블렌드(사용자 요청 2026-09-28: "원두 정보에 블랜드를 위해 %나 추가가 가능하게", "블렌드를 선택하는 게 아니라 원두 정보 적는 란은 똑같이 두 배로 생기게 하는 거지? + 같은 걸로", "로스터리나 로스팅은 … 한번 적으면 나머지도 회색으로 채워지게, 그러나 내가 추가로 또 다시 적을 수 있게"): 원두 정보는 원두 한 알마다 같은 블록(로스터리·생두 수입사·국가·지역·농장·워싱 스테이션·고도·품종·(원두만) 수분율·밀도·CoE·가공 방식·로스팅 정도·(원두만) 로스팅 날짜)이고, 블록 아래 "+ 원두 추가 (블렌드)"가 빈 블록을 하나 더 만든다. 블록이 둘 이상이면 카페 블렌드(`commercialBlend`)이고 블록마다 "원두 N · 비율 [ ]% · ✕"가 붙으며, ✕로 하나만 남기면 다시 단일 원두다. 원두 구성 세그는 "단일 원두 | 직접 블렌드"(블록이 둘 이상이면 첫 칸이 "카페 블렌드")로, 카페 블렌드를 따로 고르는 칸은 없앴다. 원두 2부터의 로스터리·로스팅 정도·로스팅 날짜는 비워 두면 원두 1의 값을 회색으로 보여 주고 그대로 저장되며(타이머 어림 물량과 같은 옅은 미리 채움, 로스팅 세그는 옅은 테두리 칸), 적으면 그 원두의 값이 된다. 원두 2부터의 블록에는 예시 자리표시("Cerrado", "11.3" 등)를 두지 않아, 그 블록의 회색 글자는 늘 원두 1에서 물려받아 저장될 값이다(가공 세부·기타 칸은 자리표시 대신 라벨). 비율 합계("합계 100%")가 100이 아니면 부드러운 안내만 하고 저장은 막지 않는다. 총량·입고 시기·예상 노트·가게 설명·봉투 사진·이름·가격은 블렌드에 한 번. 저장: 원두 1은 기록의 기존 필드(단일 원두와 같은 자리)이고, `blendComponents`에 원두 1을 포함한 모든 블록(원두별 필드 + `percent`, 비워 둔 로스터리·로스팅은 빈 값 = 원두 1과 같음)을 둔다(Room 스키마 변경 없음). 표시·통계는 `BlendBeans.beans`로 값을 풀어 쓴다. 기록 상세는 원두마다 작은 묶음("원두 1 · 브라질 Cerrado · 60%"), 블렌드 뷰 카드는 "브라질 Cerrado 60%" 줄, 같은 원두를 다시 기록하면 첫 등록의 블록이 모두 돌아온다. 커피 지도(국가·산지·농장)·품종·가공 뷰와 그 상세, 통계의 산지·가공·품종 순위, 농장·품종 등 자동 등록은 블렌드의 원두를 하나씩 센다(`BeanRecords.flatten(…, blendBeans = true)`); 노트·배전도·로스터리·수입사·Competition Lots·검색·통계의 로스터리 순위는 기록 하나로(원두 1) 센다. 직접 블렌드(내 원두를 g으로 섞기)는 그대로이고 행마다 무게 비율(합이 100이 되게 반올림한 정수 %)을 보여 준다.
31. 카페 기록과 집 추출 기록의 입력 차이(사용자 요청 2026-09-28: "카페 등록인데 레시피는 기본으로 닫혀있어도 괜찮을 것 같고 알려주시면 넣을 수 있게 펼치는 걸로"): 카페 기록의 레시피(드리퍼·필터·분쇄도·원두량·물량·물 온도·총 추출시간·사용한 물·추출 단계 로그와 타이머)는 "레시피 입력 (카페에서 알려준 경우)" 한 줄로 접혀 있고 누르면 펼친다(계산기·추출 예시는 집 추출에만). 레시피가 있는 카페 기록을 고칠 때는 펼쳐진 채 열린다. 한 번 펼친 레시피는 다시 접어도 값이 남고 저장되며, 펼친 적 없는 카페 기록은 레시피 값을 저장하지 않는다(예전에는 드리퍼·필터만 보이고 저장). 집 추출 기록은 레시피를 늘 보여 준다.
32. 위치 지정의 검색과 현재 위치(웹에는 없음, 사용자 요청 2026-09-28): "지도에서 위치 지정" 위쪽에 "주소나 이름으로 찾기" 칸(장소 이름으로 시작)과 "검색"·"현재 위치". 검색은 버튼이나 키보드의 검색 키로만 하고(입력 중 자동 검색 없음), 결과(이름·주소·종류·현재 위치에서 거리)를 누르면 그 점으로 정하고 시·도 지도가 그곳을 열며, 고른 곳의 주소를 함께 남긴다(카페는 `cafe_places.address`, 로스터리는 폼의 빈 "지역" 칸을 주소로 채움, 적어 둔 지역은 그대로). 검색은 기본으로 휴대폰의 지도 서비스(Android `Geocoder`: 13+는 리스너, 그 전은 IO 스레드의 블로킹 호출, 국내는 한국 상자로 제한; iOS `MKLocalSearch`: 국내는 한국 영역으로 치우침), 사용자가 설정 › 장소 검색에 자기 카카오 REST API 키를 넣으면 국내만 카카오 로컬(키워드 검색 + 주소 검색, `Authorization: KakaoAK`, 기존 `AiHttp`)로 하고, 키가 틀리거나(401) 카카오맵이 꺼져 있거나(403) 무료 사용량을 다 쓰거나(429) 서버 오류면 기기 검색으로 대신 찾고 그 사실을 알린다. 결과 아래 "검색: 카카오"/"검색: 기기 지도 서비스". 찾은 곳 없음·실패(오프라인)·이 기기에서 검색 불가를 각각 안내한다. 국내 검색 결과는 시·군·구 안의 곳만, 해외는 그 밖의 곳만 보인다. "현재 위치"는 누를 때만 위치 권한을 묻고(Android 정밀·대략 둘 다 요청, 대략만 허용해도 됨; iOS "앱을 사용하는 동안") 위치를 한 번 읽어(15초 제한) 점으로 정하며, 거절·위치 서비스 꺼짐·위치를 못 찾음·기기 미지원을 안내한다. 그 뒤 검색은 그 좌표에서 가까운 곳부터 거리와 함께 보인다. 지도를 누르거나 상세 지도·현재 위치로 정한 점에는 주소가 없다(시·군·구 이름은 전처럼 보임). 백그라운드 위치는 쓰지 않고, 남기는 것은 사용자가 저장한 점과 주소뿐이다.
33. 입력 중 실수로 뒤로 가도 입력이 사라지지 않게(사용자 요청 2026-09-28). 기록 폼은 실시간 임시 저장: 입력이 멈추고 0.7초 뒤 폼 전체를 설정 테이블의 기기 키(`device.draft.record.new.<모드>` — 원두/카페/커핑 새 기록마다 하나, `device.draft.record.edit.<기록 id>` — 수정마다 하나; 백업에 넣지도 복원하지도 않음)에 JSON으로 쓰고, 폼을 연 상태와 같아지면 지운다. 쓰기는 앱 전역 스코프에서 요청 순서대로 실행되어 폼을 닫은 뒤에도 마지막 입력이 남는다. 다음에 같은 모드의 새 기록(또는 같은 기록의 수정)을 열면 초안을 불러오고 "✓ 작성하던 내용을 불러왔어요." 배너에 "새로 쓰기"(초안을 지우고 처음 상태로)와 "닫기"를 둔다. 저장에 성공하면 지우고, 30일 넘게 쓰이지 않은 초안은 열 때 버린다. 프로세스가 죽었다 살아난 경우에는 SavedStateHandle의 입력이 우선한다. 고른 봉투 사진(아직 저장 안 된 바이트)은 초안에 넣지 않는다: 떠날 때 묻는 창과 배너가 다시 골라야 한다고 알린다. 입력 폼 전부(기록·보관함 원두·장비·로스터리/수입사/농장/가공·블렌드·책·동영상·클래스·내 레시피)는 시스템 뒤로 가기·제목줄 ←·"취소"에서 폼을 연 상태(불러온 기록, 새 폼의 시작값; 프로세스 종료 후에도 SavedStateHandle에 함께 보관)와 입력이 다를 때만 묻는다(`LeaveGuard`): 기록 폼은 "작성 중인 내용이 있어요"(나가도 임시 저장된다는 안내) [계속 쓰기] [나가기] [지우고 나가기], 나머지는 "저장하지 않은 내용이 있어요 / 지금 나가면 입력한 내용은 사라져요." [계속 쓰기] [나가기]. 열자마자 뒤로 가거나 저장에 성공한 뒤에는 묻지 않고, 저장 중에는 전처럼 뒤로 가기를 막는다. 대화상자에는 버튼만 둔다(입력칸 없음). 초안은 입력 중 설정 테이블에 계속 쓰이므로, 홈 위젯은 설정 테이블 대신 D-day 시작일만 지켜보고, 앱 루트가 읽는 화면 설정(`DisplayPrefs.observe`)은 값이 바뀔 때만 전달한다.

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
│     │  ├─ domain/reference/    # FlavorWheel, NoteCategories, Processes, Varieties, CoffeeCountries, WorldMap, Champions, CafeRecipes, RoasteryMapPoints, KoreaMapData(생성)
│     │  ├─ data/db/             # Room: AppDatabase, entities, DAOs, converters, migrations
│     │  ├─ data/repo/           # EntryRepository, PantryRepository, MiscRepository, StudyRepository, BlendRepository, RoadmapRepository, SettingsRepository
│     │  ├─ data/backup/         # BackupCodec (웹 호환 JSON), BackupService
│     │  ├─ data/photo/          # PhotoStore(expect), ImagePicker(expect), ImageResizer(expect)
│     │  ├─ ui/theme/            # 색·타이포·형태 토큰, 컴포넌트
│     │  ├─ ui/nav/              # AppNav, Routes(type-safe), BottomBar
│     │  ├─ ui/extract/ ui/form/ ui/calendar/ ui/bean/ ui/misc/ ui/backup/  # 화면 + ViewModel
│     │  ├─ ui/notify/           # 알림 설정 절, 하루 점검(ReminderCheck), iOS 미리 예약(ReminderScheduleAhead), 위젯 데이터(HomeWidgetFeed) — 2차 §3
│     │  ├─ ui/settings/         # 설정 페이지(화면·알림·AI·정보), 화면 설정 저장(DisplayPrefs)
│     │  ├─ ui/ai/               # AI 노트 도우미: 제공자별 요청·응답(Gemini·Tavily·OpenAI·Claude), 출처 번호·원문 확인, 설정 절, 답 화면
│     │  └─ di/                  # Koin 모듈
│     ├─ androidMain/kotlin/     # Room 드라이버/DB 빌더, PhotoStore·ImagePicker·ImageResizer·BackupFileIo 실제 구현, WorkManager 알림(ReminderWorker·ReminderNotifier)
│     ├─ iosMain/kotlin/         # 같은 expect의 iOS 구현: Room 경로, PhotoStore(Documents/photos), PHPicker·카메라, UIDocumentPicker·공유 시트, 알림 미리 예약, NSURLSession·Keychain, MapLibre 상세 지도, MKLocalSearch·CLLocationManager
│     └─ commonTest/kotlin/      # 도메인 규칙·백업 코덱·DB 마이그레이션 테스트
├─ androidApp/                   # Android 애플리케이션 (MainActivity → App()), 홈 화면 위젯(Glance, widget/)
└─ iosApp/                       # SwiftUI 호스트 + XcodeGen project.yml(MapLibre Swift 패키지), CI(ios.yml)가 빌드
```

- 패턴: 단방향 데이터 흐름(UDF). 화면당 `ViewModel`(공유 모듈, `lifecycle-viewmodel`)이 `StateFlow<UiState>`를 노출하고 Repository(Flow)를 구독.
- 데이터 접근은 Repository만 통과. 도메인 규칙은 순수 Kotlin 함수(테스트 대상).
- DI: Koin. `initKoin(platformModule)`를 각 플랫폼 진입점에서 호출.
- iOS 타깃 게이팅: `coffeejournal.enableIos=true`이고 호스트가 macOS일 때(Xcode 빌드, CI의 macOS 작업), 또는 어느 호스트에서든 `coffeejournal.iosKlibs=true`일 때(Kotlin/Native 크로스 컴파일로 klib까지만, Linux CI의 `klibs` 작업) `iosArm64/iosSimulatorArm64` 타깃을 선언한다. 둘 다 없으면 Android만 빌드한다.

### 3.1 플랫폼 경계(expect/actual)
| 인터페이스 | Android | iOS |
|---|---|---|
| `DatabaseBuilder` | `Room.databaseBuilder(context, path)` + BundledSQLiteDriver | `NSDocumentDirectory` 경로 + BundledSQLiteDriver |
| `PhotoStore` | `filesDir/photos/*.jpg` | Documents/photos |
| `ImagePicker` | Photo Picker(`PickVisualMedia`) + 카메라(TakePicture) | PHPickerViewController / UIImagePickerController |
| `ImageResizer` | Bitmap 디코드·EXIF 회전·긴 변 1280px·JPEG 82 | UIImage |
| `BackupFileIo` | SAF `CreateDocument`/`OpenDocument` + 공유 시트 | UIDocumentPicker / ShareSheet |
| `Clock/Locale` | kotlinx-datetime 공통 | 공통 |
| `ReminderPlatform`(platformModule) | WorkManager 고유 주기 작업(24시간, 첫 실행 = 정한 시각) + 종류별 알림 채널, 알림을 누르면 보관함/홈 | 미리 예약(`IosReminderPlatform` + 공통 `ReminderScheduleAhead`): 다음 30번의 알림 시각에 보낼 알림을 `UNCalendarNotificationTrigger`로 예약(가까운 것부터 최대 64개), 앱 시작·포그라운드 복귀·데이터/설정 변경 때 다시 계산, 시각이 지난 것은 보낸 것으로 기록. 권한 상태는 캐시(`canNotifyChanges`로 화면 갱신), 앱을 쓰는 중에도 배너, 누르면 앱이 열림 |
| `rememberNotificationPermissionRequest` | Android 13+ `POST_NOTIFICATIONS` 요청(그 전은 앱 알림 켜짐 여부) | `requestAuthorizationWithOptions`(알림·소리·배지), 결과는 캐시를 갱신한 뒤 메인 스레드로 |
| `openMapUri(uri, fallback)` | 암시적 VIEW 인텐트(BROWSABLE), `ActivityNotFoundException`이면 웹 대체 URL | `canOpenURL`(Info.plist `LSApplicationQueriesSchemes`: nmap) 후 `openURL`, 안 되면 웹 대체 URL |
| `AiHttp`(platformModule) | `AndroidAiHttp`: `HttpURLConnection`(연결 15초·읽기 120초, IO 디스패처, 4xx/5xx는 오류 스트림) | `IosAiHttp`: `NSURLSession`(임시 세션, 응답 120초, 4xx/5xx 본문도 전달) |
| `SecretStore`(platformModule) | `AndroidSecretStore`: Android Keystore의 내보낼 수 없는 AES-256/GCM 키(`coffeejournal.ai`)로 암호화한 파일을 `noBackupFilesDir/ai-keys/`에 | `IosSecretStore`: Keychain 일반 암호, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`(iCloud 키체인·다른 기기 백업 제외) |
| `normalizeNfc` · `SearchSuggestions` | `java.text.Normalizer` · WebView(`loadDataWithBaseURL`, 스크립트 끔, 누르면 브라우저) | `precomposedStringWithCanonicalMapping` · 표시 없음 |
| `DetailMapRenderer`(Koin) | `MapLibreDetailMapRenderer`: 공용 `MapLibreDetailMap`(MapLibre Compose + MapLibre Native Android), `ConnectivityManager`로 온라인 확인, 네이티브 라이브러리를 못 올리면 실패 보고 | `MapLibreDetailMapRenderer`: 같은 `MapLibreDetailMap`(MapLibre Native iOS, Swift 패키지), Network 프레임워크 경로 모니터(`nw_path_monitor`)로 온라인 확인 |
| `DevicePlaceSearch`(platformModule) | `AndroidPlaceSearch`: `android.location.Geocoder`(한국어, `isPresent()`가 거짓이면 지원 안 함; API 33+ `GeocodeListener`, 그 전은 IO 디스패처의 `getFromLocationName`; 국내는 한국 상자 33.0–38.7°N, 124.5–131.9°E) | `IosPlaceSearch`: `MKLocalSearch`(자연어 질의, 주소 + 관심 지점; 국내는 한국 영역, 현재 위치가 있으면 그 주변 20 km로 치우침; "찾지 못함" 오류는 결과 없음) |
| `CurrentLocation`(platformModule) + `rememberLocationPermissionRequest` | `AndroidCurrentLocation`: `LocationManager`만(Play 서비스 없음). 2분 안·100 m 안의 최근 위치가 있으면 그것, 없으면 fused(12+)·network·gps(정밀 허용 시)에 동시에 한 번 묻고 먼저 온 답(API 30+ `getCurrentLocation`, 그 전은 `requestSingleUpdate`), 취소하면 모두 멈춤. 권한은 ActivityResult `RequestMultiplePermissions`(FINE+COARSE)로 버튼을 누를 때만 | `IosCurrentLocation`: `CLLocationManager`(메인 스레드), `requestWhenInUseAuthorization` 후 `requestLocation` 한 번(10 m 정확도 요청), 취소하면 `stopUpdatingLocation`. 위치 서비스가 꺼진 거절은 공통 코드가 `servicesOn()`으로 구분 |

---

## 4. 데이터 설계 (Room, SQLite)

식별자는 웹과 동일한 문자열 id(base36 시각+랜덤)를 유지해 백업이 왕복 가능하게 한다. 목록형 부속 데이터는 JSON 컬럼(kotlinx-serialization TypeConverter)으로 저장하되, 통계 질의가 필요한 커핑 원두는 별도 테이블로 정규화한다.

| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| `entries` | id PK, created_at, category('원두'/'카페'/'커핑'), bean_mode, name, country, region, altitude, variety, farm_producer, roastery, selection, washing_station, process, process_other, package_type, moisture, density, score, arrival, roast_date, roaster_desc, roast, bag_weight, price, cafe_name, expected_notes, actual_notes, dripper, filter, dose, water, temp, grind, water_type, time, notes, cupping_type, cupping_place, steps(JSON), recipe_ref(JSON), blend_components(JSON), attributes(JSON), attribute_notes(JSON), tags(JSON), legacy_extra(JSON: beanGuidance·photoFeedback·adviceChat·consultation·practice·noteChat 보존) | 인덱스: created_at, category, lower(name) |
| `cupping_beans` | id PK, entry_id FK(CASCADE), position, name, country, region, roastery, farm_producer, altitude, variety, price, rank, process, roast, expected_notes, actual_notes, evaluation(JSON), evaluation_scores(JSON), memo, bean_mode, blend_components_text | |
| (사진) | `entries.bag_photos`(JSON 파일명 목록, 최대 2) · `entries.grounds_photo` | 구현 시 별도 테이블 대신 컬럼으로 단순화. 파일은 PhotoStore |
| `pantry_items` | id, name, roastery, package_type, weight, price, roast_level, roast_date, purchase_date, peak_start, peak_end, expected_notes, notes, status('unopened'/'opened'), opened_at, created_at, source_entry_id | |
| `misc_items` | id, type, name, notes, since, status, scope, location, favorite, photos(JSON 파일명 목록), created_at, lat, lng | type: dripper, filter, kettle, thermometer, scale, water, source, selection, process, variety, farm. `lat`/`lng`(REAL, nullable, v2): "지도에서 위치 지정"으로 찍은 로스터리 좌표(WGS84), 둘 다 있거나 둘 다 없음 |
| `cafe_places` | name PK, lat, lng, created_at, address | v2. 카페 기록의 카페 이름별 위치(카페 기록은 이름만 가지므로 이름당 1행, 이름은 앞뒤 공백·대소문자 무시로 대조). "+ 카페 추가"로 등록한 카페(방문 기록 없음)도 이 표의 행이고, 위치가 없으면 lat/lng가 비어 있다. "위치 지우기"는 행을 남기고 좌표와 주소만 비운다. `address`(TEXT, nullable, v3): 위치 지정에서 검색으로 고른 곳의 주소, 지도를 눌러 정한 점에는 없음 |
| `books` | id, created_at, title, author, status, start_date, end_date, rating, notes | |
| `videos` | id, created_at, title, channel, url, notes | |
| `classes` | id, created_at, title, class_type, date, start_date, end_date, notes | |
| `blends` | id, name, date, beans(JSON), notes, created_at | |
| `my_recipes` | id, name, from_entry_id, bean_name, rating, dose, water, temp, dripper, filter, grind, time, steps(JSON), created_at | |
| `roadmap_phases` | id, position, title, range_label, day_start, day_end, items(JSON) | 기본 1단계 시드 |
| `bean_summaries` | bean_key PK, text, generated_at | 수동 총정리 |
| `best_recipes` | bean_key PK, entry_id | |
| `settings` | key PK, value | `brew-start-date`, 마지막 필터 등. `device.` 접두 키는 이 기기 설정(알림: `device.reminders.enabled/peak/lowStock/dday/time/sent`, iOS의 `device.reminders.scheduled`)으로 백업하지 않는다 |

- 마이그레이션: `room-gradle-plugin` 스키마 export(`shared/schemas/…/1.json, 2.json, 3.json`) + `autoMigrations`. v1 → v2(2차 지도 기능)는 `AutoMigration(1, 2)`: `misc_items`에 `lat`/`lng` 열 추가, `cafe_places` 생성. 기존 행은 그대로이고 좌표는 비어 있다. v2 → v3(위치 지정 검색)은 `AutoMigration(2, 3)`: `cafe_places`에 `address` 열 추가, 기존 카페는 주소 없음(`DatabaseMigrationTest`가 1.json·2.json대로 만든 파일을 앱의 빌더로 열어 확인). 실제 열 이름은 엔티티 속성 이름(`createdAt` 등)을 따른다.
- 커핑 원두 이외의 목록형 필드(steps, recipeRef, blendComponents — 카페 블렌드면 원두별 필드와 `percent`까지, attributes, attributeNotes, tags, 사진 파일명)와 웹 전용 필드(`legacy_extra`: beanGuidance·photoFeedback·adviceChat 등)는 kotlinx-serialization JSON 컬럼으로 저장한다.
- 사진: `PhotoStore`가 `photos/<uuid>.jpg`로 저장, DB에는 파일명만. 삭제 시 파일도 삭제.
- 성능 가정: 개인 저널(수천 건 이하) → 기록 전체를 메모리에 로드해 웹과 같은 방식으로 파생 통계를 계산(Flow combine).

### 4.1 백업 JSON (웹 호환)
```
{ "exportedAt": ISO,
  "data": { "entries": [...웹 스키마 그대로, cuppingBeanDetails 포함...], "miscItems", "blends", "classes", "roadmapData", "myRecipes", "books", "beanSummaries", "bestRecipes",
            "videos": [...], "pantryItems": [...], "settings": {...} },          // 앱 확장 키
  "rawData": { "ddayStart": "YYYY-MM-DD" },
  "photos": { "bag-photo:<id>": "[dataURL,...]", "journal-photo:<id>": "dataURL" },
  "app": { "name": "coffee-journal-mobile", "schema": 3 } }
// schema 2: miscItems 객체에 선택 키 "lat", "lng"(위치를 찍은 로스터리만), data에 앱 확장 키 "cafePlaces": [{ "name", "lat", "lng", "createdAt" }]
// blendComponents 객체: 웹의 { "name", "grams" } + 값이 있을 때만 "percent", "roastery", "selection", "country", "region", "farmProducer",
//   "washingStation", "altitude", "variety", "moisture", "density", "score", "process", "processOther", "roast", "roastDate"(카페 블렌드의 원두)
// schema 3: cafePlaces 행에 선택 키 "address"(검색으로 고른 곳만)
```
- 가져오기: 웹 파일(확장 키 없음)도 그대로 수용하며, 웹이 앱 시작 시 수행하던 데이터 이전(`WebMigrations`: SCA 1–5점 척도 → 6–10점, 옛 `aroma` → `aromaIntensity`, 0–15 강도 → 1–5 0.5단위)을 복원 시 동일하게 적용하고, 손상된 항목은 건너뛰고 항목별 실패 수로 보고한다. `entries`는 모드와 무관하게 항상 id 기준 upsert(병합), 나머지 컬렉션은 "교체"/"병합" 중 선택(기본 병합). 사진 data URL은 파일로 복원(이미 규격(긴 변 1280px 이하·정방향 JPEG) 안인 사진은 재인코딩 없이 그대로 저장해 백업↔복원 왕복에도 화질이 떨어지지 않는다).
- 지도 데이터(schema 2): `miscItems`의 `lat`/`lng`는 둘 다 유한하고 범위 안일 때만 좌표로 읽는다(깨진 값은 좌표 없음, 파일 거절 아님). 병합에서 백업 항목에 좌표가 없으면(웹 파일) 기존 좌표를 유지하고, 교체는 백업 값 그대로. `cafePlaces`는 이름으로 병합(백업 행이 이긴다; 주소가 없는 schema 2 행은 같은 좌표일 때 이 기기의 주소를 유지), 교체는 표를 통째로 바꾸며, 키가 없는 파일(웹 백업)은 카페 위치를 건드리지 않는다. 결과 줄은 `cafePlaces: ✓ N개 복원됨`.
- 원자성: 사진 파일 → DB 트랜잭션 1회 → 커밋 후 참조가 끊긴 옛 사진 파일 삭제. 도중 실패 시 롤백 + 이번 복원이 쓴 파일 삭제로 복원 전 상태를 그대로 유지한다. 결과는 웹과 같은 문구의 항목별 줄(`entries: ✓ 3개 복원됨` 등)로 보고한다.
- 파일 크기 상한: `BackupFileLimits.maxFileBytes(힙)` = 힙/5, 16–256MB로 제한. 일반 기기(힙 256–512MB)에서 50–100MB 정도가 한계이며, 더 큰 웹 백업을 받으려면 `android:largeHeap` 사용 여부를 결정해야 한다(현재 미사용).
- `settings`는 통째로 내보내되 `device.` 접두 키(알림 설정·보낸 알림 기록·화면 설정)는 빼고, 가져올 때 파일에 있어도 무시하며, 교체도 이 기기의 값은 지우지 않는다(다른 폰의 알림이 권한 없이 켜지는 일 방지).
- 기기 백업(Android Auto Backup / 기기 간 전송)은 DB·WAL·사진 폴더만 포함한다. 클라우드 백업은 앱당 25MB 할당량을 넘으면 Android가 통째로 건너뛰므로, 앱 내 JSON 백업이 기본 이전 수단이다.
- 내보내기: 사진을 data URL로 포함(웹과 동일)하되 용량 안내 표시. 파일명 `커피일지-백업-YYYY-MM-DD.json`.

---

## 5. 화면 설계

### 5.1 내비게이션
- 하단 탭 4개: 새로운 추출 · 커피 달력 · 원두 · 기타 (웹 메인 탭 순서 유지, 탭별 백스택 보존).
- 전체 화면 라우트: `RecordForm(entryId?, mode)`, `EntryDetail(id)`, `PantryEditor(id?)`, `BlendForm(id?)`, `BookForm(id?)`, `VideoForm(id?)`, `ClassForm(id?)`, `MiscForm(type, id?)`, `FlatItemForm(type, id?, scope?)`(로스터리·수입사·농장·가공; scope는 새 로스터리를 지도 탭의 국내/해외로 시작), `CafeAdd`("+ 카페 추가": 카페 이름 → 카페 위치 지정 또는 위치 없이 저장), `RecipeLauncher(kind)`, `BackupRestore`, `CountryDetail(en)`, `NoteDetail(key)`, `VarietyDetail(key)`, `ProcessDetail(name)`, `MapPicker(target, name, scope, point?)`("지도에서 위치 지정": 검색·현재 위치 포함, 로스터리는 폼으로 좌표(검색으로 고르면 "lat,lng<탭>주소")를 돌려주고, 카페는 주소와 함께 바로 저장), 2차: `BrewTimer(recipe?, hasLog)`(추출 타이머, 결과는 폼 목적지의 SavedStateHandle로 돌려줌), `BrewCompare(beanKey)`(추출 비교), `Stats`(통계), `Settings`(설정: 화면·알림·AI 노트 도우미·정보), `DetailMap(mode, layer, scope, camera?, bounds?, focus?, name)`(상세 지도: 보기 또는 위치 지정 십자, 결과는 위치 지정 화면의 SavedStateHandle로), `NoteHelper(mode, query, returnToForm)`(AI 노트 도우미의 답 화면: mode "note"/"describe", 고른 노트 후보는 폼 목적지의 SavedStateHandle로).
- 앱 밖에서 여는 화면(알림·위젯): `LaunchRequests`에 홈 / 보관함 / 새 기록을 넣으면 내비게이션이 한 번 받아 홈 탭 루트 위에 연다(`MainActivity`가 인텐트에서 읽음).
- 웹의 "펼침 카드"는 모바일에서 `EntryDetail` 화면 + 목록의 접이식 요약으로 대체.

### 5.2 탭별 화면
**새로운 추출(Home)**: 상단 헤더 `coffee_journal`(웹의 " / 2026"은 뺌, 사용자 요청)·`N entries`·설정 톱니바퀴 → D-day 알약(미설정 시 날짜 입력 행) → 마시는 중 카드(가로 스크롤) → 액션 행(새 기록, 원두 보관함, 통계, 백업) → 필터 칩(전체 / 드립백·소량 → 전체·드립백·소량) → 검색창 → 원두 그룹 목록(헤더: 썸네일·이름·로스터리/수입사/농장·기간·N개 기록·최고 점수; 펼치면 총정리·베스트 레시피·"📊 추출 비교"(기록 2개 이상)·기록 요약 행; 헤더의 "최고"는 SCA 2004, CVA 기록이 있으면 "CVA 최고"를 따로). FAB `+`.

**추출 비교**: 원두 이름, 기준 설명, 표(고정된 행 이름 열 + 함께 밀리는 기록 열: 날짜, 원두량, 물량, 비율, 온도, 분쇄도, 총시간, 드리퍼, 점수, 노트). 열 머리(기준/비교 + 날짜)를 누르면 그 기록 상세.

**추출 타이머**: 레시피 카드(지금 단계·다음 단계까지 N초·목표 g(누적)) → 큰 모노 시계 + 상태 → 시작/일시정지/계속 · 초기화 → 붓기 시작/끝(늘 누를 수 있음) → 물량 패널(붓기 끝 뒤나 푸어 행을 누르면: 어림값을 옅게 미리 채운 입력칸·확인·붓기 계속) → 뜸·스월·드로우다운 → 지금까지의 행(어림 물량은 "≈ 60g", 푸어 행은 눌러서 고침, 옆의 옅은 ✕로 지우고 그 자리의 "되돌리기") → 하단 "추출 끝"(끝난 뒤 "단계 로그로 옮기기"·"이어서 추출", 옮길 행 미리보기와 레시피 대비 차이).

**통계**: 기간 칩 → 기간 → 타일(잔 수·원두 g·지출) → 잔 수 막대 → 원두 사용량·지출 → 산지·가공·품종·로스터리 상위 → 점수 추이 → 비율·점수, 온도·점수 산점도. 비어 있으면 "이 기간에는 아직 기록이 없어요. 오늘 내린 커피부터 남겨보세요."

**기록 폼**: 상단 세그(원두/카페/커핑, 진입 모드에 따라 고정) → 섹션 카드 순서: 레시피로 시작(원두) · 기본(날짜시간, 원두 형태) · 원두 정보(카페 모드: 카페 이름(아는 카페 자동완성, 이름을 적으면 그 카페의 위치와 "위치 지정/변경" — 카페 위치 지정 화면에서 바로 저장) · 원두 구성 "단일 원두 | 직접 블렌드"·이름 자동완성·가격, 원두 한 알마다 같은 블록: 로스터리(목록에 없는 이름이면 "새 로스터리예요. 저장하면 로스터리 목록에도 추가돼요.")·수입사·국가·지역·농장·워싱스테이션·고도·품종·수분율·밀도·CoE·가공(세그+기타+세부)·로스팅·로스팅일, "+ 원두 추가 (블렌드)"로 블록 추가(둘 이상이면 "원두 N · 비율 % · ✕"와 합계, 원두 2부터의 로스터리·로스팅은 원두 1 값을 회색으로), 그다음 한 번만: 총량·입고·예상 노트 칩·가게 설명·봉투 사진 2) · 레시피(드리퍼·필터·분쇄도·원두량·물량·온도·총시간(자동)·물, 접이식 비율·추출수율 계산기) · 추출 예시(읽기 전용) · 단계 로그(행 편집, "⏱ 타이머로 기록", 요약, 차이) · 테이스팅("SCA 2004 | CVA" 선택: SCA 슬라이더 13행+메모 또는 CVA 결합 양식(상자별 강도·묘사·품질 인상·노트, 컵, 점수), 플레이버 휠 시트, 내가 느낀 노트 칩+추천 칩) · 메모 · 저장/취소(하단 고정). 커핑 모드는 유형·장소·원두 카드 목록(카드마다 웹과 동일 필드)·전체 경험. 카페 모드는 `.cafe-hide` 대응 필드 숨김, 레시피는 "레시피 입력 (카페에서 알려준 경우)" 한 줄로 접힘(§2.3 31). 초안을 불러오면 맨 위에 "✓ 작성하던 내용을 불러왔어요." 배너(새로 쓰기 · 닫기, 고른 사진이 빠졌으면 다시 고르라는 줄). 입력이 바뀐 채 떠나면 묻는 창(§2.3 33).

**커피 달력**: 상단 칩(전체/스터디/클래스/커핑/카페/원두) → 월 헤더(‹ 오늘 ›) → 그리드 → 날짜 패널(바텀시트) → Today 원두 → 범례 → 필터별 하단 섹션(카페·커핑 목록 — 카페는 그 아래 "카페 위치": 목록의 카페와 방문 기록 없는 카페, 위치 지정·삭제(방문 없는 카페만)와 "+ 카페 추가" / 이 달 추출 기록 / 커피 공부(로드맵·커핑 리뷰 모음)). 스터디·클래스는 같은 탭의 별도 뷰.

**원두**: 상단 가로 스크롤 서브탭 9개(기본 커피 지도). 각 뷰는 웹 구성을 세로 스크롤 화면으로 재배치. 커피 지도는 Canvas에 SVG 폴리곤(M/L/Z만 사용, 175개국)을 그리고 탭 좌표를 point-in-polygon으로 판정, 핀치 줌 지원.

**로스터리 지도(2차 §1)**: 한국 | 해외 탭. 한국은 "로스터리 지도 | 카페 지도" 전환. 지도 아래에 그 층의 "+ 로스터리 추가"(보던 탭의 국내/해외로 폼 시작) 또는 "+ 카페 추가".
- 한국 지도: 전국(16 시·도 경계, 핀이 있는 시·도는 옅은 갈색)에서 시·도를 누르면 그 시·도의 시·군·구 지도(주변 시·도는 옅게)로 확대, "← 전국"·시스템 뒤로 가기로 복귀. 두 손가락 확대·이동(확대 후 한 손가락 이동), "전체 보기". 멀리 떨어진 섬(울릉도·독도, 옹진 섬들)은 축소해서 본다.
- 핀: 좌표가 있으면 그 자리, 없으면 지역 글에서 찾은 시·군·구(없으면 시·도) 중심. 이름 칩은 서로 겹치지 않게 비켜 놓고 점까지 지시선을 긋는다. 전국 지도에서는 한 시·도에 2곳 이상이면 "서울 3곳" 칩 하나로 묶고, 핀(또는 묶음)을 누르면 그 시·도로 확대해 선택한다(작은 시·도가 칩에 가려 눌리지 않는 문제 방지).
- 선택 패널: 기존 로스터리 패널(위치, 연결된 기록) + 찾은 지역("📍 서울특별시 성동구" / "○○ 중심에 표시") + "네이버 지도에서 열기"·"카카오맵에서 열기"(해외는 "Google 지도에서 열기"가 먼저). 위치 미입력, 지도에서 찾지 못한 곳은 지도 아래 상자로(로스터리마다 "위치 입력"/"위치 수정"이 그 로스터리 폼을 연다).
- 카페 지도: 카페 기록(카테고리 카페, 카페 이름 있음)을 카페 이름별로 묶은 카페와 "+ 카페 추가"로 등록한 카페(방문 기록 없음, `cafe_places` 행). 이름은 앞뒤 공백·대소문자 무시로 한 카페. 위치가 있는 카페를 방문 횟수와 함께(방문 없으면 숫자 없이) 표시, 누르면 방문 기록 목록(→ 기록 상세, 없으면 "방문 기록 없음")·(검색으로 정했으면) 주소·지도 앱 링크·"지도에서 위치 변경". 위치 미지정 카페는 목록과 "지도에서 위치 지정" 버튼. 방문 기록이 없는 카페만 "삭제"(확인 후 행 삭제); 방문한 카페는 기록에서 나오므로 지울 수 없고 위치 지정 화면의 "위치 지우기"로 좌표와 주소만 지운다. 기록이 모두 지워진 카페는 위치 행이 있으면 방문 기록 없는 카페로 남는다.
- 카페 추가(`CafeAdd`): 카페 이름(아는 카페 자동완성). 이미 있는 카페 이름이면(다른 표기 포함) "이미 있는 카페예요: …"로 알리고 새로 만들지 않는다. "지도에서 위치 지정"은 카페를 저장한 뒤 카페 위치 지정 화면을 이 화면 자리에 연다(저장하면 카페를 더한 화면으로 돌아감), "위치 없이 저장"은 위치 없이 등록. 입력칸은 대화상자가 아니라 화면이다(대화상자 속 입력칸은 Robolectric에서 idle이 되지 않음).
- 해외 지도: 앱의 Natural Earth 세계지도(무채색)에 핀. 좌표 또는 지역 글의 나라 중심(웹 roasteryPoint의 나라 목록 + 생산국 45 + 지도 영문 국가명).
- 상세 지도(§1.7): 시·도 지도(또는 핀 선택 중)의 오른쪽 위 "상세 지도"는 지금 보이는 범위(선택한 핀이 있으면 그 핀)로, 패널의 "상세 지도에서 보기"는 그 핀으로 연다. SGIS 지도의 확대 상태는 TalkBack에 "확대 2.5배"/"기본 배율"로 읽힌다.

**상세 지도(DetailMap)**: 제목줄 → OpenStreetMap 지도(MapLibre, 회전·기울이기 없음, 두 손가락 확대·이동) + 왼쪽 아래 출처 줄 → 잉크 2dp 선 → 아래 패널(화면 높이 42%까지 스크롤): 선택한 핀의 패널(SGIS 지도와 같은 로스터리/카페 패널, "선택 해제") 또는 사용법과 "지도에 표시한 로스터리/카페" 칩 목록(누르면 그 핀으로 이동·선택; 카페는 위치 미지정 카페 상자도), "+ 로스터리 추가"(지도의 국내/해외로 시작)/"+ 카페 추가"(더하고 돌아오면 바로 핀·목록에 보임), 그리고 네트워크 안내문. 좌표가 있는 핀은 잉크 점, 지역 중심 핀은 속 빈 원. 위치 지정 모드는 가운데 십자(흰 테두리 잉크 + 빨간 점), 찾은 "시·도 시·군·구"와 좌표, "이 위치로 지정"·"취소". 네트워크가 없으면 "인터넷에 연결되어 있지 않아요" 안내와 "← 한국 지도로 돌아가기"·"다시 시도", 렌더러 실패는 "상세 지도를 표시하지 못했어요", 20초 넘게 로딩이 끝나지 않으면 지도 위 알림.

**위치 지정(MapPicker)**: 로스터리 폼("지도 위치" 칸의 "지도에서 위치 지정/변경", "위치 지우기"), 카페 기록 상세의 "카페 위치", 달력 카페 목록 아래 "카페 위치", 카페 지도·상세 지도의 위치 미지정 상자, 카페 추가 화면, 카페 기록 폼의 카페 이름 아래 "위치 지정"에서 연다. 이름 아래 "주소나 이름으로 찾기" 칸(장소 이름으로 채워 둠) + "검색", "현재 위치"(위치 아이콘); 그 아래 결과 목록(이름 굵게, 주소, "카페 · 현재 위치에서 350 m", 끝 줄에 "검색: 카카오/기기 지도 서비스"와 "결과 닫기") 또는 안내 한 줄(§2.3 32). 결과를 누르면 목록이 닫히고 점·시·도 지도·주소 줄이 바뀐다. 국내는 전국에서 시·도를 눌러 확대한 뒤 누른 곳이 위치(빨간 표식), 찾은 "시·도 시·군·구", 검색으로 고른 곳이면 그 주소, 좌표, "현재 위치"의 결과("현재 위치로 정했어요 (오차 약 18 m)." 또는 못 정한 이유)를 보여준다. 해외 로스터리는 세계지도에서 누르고 나라 이름을 보여준다. 로스터리는 "확인"으로 폼에 돌려주고(지역 칸이 비어 있으면 검색으로 고른 곳의 주소, 없으면 "서울특별시 성동구"나 "일본"으로 채움) 폼에서 저장해야 반영된다. 카페는 "저장"으로 바로 저장(주소 포함). 카페 패널·카페 위치 줄에는 시·군·구 아래에 저장한 주소가 보인다. 기존 위치가 있으면 그 시·도에서 시작한다. 지도 아래 "상세 지도에서 정확히"는 상세 지도(지정한 점, 없으면 보이는 시·도)를 십자 모드로 열고, "이 위치로 지정"한 가운데 좌표가 위치 지정 화면의 점이 된다(찾은 지역 표시·폼 채우기는 같은 규칙).

**기타(장비)**: 유형 칩 → 정렬 토글 → 카드(사진·이름·시작일·메모) → FAB 추가. (알림·출처 링크는 설정으로 옮겼다.)

**설정**: 머리글 톱니바퀴 → 화면(글꼴 · 제목·숫자 글꼴 · 글자 크기 · 화면 전환, 모두 사각 세그먼트 + 안내문) → 미리보기 카드 → 알림(아래 5.4) → AI 노트 도우미(방식 세그먼트 4개와 설명, "키 받는 방법" 단계 안내와 링크, 방식에 필요한 키 칸(암호 입력, 저장 뒤에는 "저장됨 …a1b2"·키 확인·지우기), 모델 칸과 추천 칩, Gemini 무료 + Tavily일 때만 "검색"(기본 / 정밀 / 정밀+기본)과 "사람들 의견"(켬 / 끔) 세그먼트, 각 안내, "질문 한 번에 약 N크레딧 · 무료 1,000크레딧이면 한 달 약 M번" 한 줄) → 정보("출처 · 오픈소스 라이선스 →").

**AI 노트 도우미(NoteHelper)**: 제목줄 → 질문(모드 이름, “질문”) → 처음이면 보내는 내용 확인 창 → 묻는 동안 "출처를 찾아 답을 쓰고 있어요"와 점 셋이 하나씩 늘었다 다시 시작(480ms마다, 화면 전환 끔이면 멈춘 "…")하고, 그 아래 단계 줄(Gemini 무료 + Tavily "검색어 만들기 › 검색 › 답 쓰기", 고친 검색어로 다시 물으면 첫 단계를 그어 건너뜀; 다른 방식은 요청 하나라 "검색하고 답 쓰기"): 진행 중인 단계는 굵게 + 같은 점, 끝난 단계 ✓, 남은 단계 흐리게 → 방식·모델 줄, "AI 요약은 틀릴 수 있어요. 출처를 확인해 주세요." → 답(문장마다 작은 [n] 링크, 출처 없는 문장은 회색, Tavily·Claude는 인용 문구와 "✓ 원문 확인" 배지) → "검색어: …"(Tavily 방식은 "검색어 고치기" 입력칸과 "이 검색어로 다시 묻기") → (Google 검색) 검색 제안 → (모드 B) 노트 후보 칩과 "노트에 추가" → 출처 목록(번호·제목·도메인·기관/개인 의견 배지, 누르면 브라우저) → "다시 묻기". 키가 없거나 틀리면 이유와 "설정에서 키 넣기 →"(설정이 AI 절로 스크롤되어 열림).

### 5.4 알림 · 홈 화면 위젯 (2차 §3)
- **알림(설정 페이지의 절)**: 안내문 → "알림 받기"(마스터, 처음엔 꺼짐) → 알림 종류(피크 시작 / 원두 소진 임박 / D-day 마일스톤, 기본 켜짐) → 알림 시각(기본 09:00, 기록 폼과 같은 시간 선택 창). 스위치는 사각·헤어라인·켜지면 잉크. Android 13+와 iOS에서 켤 때 알림 권한을 묻고, 거절하면 스위치는 꺼진 채 빨간 안내와 "알림 설정 열기 →"(앱 알림 설정 화면, iOS는 설정 › 알림 › 이 앱). 켜 둔 뒤 권한이 사라지면 같은 안내를 보인다. 시각을 바꾸면 하루 점검을 다시 예약한다.
- **하루 점검**: WorkManager 주기 작업이 정한 시각에 `ReminderCheck`를 돌려 ① 보관함 원두의 예상 피크 시작일(`PantryRules.peakWindow`) ② 마시는 중 카드의 잔여량 ≤ 2잔(1잔 = 그 원두 기록 원두량의 중앙값, 없으면 15 g) ③ Coffee D-day 30·100일 단위를 알린다. 보낸 알림은 키로 기억해 다시 보내지 않고, 권한이 없거나 채널을 끈 날은 보낸 것으로 치지 않는다. 채널 이름: 피크 시작 / 원두 소진 임박 / D-day 마일스톤. 피크·소진은 누르면 원두 보관함, D-day는 홈.
- **홈 화면 위젯**(Jetpack Glance, 3×2 기본, 가로·세로 크기 조절, 최소 180×100dp): D-day 알약 문구(큰 크기에서는 "YYYY.MM.DD 첫 추출"과 기념 문구), 마시는 중 카드의 이름·"잔여량 Ng/Ng"(큰 크기에서는 "마시는 중 · …" 줄, 개봉 원두가 여럿이면 "외 N"), 빈 상태 "커피 처음 마신 날을 기록해두면 며칠째인지 보여드려요." / "아직 마시는 중인 원두가 없어요. 오늘 내린 커피부터 남겨보세요.", "+ 새 기록"(새 기록 폼으로 바로), 나머지 영역은 홈. 아이보리 바탕·잉크 글자·모노 숫자·직각·헤어라인 테두리. 위젯 선택기 미리보기(`coffee_widget_preview`)와 설명은 한국어.
- **위젯 갱신**: 앱이 살아 있는 동안 기록·보관함·블렌드 테이블이 바뀌거나(Room 무효화 추적) D-day 시작일이 바뀌면(0.8초 묶음; 설정 테이블 전체는 보지 않는다 — 기록 폼 초안이 입력 중 계속 쓰이므로) 그리고 자정에, 앱이 백그라운드로 갈 때(`MainActivity.onStop`), 하루 점검 때, 위젯이 있는 동안 매일 자정 직후(WorkManager `coffee-journal.widget-midnight`).
- **매니페스트 권한**: `POST_NOTIFICATIONS`(직접 선언). WorkManager가 병합하는 `RECEIVE_BOOT_COMPLETED`(재부팅 후 예약 복구), `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`. 위젯 수신자 `.widget.CoffeeWidgetReceiver`(exported, `APPWIDGET_UPDATE`, `@xml/coffee_widget_info`).
- **iOS 미리 예약**: iOS는 백그라운드에서 매일 도는 점검을 보장하지 않으므로, 앱 시작·포그라운드 복귀·기록/보관함/블렌드/D-day 시작일/알림 설정 변경(0.8초 묶음) 때 같은 규칙(`ReminderData.dueOn`, 하루 점검과 같은 함수)으로 다음 30번의 알림 시각마다 보낼 알림을 구해 각 알림을 처음 나오는 날 한 번 `UNCalendarNotificationTrigger`(현지 날짜·시각)로 예약한다. 이 앱의 예약(식별자 접두 `coffee-journal.reminder.`)만 바꾸고, iOS 대기 알림 한도 때문에 가까운 것부터 최대 64개. 시각이 지난 예약은 iOS가 보낸 것이므로 다음 계산 때 같은 보낸 기록에 적고 다시 예약하지 않는다. 피크·D-day는 날짜가 정해져 있고, 소진 임박은 계산 때의 기록(기록한 추출의 원두량)으로 정해져 기록을 고치면 옮겨지거나 취소된다. 권한이 없으면 예약하지 않는다. 앱을 쓰는 중에도 배너+소리, 누르면 앱이 열린다. 위젯(WidgetKit)은 아직 없다.

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
플레이버 휠 9/85, 향미 분류 9/43, 노트 동의어 45, 가공 4+4(허니 세부 5), 품종 참조 26·계보 21·표시명·한국어명 39, 커피 생산국 45/산지 60(+좌표), 지역 동의어 25, 국가 동의어 10, 대륙 매핑, 세계지도 SVG 175 폴리곤(viewBox 138 100 788 283, 회귀선 y 204.7/330.0; 등장방형 8/3 단위/도, 경도 −180° = x 0, 적도 y 267.35), 로스터리 배치 좌표 국내 11/해외 15(웹 원본 참고용, 지도는 2차에서 실제 좌표로 대체), WBrC 챔피언 9, 카페 레시피 4(단계 포함), 일반 단계 예시 6, 배전도 5단계, 장비 유형 6, 점수 티어 4.

추가(2차, 생성 데이터): 한국 지도 `KoreaMapData` + `KoreaMapProvince<코드>`(16 시·도, 시·군·구 256 = `sgg` 코드 기준·일반구 포함, 대표점, 시·도별 첫 화면 틀). `tools/korea-map/build_korea_map.py`가 vuski/admdongkor ver20260701 행정동 GeoJSON(SGIS 경계 보정본, 입력 SHA-256을 파일 머리말에 기록)을 병합·GEOS coverage 단순화(공유 경계 유지)·투영(x = (경도 − 124.5)·cos 36°·10000, y = (39 − 위도)·10000, 1단위 ≈ 11 m)해 생성한다. 0.5 km² 미만 섬 조각만 버리고 제주·울릉도·독도·서해 섬은 남긴다. 생성 Kotlin 약 235 KiB, 좌표 약 5만 점, 문자열 상수마다 60 KB 미만.

---

## 8. 테스트·품질
- `commonTest`: 도메인 규칙(이름 정규화, 범위 계산, 피크, SCA, 단계 요약·차이, 노트 정규화, 국가 판별), 백업 코덱 왕복(웹 샘플 JSON 포함), 저장 파이프라인.
- Android 계측: Room 마이그레이션·DAO 스모크(선택).
- 빌드 게이트: `:shared:testDebugUnitTest` + `:androidApp:testDebugUnitTest` + `:androidApp:assembleDebug`.
- 공급망: 공식 저장소만 사용(Google Maven·Maven Central·Gradle Plugin Portal), 모든 의존성 파일은 `gradle/verification-metadata.xml`(SHA-256)로, Gradle 배포본은 `distributionSha256Sum`으로 검증. 앱 내 라이브러리 목록은 `checkThirdPartyNotices`가 빌드마다 의존성과 일치하는지 확인.
- 화면 검증: 에뮬레이터 없이 Robolectric + Roborazzi로 실제 Compose 화면을 JVM에서 렌더해 PNG로 남긴다(`./gradlew :androidApp:recordRoborazziDebug` → `androidApp/screenshots/`). 테스트는 인메모리 Room(프레임워크 SQLite 드라이버)과 샘플 데이터(`SampleData`)를 주입한다.
- 흐름 테스트: 같은 환경에서 실제 `App()`을 띄워 탭·입력으로 사용자 흐름 전체를 수행하고, 화면 문구와 저장된 데이터를 웹 원본 핸들러 기준으로 함께 검증한다(백업 왕복, 원자적 복원, 한글 IME 조합, 자정 전환, 연속 탭 경합, 저장 중 뒤로 가기 포함).
- 카페·로스터리 추가(`PlaceAddFlowTest`): 저장소(추가는 이름당 한 행, 위치 지우기는 행 유지, 삭제), 달력에서 더한 카페가 다시 시작하지 않고 카페 지도(핀)·상세 지도(핀·목록)에 보이고, 상세 지도에서 더한 카페가 카페 지도·달력에 보임(같은 Room 흐름), 이미 있는 카페 이름은 두 번 만들지 않음, 위치를 지워도 등록한 카페는 남고 방문 없는 카페만 삭제, 해외 지도에서 더한 로스터리는 해외로 시작해 목록·지도에 바로, 위치 미입력 상자의 "위치 입력", 상세 지도에서 더한 로스터리가 그 자리 핀으로, 카페 기록 폼의 "위치 지정"과 새 로스터리 안내. 단위 테스트(`CafeMapLogicTest`): 방문 없는 카페 포함·이름 대조로 한 카페·삭제 규칙·달력 목록(보이는 기록의 카페 + 방문 없는 카페)·이름 찾기, `FormMapperTest`의 새 로스터리 판정, `DetailMapTest`의 방문 없는 카페 핀.
- 렌더 매트릭스: 모든 라우트를 기본·320dp 폭·글자 1.3/2.0배로 렌더해 줄바꿈·잘림·겹침을 확인한다(`ScreenshotMatrixTest`).
- 알림·위젯(2차 §3): 규칙 단위 테스트(`RemindersTest`: 피크 시작일, 2잔 경계·중앙값 1잔, 마일스톤, 중복 방지, 종류 끄기, 홈 카드 잔여량 연결), `ReminderPrefsTest`·`WidgetSnapshotTest`, Robolectric에서 WorkManager 테스트 도구로 작업 예약·실행(`ReminderWorkerTest`: 알림 내용·한국어 채널·누르면 열리는 화면, 설정·권한·채널 끔 존중, 같은 시각은 유지·새 시각은 교체), 알림 설정 흐름(`ReminderSettingsFlowTest`: API 35 권한 요청 허용/거절, 시간 변경 재예약), 위젯(`HomeWidgetTest`: Glance 단위 테스트로 큰/작은 배치·빈 상태·"+ 새 기록" 인텐트, 홈 화면과 같은 문구, 앱 관찰자 갱신, RemoteViews PNG), 인텐트로 새 기록 폼·보관함 열기(`LaunchTargetFlowTest`, `LaunchIntentTest`), 백업 제외(`ReminderBackupTest`). iOS 미리 예약: 공통 `ReminderScheduleAheadTest`(D일 알림 = 그날 하루 점검의 답, 첫날(정한 시각 전·후), 30일 창, 알림마다 한 번, 보낸 키·설정·종류, 64개 한도와 가까운 순, 이 앱 예약만 교체, 시각이 지난 것은 보낸 것으로 기록·다시 예약 안 함, 권한 없음, 끄기, 거절된 예약, 기록·D-day 변경, 0.8초 묶음 동기화)·`ReminderSettingsViewModelTest`(나중에 도착한 권한 상태를 화면이 따름), `ScheduleAheadDataTest`(Robolectric Room: 오늘 계획 = 하루 점검의 답, `ReminderCheck.changes()`는 기록·보관함·블렌드·D-day·알림 설정에만 반응하고 보낸 기록·예약 목록 쓰기에는 반응하지 않음), iOS 전용 `IosReminderPlatformTest`(트리거 날짜 구성, schedule/cancel이 공통 동기화를 거침; macOS CI의 시뮬레이터에서만 실행).
- AI 노트 도우미(§2.3 29): 단위 테스트(`AiParsersTest`: 네 서비스의 요청 형식과 응답 해석(가짜 JSON), Gemini 그라운딩의 글자·UTF-8 바이트 위치, OpenAI url_citation, Claude 인용 블록·동적 필터링 블록·오류 객체; `AnswerTextTest`: 문장 나누기, [n] 표시, 인용 대조 정규화, 모드 B 용어 찾기; `AiErrorsTest`: 서비스·상태별 안내; `NoteHelperServiceTest`: 방식별 흐름, 검색어 쓰기와 고정 틀로 돌아가기, 검색 깊이, pause_turn 재요청, 키 확인, 단계 보고 순서(검색어 단계 있음/없음, 다른 방식은 한 단계), 움직이는 점의 폭), 흐름 테스트(`AiFlowTest`: 가짜 HTTP·메모리 키 저장소로 설정 절, 검색 깊이, 요청을 붙잡아 둔 채 단계 줄(끝남·진행 중·남음·건너뜀, 화면 전환 끔이면 "…"), 노트 상세 → 답, 검색어 고쳐 다시 묻기, 폼 → 후보 → 노트 추가, 키 없음, 무료 프로젝트의 Google 검색 429, 모델 404, Claude), `AiPlatformTest`(앱 프롬프트 = `tools/ai-eval`의 파일과 `eval.py` 질문 틀, Keystore 파일 형식은 소프트웨어 키로), 스크린샷 70–75(75는 Tavily 검색 중인 단계 줄; 테스트 시계는 무한 애니메이션을 돌리지 않아 점 셋인 첫 프레임으로 고정).
- 타이머 ✕·블렌드 원두 블록·카페 레시피 접기(§2.3 22·30·31): `BrewTimerEngineTest`(가운데 푸어·붓는 중인 푸어·첫 푸어·메모 달린 푸어 지우기, 되돌리기, 뒤 푸어의 어림값 다시 매기기), `BlendBeansTest`(원두 1 = 기록 필드, 로스터리·로스팅 상속, 비율 합계, 무게 비율 반올림, 원두별 행), `BlendBeansFormTest`(블록 추가·✕, 원두 3개 저장·다시 열기, 빈 블록, 옛 카페 블렌드, 같은 원두 자동 채움, 카페 기록의 블록, 상세 묶음, 카페 레시피 저장 규칙), `BackupCodecTest`(원두 필드는 값이 있을 때만, 원두 1 자리 유지, 웹 모양 그대로), `BlendSourcesTest`; 흐름 테스트 `BrewTimerFlowTest`(✕ → 되돌리기 → 다시 ✕, 붓는 중 취소, 단계 로그·합계), `BlendBeansFlowTest`(+ → 두 번째 블록 → 비율 90% 안내 → 100% → 저장 → 상세 묶음 → 수정에서 ✕ → 단일 원두, 직접 블렌드 비율, 카페 레시피 접힘·펼침·저장), 스크린샷 80–83.
- 위치 지정 검색·현재 위치(§2.3 32): 단위 테스트(`PlaceSearchTest`: 카카오 키워드·주소 응답 해석(가짜 JSON), 요청 형식(키는 헤더에만, 질의 인코딩, 현재 위치면 x·y·거리순), 어느 검색이 답하는지(키 없음 → 기기, 키 있음 → 국내만 카카오, 해외는 기기), 401·403·429·5xx면 기기로 대신 찾고 알림, 오프라인은 실패, 기기 검색 실패·없음·시간 초과, 저장할 수 없는 키 저장소, 범위 밖·중복 결과 거르기와 거리, 키 확인; `MapPickerViewModelTest`: 이름으로 시작하는 칸, 검색 → 고르기 → 카페가 주소와 함께 저장, 지도를 누르면 주소가 빠짐, 해외 결과·로스터리 결과의 주소, 현재 위치 → 점과 이후 검색의 거리, 거절·위치 서비스 꺼짐·미지원·15초 시간 초과(가상 시간); `CafeMapLogicTest`: `MapPickResult`의 주소 인코딩과 옛 결과 읽기; `BackupCodecTest`: 주소 왕복과 schema 2 파일), Robolectric(`PlaceSearchFlowTest`: 가짜 검색·위치를 Koin으로 넣고 카페 이름 검색 → 고르기 → 주소와 함께 저장·달력 카페 위치에 표시, 로스터리 주소 검색 → 빈 지역 칸 채우기·적어 둔 지역 유지, 권한 요청 허용 → 점·거리, 거절 안내, 이미 허용·못 찾음·꺼짐·미지원, 찾은 곳 없음·실패·검색 불가·빈 칸 안내, 설정의 카카오 키 저장·마스킹·키 확인·국내 검색이 카카오로·백업 제외·지우기; `LocationPlatformTest`: 지오코더 결과 → 장소 이름 규칙, 13+ 리스너와 오류, `LocationManager` 거절·꺼짐·최근 위치; `DatabaseMigrationTest` v2 → v3; `MapBackupTest` 주소 복원과 옛 백업 병합; `PlatformSetupTest` Info.plist 위치 문구·매니페스트 권한), 스크린샷 48(검색 칸이 생긴 위치 지정)·76(현재 위치에서 찾은 결과)·77(설정 › 장소 검색). 실제 네트워크·GPS는 쓰지 않는다. iOS 구현(`MKLocalSearch`, `CLLocationManager`)은 Linux에서 klib 컴파일까지만 확인했다.
- 임시 저장·떠나기 확인(§2.3 33): 단위 테스트(`RecordDraftsTest`: 모드·기록별 기기 키, 왕복(고른 사진 바이트는 빠지고 개수만), 30일 만료·읽을 수 없는 초안 삭제, 오래된 초안 정리가 다른 설정을 건드리지 않음, 느린 쓰기 뒤에도 요청 순서대로 반영, 입력으로 치지 않는 화면 상태(펼친 패널·계산기·배너·오류·저장 중), 다시 열 때 패널 닫힘), 흐름 테스트(`DraftFlowTest`: 입력 → ← → 창 → 계속 쓰기/나가기 → 다시 열면 배너와 입력 복원 → 저장하면 초안 삭제, 시스템 뒤로 → 지우고 나가기, 기존 기록은 열자마자 ←·시스템 뒤로에서 묻지 않음·고친 뒤 취소 → 수정 초안 → 새로 쓰기, 프로세스 종료 복원이 초안보다 우선하고 연 상태도 복원됨(이전 버전 상태는 새 폼과 비교), 고른 사진은 초안에 없고 배너가 알림, 31일 된 초안은 버리고 7일 된 초안은 불러옴, 보관함·장비·로스터리·블렌드·책·클래스·동영상·기록(원두·카페) 수정 화면을 열자마자 떠나면 묻지 않고 고치면 묻고 나가도 저장 안 됨, 보관함 새 원두의 계속 쓰기·취소·저장 후 닫힘, 내 레시피(화면 떠나기와 폼 "취소" → "닫기"), 각 폼 뷰모델의 "연 상태" 비교(가격 서식·오류 표시는 입력 아님, 되돌리면 변경 없음, 프로세스 종료 후에도 불러온 항목 기준), 위젯 관찰자가 초안 쓰기에 반응하지 않음(`HomeWidgetTest`), 스크린샷 84–85(떠나기 창, 초안 배너).
- 현재 규모(지도·기록 분석·알림·상세 지도·설정·AI 노트 도우미 병합, iOS 상세 지도·알림 미리 예약, 타이머 ✕·블렌드 원두 블록·카페 레시피 접기·임시 저장·위치 검색·카페·로스터리 바로 추가 후): `shared` 단위 420개, `androidApp` 흐름·스크린샷·마이그레이션 407개, 모두 통과(건너뜀 0).
- 접근성: 최소 터치 48dp, 대비 4.5:1(잉크/아이보리), 콘텐츠 설명, 토글·펼침 상태 노출.
- 네트워크(상세 지도, §1.7): 앱에서 네트워크를 쓰는 곳은 상세 지도, 사용자가 키를 넣고 질문했을 때의 AI 노트 도우미(§2.3 29), 위치 지정에서 "검색"을 눌렀을 때의 장소 검색(§2.3 32: 휴대폰의 지도 서비스 — Android `Geocoder`, iOS `MKLocalSearch` — 또는 사용자가 자기 키를 넣은 경우 국내만 카카오 로컬 `dapi.kakao.com`; 보내는 것은 적은 말과, 현재 위치를 정한 뒤면 카카오·Apple 지도에는 그 좌표)뿐이다(INTERNET·ACCESS_NETWORK_STATE). 위치 권한(ACCESS_FINE_LOCATION·ACCESS_COARSE_LOCATION)은 "현재 위치" 버튼을 누를 때만 묻고 한 번 읽는 데만 쓴다(백그라운드 위치 없음, 원래 MapLibre가 선언한 것을 제거했다가 이 버튼을 위해 직접 선언); MapLibre가 선언한 Wi-Fi 상태 권한은 계속 제거. iOS는 `NSLocationWhenInUseUsageDescription`("카페·로스터리 위치를 현재 위치로 정할 때만 사용해요."). 요청은 OpenFreeMap(tiles.openfreemap.org)의 보이는 지역 타일·글리프뿐, 미리 받기 없음, MapLibre 앰비언트 캐시만. 흐름 테스트는 MapLibre 네이티브 렌더러가 JVM에서 돌지 않으므로 `DetailMapRenderer`를 가짜로 바꿔(Koin) 핀·카메라·오프라인·실패·느린 로딩을 검증하고, 실제 렌더러는 네이티브 라이브러리를 못 올릴 때 안전하게 안내로 떨어지는지 확인한다. iOS도 같은 렌더링 코드(`MapLibreDetailMap`)를 쓰고, Linux에서는 klib 컴파일까지만 확인한다(앱 링크·Swift 패키지 해석은 macOS CI). 스타일은 단위 테스트(구조·출처·팔레트)와 MapLibre style-spec 검증기(개발 중 수동)로 확인했다.

## 9. iOS (완료)
1. macOS에서 `coffeejournal.enableIos=true`로 iOS 타깃을 켜 `Shared` 정적 프레임워크를 만든다(Xcode 빌드 단계 `embedAndSignAppleFrameworkForXcode`). Linux에서는 `coffeejournal.iosKlibs=true`로 klib까지 컴파일해 iOS에서만 깨지는 변경을 잡는다.
2. `iosMain`의 actual 구현(§3.1 표의 iOS 열): 사진(PHPicker·카메라, Documents/photos), 백업 파일(UIDocumentPicker·공유 시트), 알림(미리 예약), AI(NSURLSession·Keychain), 상세 지도(MapLibre Native iOS, Swift 패키지), 장소 검색·현재 위치(MKLocalSearch·CLLocationManager).
3. `iosApp/` SwiftUI 앱이 `MainViewController()`를 띄운다. 하단 탭·내비게이션은 Compose 공용 코드 그대로다. Xcode 프로젝트는 `project.yml`에서 XcodeGen으로 만든다.
4. CI(`.github/workflows/ios.yml`): klib 컴파일(모든 관련 푸시), 서명 없는 .ipa(직접 실행·태그·release 입력), 시뮬레이터에서 공유 테스트와 Debug 앱 실행 확인(직접 실행). 설치는 사용자의 Apple ID 서명으로 하고, 기본·추천은 컴퓨터 없이 아이폰 혼자 7일 갱신하는 SideStore(루트 `README.md`, 다른 도구는 Sideloadly · AltStore).
5. 남은 것: 홈 화면 위젯(WidgetKit 확장과 앱 그룹이 필요하고, 무료 Apple ID 서명에서는 앱 그룹이 제대로 되지 않을 수 있어 미룸).

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

진행 상황: M0–M6 완료. 이후 웹 원본 대비 전수 감사(확정 결함 105건)를 거쳐, 사용자 스타일 결정으로 제외한 5건을 뺀 100건을 수정하고 흐름 테스트로 고정했다. 이어서 2차 기능(`docs/feature-plan-v2.md`), 설정·AI 노트 도우미, iOS 앱(§9, v1.1.0), 사용자 요청 기능(§2.3 30–33: 블렌드 원두 블록, 카페 레시피 접기, 위치 검색·현재 위치, 임시 저장, 카페·로스터리 바로 추가, v1.2.0)을 더했다. v1.3.0에서는 위치 검색을 키 없이도 가게 이름으로 찾게 하고(휴대폰 지도 서비스 + OpenStreetMap, 이름 + 동네는 동네 둘레에서 다시 찾기, 카카오 "더 보기"), 키마다 전체 화면 "키 받는 법" 안내를 붙였다(`docs/dev/implementation-notes.md` "장소 검색과 키 안내").
