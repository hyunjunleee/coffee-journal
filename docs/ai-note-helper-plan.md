# AI 노트 도우미 계획 (초안)

사용자 요청(2026-09-26): 향미 노트를 찾을 때 AI의 도움을 받는다. 가장 좋은 무료 AI API에 연결하고, AI가 항상 인터넷에 실제로 나오는 향·맛 표현을 직접 찾아 출처와 함께 설명·대답하도록 미리 지시(프롬프트)를 넣는다.

이 문서는 계획이다. 사실 항목은 모두 2026-09-26에 공식 문서에서 확인했고 링크를 달았다. **구현은 12장에 있다.** 12장의 결정(2026-09-26)이 0~11장과 다르면 12장이 맞다: 무료 기본값은 Gemini 2.5 + Google 검색이 아니라 "Gemini 무료 + Tavily"이고, 키는 앱에 넣지 않으며, AI 기록(1-C)은 만들지 않았다.

## 0. 결론

- **가능하다.** 다만 무료이면서 실제 웹 출처를 달아 주는 API는 지금 사실상 하나다. Google Gemini API 무료 등급의 **Gemini 2.5 Flash / 2.5 Flash-Lite**에 **Google 검색 그라운딩**을 켜는 조합으로, 두 모델 합쳐 하루 500건까지 무료다.
- **Gemini 3.x는 제외된다.** 3.x 모델은 무료 등급에서 검색 그라운딩을 쓸 수 없다.
- **무료 등급의 조건:**
  - 보낸 질문과 받은 답이 Google 제품 개선에 쓰이고, 사람이 검토할 수 있다.
  - 답과 함께 "Google 검색 제안"을 반드시 보여야 하고, 답을 고치거나 다른 내용을 섞으면 안 된다.
  - Google은 모바일 앱에 키를 넣지 말라고 권한다. 그래서 앱(APK)에는 어떤 키도 넣지 않고, 사용자가 자기 키를 만들어 설정에 붙여 넣는다. 앱 안에 키 발급 절차를 단계별로 안내한다(5장).
  - 이용자는 18세 이상이어야 한다.
- **"항상 실제 출처"는 앱 쪽 장치로 보장한다.** 모델 말만 믿지 않고, 응답에 붙은 검색 근거 메타데이터를 앱이 확인한다(4장).
  - 출처가 하나도 없는 답은 보여주지 않는다.
  - 문장마다 연결된 출처 번호를 붙인다.
  - 2단계에서 "실제 문구 확인" 표시를 더한다.

## 1. 만들 기능

| | 어디서 | 하는 일 |
|---|---|---|
| A. 노트 설명 | 노트 상세(`Route.NoteDetail`), 플레이버 휠 용어, 기록 폼의 향미 노트 칩 | "✦ 출처로 알아보기": 그 표현(예: 베르가못, 흑설탕, 타마린드)이 커피에서 무슨 뜻인지, 비슷한 표현, 실제 로스터리·SCA·WCR 자료에서 어떻게 쓰이는지를 번호 출처와 함께 보여준다 |
| B. 맛 묘사 → 노트 찾기 | 기록 폼 노트 칸 옆 "✦ AI에게 묻기", 원두 탭 › 노트 | "잘 익은 자두 같고 끝이 쌉쌀해요" 같은 문장에 어울리는 휠 용어 후보와 그 근거 출처를 준다. 답에 나온 용어 중 앱의 휠·노트 목록에 있는 것만, 답과 분리된 칸에서 "노트에 추가" 칩으로 따로 보여준다(답에 섞지 않는다) |
| C. AI 기록 | 설정 › AI › 기록 | 질문, 답, 출처를 이 기기의 대화 기록으로 저장한다(약관상 대화 기록으로 최대 2년). JSON 백업과 클라우드 백업에는 넣지 않는다 |

## 2. API 비교 (2026-09-26 확인)

| 선택지 | 비용 | 웹 검색·출처 | 비고 |
|---|---|---|---|
| **Gemini 2.5 Flash / Flash-Lite + Google 검색 그라운딩** | 토큰 무료. 검색 그라운딩은 두 모델 합쳐 하루 500건 무료 | 내장. 응답에 `groundingChunks`(출처 URL·제목), `groundingSupports`(답 속 문장 위치 ↔ 출처 번호), `webSearchQueries`, `searchEntryPoint`(검색 제안 HTML)가 온다 | **추천(무료 중 유일한 내장 인용)**. 모델별 분당·일일 한도는 이제 문서에 숫자가 없고 AI Studio에서만 보인다. 2025-12에 무료 한도가 크게 줄었다는 포럼 보고가 있다(Flash 하루 250 → 20). 2.5 모델 종료일은 아직 공지되지 않았다 |
| Gemini 3.x (3.5 Flash 등) | 토큰 무료 | 무료 등급에서 검색 그라운딩 불가. 유료는 월 5,000회 무료 뒤 1,000회당 $14 | 무료 조건에 맞지 않음 |
| Groq | 무료(gpt-oss 하루 1K 요청 등) | 웹 검색 내장이던 `compound`는 2026-09-21 종료. `browser_search`의 무료 여부와 URL 반환은 확인 불가 | 부적합 |
| OpenRouter 무료 모델 | 무료(하루 50요청) | 웹 검색은 항상 유료(요청당 약 $0.007) | 부적합 |
| 무료 LLM + 무료 검색 API(Tavily 월 1,000크레딧, Exa 월 $10 크레딧 등) | 무료 | 앱이 검색 → 결과를 LLM에 넣고 → 인용을 직접 맞춰야 한다 | 가능하지만 인용 검증을 모두 직접 구현해야 하고, 서비스가 둘이라 키도 둘 |
| (유료 참고) Claude API + 웹 검색 도구 | 검색 1,000회당 $10 + 토큰. Haiku 4.5 기준 질문 1회 약 2~4센트(추정) | 인용마다 url·제목·**`cited_text`(원문 그대로 최대 150자)**가 온다 | "실제 문구"가 응답에 그대로 오므로 요구에 가장 직접적으로 맞음. 유료 |
| (유료 참고) Perplexity Sonar | 토큰 + 요청 1,000회당 $5~12 | 내장 | 유료 |

