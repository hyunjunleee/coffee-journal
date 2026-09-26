# AI 노트 도우미 계획 (초안)

사용자 요청(2026-09-26): 향미 노트를 찾을 때 AI의 도움을 받는다. 가장 좋은 무료 AI API에 연결하고, AI가 항상 인터넷에 실제로 나오는 향·맛 표현을 직접 찾아 출처와 함께 설명·대답하도록 미리 지시(프롬프트)를 넣는다.

이 문서는 계획이다. 구현 전에 11장의 결정이 필요하다. 사실 항목은 모두 2026-09-26에 공식 문서에서 확인했고 링크를 달았다.

## 0. 결론

- **가능하다.** 다만 무료이면서 실제 웹 출처를 달아 주는 API는 지금 사실상 하나다. Google Gemini API 무료 등급의 **Gemini 2.5 Flash / 2.5 Flash-Lite**에 **Google 검색 그라운딩**을 켜는 조합으로, 두 모델 합쳐 하루 500건까지 무료다.
- **Gemini 3.x는 제외된다.** 3.x 모델은 무료 등급에서 검색 그라운딩을 쓸 수 없다.
- **무료 등급의 조건:**
  - 보낸 질문과 받은 답이 Google 제품 개선에 쓰이고, 사람이 검토할 수 있다.
  - 답과 함께 "Google 검색 제안"을 반드시 보여야 하고, 답을 고치거나 다른 내용을 섞으면 안 된다.
  - Google은 모바일 앱에 키를 넣지 말라고 권한다. 이 앱은 비공개 저장소에서 본인 휴대폰에만 설치하므로, 결제가 꺼진 무료 키만 빌드에 넣고 유료 키는 앱에서만 입력한다(5장).
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
| 모바일 앱에 키를 하드코딩하지 말 것(서버 프록시 권장). 2026-05-28부터 새 키는 인증 키 형식이다 | 무료 키만, 결제가 꺼진 프로젝트의 것을 GitHub Secret으로 빌드에 넣는다(개인 설치용, 위험은 5장). 유료 키는 앱에서만 입력한다. 서버 프록시는 5장의 대안 |
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
- **무료 Gemini 키 (결정 2026-09-26): 휴대폰에서 입력하지 않는다.**
  - 사용자가 [Google AI Studio](https://aistudio.google.com/)에서 결제가 꺼진 프로젝트의 무료 키를 만든다.
  - 그 키를 GitHub 저장소 Secret `GEMINI_API_KEY`로 한 번 등록한다(서명 키와 같은 곳).
  - CI가 서명 APK를 만들 때 키를 넣어 준다. PoC 평가(8장)도 같은 Secret을 쓴다.
  - 위험: APK에서 키를 꺼낼 수 있다. Google 문서도 모바일 앱에 키를 넣지 말라고 권한다.
    - 비공개 저장소의 APK를 본인 휴대폰에만 설치하는 동안에는 위험이 작다.
    - APK를 남에게 주면 그 사람도 이 키로 무료 한도를 쓸 수 있다.
    - 그래서 결제가 켜진 프로젝트의 키는 절대 이 Secret에 넣지 않는다. 넣으면 요금이 나갈 수 있다.
  - 설정 › AI에서 다른 무료 키로 바꾸거나 지울 수 있다. 앱에서 입력한 키가 빌드에 들어간 키보다 먼저 쓰인다.
- **유료 키 (결정 2026-09-26): Gemini 유료, OpenAI(GPT), Anthropic(Claude).**
  - 설정 › AI에서 제공자를 고르고 키를 붙여 넣는다.
  - 유료 키는 APK, 저장소, CI 어디에도 넣지 않는다. 기기 안에서만 암호화해 보관한다.
  - "키 확인" 버튼(짧은 요청 한 번)과 키 삭제 버튼을 둔다.
  - 질문마다 대략의 비용 안내를 붙인다(2장 가격 기준).
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
  - 저장소 Secret `GEMINI_API_KEY`가 있으면 GitHub Actions에서 실제로 질문한다. 없으면 파서 자체 점검만 한다.
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
2. **키:** 무료 Gemini 키는 GitHub Secret으로 빌드에 넣어 휴대폰 입력이 없다. 유료 키는 앱에서만 입력한다(5장).
3. **범위:** 모드 A(노트 설명)와 B(맛 묘사 → 노트)를 함께 만든다(제안). 두 모드는 API 호출과 출처 검사를 공유하고, B에 "노트에 추가" 칩이 더해질 뿐이다.
4. **PoC:** GitHub Actions에서 사용자의 무료 키(Secret)로 돌린다. 결과를 보고 프롬프트를 다듬은 뒤 앱 구현으로 간다.
