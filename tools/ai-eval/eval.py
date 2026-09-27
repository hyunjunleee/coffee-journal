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
            "quora.com", "instagram.com", "youtube.com", "facebook.com", "x.com", "twitter.com", "home.coffeegeek.com",
            "coffeegeek.com", "home-barista.com", "coffeeforums.co.uk")
# the community-only searches of the "people's impressions" plans (Tavily include_domains)
CROWD = ("reddit.com", "home-barista.com", "coffeegeek.com", "coffeeforums.co.uk", "quora.com")
CROWD_KO = ("blog.naver.com", "tistory.com", "brunch.co.kr", "cafe.naver.com")


def wheel_terms():
    """The app's own vocabulary: flavor wheel terms (English) and note categories (Korean), read from the Kotlin sources."""
    wheel = (REFERENCE / "FlavorWheel.kt").read_text(encoding="utf-8")
    terms = [t for group in re.findall(r'Group\("[^"]*", listOf\(([^)]*)\)\)', wheel) for t in re.findall(r'"([^"]+)"', group)]
    notes = (REFERENCE / "NoteCategories.kt").read_text(encoding="utf-8")
    subs = re.findall(r'Sub\("([^"]+)"', notes)
    return terms, subs


PEOPLE_NOTE = ("- 사람들의 느낌: 커뮤니티·개인 블로그 글에서 사람들이 이 노트를 어떻게 느끼고 표현하는지 "
               "(개인 의견이라고 밝히고 문장마다 [n]을 붙인다. 그런 출처가 있을 때만)")
PEOPLE_DESCRIBE = ("- 비슷하게 느낀 사람들의 말: 커뮤니티·개인 글에서 비슷한 맛을 뭐라고 부르는지 "
                   "(개인 의견이라고 밝히고 문장마다 [n]을 붙인다. 그런 출처가 있을 때만)")


