#!/usr/bin/env python3
"""PoC for the AI note helper (docs/ai-note-helper-plan.md).

Asks Gemini (free tier, Google Search grounding) the two kinds of questions the app will ask -- explain a flavor note
(mode "note") and find notes for a described taste (mode "describe") -- with the system prompt in
system_prompt_ko.txt, and measures what the plan's source checks need: how many answers come back grounded, how many
sources and which kinds of sites, how much of each answer is tied to a source, whether the quoted expressions sit in
tied sentences, and whether the grounding segment offsets are character or UTF-8 byte offsets.

Standard library only. The key is read from the environment (GEMINI_API_KEY) and sent as a header; it is never
printed or written. Output: <out>/report.md (for a person to read, with the sources) and <out>/raw.jsonl.

  python3 tools/ai-eval/eval.py --selftest
  GEMINI_API_KEY=... python3 tools/ai-eval/eval.py --model gemini-2.5-flash --notes 12 --describes 6 --out out/
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
API = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
REFERENCE = ROOT / "shared/src/commonMain/kotlin/com/coffeejournal/domain/reference"

# kinds of source, for the report only (the app shows the domain either way)
INSTITUTIONS = ("sca.coffee", "worldcoffeeresearch.org", "coffeeinstitute.org", "allianceforcoffeeexcellence.org",
                "cupofexcellence.org", "ico.org", "ncausa.org")
PERSONAL = ("blog.naver.com", "cafe.naver.com", "tistory.com", "brunch.co.kr", "velog.io", "medium.com", "reddit.com",
            "quora.com", "instagram.com", "youtube.com", "facebook.com", "x.com", "twitter.com", "home.coffeegeek.com")


def wheel_terms():
    """The app's own vocabulary: flavor wheel terms (English) and note categories (Korean), read from the Kotlin sources."""
    wheel = (REFERENCE / "FlavorWheel.kt").read_text(encoding="utf-8")
    terms = [t for group in re.findall(r'Group\("[^"]*", listOf\(([^)]*)\)\)', wheel) for t in re.findall(r'"([^"]+)"', group)]
    notes = (REFERENCE / "NoteCategories.kt").read_text(encoding="utf-8")
    subs = re.findall(r'Sub\("([^"]+)"', notes)
    return terms, subs


def question(mode, case, terms, subs):
    if mode == "note":
        return (f'노트 설명. 향미 노트: "{case}"\n'
                "형식:\n"
                "- 한 줄 뜻: 커피에서 이 노트가 가리키는 향·맛\n"
                "- 실제 쓰임 2~4개: 인용 + 어떤 원두·가공·로스팅에서 나왔는지\n"
                "- 비슷한 표현·헷갈리는 표현\n"
                "- Coffee Taster's Flavor Wheel에서의 위치(찾은 경우에만)")
    return (f'맛 묘사로 노트 찾기. 마신 사람의 묘사: "{case}"\n'
            "아래 목록 안에서 어울리는 용어 3~5개를 고르고, 각각 그렇게 부르는 근거를 실제 문서의 인용으로 보여줘.\n"
            "형식:\n"
            "- 용어: 근거(인용과 출처)\n"
            "- 후보를 구별하는 방법(출처가 있을 때만)\n"
            f"플레이버 휠 용어: {', '.join(terms)}\n"
            f"앱의 한국어 노트 분류: {', '.join(subs)}")


def request_body(system, text):
    return {
        "system_instruction": {"parts": [{"text": system}]},
        "contents": [{"role": "user", "parts": [{"text": text}]}],
        "tools": [{"google_search": {}}],
        "generationConfig": {"temperature": 0.2},
    }


def call(model, key, body, timeout=120):
    req = urllib.request.Request(
        API.format(model=urllib.parse.quote(model)),
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
        method="POST",
    )
    start = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8")), time.monotonic() - start
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        try:
            detail = json.loads(detail)
        except ValueError:
            pass
        return e.code, detail, time.monotonic() - start
    except (urllib.error.URLError, TimeoutError) as e:
        return 0, str(e), time.monotonic() - start


