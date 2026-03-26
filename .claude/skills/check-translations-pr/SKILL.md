---
name: check-translations-pr
description: Check whether a translations PR provides full language coverage for all translatable strings it contains, and identify which languages are still missing. Use this whenever the user asks about translation status, whether translations are complete or ready, which languages are missing, or wants to verify a translations PR before merging. Also trigger when the user pastes a PR number or URL alongside any mention of translations, or asks something like "are we good to ship?" in a context that involves string changes.
---

These are instructions for checking translation coverage in a pull request — verifying that every supported language receives all newly added or modified strings.

Complete this in four steps:
1. Resolve the PR number
2. Get the string keys being translated
3. Determine languages covered and supported
4. Report coverage

---

## STEP 1: RESOLVE THE PR NUMBER

Do this before anything else.

- If the user provided a PR number or URL, use that.

**CRITICAL**: If no PR number was provided, stop immediately and ask:
> "What's the translations PR number (or URL)?"

Do not run any other commands or proceed until you have a PR number.

---

## STEP 2: GET STRING KEYS BEING TRANSLATED

Run this command against the PR diff to extract all translated string keys:

```bash
gh pr diff <PR_NUMBER> 2>&1 | grep '^+' | grep -v '^+++' | grep 'string name=' | sed 's/.*name="\([^"]*\)".*/\1/' | sort -u
```

**CRITICAL**: If no string keys are found, report that and stop — there is nothing to check.

---

## STEP 3: GET LANGUAGE COVERAGE

### Languages covered by the PR

```bash
gh pr diff <PR_NUMBER> 2>&1 | grep '^+++' | grep 'strings.*\.xml' | grep -oE 'values-[a-z]{2}/' | cut -c1-9 | sort -u
```

### Full list of supported languages

```bash
ls app/src/main/res/ | grep -E "^values-[a-z]{2}$" | sort
```

Compare the two lists to identify which supported language directories are absent from the PR diff.

---

## STEP 4: REPORT COVERAGE

Present results in this exact format:

```
## Translation Coverage — PR #<N>

**String keys being translated:**
- `key_one`
- `key_two`

**Languages covered:** <X> / <total>

**Missing languages:**
- `values-hu` — Hungarian
- `values-pt` — Portuguese
(or: None — all languages covered)

**Status:** COMPLETE / INCOMPLETE
```

**CRITICAL**: Always include the full language name alongside the directory code. If all languages are covered, say so clearly.