# Design: Gemma 3 1B Shadow Path for @history

**Date:** 2026-03-04
**Branch:** poc/aitor/ai/history-search

---

## Context

Three on-device ranking options for the @history feature have been prototyped:
- Option A: BM25 keyword pre-filtering
- Option B: MediaPipe USE Lite semantic embeddings
- Option C: Gemini Nano via ML Kit GenAI Prompt API (shadow path, logcat only)

Option C is limited to ~10–15% of devices (Pixel 8+, S24+). This design adds Option D: Gemma 3 1B IT via MediaPipe LLM Inference Task, which extends on-device LLM coverage to ~90% of devices and produces both a ranked list and a synthesized natural-language answer.

---

## Goal

Evaluate on-device LLM quality and latency using Gemma 3 1B IT across a broad device base, without any UI changes. The on-device result is logcatted only; Duck.ai always opens normally via the embeddings / BM25 / full-history path.

---

## Architecture

Same shadow path pattern as `GeminiNanoSearcher`:

```
aiCoreEnabled ON    →  GeminiNanoSearcher.search()  — logcats, always falls through ↓
gemmaEnabled ON     →  GemmaSearcher.search()        — logcats, always falls through ↓
embeddingsEnabled   →  EmbeddingScorer               →  top 10 sent to Duck.ai
bm25Enabled         →  Bm25Scorer                    →  top 10 sent to Duck.ai
both OFF            →  full history                  →  top 20 sent to Duck.ai
```

Both shadow paths are independent and can run simultaneously.

---

## Components

### New dependency
`com.google.mediapipe:tasks-genai` — MediaPipe LLM Inference Task. Same library family as the existing `tasks-text` (embeddings) dependency.

### New toggle
`gemmaEnabled()` in `AiHistorySearchFeature`, defaulting to `INTERNAL`.

### New class: `GemmaSearcher`
Mirrors `GeminiNanoSearcher`. Responsibilities:
- Lazy model initialisation on first use
- Background model download on first `@history` query if not yet downloaded (~700 MB)
- Prompt construction (ranking + synthesis in a single inference call)
- Logcatting the result with latency
- Never throws — all outcomes are logged, caller always falls through

### Wiring
Shadow call added to `buildResult()` in `AiHistorySearchInteractor`, alongside the existing `aiCoreEnabled` check.

---

## Model

**Primary:** Gemma 3 1B IT INT4 (MediaPipe `.task` format, ~700 MB)
- RAM requirement: ~2.5 GB → ~90% device coverage
- Target inference latency: 2–5s for this task's input/output size

**Fallback:** If Gemma 3 1B MediaPipe format is not yet available at implementation time, use Gemma 2B IT INT4 (~1.5 GB, 4 GB RAM minimum, ~80% coverage). Same API, drop-in replacement.

Model is downloaded to the app's files directory on first use. Until download completes the searcher logs "model downloading / not ready" and returns immediately.

---

## Prompt

Single inference call requesting both ranking and synthesis:

```
Here are pages from my browser history:
- [title] — [url] ([date])
...

Question: [query]

1. List the 3–5 most relevant pages in order of relevance,
   with a one-sentence reason for each.
2. Write a 2–3 sentence summary of what these pages suggest
   about my research on this topic.
If nothing is relevant, say so clearly.
```

Input: up to 30 entries (~500–800 tokens). Output: ~150–250 tokens.

---

## Out of scope

- Wiring the on-device result into the Duck.ai UI
- User-initiated model download (easy follow-up: `GemmaSearcher` download logic is self-contained)
- Replacing the Duck.ai backend call