def domain_of(chunk):
    """Grounding chunk URIs are often redirect links; the title usually carries the site's domain."""
    title = (chunk.get("title") or "").strip().lower()
    if re.fullmatch(r"[a-z0-9.-]+\.[a-z]{2,}", title):
        return title.removeprefix("www.")
    host = urllib.parse.urlparse(chunk.get("uri") or "").hostname or ""
    return host.removeprefix("www.")


def kind_of(domain):
    if any(domain == d or domain.endswith("." + d) for d in INSTITUTIONS):
        return "institution"
    if any(domain == d or domain.endswith("." + d) for d in PERSONAL):
        return "personal"
    return "other"


def locate(text, segment, cursor=0):
    """(start, end, kind) of a grounding segment in [text], in characters.

    Found by the segment's own text first (robust whatever the offsets mean), then checked against the offsets:
    kind is "chars" when startIndex/endIndex are character offsets, "bytes" when they are UTF-8 byte offsets.
    """
    seg_text = segment.get("text") or ""
    si, ei = segment.get("startIndex", 0), segment.get("endIndex", 0)
    kind = "unknown"
    if seg_text and text[si:ei] == seg_text:
        kind = "chars"
    else:
        raw = text.encode("utf-8")
        if seg_text and raw[si:ei].decode("utf-8", "replace") == seg_text:
            kind = "bytes"
    if seg_text:
        i = text.find(seg_text, cursor)
        if i < 0:
            i = text.find(seg_text)
        if i >= 0:
            return i, i + len(seg_text), kind
    if kind == "bytes":
        raw = text.encode("utf-8")
        return len(raw[:si].decode("utf-8", "ignore")), len(raw[:ei].decode("utf-8", "ignore")), kind
    return None


QUOTE = re.compile(r'["“]([^"”\n]{2,160})["”]')


def analyse(resp):
    cands = resp.get("candidates") or []
    if not cands:
        return {"grounded": False, "text": "", "finish": resp.get("promptFeedback", {}).get("blockReason", "NO_CANDIDATE")}
    cand = cands[0]
    text = "".join(p.get("text", "") for p in (cand.get("content") or {}).get("parts", []))
    gm = cand.get("groundingMetadata") or {}
    chunks = [c.get("web") or {} for c in gm.get("groundingChunks", [])]
    spans, kinds, cursor = [], set(), 0
    for s in gm.get("groundingSupports", []):
        found = locate(text, s.get("segment") or {}, cursor)
        if not found:
            continue
        start, end, kind = found
        kinds.add(kind)
        cursor = end
        spans.append((start, end, sorted(set(s.get("groundingChunkIndices", [])))))
    covered = set()
    for start, end, _ in spans:
        covered.update(range(start, end))
    visible = [i for i, ch in enumerate(text) if not ch.isspace()]
    coverage = (sum(1 for i in visible if i in covered) / len(visible)) if visible else 0.0
    quotes = [(m.start(), m.end(), m.group(1)) for m in QUOTE.finditer(text)]
    quoted_tied = sum(1 for qs, qe, _ in quotes if any(s <= qs and qe <= e for s, e, _ in spans))
    domains = [domain_of(c) for c in chunks]
    return {
        "grounded": bool(chunks),
        "text": text,
        "finish": cand.get("finishReason"),
        "sources": [{"n": i + 1, "title": c.get("title"), "uri": c.get("uri"), "domain": d, "kind": kind_of(d)}
                    for i, (c, d) in enumerate(zip(chunks, domains))],
        "queries": gm.get("webSearchQueries", []),
        "has_search_entry_point": bool((gm.get("searchEntryPoint") or {}).get("renderedContent")),
        "spans": spans,
        "coverage": round(coverage, 3),
        "quotes": len(quotes),
        "quotes_tied": quoted_tied,
        "offset_kinds": sorted(kinds),
        "usage": resp.get("usageMetadata", {}),
    }


