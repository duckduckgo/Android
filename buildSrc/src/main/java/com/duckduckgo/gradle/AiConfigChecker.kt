/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.gradle

import java.io.File

data class Violation(val message: String, val location: String? = null) {
    fun format(): String = if (location != null) "$message ($location)" else message
}

/**
 * Validates the AI config topology.
 *
 * Claude Code loads CLAUDE.md and every `.md` under `.claude/rules/` at session start, so a rule
 * without a `paths:` glob costs context in every session. `.claude/docs/` is not loaded at all — a doc
 * is only ever read because something already in context told Claude to read it. That makes two
 * invariants worth enforcing mechanically: no doc is unreachable, and no pointer dangles.
 */
class AiConfigChecker(private val repoRoot: File) {

    private val claudeMd = File(repoRoot, "CLAUDE.md")
    private val rulesDir = File(repoRoot, ".claude/rules")
    private val docsDir = File(repoRoot, ".claude/docs")
    private val skillsDir = File(repoRoot, ".claude/skills")

    fun check(): List<Violation> {
        if (!claudeMd.exists()) return listOf(Violation("CLAUDE.md not found at repo root"))
        val modules = discoverModules()
        return checkDocsAreReachable() +
            checkConfigReferences() +
            checkFrontmatter() +
            checkSkills() +
            checkDanglingReferences(modules) +
            checkVolatileFacts()
    }

    private fun rules(): List<File> = rulesDir.listFiles { f -> f.isFile && f.extension == "md" }?.sortedBy { it.name } ?: emptyList()

    private fun docs(): List<File> = docsDir.listFiles { f -> f.isFile && f.extension == "md" }?.sortedBy { it.name } ?: emptyList()

    private fun skills(): List<File> =
        skillsDir.listFiles { f -> f.isDirectory }?.mapNotNull { File(it, "SKILL.md").takeIf(File::exists) }?.sortedBy { it.parentFile.name }
            ?: emptyList()

    /**
     * Nothing loads `.claude/docs/`, so a doc that no always-loaded file points at is dead weight: it
     * will never be read. "Always loaded" is CLAUDE.md plus the rules, since a rule reaches context
     * either unconditionally or via its `paths:` glob.
     */
    private fun checkDocsAreReachable(): List<Violation> {
        val referenced = (listOf(claudeMd) + rules()).flatMap { configReferencesIn(it).map { ref -> ref.value } }.toSet()
        return docs()
            .filter { ".claude/docs/${it.name}" !in referenced }
            .map {
                Violation(
                    "Unreachable doc: .claude/docs/${it.name} is not referenced from CLAUDE.md or any rule, so it will never be read",
                )
            }
    }

    /**
     * Every rule or doc path mentioned anywhere in the config must exist. These pointers are the only
     * way a doc gets read, so a typo silently removes guidance rather than failing loudly.
     */
    private fun checkConfigReferences(): List<Violation> {
        val violations = mutableListOf<Violation>()
        for (file in listOf(claudeMd) + rules() + docs() + skills()) {
            for (ref in configReferencesIn(file)) {
                if (!File(repoRoot, ref.value).exists()) {
                    violations += Violation(
                        "Dangling AI config reference: '${ref.value}' does not exist",
                        "${relativize(file)}:${ref.line}",
                    )
                }
            }
        }
        return violations
    }

    /**
     * Frontmatter only does something in two places: `paths:` on a rule, and `description:` on a skill.
     * Anything else is inert and misleads whoever reads it next — a `description:` on a rule in
     * particular looks like it controls loading, and does not.
     */
    private fun checkFrontmatter(): List<Violation> {
        val violations = mutableListOf<Violation>()
        for (file in rules()) {
            val keys = frontmatterKeys(file)
            keys.filter { it != "paths" }.forEach { key ->
                violations += Violation(
                    "Inert frontmatter in .claude/rules/${file.name}: '$key:' does nothing — a rule loads on its `paths:` glob, " +
                        "or unconditionally when it has none. Only skills use `description:`",
                )
            }
            if ("paths" in keys && pathsGlobs(file).isEmpty()) {
                violations += Violation("Empty `paths:` in .claude/rules/${file.name}: it matches nothing, so the rule never loads")
            }
        }
        for (file in docs()) {
            frontmatterKeys(file).forEach { key ->
                violations += Violation(
                    "Inert frontmatter in .claude/docs/${file.name}: '$key:' does nothing — docs are not loaded by Claude Code, " +
                        "they are read because CLAUDE.md or a rule points at them",
                )
            }
        }
        return violations
    }

