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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AiConfigCheckerTest {

    @TempDir
    lateinit var repo: File

    private fun write(path: String, content: String) {
        val f = File(repo, path)
        f.parentFile.mkdirs()
        f.writeText(content)
    }

    /**
     * A consistent repo: an always-loaded rule, a path-scoped rule, a doc reachable from CLAUDE.md and
     * another reachable from a rule, a well-formed skill, and no dangling references.
     */
    private fun validRepo() {
        write("settings.gradle", "// modules auto-discovered\n")
        write("di/build.gradle", "// module\n")

        write(
            "CLAUDE.md",
            """
            |# CLAUDE.md — Project
            |
            |Versions live in the build files.
            |
            || Read | When |
            ||---|---|
            || `.claude/docs/contributions.md` | opening a PR |
            """.trimMargin(),
        )

        write(".claude/rules/architecture.md", "# Architecture\n\nSee module `:di`.\nCrashes: `.claude/docs/dagger-scopes.md`.\n")
        write(".claude/rules/maestro-ui-tests.md", "---\npaths:\n  - \".maestro/**\"\n---\n# Maestro\n")
        write(".claude/docs/contributions.md", "# Contributions\n")
        write(".claude/docs/dagger-scopes.md", "# Dagger scopes\n")
        write(".claude/skills/do-thing/SKILL.md", "---\nname: do-thing\ndescription: Use when doing the thing.\n---\n# Do thing\n")
    }

    private fun check(): List<Violation> = AiConfigChecker(repo).check()

    @Test
    fun `valid repo has no violations`() {
        validRepo()
        assertEquals(emptyList<Violation>(), check())
    }

    @Test
    fun `missing CLAUDE_md is reported`() {
        val violations = check()
        assertTrue(violations.any { it.message.contains("CLAUDE.md not found") }, "got: $violations")
    }

    // --- docs reachability ---------------------------------------------------------------------

    @Test
    fun `doc referenced from neither CLAUDE_md nor a rule is unreachable`() {
        validRepo()
        write(".claude/docs/orphan.md", "# Orphan\n")
        val violations = check()
        assertTrue(
            violations.any { it.message.contains("Unreachable doc") && it.message.contains("orphan.md") },
            "got: $violations",
        )
    }

    @Test
    fun `doc referenced only from a rule is reachable`() {
        validRepo()
        assertTrue(violations().none { it.message.contains("Unreachable doc") }, "dagger-scopes.md is referenced by a rule")
    }

    private fun violations(): List<Violation> = check()

    // --- dangling config references -----------------------------------------------------------

    @Test
    fun `CLAUDE_md pointing at a missing doc is reported`() {
        validRepo()
        val claude = File(repo, "CLAUDE.md")
        claude.writeText(claude.readText() + "\n| `.claude/docs/ghost.md` | never |\n")
        val violations = check()
        assertTrue(
            violations.any { it.message.contains("Dangling AI config reference") && it.message.contains("ghost.md") },
            "got: $violations",
        )
    }

    @Test
    fun `rule pointing at a missing doc is reported`() {
        validRepo()
        write(".claude/rules/architecture.md", "# Architecture\n\nRead `.claude/docs/missing.md`.\n")
        val violations = check()
        assertTrue(
            violations.any { it.message.contains("Dangling AI config reference") && it.message.contains("missing.md") },
            "got: $violations",
        )
    }

    @Test
    fun `doc pointing at a missing rule is reported`() {
        validRepo()
        write(".claude/docs/contributions.md", "# Contributions\n\nSee `.claude/rules/gone.md`.\n")
        val violations = check()
        assertTrue(
            violations.any { it.message.contains("Dangling AI config reference") && it.message.contains("gone.md") },
            "got: $violations",
        )
    }

    @Test
    fun `config reference inside a code fence is ignored`() {
        validRepo()
        write(".claude/docs/contributions.md", "# Contributions\n\n```\nsee .claude/docs/not-real.md\n```\n")
        assertTrue(check().none { it.message.contains("not-real.md") }, "fenced references are illustrative")
    }

    // --- frontmatter --------------------------------------------------------------------------

    @Test
    fun `description on a rule is reported as inert`() {
        validRepo()
        write(".claude/rules/architecture.md", "---\ndescription: does nothing here\n---\n# Architecture\n")
        val violations = check()
        assertTrue(
            violations.any { it.message.contains("Inert frontmatter") && it.message.contains("description") },
            "got: $violations",
        )
    }

    @Test
    fun `paths on a rule is accepted`() {
        validRepo()
        assertTrue(check().none { it.message.contains("Inert frontmatter") }, "paths: is the one meaningful rule key")
    }

    @Test
    fun `empty paths block is reported`() {
        validRepo()
        write(".claude/rules/maestro-ui-tests.md", "---\npaths:\n---\n# Maestro\n")
        val violations = check()
        assertTrue(violations.any { it.message.contains("Empty `paths:`") }, "got: $violations")
    }

    @Test
    fun `frontmatter on a doc is reported as inert`() {
        validRepo()
        write(".claude/docs/contributions.md", "---\npaths:\n  - \"**/*.kt\"\n---\n# Contributions\n")
        val violations = check()
        assertTrue(
            violations.any { it.message.contains("Inert frontmatter") && it.message.contains("docs/contributions.md") },
            "got: $violations",
        )
    }

    // --- skills -------------------------------------------------------------------------------

    @Test
    fun `skill without description is reported`() {
        validRepo()
        write(".claude/skills/do-thing/SKILL.md", "---\nname: do-thing\n---\n# Do thing\n")
        val violations = check()
        assertTrue(
            violations.any { it.message.contains("Skill missing `description:`") },
            "got: $violations",
        )
    }

    @Test
    fun `skill without name is reported`() {
        validRepo()
        write(".claude/skills/do-thing/SKILL.md", "---\ndescription: Use when doing the thing.\n---\n# Do thing\n")
        val violations = check()
        assertTrue(violations.any { it.message.contains("Skill missing `name:`") }, "got: $violations")
    }

    // --- dangling code references -------------------------------------------------------------

    @Test
    fun `unknown module reference is reported`() {
        validRepo()
        write(".claude/rules/architecture.md", "# Architecture\n\nUses module `:ghost-impl`.\n")
        val violations = check()
        assertTrue(violations.any { it.message.contains("':ghost-impl'") }, "got: $violations")
    }

    @Test
    fun `known module reference resolves`() {
        validRepo()
        assertTrue(check().none { it.message.contains(":di") }, "di/build.gradle exists")
    }

    @Test
    fun `missing repo-rooted path is reported`() {
        validRepo()
        write(".claude/docs/contributions.md", "# Contributions\n\nSee `di/src/main/Gone.kt`.\n")
        val violations = check()
        assertTrue(violations.any { it.message.contains("Gone.kt") }, "got: $violations")
    }

    @Test
    fun `external looking path is not validated`() {
        validRepo()
        write(".claude/docs/contributions.md", "# Contributions\n\nSee `windows-browser/rules/foo.md`.\n")
        assertTrue(check().none { it.message.contains("windows-browser") }, "paths outside the repo are illustrative")
    }

    @Test
    fun `elided path resolves by leaf name`() {
        validRepo()
        write("di/src/main/Real.kt", "// file\n")
        write(".claude/docs/contributions.md", "# Contributions\n\nSee `di/.../Real.kt`.\n")
        assertTrue(check().none { it.message.contains("Real.kt") }, "got: ${check()}")
    }

    @Test
    fun `glob in a reference is not validated`() {
        validRepo()
        write(".claude/docs/contributions.md", "# Contributions\n\nCovers `**/wideevents/**`.\n")
        assertTrue(check().none { it.message.contains("wideevents") }, "globs are patterns, not paths")
    }

    // --- volatile facts -----------------------------------------------------------------------

    @Test
    fun `restated tool version in CLAUDE_md is reported`() {
        validRepo()
        val claude = File(repo, "CLAUDE.md")
        claude.writeText(claude.readText() + "\nBuild: Gradle 7.6, Kotlin 1.9.24.\n")
        val violations = check()
        assertTrue(violations.any { it.message.contains("Restated tool version") }, "got: $violations")
    }

    @Test
    fun `tool version inside a code fence is allowed`() {
        validRepo()
        val claude = File(repo, "CLAUDE.md")
        claude.writeText(claude.readText() + "\n```\nexample: Gradle 7.6\n```\n")
        assertTrue(check().none { it.message.contains("Restated tool version") }, "fenced examples are not restatements")
    }

    @Test
    fun `tool version in a rule is allowed`() {
        validRepo()
        write(".claude/rules/architecture.md", "# Architecture\n\nA library requires Kotlin 2.3.0.\n")
        assertTrue(check().none { it.message.contains("Restated tool version") }, "the guard is CLAUDE.md-only")
    }

    @Test
    fun `single integer version is not flagged`() {
        validRepo()
        val claude = File(repo, "CLAUDE.md")
        claude.writeText(claude.readText() + "\nBuilding requires JDK 21 and JVM target 17.\n")
        assertTrue(check().none { it.message.contains("Restated tool version") }, "single integers are not semvers")
    }
}