def annotate(text, spans):
    """[text] with [n] after every sentence the grounding ties to sources (n = 1-based source numbers)."""
    out = text
    for start, end, idx in sorted(spans, key=lambda s: s[1], reverse=True):
        if idx:
            out = out[:end] + "[" + ",".join(str(i + 1) for i in idx) + "]" + out[end:]
    return out


def quota_exhausted_for_today(detail):
    blob = json.dumps(detail, ensure_ascii=False) if not isinstance(detail, str) else detail
    return "PerDay" in blob or "per day" in blob.lower()


def run(args):
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        sys.exit("GEMINI_API_KEY is not set")
    system = (HERE / "system_prompt_ko.txt").read_text(encoding="utf-8")
    cases = json.loads((HERE / "cases.json").read_text(encoding="utf-8"))
    terms, subs = wheel_terms()
    plan = [("note", c) for c in cases["note"][: args.notes]] + [("describe", c) for c in cases["describe"][: args.describes]]
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows, stopped = [], None
    with (out / "raw.jsonl").open("w", encoding="utf-8") as raw:
        for n, (mode, case) in enumerate(plan, 1):
            body = request_body(system, question(mode, case, terms, subs))
            status, resp, secs = call(args.model, key, body)
            if status == 429 and not quota_exhausted_for_today(resp):
                time.sleep(45)
                status, resp, secs = call(args.model, key, body)
            row = {"n": n, "mode": mode, "case": case, "model": args.model, "status": status, "seconds": round(secs, 1)}
            if status == 200:
                row.update(analyse(resp))
            else:
                row["error"] = resp if isinstance(resp, str) else json.dumps(resp, ensure_ascii=False)[:600]
            rows.append(row)
            raw.write(json.dumps({**row, "spans": row.get("spans")}, ensure_ascii=False) + "\n")
            print(f"[{n}/{len(plan)}] {mode} {case!r}: HTTP {status}, sources {len(row.get('sources', []))}, "
                  f"coverage {row.get('coverage', '-')}, {row['seconds']} s", flush=True)
            if status == 429 and quota_exhausted_for_today(resp):
                stopped = "the free daily quota ran out"
                break
            if status in (400, 401, 403):
                stopped = f"HTTP {status} (key or request refused)"
                break
            time.sleep(args.delay)
    (out / "report.md").write_text(report(rows, args, stopped), encoding="utf-8")
    print((out / "report.md").read_text(encoding="utf-8").split("\n## ", 2)[0])