    /** A skill is invoked by matching its `description:` against the prompt, so it is useless without one. */
    private fun checkSkills(): List<Violation> {
        val violations = mutableListOf<Violation>()
        for (file in skills()) {
            val keys = frontmatterKeys(file)
            val rel = ".claude/skills/${file.parentFile.name}/SKILL.md"
            if ("name" !in keys) violations += Violation("Skill missing `name:` frontmatter: $rel")
            if ("description" !in keys) {
                violations += Violation("Skill missing `description:` frontmatter: $rel — without it the skill can never be invoked")
            }
        }
        return violations
    }

    /**
     * CLAUDE.md intentionally states no tool version numbers — versions live in the build files and are
     * pointed to, not restated, so they cannot go stale. Flag any known tool name immediately followed by
     * a semver. Scoped to CLAUDE.md: rules and docs legitimately carry example versions.
     */
    private fun checkVolatileFacts(): List<Violation> {
        val violations = mutableListOf<Violation>()
        forEachProseLine(claudeMd) { line, lineNo ->
            VOLATILE_FACT_REGEX.findAll(line).forEach { m ->
                violations += Violation(
                    "Restated tool version in CLAUDE.md: '${m.value.trim()}' — remove it and point to the source of truth " +
                        "(versions live in the build files)",
                    "CLAUDE.md:$lineNo",
                )
            }
        }
        return violations
    }

    private enum class RefKind { MODULE, PATH }

    private data class Reference(val value: String, val kind: RefKind, val line: Int)

    private fun checkDanglingReferences(modules: Set<String>): List<Violation> {
        val violations = mutableListOf<Violation>()
        for (file in listOf(claudeMd) + rules() + docs()) {
            for (ref in extractReferences(file)) {
                if (shouldIgnore(ref.value)) continue
                val resolved = when (ref.kind) {
                    RefKind.MODULE -> resolveModule(ref.value, modules)
                    RefKind.PATH -> resolvePath(ref.value, file)
                }
                if (!resolved) {
                    val reason = if (ref.kind == RefKind.MODULE) "does not resolve to a module" else "does not exist"
                    violations += Violation("Dangling reference: '${ref.value}' $reason", "${relativize(file)}:${ref.line}")
                }
            }
        }
        return violations
    }

    /** `.claude/rules/foo.md` / `.claude/docs/foo.md` references, in backticks or markdown links. */
    private fun configReferencesIn(file: File): List<Reference> {
        val refs = mutableListOf<Reference>()
        forEachProseLine(file) { line, lineNo ->
            CONFIG_PATH_REGEX.findAll(line).forEach { m ->
                refs += Reference(m.value.trim('`', '(', ')'), RefKind.PATH, lineNo)
            }
        }
        return refs
    }

    private fun frontmatterKeys(file: File): List<String> {
        val lines = file.readLines()
        if (lines.firstOrNull()?.trim() != "---") return emptyList()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return emptyList()
        return lines.subList(1, end + 1)
            .filter { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("\t") && !it.startsWith("-") }
            .mapNotNull { FRONTMATTER_KEY_REGEX.find(it)?.groupValues?.get(1) }
    }

    private fun pathsGlobs(file: File): List<String> {
        val lines = file.readLines()
        val start = lines.indexOfFirst { it.trim() == "paths:" }
        if (start < 0) return emptyList()
        return lines.drop(start + 1)
            .takeWhile { it.startsWith(" ") || it.startsWith("\t") }
            .mapNotNull { PATHS_ENTRY_REGEX.find(it)?.groupValues?.get(1)?.takeIf(String::isNotBlank) }
    }

