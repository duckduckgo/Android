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
import java.nio.file.Files

data class Violation(val message: String, val location: String? = null) {
    fun format(): String = if (location != null) "$message ($location)" else message
}

class AiConfigChecker(private val repoRoot: File) {

    private val agentsMd = File(repoRoot, "AGENTS.md")
    private val rulesDir = File(repoRoot, ".cursor/rules")
    private val skillsDir = File(repoRoot, ".claude/skills")

    fun check(): List<Violation> {
        if (!agentsMd.exists()) return listOf(Violation("AGENTS.md not found at repo root"))
        val agentsText = agentsMd.readText()
        val modules = discoverModules()
        return checkRulesIndex(agentsText) +
            checkSkillsIndex(agentsText) +
            checkDanglingReferences(modules) +
            checkSymlinkParity()
    }

    private fun checkRulesIndex(agentsText: String): List<Violation> {
        val violations = mutableListOf<Violation>()
        val indexed = RULE_PATH_REGEX.findAll(agentsText).map { it.groupValues[1] }.toSet()

        val mdcFiles = rulesDir.listFiles { f -> f.isFile && f.extension == "mdc" }?.sortedBy { it.name } ?: emptyList()
        for (file in mdcFiles) {
            val rel = ".cursor/rules/${file.name}"
            if (rel !in indexed) {
                violations += Violation("Rule file not indexed: $rel has no row in the AGENTS.md \"Detailed Rules\" table")
            }
        }
        for (path in indexed.sorted()) {
            if (!File(repoRoot, path).exists()) {
                violations += Violation("Indexed rule missing: $path is listed in AGENTS.md but does not exist")
            }
        }
        return violations
    }

    private fun checkSkillsIndex(agentsText: String): List<Violation> {
        val violations = mutableListOf<Violation>()
        val indexed = SKILL_PATH_REGEX.findAll(agentsText).map { it.groupValues[1] }.toSet()

        val skillDirs = skillsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        for (dir in skillDirs) {
            val rel = ".claude/skills/${dir.name}"
            if (!File(dir, "SKILL.md").exists()) {
                violations += Violation("Skill missing SKILL.md: $rel/")
            }
            if (rel !in indexed) {
                violations += Violation("Skill not indexed: $rel/ has no row in the AGENTS.md \"Skills\" table")
            }
        }
        for (path in indexed.sorted()) {
            val dir = File(repoRoot, path)
            if (!dir.isDirectory || !File(dir, "SKILL.md").exists()) {
                violations += Violation("Indexed skill missing: $path is listed in AGENTS.md but has no directory containing SKILL.md")
            }
        }
        return violations
    }

    private enum class RefKind { MODULE, PATH }

    private data class Reference(val value: String, val kind: RefKind, val line: Int)

    private fun checkDanglingReferences(modules: Set<String>): List<Violation> {
        val violations = mutableListOf<Violation>()
        val files = (listOf(agentsMd) + (rulesDir.listFiles { f -> f.isFile && f.extension == "mdc" }?.toList() ?: emptyList()))
            .sortedBy { it.name }
        for (file in files) {
            for (ref in extractReferences(file)) {
                if (shouldIgnore(ref.value)) continue
                val resolved = when (ref.kind) {
                    RefKind.MODULE -> resolveModule(ref.value, modules)
                    RefKind.PATH -> resolvePath(ref.value, file)
                }
                if (!resolved) {
                    val reason = if (ref.kind == RefKind.MODULE) "does not resolve to a module" else "does not exist"
                    violations += Violation("Dangling reference: '${ref.value}' $reason", "${file.name}:${ref.line}")
                }
            }
        }
        return violations
    }

    private fun checkSymlinkParity(): List<Violation> {
        val violations = mutableListOf<Violation>()
        val claudeRulesDir = File(repoRoot, ".claude/rules")

        // 1. Every .cursor/rules/*.mdc must have a valid symlink in .claude/rules/.
        val mdcFiles = rulesDir.listFiles { f -> f.isFile && f.extension == "mdc" }?.sortedBy { it.name } ?: emptyList()
        for (file in mdcFiles) {
            val name = file.nameWithoutExtension
            val link = File(claudeRulesDir, "$name.md")
            if (!Files.isSymbolicLink(link.toPath())) {
                violations += Violation("Missing rule symlink: .claude/rules/$name.md should be a symlink to ../../.cursor/rules/$name.mdc")
                continue
            }
            val target = Files.readSymbolicLink(link.toPath()).toString()
            val expected = "../../.cursor/rules/$name.mdc"
            if (target != expected) {
                violations += Violation("Wrong rule symlink target: .claude/rules/$name.md -> $target (expected $expected)")
            }
        }

        // 2. Every entry in .claude/rules/ must be a symlink (no regular files).
        val claudeEntries = claudeRulesDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        for (entry in claudeEntries) {
            if (!Files.isSymbolicLink(entry.toPath())) {
                violations += Violation("Not a symlink: .claude/rules/${entry.name} must be a symlink into .cursor/rules/")
            }
        }
        return violations
    }

    private fun extractReferences(file: File): List<Reference> {
        val refs = mutableListOf<Reference>()
        var inFence = false
        file.readLines().forEachIndexed { index, line ->
            val lineNo = index + 1
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed

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

    private fun shouldIgnore(value: String): Boolean {
        return value.startsWith("http://") ||
            value.startsWith("https://") ||
            value.startsWith("mailto:") ||
            value.startsWith("#") ||
            value.contains("<") ||
            value.contains(">")
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
            .filter { it.isDirectory && File(it, "build.gradle").exists() }
            .map { it.name }
            .toSet()
    }

    private fun findByName(leaf: String): Boolean {
        return repoRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "node_modules" }
            .any { it.name == leaf }
    }

    companion object {
        private val RULE_PATH_REGEX = Regex("`(\\.cursor/rules/[^`]+\\.mdc)`")
        private val SKILL_PATH_REGEX = Regex("`(\\.claude/skills/[^`/]+)/?`")
        private val MD_LINK_REGEX = Regex("\\[[^\\]]*]\\(([^)]+)\\)")
        private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
        private val MODULE_REGEX = Regex("^:[a-z0-9]+(-[a-z0-9]+)*(:[a-z0-9]+(-[a-z0-9]+)*)*$")
    }
}