def report(rows, args, stopped):
    ok = [r for r in rows if r["status"] == 200]
    grounded = [r for r in ok if r.get("grounded")]
    kinds = {"institution": 0, "personal": 0, "other": 0}
    for r in grounded:
        for s in r["sources"]:
            kinds[s["kind"]] += 1
    offsets = sorted({k for r in grounded for k in r.get("offset_kinds", [])})
    mean = lambda xs: (sum(xs) / len(xs)) if xs else 0.0  # noqa: E731
    lines = [
        f"# AI note helper PoC: {args.model}",
        "",
        f"- asked: {len(rows)} (note {sum(r['mode'] == 'note' for r in rows)}, describe {sum(r['mode'] == 'describe' for r in rows)})"
        + (f"; stopped early: {stopped}" if stopped else ""),
        f"- HTTP 200: {len(ok)}; grounded (at least one source): {len(grounded)}",
        f"- sources per grounded answer: {mean([len(r['sources']) for r in grounded]):.1f}"
        f" (institution {kinds['institution']}, personal {kinds['personal']}, other {kinds['other']})",
        f"- share of each answer tied to a source (non-space characters): mean {mean([r['coverage'] for r in grounded]):.0%}",
        f"- quoted expressions: {sum(r['quotes'] for r in grounded)}, inside a tied sentence: {sum(r['quotes_tied'] for r in grounded)}",
        f"- search suggestions returned: {sum(r['has_search_entry_point'] for r in grounded)} of {len(grounded)}",
        f"- grounding segment offsets: {', '.join(offsets) or 'n/a'}",
        f"- seconds per answer: mean {mean([r['seconds'] for r in ok]):.1f}",
        "",
        "| # | mode | question | HTTP | sources | tied | quotes (tied) | s |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for r in rows:
        lines.append(f"| {r['n']} | {r['mode']} | {r['case']} | {r['status']} | {len(r.get('sources', []))} | "
                     f"{r.get('coverage', 0):.0%} | {r.get('quotes', 0)} ({r.get('quotes_tied', 0)}) | {r['seconds']} |")
    lines += ["", "## Answers", ""]
    for r in rows:
        lines += [f"### {r['n']}. {r['mode']}: {r['case']}", ""]
        if r["status"] != 200:
            lines += [f"HTTP {r['status']}: `{r.get('error', '')[:300]}`", ""]
            continue
        lines += [annotate(r["text"], r.get("spans", [])), ""]
        if r.get("queries"):
            lines += ["검색어: " + " · ".join(r["queries"]), ""]
        for s in r.get("sources", []):
            lines.append(f"[{s['n']}] {s['title']} ({s['kind']}) {s['uri']}")
        lines.append("")
    return "\n".join(lines)


def selftest():
    """Checks the parsing on a hand-made response (not real API output): offsets, coverage, quotes, markers."""
    text = '베르가못은 얼그레이 홍차의 향이다. 한 로스터리는 "bergamot, jasmine"이라고 적었다. 출처 없는 문장.'
    first = text.index(".") + 1
    second_start = first + 1
    second_end = text.index(".", second_start) + 1
    raw = text.encode("utf-8")
    b = lambda i: len(text[:i].encode("utf-8"))  # noqa: E731
    fake = {"candidates": [{
        "content": {"parts": [{"text": text}]},
        "finishReason": "STOP",
        "groundingMetadata": {
            "webSearchQueries": ["bergamot coffee tasting note"],
            "searchEntryPoint": {"renderedContent": "<div></div>"},
            "groundingChunks": [{"web": {"uri": "https://vertexaisearch.cloud.google.com/grounding-api-redirect/x", "title": "sca.coffee"}},
                                {"web": {"uri": "https://example-roaster.com/p/1", "title": "Example Roaster"}}],
            "groundingSupports": [
                {"segment": {"startIndex": b(0), "endIndex": b(first), "text": text[:first]}, "groundingChunkIndices": [0]},
                {"segment": {"startIndex": b(second_start), "endIndex": b(second_end), "text": text[second_start:second_end]}, "groundingChunkIndices": [1, 0]},
            ],
        },
    }]}
    a = analyse(fake)
    assert raw  # utf-8 round trip sanity
    assert a["grounded"] and len(a["sources"]) == 2, a
    assert a["offset_kinds"] == ["bytes"], a["offset_kinds"]
    assert a["sources"][0]["domain"] == "sca.coffee" and a["sources"][0]["kind"] == "institution", a["sources"][0]
    assert a["sources"][1]["domain"] == "example-roaster.com", a["sources"][1]
    assert a["quotes"] == 1 and a["quotes_tied"] == 1, a
    assert 0.6 < a["coverage"] < 0.9, a["coverage"]
    marked = annotate(a["text"], a["spans"])
    assert "향이다.[1]" in marked and '적었다.[1,2]' in marked and marked.endswith("출처 없는 문장."), marked
    terms, subs = wheel_terms()
    assert len(terms) >= 80 and len(subs) >= 40, (len(terms), len(subs))
    print(f"selftest ok ({len(terms)} wheel terms, {len(subs)} note categories)")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--selftest", action="store_true")
    p.add_argument("--model", default="gemini-2.5-flash")
    p.add_argument("--notes", type=int, default=12)
    p.add_argument("--describes", type=int, default=6)
    p.add_argument("--delay", type=float, default=7.0, help="seconds between requests (free tier per-minute limits)")
    p.add_argument("--out", default=str(HERE / "out"))
    args = p.parse_args()
    if args.selftest:
        selftest()
    else:
        run(args)


if __name__ == "__main__":
    main()