    /** Runs [block] for each line outside a fenced code block, with a 1-based line number. */
    private fun forEachProseLine(file: File, block: (String, Int) -> Unit) {
        var inFence = false
        file.readLines().forEachIndexed { index, line ->
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed
            block(line, index + 1)
        }
    }

    private fun extractReferences(file: File): List<Reference> {
        val refs = mutableListOf<Reference>()
        forEachProseLine(file) { line, lineNo ->
            MD_LINK_REGEX.findAll(line).forEach { m ->
                refs += Reference(m.groupValues[1].trim(), RefKind.PATH, lineNo)
            }
            INLINE_CODE_REGEX.findAll(line).forEach { m ->
                val token = m.groupValues[1].trim()
                when {
                    MODULE_REGEX.matches(token) -> refs += Reference(token, RefKind.MODULE, lineNo)
                    token.contains("/") -> refs += Reference(token, RefKind.PATH, lineNo)
                }
            }
        }
        return refs
    }

    private fun relativize(file: File): String = file.canonicalPath.removePrefix(repoRoot.canonicalPath).trimStart(File.separatorChar)

    private fun shouldIgnore(value: String): Boolean {
        return value.startsWith("http://") ||
            value.startsWith("https://") ||
            value.startsWith("mailto:") ||
            value.startsWith("#") ||
            value.contains("<") ||
            value.contains(">") ||
            value.contains("*")
    }

    private fun resolvePath(raw: String, file: File): Boolean {
        val path = raw.removePrefix("./").trimEnd('/')
        if (path.isEmpty()) return true
        // Markdown links are often relative to the containing file's directory (e.g. "../../foo/bar.md").
        if (File(file.parentFile, path).exists()) return true
        val firstSegment = path.substringBefore('/')
        // Only validate references that claim to be repo-rooted; others are external/illustrative.
        if (!File(repoRoot, firstSegment).exists()) return true
        if (path.contains("...")) {
            return findByName(path.substringAfterLast('/'))
        }
        return File(repoRoot, path).exists()
    }

    private fun resolveModule(ref: String, modules: Set<String>): Boolean {
        val name = ref.trimStart(':').substringAfterLast(':')
        return name in modules
    }

    private fun discoverModules(): Set<String> {
        return repoRoot.walkTopDown()
            .maxDepth(2)
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "node_modules" }
            .filter { it.isDirectory && (File(it, "build.gradle").exists() || File(it, "build.gradle.kts").exists()) }
            .map { it.name }
            .toSet()
    }

    private fun findByName(leaf: String): Boolean {
        return repoRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "node_modules" }
            .any { it.name == leaf }
    }

    companion object {
        private val CONFIG_PATH_REGEX = Regex("\\.claude/(?:rules|docs)/[A-Za-z0-9._-]+\\.md")
        private val FRONTMATTER_KEY_REGEX = Regex("^([A-Za-z][A-Za-z0-9_-]*)\\s*:")
        private val PATHS_ENTRY_REGEX = Regex("^\\s*-\\s*\"?([^\"]*)\"?\\s*$")
        // A known tool name immediately followed by a semver (x.y or x.y.z). Single integers
        // (e.g. "JDK 21", "JVM target 17") are intentionally not matched.
        private val VOLATILE_FACT_REGEX =
            Regex("""(?i)\b(kotlin|gradle|ktlint|google java format|agp)\b\s*\(?\s*v?\d+\.\d+(?:\.\d+)?\)?""")
        private val MD_LINK_REGEX = Regex("\\[[^\\]]*]\\(([^)]+)\\)")
        private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
        private val MODULE_REGEX = Regex("^:[a-z0-9]+(-[a-z0-9]+)*(:[a-z0-9]+(-[a-z0-9]+)*)*$")
    }
}