출처:
- Gemini: [가격](https://ai.google.dev/gemini-api/docs/pricing), [한도](https://ai.google.dev/gemini-api/docs/rate-limits), [검색 그라운딩](https://ai.google.dev/gemini-api/docs/google-search), [구조화 출력](https://ai.google.dev/gemini-api/docs/structured-output), [URL context](https://ai.google.dev/gemini-api/docs/url-context), [약관](https://ai.google.dev/gemini-api/terms), [지원 지역](https://ai.google.dev/gemini-api/docs/available-regions), [API 키](https://ai.google.dev/gemini-api/docs/api-key), [한도 축소 포럼](https://discuss.ai.google.dev/t/do-they-really-think-we-wouldnt-notice-a-92-free-tier-quota/111262)
- Groq: [종료 목록](https://console.groq.com/docs/deprecations), [한도](https://console.groq.com/docs/rate-limits)
- OpenRouter: [웹 검색](https://openrouter.ai/docs/guides/features/plugins/web-search)
- 검색 API: [Tavily](https://docs.tavily.com/documentation/api-credits), [Exa](https://exa.ai/pricing)
- 유료 참고: [Claude 웹 검색](https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool), [Perplexity](https://docs.perplexity.ai/docs/getting-started/pricing)

### 2.1 유료 키로 쓸 제공자 (2026-09-26 확인)

**OpenAI (GPT)**
- 호출: Responses API(`POST https://api.openai.com/v1/responses`)에 `tools: [{type: "web_search"}]`를 넣고, `tool_choice: "required"`로 검색을 강제한다.
- 요청 옵션:
  - `include: ["web_search_call.action.sources"]`로 참고한 전체 URL을 받는다.
  - `user_location`에 `{type: "approximate", country: "KR"}`를 준다. 빠뜨리면 미국으로 간주한다.
  - `filters.allowed_domains`를 쓸 수 있다.
- 인용: 답의 `annotations[]`에 `url_citation`(url, title, start_index, end_index)이 온다. 인용은 화면에 보이고 누를 수 있어야 한다.
- 가격:
  - 검색 1,000회당 $10에, 검색 내용 토큰을 모델 요금으로 따로 낸다.
  - 가장 싼 검색 지원 모델은 `gpt-5-nano`(입력 $0.05 / 출력 $0.40, 100만 토큰당)다.
- 출처:
  - [웹 검색](https://developers.openai.com/api/docs/guides/tools-web-search)
  - [가격](https://developers.openai.com/api/docs/pricing)
  - [요청 형식](https://developers.openai.com/api/reference/resources/responses/methods/create)

**Gemini 유료 (3.x)**
- 모델과 가격:
  - 최신 Flash는 `gemini-3.8-flash`다.
  - 검색 그라운딩은 모델이 실행한 검색마다 과금한다. 3.x 전체 합쳐 월 5,000회 무료, 그 뒤 1,000회당 $14.
- 구조화 출력과 검색: 3.x에서 함께 쓸 수 있지만 아직 미리보기다.
- API 면: 새로 권장되는 면은 Interactions API(`/v1beta/interactions`, 2026-06 GA)다. 기존 `generateContent`도 계속 지원된다. 무료 2.5는 `generateContent`로 쓴다.
- 출처: [가격](https://ai.google.dev/gemini-api/docs/pricing), [구조화 출력](https://ai.google.dev/gemini-api/docs/structured-output), [Interactions](https://ai.google.dev/gemini-api/docs/interactions-overview)

**Anthropic (Claude)**
- 호출: Messages API에 웹 검색 도구를 넣는다.
- 인용: 인용마다 url, title, `cited_text`(원문 그대로 최대 150자)가 온다.
- 가격: 검색 1,000회당 $10에 토큰 요금이 붙는다.
- 구현할 때 공식 문서로 모델 ID와 도구 버전을 다시 확인한다.
- 출처: [웹 검색 도구](https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool)

**세 제공자 공통 처리**
- 각자의 인용 형식을 같은 `GroundedAnswer`로 바꾼다.
  - Gemini: `groundingChunks` + `groundingSupports`
  - OpenAI: `url_citation`
  - Claude: `citations` + `cited_text`
- 인용 위치(offset)는 문서마다 설명이 엇갈린다.
  - Gemini 참조 문서는 "bytes"라고 쓰고, 이전 안내서는 "character"라고 쓴다.
  - OpenAI는 "characters"라고 쓰지만, 코드 포인트인지 UTF-16 단위인지는 확인하지 못했다.
- 그래서 앱은 위치 숫자를 믿지 않고, 인용에 딸려 오는 텍스트를 답에서 직접 찾는다. 찾지 못할 때만 위치 숫자를 쓰고, Gemini의 경우 UTF-8 바이트로 해석한다.
- PoC가 Gemini 2.5의 실제 위치 단위를 잰다(`offset_kinds`).

## 3. Gemini 무료 등급의 조건과 앱에서의 대응

| 조건(약관·문서) | 앱에서의 대응 |
|---|---|
| 무료 등급의 입력·출력은 Google 제품 개선에 쓰이고 사람이 검토할 수 있다. 검색 그라운딩은 질문과 답을 30일 보관한다 | 처음 켤 때 동의 창에 그대로 적는다. 보내는 것은 질문 문장(과 노트 이름)뿐이다. 기록, 원두, 위치, 사진은 보내지 않는다 |
| 그라운딩 결과는 검색 제안(Search Suggestions)과 함께, 질문한 사람에게만 보여야 한다 | 답 아래에 검색 제안(`searchEntryPoint.renderedContent`)을 그대로 표시한다 |
| 그라운딩 결과를 고치거나 다른 내용을 섞으면 안 된다. 캐시·분석·클릭 추적 금지. 사용자의 대화 기록에는 최대 2년 저장 가능 | 답 본문은 그대로 보인다. 공식 예제처럼 출처 번호만 붙인다. 앱의 휠 용어 칩은 답과 분리된 칸에 둔다. 저장은 사용자의 AI 기록(1-C)으로만 한다 |
| 모바일 앱에 키를 하드코딩하지 말 것(서버 프록시 권장). 2026-05-28부터 새 키는 인증 키 형식이다 | APK에는 어떤 키도 넣지 않는다. 사용자가 자기 키를 설정에 넣고(5장), 앱이 발급 절차를 안내한다. 개발용 키(`GEMINI_API_KEY_DEBUG`)는 PoC에만 쓴다 |
| 18세 이상. EEA·스위스·영국은 유료만. 한국은 지원 지역 | 동의 창에 적는다 |
| 약관에 "업무·전문 목적이며 소비자용이 아님" 문구가 있다 | 개인이 자기 키로 자기 앱에서 쓰는 경우의 해석 여지를 11장에 남긴다 |
| 구조화 출력(JSON 스키마)과 검색 도구를 함께 쓰는 것은 3.x(미리보기)에서만 된다 | 2.5에서는 JSON을 강제하지 않는다. 텍스트 답과 그라운딩 메타데이터를 앱이 해석한다 |

## 4. "항상 실제 출처" 보장 장치 (다섯 겹)

1. **검색 강제.** 요청마다 `tools: [{google_search: {}}]`를 넣고, 시스템 지시로 "매번 검색하고, 검색 결과에 나온 표현만 쓸 것"을 요구한다(6장).
2. **출처 없으면 안 보여준다.** `groundingChunks`가 0개이면 같은 질문을 한 번 다시 보낸다. 그래도 0개면 답을 보이지 않고 "출처를 찾지 못했어요 · 다른 말로 물어보기"를 보인다.
3. **문장별 출처 번호.** `groundingSupports[].segment(startIndex, endIndex)`와 `groundingChunkIndices`로 해당 문장 끝에 [1][2]를 붙인다.
   - 출처와 연결되지 않은 문장은 회색 글씨에 "출처 연결 없음" 표시만 한다.
   - 삭제하지는 않는다. 삭제하면 약관상 답을 고치는 것이 된다.
   - 연결된 문장 비율도 함께 보여준다.
4. **출처 목록.** 번호, 제목, 실제 도메인을 보이고 누르면 브라우저로 연다.
   - 출처 URL은 `vertexaisearch.cloud.google.com` 경유 링크일 수 있다. 제목과 함께 오는 원래 도메인을 보여준다.
   - 신뢰 출처는 도메인 뒤에 작은 표시를 붙인다: SCA(sca.coffee), WCR(worldcoffeeresearch.org), CQI, 로스터리 공식 페이지, 학술지.
   - 블로그와 커뮤니티는 "개인 글"로 구분한다.
5. **(2단계) 실제 문구 확인.** 답에 "실제 문구" 인용이 있으면 그 문구가 출처 페이지에 정말 있는지 확인해 ✓를 붙인다. 방법은 둘이다.
   - (가) Gemini의 URL context 도구(무료, 요청당 URL 20개)로 해당 페이지를 읽게 해 확인한다.
   - (나) 앱이 페이지를 직접 받아 문자열을 찾는다.
   - 두 방법 모두 약관의 "분석 금지"와 부딪히는지 해석이 필요하다. 1단계에는 넣지 않는다.

## 5. 키·보안·개인정보

- **무료라도 키가 필요한 이유.** Gemini API는 무료 등급도 익명으로는 쓸 수 없다. 요청마다 API 키가 있어야 하고, 무료 한도(하루 요청 수 등)는 그 키가 속한 Google 프로젝트에 매겨진다. 약관 동의와 남용 차단도 그 계정 단위다. 그래서 누군가의 Google 계정에서 만든 키가 하나는 있어야 한다.
- **키는 사용자가 직접 넣는다 (결정 2026-09-26).** 앱(APK), 저장소, CI 빌드 어디에도 키를 넣지 않는다. 무료든 유료든 사용자가 자기 키를 만들어 설정 › AI에 붙여 넣는다.
  - 설정 › AI의 순서: 제공자 선택(Gemini 무료 / Gemini 유료 / OpenAI / Claude) → "키 받는 방법" 안내(5.1) → 키 붙여넣기 → "키 확인" → 저장.
  - "키 확인"은 짧은 요청 한 번(검색 없이)으로 키가 동작하는지 본다. 결과에 따라 다음 중 하나를 알린다: 정상 / 키 틀림 / 결제 필요 / 이 지역 불가 / 한도 초과.
  - 키는 마지막 4자리만 보여준다(예: `…a1b2`). 키 삭제 버튼을 둔다.
  - 유료 제공자는 질문 전에 대략의 비용(2.1절)을 보여준다.
- **개발용 키.** 저장소 Secret `GEMINI_API_KEY_DEBUG`는 PoC 평가(8장)와 개발 확인에만 쓴다. 안드로이드 빌드 워크플로는 이 Secret을 읽지 않는다.
- **저장 방식.**
  - Android Keystore에 내보낼 수 없는 AES-GCM 키를 만들고, 그 키로 API 키를 암호화해 `noBackupFilesDir`에 둔다. 클라우드 백업, 기기 이전, JSON 백업 어디에도 가지 않는다.
  - `androidx.security:security-crypto`(EncryptedSharedPreferences)는 2025-07 1.1.0에서 폐기되어 쓰지 않는다([공지](https://developer.android.com/jetpack/androidx/releases/security), [Keystore 안내](https://developer.android.com/privacy-and-security/keystore)).
- **네트워크.** 앱은 이미 상세 지도 때문에 INTERNET 권한이 있다. HTTP는 APK에 이미 들어 있는 OkHttp 4.12.0(MapLibre가 가져옴)을 쓴다. 따라서 새 라이브러리가 0개이고 의존성 검증 파일도 바뀌지 않는다.
- **대안 1: 서버 프록시.** Cloudflare Workers 무료 등급에 키를 두고 앱은 프록시만 부른다. 키가 기기에 없지만 서버 운영과 남용 방지가 필요하다.
- **대안 2: Firebase AI Logic.** 무료 Spark 요금제로 Gemini 무료 등급을 쓸 수 있다. 다만 2026-11-02부터 App Check가 필수이고, Play 스토어가 아닌 사이드로드 APK는 Play Integrity 검사를 통과하기 어렵다([가격](https://firebase.google.com/docs/ai-logic/pricing)). 지금 배포 방식과 맞지 않는다.

## 6. 프롬프트 초안 (시스템 지시)

```
너는 스페셜티 커피의 향미 표현을 조사하는 도우미다. 대답은 한국어로 한다.

규칙
1. 매 질문마다 반드시 Google 검색을 먼저 한다. 검색 결과에 실제로 나온 내용만 쓴다. 기억이나 추측으로 쓰지 않는다.
2. 향·맛 표현은 인터넷 문서에 실제로 쓰인 형태 그대로 인용한다. 인용은 큰따옴표로 감싸고, 어느 출처인지 알 수 있게 쓴다.
   예: 어느 로스터리의 테이스팅 노트에 "bergamot, jasmine, black tea"라고 적혀 있다.
3. 출처 우선순위: SCA·WCR(Sensory Lexicon, Coffee Taster's Flavor Wheel)·CQI 같은 기관 자료 > 로스터리·생산자 공식 페이지 > 전문 매체·학술 자료 > 개인 블로그·커뮤니티. 개인 글은 "개인 의견"이라고 밝힌다.
4. 찾지 못한 것은 "찾지 못했다"고 쓴다. 출처 없이 일반론을 덧붙이지 않는다.
5. 커피 향미와 무관한 질문에는 답하지 않고, 향미 노트 질문을 해 달라고 한다.

형식(모드 A: 노트 설명, 질문: "{노트}")
- 한 줄 뜻: 커피에서 {노트}가 가리키는 향·맛
- 실제 쓰임 2~4개: 인용 + 어떤 원두·가공·로스팅에서 나왔는지
- 비슷한 표현·헷갈리는 표현
- 참고: Coffee Taster's Flavor Wheel에서의 위치(있을 때만, 출처와 함께)

형식(모드 B: 맛 묘사, 질문: "{사용자 문장}")
- 어울리는 휠 용어 후보 3~5개: 각 후보마다 그렇게 부르는 근거(인용)
- 후보를 구별하는 방법(출처가 있을 때만)
```

모드 B에는 앱이 가진 목록(플레이버 휠 영문 용어 89개와 한국어 노트 분류 43개)을 함께 보내, 그 목록 안에서 고르도록 한다. 앱은 답에 나온 용어를 목록과 대조해, 목록에 있는 것만 "노트에 추가" 칩으로 따로 보인다.

생성 설정:
- `temperature` 0.2로 둔다.
- 기본 모델은 `gemini-2.5-flash`다. 설정에서 `gemini-2.5-flash-lite`(더 빠르고 한도가 넉넉할 가능성)를 고를 수 있다.
- 한도 초과(429)가 나면 다른 쪽 모델로 바꾸라고 안내한다.

## 7. 앱 구조

- **`shared/commonMain/.../ai/`**
  - `AiProvider`: 제공자 인터페이스.
  - `GeminiGroundedProvider`: `generateContent` REST 호출, kotlinx.serialization으로 요청·응답 처리.
  - `GroundedAnswer`: 본문, 문장→출처 연결, 출처 목록, 검색 제안 HTML, 검색어.
  - `NoteHelperPrompts`: 6장의 시스템 지시.
  - `GroundingCheck`: 4장 ②·③의 판정과 번호 붙이기.
  - `NoteHelperViewModel`.
- **플랫폼 경계(expect/actual)**
  - `AiHttp`: Android는 OkHttp, iOS는 NSURLSession.
  - `AiKeyStore`: Android는 Keystore + 파일, iOS는 Keychain.
- **DB:** Room v3에 `ai_history`(id, mode, question, answer_json, model, created_at) 표를 추가하는 자동 마이그레이션. JSON 백업에서는 뺀다.
- **화면:**
  - 설정 › AI: 켜기, 키 입력·확인·삭제, 모델, 오늘 사용 횟수(앱이 센 값), 동의 문구, AI 기록.
  - `Route.NoteHelper(mode, query)` 답 화면: 본문과 [번호], 출처 목록, 검색 제안, "노트에 추가" 칩(모드 B), "다시 묻기".
  - 진입 버튼: 노트 상세, 휠, 기록 폼.
- **오류 안내:** 키 없음·틀림(400/403), 한도 초과(429), 오프라인, 안전 필터 차단, 출처 없음(4장 ②).
- **출처 화면:** Gemini API·Google 검색 그라운딩 이용 조건을 추가한다.

## 8. 테스트와 평가

- **CI는 네트워크를 쓰지 않는다.** 가짜 제공자로 흐름 테스트를 돌린다(버튼 → 답 화면, 출처 번호, 출처 없음 처리, 429 안내, 키 저장·삭제).
- **파서·검증 단위 테스트:** 실제 응답을 한 번 녹화해 키와 개인정보를 뺀 뒤 테스트 자료로 커밋하고 사용한다.
- **품질 평가(PoC):** `tools/ai-eval/`에 스크립트를 둔다. 노트 30개(베르가못, 자스민, 흑설탕, 리치, 타마린드, 위스키, 발효 등) × 모드 A, 묘사 문장 20개 × 모드 B를 돌린다. 지표는 아래와 같다.
  - 출처가 있는 답의 비율
  - 문장-출처 연결률
  - 인용 문구가 실제 페이지에 있는 비율(사람 표본 확인)
  - 기관·로스터리 출처 비율
  - 한국어 출처 비율
  - 응답 시간
  - 결과는 `docs/ai-eval-results.md`에 남긴다.
- **PoC 실행 방법 (구현됨):** `tools/ai-eval/eval.py`(파이썬 표준 라이브러리만)와 `.github/workflows/ai-eval.yml`.
  - 저장소 Secret `GEMINI_API_KEY_DEBUG`(개발용)가 있으면 GitHub Actions에서 실제로 질문한다. 없으면 파서 자체 점검만 한다.
  - 기본 질문 수: 노트 설명 12개, 맛 묘사 6개. 요청 사이 7초 간격을 둔다.
  - 무료 일일 한도가 다하면 그 자리에서 멈추고, 거기까지의 보고서를 남긴다.
  - 보고서는 실행 페이지 요약과 7일짜리 아티팩트에만 남긴다. 그라운딩 답은 약관상 캐시하지 않으므로 저장소에 커밋하지 않고, 저장소에는 집계 수치만 `docs/ai-eval-results.md`로 남긴다.
  - 키는 채팅에 붙여 넣지 않는다.
  - 문서만, 또는 평가 도구만 바뀐 푸시에서는 안드로이드 빌드를 건너뛴다(`android.yml`의 `paths-ignore`).
  - 노트 30개·묘사 20개 대신 20개·8개로 시작한다(무료 한도).

## 9. 단계

1. **PoC:** 평가 스크립트로 프롬프트를 다듬고 무료 한도를 실측해 사용자와 결과를 본다.
2. **앱:** 설정 › AI(동의, 빌드 키 또는 입력 키, 유료 제공자 선택), 모드 A와 B, 출처 장치 ①~④, 기록 폼 연결, "노트에 추가", AI 기록.
3. **유료 제공자:** Gemini 유료, OpenAI, Claude 어댑터. 각 제공자의 인용 형식을 같은 `GroundedAnswer`로 바꾼다.
4. **선택:** ⑤ 실제 문구 확인, iOS 연결.

## 10. 위험과 한계

- **무료 한도가 예고 없이 줄 수 있다.** 2025-12에 실제로 줄었다. 앱은 한도 초과를 친절히 안내하고 다른 모델로 바꾸게 한다.
- **2.5 모델이 종료되면 무료 검색 그라운딩이 사라질 수 있다.** 3.x에는 무료 검색이 없다. 그때는 유료(Gemini 3.x 월 5,000회 무료 후 과금, 또는 Claude)로 바꿔야 한다. 제공자 인터페이스를 둬서 교체 비용을 줄인다.
- **출처가 있어도 요약이 틀릴 수 있다.** 답 위에 "AI 요약은 틀릴 수 있어요. 출처를 확인해 주세요"를 둔다.
- **한국어 전문 자료가 적다.** 영어 출처가 많을 것이다. 인용은 원문 그대로 두고 설명만 한국어로 한다.
- **약관 해석 여지가 있다.** "소비자용 아님" 문구와, 4장 ⑤의 "분석 금지"와의 관계가 그렇다.

## 11. 결정 (2026-09-26)

1. **제공자:** 기본은 Gemini 무료(2.5 Flash / Flash-Lite + Google 검색 그라운딩). 설정에서 유료 키를 넣으면 다음도 쓸 수 있다.
   - Gemini 유료: 3.x, 검색 그라운딩 과금.
   - OpenAI GPT: Responses API 웹 검색.
   - Anthropic Claude: 웹 검색 도구, `cited_text` 인용.
2. **키:** 앱에는 어떤 키도 넣지 않는다. 무료·유료 모두 사용자가 설정 › AI에 직접 넣고, 앱이 키 받는 절차를 단계별로 안내한다(5.1). 개발용 무료 키는 `GEMINI_API_KEY_DEBUG` Secret으로 PoC에만 쓴다.
3. **범위:** 모드 A(노트 설명)와 B(맛 묘사 → 노트)를 함께 만든다(제안). 두 모드는 API 호출과 출처 검사를 공유하고, B에 "노트에 추가" 칩이 더해질 뿐이다.
4. **PoC:** GitHub Actions에서 개발용 키 `GEMINI_API_KEY_DEBUG`로 돌린다. 결과를 보고 프롬프트를 다듬은 뒤 앱 구현으로 간다.

## 12. 구현 (2026-09)

### 12.1 결정 (사용자, 2026-09-26)
- **키는 어디에도 넣지 않는다.** APK·저장소·CI 빌드 어디에도 키가 없다. 사용자가 설정 › AI 노트 도우미에서 방식을 고르고 자기 키를 붙여 넣는다. 앱이 키 받는 순서를 서비스별로 안내한다.
- **검색할 수 있는 방식을 모두 만들고 사용자가 고른다.** 넷 다 실제 웹 출처에 묶인 답만 보인다.
- **모드 A·B를 함께 만든다.** A는 노트 설명, B는 맛 묘사 → 앱의 휠 용어·노트 분류 후보(고른 후보는 기록 폼의 "내가 느낀 노트"에 붙음).
- **Claude도 만든다.** SDK 없이 다른 방식처럼 HTTP로 부른다(새 의존성 없음). 같은 날 "Claude 제외"로 바뀌었다가 다시 "포함"으로 확정됐다.
- 2.5 모델을 기본으로 쓰려던 계획(0장)은 버렸다. 새 무료 키로 잰 결과(2026-09-26): `gemini-2.5-flash`·`2.5-flash-lite`·`2.5-pro`는 HTTP 404 "no longer available to new users", `gemini-3.5-flash`·`3.5-flash-lite`·`3.1-flash-lite`는 도구 없이 200, `tools:[{google_search:{}}]`를 붙이면 3.x 모두 HTTP 429 "You exceeded your current quota, please check your plan and billing details"(무료 등급에는 검색 그라운딩이 없음). 그래서 무료 기본값은 검색을 Tavily에 맡기는 방식이다.

### 12.2 방식

| 방식 | 키 | 비용 | 호출 | 기본 모델 · 추천 |
|---|---|---|---|---|
| Gemini 무료 + Tavily (기본) | Gemini, Tavily | 둘 다 무료 등급(Tavily 월 1,000크레딧; 질문 1번 = 검색 한 번, "정밀" advanced 2크레딧(월 약 500번) 또는 "기본" basic 1크레딧(월 약 1,000번)) | Gemini `generateContent`(검색어 쓰기, JSON) → Tavily `POST /search` 한 번 → Gemini `generateContent`(도구 없음) | `gemini-3.5-flash-lite` · 3.5-flash, 3.1-flash-lite |
| Gemini + Google 검색 | Gemini | 결제한 프로젝트. 검색은 3.x 합쳐 월 5,000회 무료, 뒤 1,000회당 $14 | `generateContent` + `google_search` | `gemini-3.5-flash` · 3.8-flash, 3.5-flash-lite |
| GPT (OpenAI) | OpenAI | 웹 검색 1,000회당 $10 + 토큰 | Responses `web_search`, `tool_choice: required`, 위치 KR, `include: web_search_call.action.sources` | `gpt-5-nano` · gpt-5.5 |
| Claude (Anthropic) | Anthropic | 웹 검색 1,000회당 $10 + 토큰(검색 결과도 입력 토큰) | Messages + `web_search`(Opus 5·Sonnet 5는 `web_search_20260209`, Haiku 4.5는 `web_search_20250305`), `max_uses` 3, 위치 KR, `max_tokens` 16000, thinking·temperature 없음. `claude-opus-5`만 `anthropic-beta: server-side-fallback-2026-07-01` + `fallbacks: "default"` | `claude-opus-5` · sonnet-5, haiku-4-5 |

- 모델 칸은 자유 입력이고, 비우면 기본 모델을 쓴다.
- 시스템 지시: Google 검색·GPT·Claude는 `tools/ai-eval/system_prompt_ko.txt`, Gemini 무료 + Tavily는 `tools/ai-eval/sources_prompt_ko.txt`(답)와 `tools/ai-eval/query_prompt_ko.txt`(검색어). 앱의 `NoteHelperPrompts`와 글자까지 같아야 하고 테스트(`AiPlatformTest`)가 확인한다. 질문 틀(모드 A·B)은 `eval.py question()`과 같다(모드 B에는 휠 영문 용어 89개와 노트 분류 43개가 들어감).

### 12.3 Gemini 무료 + Tavily 파이프라인
1. **검색어 쓰기** (2026-09-27 추가). 긴 한국어 묘사를 그대로 검색하면 관련도가 흐려지고 영어 로스터리 페이지를 놓쳐서, 고른 Gemini 모델(도구 없음, 무료 등급)에 `QUERY_SYSTEM`(`query_prompt_ko.txt`)과 노트 이름 또는 묘사("향미 노트: …" / "맛 묘사: …")를 보내 짧은 영어 검색어 하나를 받는다(예: "ripe plum bitter finish tasting notes specialty coffee"). 향미 표현은 그대로 옮기고, 12단어쯤, 전체를 따옴표로 감싸지 않으며, "flavored"는 쓰지 않는다: 모델이 "peach flavored coffee beans"처럼 쓰거나 "hazelnut coffee tasting notes"로 찾으면 어느 깊이로 찾아도 향을 입힌 가향 커피 상품만 나왔다. 그래서 "hazelnut tasting note specialty coffee"처럼 스페셜티 커피의 테이스팅 노트를 겨냥한다. 요청은 `generationConfig`의 `responseMimeType: application/json`과 `responseSchema`({"queries": [문자열]}), temperature 0, maxOutputTokens 200(도구 없는 구조화 출력은 무료 등급에서 된다). 받은 목록의 첫 검색어만 쓰고(공백 정리, 전체를 감싼 따옴표 제거), 비어 있거나 200자를 넘으면 실패로 본다. 키가 거절된 경우(400 키 오류·401·403)만 오류로 알리고, 그 밖의 실패(오류 상태, 연결 없음, JSON이 아님, 빈 목록)는 조용히 전의 틀로 검색한다: 모드 A `"<영문 이름 또는 노트 이름>" coffee flavor note meaning tasting notes`(라벨 괄호 안에 영문이 있으면 그것), 모드 B `coffee tasting notes <묘사>`(300자에서 자름). 모델·한도 문제는 답 단계가 알린다.
2. **Tavily 검색 한 번**, 깊이는 설정 › AI 노트 도우미 › "검색"(이 방식일 때만 보임, `device.ai.searchDepth`, 백업 제외, 모르는 값은 기본값으로 읽음): "정밀"(기본값) = advanced(2크레딧: 결과 5개, 페이지당 조각 3개, `include_raw_content: "text"`; 그 향미를 다루는 페이지와 인용할 수 있는 긴 조각), "기본" = basic(1크레딧: 결과 5개, 조각 없음; 더 빠르고 절반). 무료 월 1,000크레딧이면 정밀 약 500번, 기본 약 1,000번. 검색이 실패하면 그 오류를 알린다. 셋째 선택지("정밀 + 사람들 의견": advanced 본 검색 + 커뮤니티 도메인만 찾는 검색과 "개인 의견" 요약)는 평가 중이라 만들지 않았고, `SearchDepth`는 그 자리를 남겨 두었다.
   - 결정 근거(평가 #14, 새 사례 6개 × 방식 9개, Tavily 캐시가 깊이를 넘나들지 않게 basic부터): advanced 한 번은 문장 56개 중 46개가 출처와 연결되고 인용 52개 중 51개가 원문에 있었으며 12크레딧; advanced + basic은 39/48, 51/51, 18크레딧; advanced 두 번은 37/48, 41/43, 24크레딧; basic 한 번은 28/36, 36/36, 6크레딧. advanced는 그 향미를 다루는 페이지(예: unpacking.coffee/flavors/…)를 그 용어 주변 조각과 함께 찾았고, 둘째 검색은 여섯째 출처만 더했을 뿐 인용되는 내용을 늘리지 않았다. 검색어 쓰기 없이 고정 틀로 찾으면 훨씬 나빴다(긴 한국어 묘사는 문장을 그대로 찾아 0/1, 0/2).
   - Tavily는 검색어마다 결과를 캐시한다. advanced로 찾은 검색어를 basic으로 다시 찾으면 advanced 결과가 오고, 반복해도 크레딧은 든다.
3. **Gemini(도구 없음)**에 질문과 출처 묶음 `[n] <제목> — <도메인>\n<URL>\n<content>`을 보낸다(토큰을 줄이려고 raw_content가 아니라 content). 지시: 주어진 출처만으로 답하고, 출처 n을 쓴 항목·문장 끝마다 [n], 향·맛 표현은 출처 그대로 큰따옴표로(잘린 조각은 온전한 부분만), 뜻풀이가 없으면 출처의 묘사 문장으로 요약할 수 있고 그것도 없으면 "찾지 못했어요".
4. **앱이 확인한다.** [n] 표시를 걷어 내 문장 끝 번호로 바꾸고(없는 번호는 버림), 번호가 붙은 문장의 큰따옴표 표현을 그 출처의 raw_content(없으면 content)에서 찾는다. 정규화(NFC, 소문자, 공백 하나로, 곧은 따옴표)한 부분 문자열이면 "✓ 원문 확인", 아니면 "원문에서 찾지 못함". 잘린 조각("… red c")도 원문의 일부면 찾은 것으로 친다. 번호 없는 문장은 회색으로 남기고 지우지 않는다. 번호가 붙은 문장이 없으면 답 대신 "출처를 찾지 못했어요".
- 따옴표: 답 모델이 노트 이름 자체를 따옴표로 감싸("시나몬 (cinnamon)") 인용 확인에 걸리는 일이 있어, 두 답 지시(`system_prompt_ko.txt`·`sources_prompt_ko.txt`)의 인용 규칙에 "노트 이름이나 검색어는 따옴표로 감싸지 않는다"를 더했다.
- 평가(코디네이터 세션, 무료 키, `gemini-3.5-flash-lite`, Tavily 대신 실제 페이지, 4건): 인용 11개 모두 인용한 출처에 그대로 있었고, 없는 번호를 붙인 경우는 없었으며, 답 한 번에 약 1초. 잘린 조각을 그대로 인용한 사례("Lychee, white peach, red c")가 있어 지시에 "온전한 단어와 구절로만"을 더했다.

### 12.4 다른 방식의 출처 연결
- **Gemini + Google 검색**: `groundingChunks`가 출처(링크는 vertexaisearch 경유, 제목이 보통 도메인), `groundingSupports`의 세그먼트 끝에 번호를 붙인다(Google 예제와 같음). 위치는 세그먼트 글을 앞 세그먼트 뒤에서부터 찾고, 없을 때만 인덱스를 UTF-8 바이트로 읽는다(문서마다 글자/바이트가 다름). 답 글은 고치지 않고 번호만 더하며, 검색 제안(`searchEntryPoint.renderedContent`)을 WebView(스크립트 끔, 높이 64dp, 누르면 브라우저)로 함께 보인다. 답은 화면에만 있고 저장하지 않는다.
- **GPT**: `url_citation`의 end_index가 든 문장 끝에 번호를 붙이고, 모델이 글 속에 쓴 마크다운 링크 `([도메인](URL))`은 번호로 대신하므로 걷어 낸다. 출처는 처음 인용된 순서, `utm_source=openai`만 다른 주소는 하나로 친다.
- **Claude**: 텍스트 블록을 순서대로 이어 붙이고, 인용이 있는 블록의 끝에 번호를 붙인다. 출처는 인용된 URL(처음 인용 순서) 다음에 나머지 검색 결과. 인용 블록 안의 큰따옴표 표현이 그 인용의 `cited_text`(원문 그대로 최대 150자)에 있으면 "✓ 원문 확인"을 붙이고, 없어도 "못 찾음"은 표시하지 않는다(150자에 없다고 원문에 없는 것은 아님). `stop_reason`은 내용보다 먼저 본다: `refusal` → "요청이 거절됐어요", `pause_turn` → 받은 내용을 그대로 assistant 메시지로 붙여 다시 요청(최대 3번), `max_tokens` → 받은 만큼과 "답이 길어 끝이 잘렸어요". 검색 결과는 목록 또는 오류 객체이고, 모르는 블록(thinking, 동적 필터링의 코드 실행과 `caller`가 붙은 블록)은 건너뛴다.
- 넷 모두 답 아래에 "검색어: q1 · q2"(고정폭, 흐리게)를 보인다. Gemini 무료 + Tavily는 앱이 보낸 검색어, Google 검색은 `groundingMetadata.webSearchQueries`, GPT는 `web_search_call` 중 `action.type`이 search인 것의 `action.query`, Claude는 이름이 web_search인 `server_tool_use`의 `input.query`(동적 필터링이 코드 실행에서 부른 것 포함). Gemini 무료 + Tavily만 "검색어 고치기"로 화면 안 입력칸(대화상자가 아님: Robolectric에서 대화상자 속 입력칸은 Compose가 idle이 되지 않음)에서 검색어를 고쳐 "이 검색어로 다시 묻기"를 할 수 있고, 이때는 검색어 쓰기 단계를 건너뛴다. 나머지는 읽기만 한다.
- 넷 모두: 출처 목록은 번호·제목·도메인에 기관(sca.coffee, worldcoffeeresearch.org 등 7곳) / 개인 글(blog.naver.com, tistory.com, reddit.com 등 13곳) 표시. 번호가 붙은 문장이 하나도 없으면 답을 보이지 않는다.

### 12.5 키 · 설정 · 네트워크
- **키 저장**: `SecretStore`. Android는 Android Keystore에 내보낼 수 없는 AES-256/GCM 키(별칭 `coffeejournal.ai`)를 만들고, 키마다 암호문과 IV를 `noBackupFilesDir/ai-keys/<이름>.bin`에 둔다. DB·JSON 백업·클라우드 백업·기기 이전에 가지 않는다. 풀리지 않는 파일(키 삭제, 변조)은 지우고 "키 없음"으로 본다. `androidx.security-crypto`는 쓰지 않는다. iOS는 "아직 지원 안 함".
- **설정**: 방식, 방식별 모델, 방식별 안내 확인은 `device.ai.*` 키(백업 제외). 입력 중인 키는 저장 상태 번들에 남기지 않는다. 저장된 키는 마지막 4자리("…a1b2")만 보인다.
- **키 확인**: Gemini는 도구 없는 아주 짧은 요청(`maxOutputTokens` 8, 고른 방식의 모델), Tavily는 basic 검색 1개(1크레딧, 화면에 적음), OpenAI·Anthropic은 `GET /v1/models`(무료; 목록이 끝까지 왔는데 고른 모델이 없으면 "이 모델은 쓸 수 없어요"). 결과는 정상 / 키가 틀려요 / 결제가 필요해요 / 한도를 넘었어요 / 이 모델은 쓸 수 없어요 / 연결 실패.
- **동의**: 방식마다 처음 물을 때 무엇을(질문 글만; 기록·원두·장소·사진은 안 보냄) 어디로 보내는지, Gemini 무료 등급의 학습·사람 검토 조건과 유료 방식의 과금을 보이고 확인을 받는다.
- **HTTP**: `AiHttp`. Android는 `HttpURLConnection`(연결 15초, 읽기 120초, IO 디스패처, 4xx/5xx는 오류 스트림). 새 라이브러리 0개라 `verification-metadata.xml`과 라이브러리 목록은 그대로다. iOS는 미연결.

### 12.6 오류 안내 (요지)
| 서비스 | 상태 | 안내 |
|---|---|---|
| Gemini | 400 `API_KEY_INVALID`, 401/403 | 키가 맞지 않아요 + "설정에서 키 넣기" |
| Gemini | 404 ("no longer available to new users") | ‘모델’은 쓸 수 없어요, 새 키에는 열어 주지 않는 모델 + "설정에서 모델 바꾸기" |
| Gemini | 429 (Google 검색 요청) | Google 검색은 결제를 켠 프로젝트에서만 돼요. 결제를 켜거나 'Gemini 무료 + Tavily'를 고르세요 |
| Gemini | 429 (그 밖) / 402 / 503 | 한도(태평양 시간 자정에 다시 채워짐) / 선불 크레딧 없음 / 붐빔 |
| Tavily | 401 / 429 / 432 / 433 | 키 / 너무 잦음 / 이번 달 크레딧 소진 / 종량제 한도 |
| OpenAI | 401 / 429 `insufficient_quota` / 429 / 402·`credit_balance_exhausted` / 404 | 키 / 결제 필요 / 요청 한도 / 크레딧 소진 / 모델 |
| Anthropic | 400 "credit balance" / 400 웹 검색 꺼짐 / 401 / 402 / 403 / 404 / 429 / 500·529 | 크레딧 / 관리자가 platform.claude.com/settings/privacy에서 켜야 함 / 키 / 결제 / 권한 / 모델 / 한도 / 붐빔 |
| 공통 | 연결 없음 · 출처 0 · 안전 필터 · 거절 | 연결 실패 · 출처를 찾지 못했어요 · 다른 말로 물어봐 주세요 |

### 12.7 화면과 테스트
- 진입: 노트 상세 "✦ 출처로 알아보기"(모드 A, 질문 = 노트 라벨), 기록 폼 "✦ AI에게 묻기"(모드 B). 폼의 묘사 입력은 대화상자가 아니라 노트 칸 아래 패널이다(대화상자 속 입력칸은 Robolectric에서 Compose가 idle이 되지 않았다, 구현 규약). 답 화면은 `Route.NoteHelper(mode, query, returnToForm)`, 고른 후보는 이전 목적지의 SavedStateHandle(`NoteHelperResult.KEY`)로 돌아가 중복 없이 붙는다. 키가 없으면 답 화면이 이유와 "설정에서 키 넣기 →"를 보이고, 설정은 AI 절로 스크롤되어 열리며, 돌아오면 이어서 묻는다.
- 공용 단위 테스트(`shared/src/commonTest/.../ui/ai`, 가짜 JSON은 모두 synthetic으로 표시): 네 서비스의 요청 형식과 응답 해석, 검색어 쓰기의 요청·JSON 검사(첫 검색어만)·틀로 돌아가기, advanced 검색 한 번과 실패 처리, 서비스별 검색어 읽기, Gemini 세그먼트의 글자/UTF-8 바이트 위치(한국어), [n] 해석, 인용 대조 정규화(NFD 한글·따옴표·공백·잘린 조각), 모드 B 용어 찾기, 서비스·상태별 오류, pause_turn 재요청·거절·출처 없음, 키 확인, 설정 저장.
- 흐름 테스트(`AiFlowTest`, 가짜 HTTP·메모리 키 저장소): 설정 절(방식, 키 저장·가림·확인 정상/401, 안내 펼침과 링크, 모델 칩, 지우기 확인, 백업 제외), 노트 상세 → 동의 → 답(검색어 쓰기와 검색, 번호, 회색 문장, ✓·✗, 기관·개인 글, [n] → 출처, 링크 열기, 동의는 한 번), 검색어 고쳐 다시 묻기, 동의 거절, 폼 → 후보 → 노트에 추가, 키 없음 → 설정 → 답, 무료 프로젝트 Google 검색 429, 모델 404, Claude. `AiPlatformTest`: 프롬프트 파일 세 개·질문 틀 동기화, Keystore 파일 형식(소프트웨어 키). 스크린샷 70–74.

### 12.8 확인한 것 · 못 한 것
- 확인: 요청·응답 형식과 오류 코드는 2026-09-26 공식 문서 기준(코디네이터 세션에서 확인)이고, Gemini의 404·429 문구는 새 무료 키로 잰 값이다. Gemini 무료 + Tavily 파이프라인은 12.3의 평가로 확인했다. Anthropic 약관·개인정보 링크는 curl로 200을 확인했다.
- 못 한 것: 이 저장소의 테스트는 네트워크를 쓰지 않으므로 앱이 실제 서비스에 보낸 요청은 없다(응답은 문서 모양으로 손으로 쓴 가짜). OpenAI·Anthropic의 실제 응답(특히 url_citation 위치 단위, 동적 필터링 블록 모양), Google 검색 제안 HTML이 WebView에서 어떻게 보이는지, AndroidKeyStore 동작(Robolectric에 없음)은 기기에서 확인해야 한다. openai.com의 약관·개인정보 페이지는 Cloudflare가 curl을 막아(403) 직접 확인하지 못했고, Wayback Machine에 2026-09의 200 응답 사본이 있는 것만 확인했다. iOS는 연결하지 않았다.

