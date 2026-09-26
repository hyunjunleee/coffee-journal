# AI 노트 도우미 PoC

`docs/ai-note-helper-plan.md`의 PoC입니다. Gemini 무료 등급(Google 검색 그라운딩)에 앱이 할 두 종류의 질문을 보냅니다.

- 노트 설명: `cases.json`의 `note`
- 맛 묘사로 노트 찾기: `cases.json`의 `describe`

시스템 지시는 `system_prompt_ko.txt`입니다. 평가 항목은 다음과 같습니다.

- 출처가 붙은 답의 비율
- 출처 수와 종류(기관 / 개인 글 / 기타)
- 답 중 출처와 연결된 비율
- 인용 문구가 출처 연결 문장 안에 있는지
- 응답 시간

## 실행

- GitHub Actions의 "AI note helper eval"로 실행합니다.
  - `tools/ai-eval/`을 바꿔 푸시하면 자동으로 돕니다.
  - Actions 탭에서 모델과 개수를 골라 수동으로도 실행할 수 있습니다.
  - 저장소 Secret `GEMINI_API_KEY`(결제가 꺼진 프로젝트의 무료 키)가 있어야 실제 질문을 보냅니다. 없으면 자체 점검만 합니다.
  - 보고서는 실행 페이지 요약과 7일짜리 아티팩트로 남습니다. 저장소에는 커밋하지 않습니다.
- 로컬 실행은 `GEMINI_API_KEY=… python3 tools/ai-eval/eval.py --notes 3 --describes 2`입니다. 파이썬 표준 라이브러리만 씁니다.
- 자체 점검(키 불필요)은 `python3 tools/ai-eval/eval.py --selftest`입니다.
