# AI 노트 도우미 PoC

`docs/ai-note-helper-plan.md`의 PoC입니다. Gemini 무료 등급(Google 검색 그라운딩)에 앱이 할 두 종류의 질문을 보냅니다.

- 노트 설명: `cases.json`의 `note`
- 맛 묘사로 노트 찾기: `cases.json`의 `describe`

시스템 지시는 `system_prompt_ko.txt`입니다. 앱은 같은 글을 "Gemini + Google 검색", GPT, Claude 방식의 지시로 쓰고, "Gemini 무료 + Tavily" 방식(Tavily가 찾은 글을 번호를 붙여 넘기는 방식)에는 `sources_prompt_ko.txt`를 씁니다. 두 파일은 앱의 `NoteHelperPrompts`와 글자 하나까지 같아야 하고, 앱 테스트(`AiPlatformTest`)가 확인합니다. 한쪽을 고치면 다른 쪽도 고칩니다. 평가 항목은 다음과 같습니다.

- 출처가 붙은 답의 비율
- 출처 수와 종류(기관 / 개인 글 / 기타)
- 답 중 출처와 연결된 비율
- 인용 문구가 출처 연결 문장 안에 있는지
- 응답 시간

## 실행

- GitHub Actions의 "AI note helper eval"로 실행합니다.
  - `tools/ai-eval/`을 바꿔 푸시하면 자동으로 돕니다.
  - Actions 탭에서 모델과 개수를 골라 수동으로도 실행할 수 있습니다.
  - 저장소 Secret `GEMINI_API_KEY_DEBUG`(개발용 무료 키, 결제가 꺼진 프로젝트)가 있어야 실제 질문을 보냅니다. 없으면 자체 점검만 합니다.
  - 이 키는 개발용이라 앱(APK)에는 넣지 않습니다. 앱에서는 사용자가 자기 키를 직접 입력합니다.
  - 저장소 Secret `TAVILY_API_KEY_DEBUG`(개발용 무료 Tavily 키)도 있으면 실제 "Gemini 무료 + Tavily" 파이프라인(`--tavily`: 검색어 만들기 → Tavily → Gemini → 인용 확인)을 돌리고, 없으면 미리 고른 페이지로 대신합니다(`--sources`). 푸시 한 번에 노트 6개·묘사 4개, Tavily 약 30크레딧(월 1,000 무료).
  - 검색 설정 비교: `--tavily --plans basic,adv+basic,…`는 같은 질문을 플랜마다 답하게 하고, `--retrieval --depths ultra-fast,fast,basic,advanced,basic`은 답 없이 Tavily 결과만 깊이별로 나란히 놓습니다. Tavily는 전에 검색한 같은 검색어를 캐시에서 돌려줄 수 있어서(advanced 뒤의 basic이 advanced 결과를 그대로 받음), 공정하게 비교하려면 검색한 적 없는 질문(`--notes 7-10`처럼 범위로 고름)을 낮은 깊이부터 검색합니다. 크레딧은 Tavily가 응답에 적어 준 값(`include_usage`)으로 셉니다.
  - 보고서는 실행 페이지 요약과 7일짜리 아티팩트로 남습니다. 저장소에는 커밋하지 않습니다.
- 로컬 실행은 `GEMINI_API_KEY=… python3 tools/ai-eval/eval.py --notes 3 --describes 2`입니다. 파이썬 표준 라이브러리만 씁니다.
- 자체 점검(키 불필요)은 `python3 tools/ai-eval/eval.py --selftest`입니다.