def question(mode, case, terms, subs, people=False):
    """The app's question (NoteHelperPrompts.question); people=True adds the experimental people's-impressions section."""
    if mode == "note":
        return (f'노트 설명. 향미 노트: "{case}"\n'
                "형식:\n"
                "- 한 줄 뜻: 커피에서 이 노트가 가리키는 향·맛\n"
                "- 실제 쓰임 2~4개: 인용 + 어떤 원두·가공·로스팅에서 나왔는지\n"
                "- 비슷한 표현·헷갈리는 표현\n"
                "- Coffee Taster's Flavor Wheel에서의 위치(찾은 경우에만)" + ("\n" + PEOPLE_NOTE if people else ""))
    return (f'맛 묘사로 노트 찾기. 마신 사람의 묘사: "{case}"\n'
            "아래 목록 안에서 어울리는 용어 3~5개를 고르고, 각각 그렇게 부르는 근거를 실제 문서의 인용으로 보여줘.\n"
            "형식:\n"
            "- 용어: 근거(인용과 출처)\n"
            "- 후보를 구별하는 방법(출처가 있을 때만)\n" + (PEOPLE_DESCRIBE + "\n" if people else "") +
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
    plan = [("note", c) for c in pick(cases["note"], args.notes)] + [("describe", c) for c in pick(cases["describe"], args.describes)]
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


def probe(models, out_dir):
    """One short question per model, without and with Google Search, to see which models this key may use and which
    of them ground for free (new projects lose older models: gemini-2.5-flash answers 404 "no longer available to new
    users")."""
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        sys.exit("GEMINI_API_KEY is not set")
    ask = '커피 테이스팅 노트에서 "bergamot"은 무슨 뜻인지 한 문장으로.'
    lines = ["# Model probe", "", "| model | plain | with google_search | sources | message |", "|---|---|---|---|---|"]
    for model in models:
        plain = {"contents": [{"role": "user", "parts": [{"text": ask}]}], "generationConfig": {"maxOutputTokens": 60}}
        st1, r1, _ = call(model, key, plain, timeout=60)
        time.sleep(3)
        st2, r2, _ = call(model, key, request_body("짧게 답한다. 반드시 검색한다.", ask), timeout=90)
        grounded = analyse(r2) if st2 == 200 else {}
        msg = ""
        for st, r in ((st2, r2), (st1, r1)):
            if st != 200:
                msg = (r.get("error", {}).get("message", "") if isinstance(r, dict) else str(r))[:220].replace("|", "/").replace("\n", " ")
                break
        lines.append(f"| {model} | {st1} | {st2} | {len(grounded.get('sources', []))} | {msg} |")
        print(lines[-1], flush=True)
        time.sleep(3)
    Path(out_dir).mkdir(parents=True, exist_ok=True)
    (Path(out_dir) / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


# --- "Gemini 무료 + Tavily" pipeline, debugged without a Tavily key ------------------------------------------------

FALLBACK_SOURCES_PROMPT = (
    "너는 스페셜티 커피의 향미 표현을 조사하는 도우미다. 대답은 한국어로 한다.\n"
    "아래에 번호가 붙은 출처만 근거로 쓴다. 출처에 없는 내용은 쓰지 않는다.\n"
    "출처 n의 내용을 쓴 문장 끝에는 [n]을 붙인다(여러 개면 [1][3]).\n"
    "향·맛 표현은 출처에 적힌 그대로 큰따옴표로 인용한다. 번역하거나 고치지 않는다.\n"
    "출처에서 찾지 못한 것은 \"찾지 못했어요\"라고 쓴다.\n"
)


def sources_prompt():
    f = HERE / "sources_prompt_ko.txt"
    return f.read_text(encoding="utf-8") if f.exists() else FALLBACK_SOURCES_PROMPT


def fetch_page(url, timeout=30):
    """(title, plain text) of a web page, or (None, None)."""
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/128.0 Safari/537.36",
                                               "Accept-Language": "en,ko;q=0.8"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            html = r.read(3_000_000).decode(r.headers.get_content_charset() or "utf-8", "replace")
    except Exception as e:  # noqa: BLE001 - a page that fails is reported, not fatal
        return None, f"{type(e).__name__}: {e}"
    import html as htmllib
    html = re.sub(r"(?s)<!--.*?-->", " ", html)
    title = htmllib.unescape((re.search(r"<title[^>]*>(.*?)</title>", html, re.S | re.I) or [None, ""])[1]).strip()
    body = re.sub(r"(?is)<(script|style|noscript|svg|header|footer|nav)[^>]*>.*?</\1>", " ", html)
    body = re.sub(r"(?s)<[^>]+>", " ", body)
    text = re.sub(r"\s+", " ", htmllib.unescape(body)).strip()
    return title or url, text


def tavily_like_content(text, keywords, chunks=3, size=500):
    """Up to [chunks] snippets of at most [size] characters around the keywords, joined like Tavily's content."""
    low = text.lower()
    found = []
    for kw in keywords:
        for m in re.finditer(re.escape(kw.lower()), low):
            start = max(0, m.start() - size // 2)
            if all(abs(start - s) >= size for s in found):
                found.append(start)
            if len(found) >= chunks:
                break
        if len(found) >= chunks:
            break
    if not found:
        found = [0]
    def clip(start):
        piece = text[start:start + size]
        if start > 0 and " " in piece[:40]:
            piece = piece[piece.index(" ") + 1:]
        if start + size < len(text) and " " in piece[-40:]:
            piece = piece[: piece.rindex(" ")]
        return piece.strip()
    return " [...] ".join(clip(s) for s in sorted(found))


def norm(s):
    import unicodedata
    s = unicodedata.normalize("NFC", s).lower()
    s = s.replace("“", '"').replace("”", '"').replace("‘", "'").replace("’", "'")
    return re.sub(r"\s+", " ", s).strip()


MARKER = re.compile(r"\[(\d+)\]")


def check_cited(text, sources):
    """Sentence-level [n] markers and verbatim quotes, checked against each cited source's full text."""
    sentences = [s for s in re.split(r"(?<=[.!?。])\s+|\n+", text) if s.strip()]
    cited = [s for s in sentences if MARKER.search(s)]
    bad_refs, quotes, verified = 0, 0, 0
    detail = []
    for s in cited:
        refs = [int(n) for n in MARKER.findall(s)]
        bad_refs += sum(1 for n in refs if not 1 <= n <= len(sources))
        for q in QUOTE.findall(s):
            quotes += 1
            ok = any(1 <= n <= len(sources) and norm(q) in norm(sources[n - 1]["raw"]) for n in refs)
            verified += ok
            detail.append((q, refs, ok))
    return {"sentences": len(sentences), "cited": len(cited), "bad_refs": bad_refs, "quotes": quotes,
            "verified": verified, "quote_detail": detail}


def run_sources(args):
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        sys.exit("GEMINI_API_KEY is not set")
    data = json.loads((HERE / "cases_sources.json").read_text(encoding="utf-8"))
    terms, subs = wheel_terms()
    system = sources_prompt()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    lines = [f"# Gemini free + sources: {args.model}", "",
             "| # | mode | question | pages ok | HTTP | cited sentences | bad [n] | quotes verified | s |",
             "|---|---|---|---|---|---|---|---|---|"]
    answers = []
    for n, c in enumerate(data["cases"], 1):
        sources = []
        for url in c["urls"]:
            title, text = fetch_page(url)
            if title is None:
                answers.append(f"- fetch failed: {url} ({text})")
                continue
            sources.append({"title": title, "url": url, "domain": urllib.parse.urlparse(url).hostname,
                            "content": tavily_like_content(text, c["keywords"]), "raw": text[:200_000]})
        block = "\n\n".join(f"[{i}] {s['title']} — {s['domain']}\n{s['url']}\n{s['content']}" for i, s in enumerate(sources, 1))
        body = {
            "system_instruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [{"text": question(c["mode"], c["case"], terms, subs) + "\n\n출처:\n" + block}]}],
            "generationConfig": {"temperature": 0.2},
        }
        status, resp, secs = call(args.model, key, body)
        if status == 200:
            text = "".join(p.get("text", "") for p in ((resp.get("candidates") or [{}])[0].get("content") or {}).get("parts", []))
            chk = check_cited(text, sources)
            lines.append(f"| {n} | {c['mode']} | {c['case']} | {len(sources)}/{len(c['urls'])} | 200 | {chk['cited']}/{chk['sentences']} | "
                         f"{chk['bad_refs']} | {chk['verified']}/{chk['quotes']} | {secs:.1f} |")
            answers += [f"### {n}. {c['mode']}: {c['case']}", "", text, ""]
            answers += [f"- {'✓' if ok else '✗'} \"{q}\" → {refs}" for q, refs, ok in chk["quote_detail"]]
            answers += [""] + [f"[{i}] {s['title']} ({s['domain']}) {s['url']}" for i, s in enumerate(sources, 1)] + [""]
        else:
            err = resp.get("error", {}).get("message", "") if isinstance(resp, dict) else str(resp)
            lines.append(f"| {n} | {c['mode']} | {c['case']} | {len(sources)}/{len(c['urls'])} | {status} | - | - | - | {secs:.1f} |")
            answers += [f"### {n}. {c['case']}", "", f"HTTP {status}: {err[:300]}", ""]
        print(lines[-1], flush=True)
        time.sleep(args.delay)
    (out / "report.md").write_text("\n".join(lines + ["", "## Answers", ""] + answers) + "\n", encoding="utf-8")


# --- the real "Gemini 무료 + Tavily" pipeline (needs TAVILY_API_KEY as well) --------------------------------------

TAVILY_URL = "https://api.tavily.com/search"

FALLBACK_QUERY_PROMPT = (
    "Turn the coffee flavor note or taste description into at most 2 short English web-search queries that find coffee "
    "tasting-note pages. Keep flavor words literal. Answer as JSON {\"queries\": [...]}."
)


def query_prompt():
    f = HERE / "query_prompt_ko.txt"
    return f.read_text(encoding="utf-8") if f.exists() else FALLBACK_QUERY_PROMPT


def template_query(mode, case):
    """The app's fallback when the query step fails (NoteHelperPrompts.tavilyQuery)."""
    if mode == "note":
        m = re.match(r"^(.*?)\s*[(（]([^()（）]+)[)）]\s*$", case.strip())
        name = m.group(2).strip() if m and re.search(r"[A-Za-z]", m.group(2)) else (m.group(1).strip() if m else case.strip())
        return f'"{name}" coffee flavor note meaning tasting notes'
    return ("coffee tasting notes " + case.strip())[:300]


def write_queries(model, key, mode, case):
    """The query-writing step: the free model, JSON output, at most 2 queries; the template when anything is off."""
    body = {
        "system_instruction": {"parts": [{"text": query_prompt()}]},
        "contents": [{"role": "user", "parts": [{"text": ("노트: " if mode == "note" else "맛 묘사: ") + case}]}],
        "generationConfig": {"temperature": 0, "maxOutputTokens": 1024, "responseMimeType": "application/json",
                             "responseSchema": {"type": "OBJECT", "properties": {"queries": {"type": "ARRAY", "items": {"type": "STRING"}}},
                                                "required": ["queries"]}},
    }
    status, resp, secs = call(model, key, body, timeout=60)
    if status == 200:
        try:
            text = "".join(p.get("text", "") for p in resp["candidates"][0]["content"]["parts"])
            qs = [q.strip() for q in json.loads(text)["queries"] if isinstance(q, str) and q.strip()][:2]
            if qs and all(len(q) <= 200 for q in qs):
                return qs, "model", secs
        except (KeyError, IndexError, ValueError, TypeError):
            pass
    return [template_query(mode, case)], f"template (HTTP {status})", secs


def pick(items, spec):
    """--notes / --describes: "6" is the first six cases, "7-10" the seventh to the tenth (a case searched before can come
    back from Tavily's cache, so a fair depth comparison takes cases no run has searched yet)."""
    spec = str(spec).strip()
    if "-" in spec:
        a, b = (int(x) for x in spec.split("-", 1))
        return items[a - 1: b]
    return items[: int(spec)]


def tavily_search(key, query, depth, domains=None):
    body = {"query": query, "search_depth": depth, "max_results": 5, "chunks_per_source": 3,
            "include_raw_content": "text", "include_answer": False, "include_usage": True}
    if domains:
        body["include_domains"] = list(domains)
    req = urllib.request.Request(TAVILY_URL, method="POST", headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
                                 data=json.dumps(body).encode("utf-8"))
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:300]
    except (urllib.error.URLError, TimeoutError) as e:
        return 0, str(e)


PLANS = {
    "adv+adv": ["advanced", "advanced"],  # two queries: 4 credits
    "adv+basic": ["advanced", "basic"],   # two queries: 3 credits
    "basic+basic": ["basic", "basic"],    # two queries: 2 credits
    "adv": ["advanced"],                  # first query only: 2 credits
    "basic": ["basic"],                   # first query only: 1 credit
    "fast": ["fast"],                     # first query only: 1 credit
    "ufast": ["ultra-fast"],              # first query only: 1 credit
    # people's impressions: the main search plus one restricted to communities (the first query + " discussion", or a
    # Korean query on Korean blogs); a different query text keeps Tavily's cache from mixing the two
    "adv+crowd": ["advanced", "basic@crowd"],         # 3 credits
    "adv+crowdadv": ["advanced", "advanced@crowd"],   # 4 credits
    "adv+crowd-ko": ["advanced", "basic@crowd-ko"],   # 3 credits
    # the same (first) query at both depths, basic searched first so it cannot come back as the advanced result
    "sameadv+basic": ["basic@q1", "advanced@q1"],                        # 3 credits
    "sameadv+basic+ko": ["basic@q1", "advanced@q1", "basic@crowd-ko"],   # 4 credits
}
CREDITS = {"advanced": 2, "basic": 1, "fast": 1, "ultra-fast": 1}


def korean_query(mode, case):
    """The Korean-blog search of adv+crowd-ko: the note's Korean name or the description, as home-café posts word it."""
    if mode == "note":
        m = re.match(r"^(.*?)\s*[(（]([^()（）]+)[)）]\s*$", case.strip())
        return f"커피 원두 {(m.group(1) if m else case).strip()} 노트 후기"
    return f"커피 원두 후기 {case.strip()}"


def plan_searches(plan_name, queries, mode, case):
    """(query, depth, domains, label) for each search of the plan: plain steps take the written queries in order."""
    out, i = [], 0
    for step in PLANS[plan_name]:
        depth, _, target = step.partition("@")
        if target == "q1":
            out.append((queries[0], depth, None, "main"))
        elif target == "crowd":
            out.append((queries[0] + " discussion", depth, CROWD, "crowd"))
        elif target == "crowd-ko":
            out.append((korean_query(mode, case), depth, CROWD_KO, "crowd-ko"))
        elif i < len(queries):
            out.append((queries[i], depth, None, "main"))
            i += 1
    return out


def charged(resp, depth):
    """The credits Tavily reports for the request (include_usage), else the price list."""
    usage = resp.get("usage") if isinstance(resp, dict) else None
    if isinstance(usage, dict) and isinstance(usage.get("credits"), (int, float)):
        return usage["credits"]
    return CREDITS[depth]


def run_retrieval(args):
    """Tavily only (no answer call): what each depth returns for the same written queries -- result overlap, sources,
    institution share, whether the flavor words appear in the snippets -- plus the snippets side by side. A depth may
    repeat ("basic,advanced,basic"): the repeat shows whether an earlier search of the same query is served again."""
    gkey = os.environ.get("GEMINI_API_KEY", "").strip()
    tkey = os.environ.get("TAVILY_API_KEY", "").strip()
    if not gkey or not tkey:
        sys.exit("GEMINI_API_KEY and TAVILY_API_KEY are both needed")
    cases = json.loads((HERE / "cases.json").read_text(encoding="utf-8"))
    plan = [("note", c) for c in pick(cases["note"], args.notes)] + [("describe", c) for c in pick(cases["describe"], args.describes)]
    depths = [d.strip() for d in args.depths.split(",") if d.strip()]
    labels = [d if depths[:i].count(d) == 0 else f"{d} ({depths[:i].count(d) + 1})" for i, d in enumerate(depths)]
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows = ["| # | query | depth | results | institution | snippets with the flavor word | snippet chars | same URLs as " + labels[0] +
            " | identical to | credits | s |",
            "|---|---|---|---|---|---|---|---|---|---|---|"]
    side, credits = [], 0
    per = {lb: {"n": 0, "res": 0, "hit": 0, "chars": 0, "cred": 0, "secs": 0.0, "same": 0} for lb in labels}
    for n, (mode, case) in enumerate(plan, 1):
        queries, how, _ = write_queries(args.model, gkey, mode, case)
        time.sleep(2)
        words = [w.lower() for w in re.findall(r"[A-Za-z]{3,}", " ".join(queries))
                 if w.lower() not in {"coffee", "tasting", "notes", "note", "flavor", "flavour", "meaning", "descriptor", "the", "and", "with"}]
        for qi, q in enumerate(queries):
            got = {}
            for d, lb in zip(depths, labels):
                t0 = time.monotonic()
                status, resp = tavily_search(tkey, q, d)
                secs = time.monotonic() - t0
                if status != 200:
                    rows.append(f"| {n}.{qi + 1} | {q} | {lb} | HTTP {status} | - | - | - | - | - | - | {secs:.1f} |")
                    continue
                cred = charged(resp, d)
                credits += cred
                res = resp.get("results", [])
                got[lb] = res
                sig = [(r.get("url"), r.get("content")) for r in res]
                twins = [o for o, ores in got.items() if o != lb and [(r.get("url"), r.get("content")) for r in ores] == sig]
                inst = sum(1 for r in res if kind_of((urllib.parse.urlparse(r.get("url") or "").hostname or "").removeprefix("www.")) == "institution")
                hit = sum(1 for r in res if any(w in (r.get("content") or "").lower() for w in words))
                chars = sum(len(r.get("content") or "") for r in res)
                base = {r.get("url") for r in got.get(labels[0], [])}
                same = sum(1 for r in res if r.get("url") in base)
                t = per[lb]
                t["n"] += 1; t["res"] += len(res); t["hit"] += hit; t["chars"] += chars; t["cred"] += cred; t["secs"] += secs; t["same"] += same
                rows.append(f"| {n}.{qi + 1} | {q} | {lb} | {len(res)} | {inst} | {hit}/{len(res)} | {chars} | {same}/{len(res)} | "
                            f"{', '.join(twins) or '-'} | {cred} | {secs:.1f} |")
            side += [f"### {n}.{qi + 1} {case} → {q} ({how})", ""]
            for lb, res in got.items():
                side.append(f"**{lb}**")
                for r in res:
                    side.append(f"- {r.get('title')} ({urllib.parse.urlparse(r.get('url') or '').hostname}) — {(r.get('content') or '')[:260]}")
                side.append("")
        print(rows[-1], flush=True)
    summary = ["| depth | queries | results/query | snippets with the flavor word | snippet chars/query | same URLs as " + labels[0] +
               " | credits | s/query |", "|---|---|---|---|---|---|---|---|"]
    for lb, t in per.items():
        k = max(t["n"], 1)
        summary.append(f"| {lb} | {t['n']} | {t['res'] / k:.1f} | {t['hit']}/{t['res']} | {t['chars'] / k:.0f} | {t['same']}/{t['res']} | "
                       f"{t['cred']} | {t['secs'] / k:.1f} |")
    (out / "report.md").write_text("\n".join([f"# Tavily depths compared ({', '.join(labels)}), queries by {args.model}", "",
                                              f"Tavily credits used: {credits}", ""] + summary + [""] + rows +
                                             ["", "## Snippets side by side", ""] + side) + "\n", encoding="utf-8")


def people_line(text, personal_ids):
    """The people's-impressions line of an answer: "cited" when it cites a personal source, "empty" when it found none,
    "other" when it cites only other sources, "-" when the answer has no such line."""
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.lstrip("-* ").startswith(("사람들의 느낌", "비슷하게 느낀 사람들의 말")):
            indent = len(line) - len(line.lstrip())
            block = [line]
            for more in lines[i + 1:]:  # its sub-items, if the answer lists them under the line
                if more.strip() and len(more) - len(more.lstrip()) <= indent:
                    break
                block.append(more)
            refs = {int(x) for x in re.findall(r"\[(\d+)\]", "\n".join(block))}
            if refs & personal_ids:
                return "cited"
            return "empty" if not refs else "other"
    return "-"


def run_tavily(args):
    gkey = os.environ.get("GEMINI_API_KEY", "").strip()
    tkey = os.environ.get("TAVILY_API_KEY", "").strip()
    if not gkey or not tkey:
        sys.exit("GEMINI_API_KEY and TAVILY_API_KEY are both needed")
    cases = json.loads((HERE / "cases.json").read_text(encoding="utf-8"))
    plan = [("note", c) for c in pick(cases["note"], args.notes)] + [("describe", c) for c in pick(cases["describe"], args.describes)]
    terms, subs = wheel_terms()
    system = sources_prompt()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    plans = [x.strip() for x in args.plans.split(",") if x.strip()]
    lines = [f"# Gemini free + Tavily: {args.model}" + (" (with the people's impressions section)" if args.people else ""), "",
             "| # | plan | mode | question | queries | sources | domains | personal | institution | cited sentences | bad [n] | quotes verified | people line | credits | s |",
             "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    answers, credits = [], 0
    totals = {pl: {"q": 0, "v": 0, "cited": 0, "sent": 0, "src": 0, "inst": 0, "cred": 0, "n": 0, "dom": 0, "pers": 0, "people": 0}
              for pl in plans}
    written = {}
    for n, (mode, case, plan_name) in enumerate([(m, c, pl) for (m, c) in plan for pl in plans], 1):
        if case not in written:
            written[case] = write_queries(args.model, gkey, mode, case)
            time.sleep(2)
        queries, how, qsecs = written[case]
        if plan_name.startswith("tmpl-"):
            queries, how, qsecs = [template_query(mode, case)], "template", 0.0
        searches = plan_searches(plan_name.removeprefix("tmpl-"), queries, mode, case)
        queries = [q for q, _, _, _ in searches]
        got, errors, t0 = [], [], time.monotonic()
        for q, depth, domains, label in searches:
            status, resp = tavily_search(tkey, q, depth, domains)
            if status != 200:
                errors.append(f"Tavily {status}: {resp}")
                continue
            cred = charged(resp, depth)
            credits += cred
            totals[plan_name]["cred"] += cred
            got.append((label != "main", depth != "advanced", label, resp.get("results", [])[: args.per_search]))
        sources, seen = [], set()
        for _, _, label, results in sorted(got, key=lambda g: (g[0], g[1])):
            for r in results:
                if r.get("url") in seen or len(sources) >= args.max_sources:
                    continue
                seen.add(r.get("url"))
                sources.append({"title": r.get("title") or r.get("url"), "url": r.get("url"), "from": label,
                                "domain": urllib.parse.urlparse(r.get("url") or "").hostname or "",
                                "content": r.get("content") or "", "raw": r.get("raw_content") or r.get("content") or ""})
        if not sources:
            lines.append(f"| {n} | {plan_name} | {mode} | {case} | {' · '.join(queries)} | 0 | 0 | 0 | 0 | - | - | - | - | {credits} | - |")
            answers += [f"### {n}. {case}", "", "no sources: " + "; ".join(errors), ""]
            continue
        block = "\n\n".join(f"[{i}] {s['title']} — {s['domain']}\n{s['url']}\n{s['content']}" for i, s in enumerate(sources, 1))
        body = {"system_instruction": {"parts": [{"text": system}]},
                "contents": [{"role": "user", "parts": [{"text": question(mode, case, terms, subs, args.people) + "\n\n출처\n" + block}]}],
                "generationConfig": {"temperature": 0.2}}
        status, resp, secs = call(args.model, gkey, body)
        total = qsecs + (time.monotonic() - t0)
        if status == 200:
            text = "".join(p.get("text", "") for p in ((resp.get("candidates") or [{}])[0].get("content") or {}).get("parts", []))
            chk = check_cited(text, sources)
            inst = sum(1 for s_ in sources if kind_of(s_["domain"].removeprefix("www.")) == "institution")
            pers_ids = {i for i, s_ in enumerate(sources, 1) if kind_of(s_["domain"].removeprefix("www.")) == "personal"}
            doms = len({s_["domain"].removeprefix("www.") for s_ in sources})
            people = people_line(text, pers_ids)
            t = totals[plan_name]
            t["q"] += chk["quotes"]; t["v"] += chk["verified"]; t["cited"] += chk["cited"]; t["sent"] += chk["sentences"]
            t["src"] += len(sources); t["inst"] += inst; t["n"] += 1; t["dom"] += doms; t["pers"] += len(pers_ids)
            t["people"] += people == "cited"
            lines.append(f"| {n} | {plan_name} | {mode} | {case} | {' · '.join(queries)} ({how}) | {len(sources)} | {doms} | {len(pers_ids)} | {inst} | "
                         f"{chk['cited']}/{chk['sentences']} | {chk['bad_refs']} | {chk['verified']}/{chk['quotes']} | {people} | {credits} | {total:.1f} |")
            answers += [f"### {n}. [{plan_name}] {mode}: {case}", "", "검색어: " + " · ".join(queries), "", text, ""]
            answers += [f"- {'✓' if ok else '✗'} \"{q}\" → {refs}" for q, refs, ok in chk["quote_detail"]]
            answers += [""] + [f"[{i}] {s['title']} ({s['domain']}{', ' + s['from'] if s['from'] != 'main' else ''}) {s['url']}"
                               for i, s in enumerate(sources, 1)] + [""]
        else:
            err = resp.get("error", {}).get("message", "") if isinstance(resp, dict) else str(resp)
            lines.append(f"| {n} | {plan_name} | {mode} | {case} | {' · '.join(queries)} | {len(sources)} | - | - | - | HTTP {status} | - | - | - | {credits} | - |")
            answers += [f"### {n}. {case}", "", f"Gemini HTTP {status}: {err[:300]}", ""]
        print(lines[-1], flush=True)
        time.sleep(args.delay)
    summary = ["", "| plan | answers | sources/answer | domains/answer | personal sources/answer | institution sources | cited sentences | "
               "quotes verified | people line cited | credits |", "|---|---|---|---|---|---|---|---|---|---|"]
    for pl, t in totals.items():
        n_ = max(t["n"], 1)
        summary.append(f"| {pl} | {t['n']} | {t['src'] / n_:.1f} | {t['dom'] / n_:.1f} | {t['pers'] / n_:.1f} | {t['inst']} | {t['cited']}/{t['sent']} | "
                       f"{t['v']}/{t['q']} | {t['people'] if args.people else '-'} | {t['cred']} |")
    (out / "report.md").write_text("\n".join(lines[:2] + summary + [""] + lines[2:] + ["", f"Tavily credits used: {credits}", "", "## Answers", ""] + answers) + "\n", encoding="utf-8")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--selftest", action="store_true")
    p.add_argument("--model", default="gemini-3.8-flash")
    p.add_argument("--notes", default="12", help='how many notes ("6") or which ("7-10", from 1)')
    p.add_argument("--describes", default="6", help='how many descriptions ("4") or which ("5-8", from 1)')
    p.add_argument("--delay", type=float, default=7.0, help="seconds between requests (free tier per-minute limits)")
    p.add_argument("--out", default=str(HERE / "out"))
    p.add_argument("--probe", default="", help="comma-separated models: one short question each, without and with search")
    p.add_argument("--sources", action="store_true", help="the Gemini free + Tavily pipeline with fetched pages (cases_sources.json)")
    p.add_argument("--tavily", action="store_true", help="the real Gemini free + Tavily pipeline (needs TAVILY_API_KEY)")
    p.add_argument("--plans", default="adv+basic", help="comma-separated plans: adv+adv, adv+basic, basic+basic, adv, basic, fast, ufast, adv+crowd, adv+crowdadv, adv+crowd-ko, sameadv+basic, sameadv+basic+ko; prefix tmpl- to skip the query step")
    p.add_argument("--per-search", type=int, default=5, help="sources kept from each search")
    p.add_argument("--max-sources", type=int, default=15, help="sources given to the answer at most")
    p.add_argument("--people", action="store_true", help="ask for the people's impressions section (experiment)")
    p.add_argument("--retrieval", action="store_true", help="Tavily only: compare search depths for the written queries")
    p.add_argument("--depths", default="advanced,basic,fast", help="depths for --retrieval (basic, advanced, fast, ultra-fast; a repeat searches again)")
    args = p.parse_args()
    if args.selftest:
        selftest()
    elif args.retrieval:
        run_retrieval(args)
    elif args.tavily:
        run_tavily(args)
    elif args.sources:
        run_sources(args)
    elif args.probe:
        probe([m.strip() for m in args.probe.split(",") if m.strip()], args.out)
    else:
        run(args)


if __name__ == "__main__":
    main()
