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
import java.nio.file.Paths
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

    /** Create a relative symlink at [linkPath] pointing at [target] (a relative path string). */
    private fun symlink(linkPath: String, target: String) {
        val link = File(repo, linkPath)
        link.parentFile.mkdirs()
        Files.createSymbolicLink(link.toPath(), Paths.get(target))
    }

    /** A fully consistent repo: CLAUDE.md imports AGENTS.md and indexes every rule, refs resolve, symlinks valid. */
    private fun validRepo() {
        write(".cursor/rules/architecture.mdc", "---\nalwaysApply: true\n---\n# Arch\nSee `app/build.gradle` and module `:di`.\n")
        write(".cursor/rules/pixels.mdc", "# Pixels\n")
        symlink(".claude/rules/architecture.md", "../../.cursor/rules/architecture.mdc")
        symlink(".claude/rules/pixels.md", "../../.cursor/rules/pixels.mdc")
        write(".claude/skills/my-skill/SKILL.md", "---\nname: my-skill\ndescription: example skill\n---\nbody\n")
        symlink(".cursor/skills/my-skill/SKILL.md", "../../../.claude/skills/my-skill/SKILL.md")
        write("app/build.gradle", "// app\n")
        write("di/build.gradle", "// di module\n")
        // AGENTS.md carries no rules table: Cursor auto-loads .cursor/rules/*.mdc natively.
        write("AGENTS.md", "# AGENTS\n\nProject overview, build commands, architecture.\n")
        // CLAUDE.md imports AGENTS.md and indexes every rule, since Claude cannot auto-load the .mdc files.
        write(
            "CLAUDE.md",
            """
            |@AGENTS.md
            |
            |## Detailed Rules
            || File | Covers |
            ||---|---|
            || `.cursor/rules/architecture.mdc` | arch |
            || `.cursor/rules/pixels.mdc` | pixels |
            """.trimMargin(),
        )
    }

    @Test
    fun whenAllRulesIndexedThenNoRulesViolations() {
        validRepo()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Rule file not indexed") }, "unexpected: $violations")
        assertTrue(violations.none { it.message.contains("Indexed rule missing") }, "unexpected: $violations")
    }

    @Test
    fun whenRuleFileNotInTableThenViolation() {
        validRepo()
        write(".cursor/rules/orphan.mdc", "# Orphan\n") // exists but not in AGENTS.md table
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Rule file not indexed") && it.message.contains("orphan.mdc") }, "got: $violations")
    }

    @Test
    fun whenAgentsMdMissingThenViolation() {
        // no validRepo(): nothing written, so AGENTS.md does not exist
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("AGENTS.md not found") }, "got: $violations")
    }

    @Test
    fun whenTableListsMissingRuleThenViolation() {
        validRepo()
        // add a table row pointing at a rule file that does not exist
        val claude = File(repo, "CLAUDE.md")
        claude.writeText(claude.readText() + "\n| `.cursor/rules/ghost.mdc` | ghost |\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Indexed rule missing") && it.message.contains("ghost.mdc") }, "got: $violations")
    }

    @Test
    fun whenClaudeMdIndexesAllRulesThenNoViolation() {
        validRepo() // CLAUDE.md imports AGENTS.md and indexes every rule
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Rule file not indexed") }, "unexpected: $violations")
        assertTrue(violations.none { it.message.contains("Indexed rule missing") }, "unexpected: $violations")
    }

    @Test
    fun whenClaudeMdMissingRuleThenViolationNamesClaudeMd() {
        validRepo()
        write(
            "CLAUDE.md",
            """
            |@AGENTS.md
            |
            || File | Covers |
            ||---|---|
            || `.cursor/rules/architecture.mdc` | arch |
            """.trimMargin(), // omits pixels.mdc
        )
        val violations = AiConfigChecker(repo).check()
        assertTrue(
            violations.any {
                it.message.contains("Rule file not indexed") && it.message.contains("pixels.mdc") && it.message.contains("CLAUDE.md")
            },
            "got: $violations",
        )
    }

    @Test
    fun whenClaudeMdMissingThenViolation() {
        validRepo()
        File(repo, "CLAUDE.md").delete()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("CLAUDE.md not found") }, "got: $violations")
    }

    @Test
    fun whenClaudeMdDoesNotImportAgentsThenViolation() {
        validRepo()
        // Full rules table, but no `@AGENTS.md` import.
        write(
            "CLAUDE.md",
            """
            |# Claude
            |
            || File | Covers |
            ||---|---|
            || `.cursor/rules/architecture.mdc` | arch |
            || `.cursor/rules/pixels.mdc` | pixels |
            """.trimMargin(),
        )
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("CLAUDE.md must import AGENTS.md") }, "got: $violations")
    }

    @Test
    fun whenClaudeMdImportsAgentsThenNoImportViolation() {
        validRepo() // CLAUDE.md starts with `@AGENTS.md`
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("must import AGENTS.md") }, "unexpected: $violations")
    }

    @Test
    fun whenRulesTableLivesInImportedAgentsMdThenNoViolation() {
        validRepo()
        // The index rows may live in AGENTS.md (a .md file Claude reads via the import) instead of CLAUDE.md.
        write(
            "AGENTS.md",
            """
            |# AGENTS
            |
            || File | Covers |
            ||---|---|
            || `.cursor/rules/architecture.mdc` | arch |
            || `.cursor/rules/pixels.mdc` | pixels |
            """.trimMargin(),
        )
        write("CLAUDE.md", "@AGENTS.md\n\n# Claude extras\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Rule file not indexed") }, "unexpected: $violations")
    }

    @Test
    fun whenAllReferencesResolveThenNoDanglingViolations() {
        validRepo()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenInlineModuleRefResolvesThenNoViolation() {
        validRepo()
        // architecture.mdc already references `:di`, and di/build.gradle exists -> resolves
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("':di'") }, "unexpected: $violations")
    }

    @Test
    fun whenInlineModuleRefDanglingThenViolation() {
        validRepo()
        write(".cursor/rules/architecture.mdc", "# Arch\nUses module `:ghost-impl`.\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Dangling reference") && it.message.contains(":ghost-impl") }, "got: $violations")
    }

    @Test
    fun whenRepoRootedPathDanglingThenViolation() {
        validRepo()
        write(".cursor/rules/architecture.mdc", "# Arch\nSee `app/src/main/Gone.kt`.\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Dangling reference") && it.message.contains("Gone.kt") }, "got: $violations")
    }

    @Test
    fun whenNonRepoRootedPathThenNoViolation() {
        validRepo()
        write(".cursor/rules/architecture.mdc", "# Arch\nSee `windows-browser/.cursor/rules/wide-events.mdc`.\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenReferenceInsideFencedCodeBlockThenIgnored() {
        validRepo()
        write(".cursor/rules/architecture.mdc", "# Arch\n```\nuse module :ghost and path app/src/Gone.kt here\n```\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenUrlOrAnchorOrPlaceholderThenIgnored() {
        validRepo()
        write(
            ".cursor/rules/architecture.mdc",
            "# Arch\nLink [docs](https://example.com/x) and [top](#section) and `strings-<feature>.xml`.\n",
        )
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenModuleRefIsNestedThenResolvesByLeafSegment() {
        validRepo()
        write("feature-toggles/feature-toggles-api/build.gradle", "// nested module\n")
        write(".cursor/rules/architecture.mdc", "# Arch\nDepends on `:feature-toggles-api`.\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenModuleDefinedWithKotlinBuildScriptThenResolves() {
        validRepo()
        write("kts-feature/build.gradle.kts", "// kts module\n")
        write(".cursor/rules/architecture.mdc", "# Arch\nDepends on `:kts-feature`.\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenXmlNamespaceAttributeThenNotTreatedAsModule() {
        validRepo()
        write(".cursor/rules/architecture.mdc", "# Arch\nUse `app:buttonSize` and `android:text` attributes.\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenFileRelativeLinkResolvesThenNoViolation() {
        validRepo()
        write("privacy-config/README.md", "# pc\n")
        // A markdown link target relative to the rule file's own directory (.cursor/rules/).
        write(".cursor/rules/architecture.mdc", "# Arch\nSee [readme](../../privacy-config/README.md).\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Dangling reference") }, "unexpected: $violations")
    }

    @Test
    fun whenSymlinksValidThenNoSymlinkViolations() {
        validRepo()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("symlink") }, "unexpected: $violations")
    }

    @Test
    fun whenRuleSymlinkMissingThenViolation() {
        validRepo()
        File(repo, ".claude/rules/pixels.md").delete()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Missing rule symlink") && it.message.contains("pixels.md") }, "got: $violations")
    }

    @Test
    fun whenRuleSymlinkTargetWrongThenViolation() {
        validRepo()
        File(repo, ".claude/rules/pixels.md").delete()
        symlink(".claude/rules/pixels.md", "../../.cursor/rules/wrong.mdc")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Wrong rule symlink target") && it.message.contains("pixels.md") }, "got: $violations")
    }

    @Test
    fun whenClaudeRulesEntryIsRegularFileThenViolation() {
        validRepo()
        write(".claude/rules/notes.md", "regular file\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Not a symlink") && it.message.contains("notes.md") }, "got: $violations")
    }

    @Test
    fun whenSkillSymlinkMissingThenViolation() {
        validRepo()
        File(repo, ".cursor/skills/my-skill/SKILL.md").delete()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Missing skill symlink") && it.message.contains("my-skill") }, "got: $violations")
    }

    @Test
    fun whenSkillSymlinkTargetWrongThenViolation() {
        validRepo()
        File(repo, ".cursor/skills/my-skill/SKILL.md").delete()
        symlink(".cursor/skills/my-skill/SKILL.md", "../../../.claude/skills/wrong/SKILL.md")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Wrong skill symlink target") && it.message.contains("my-skill") }, "got: $violations")
    }

    @Test
    fun whenCursorSkillEntryIsRegularFileThenViolation() {
        validRepo()
        write(".claude/skills/regular-skill/SKILL.md", "---\nname: regular-skill\ndescription: x\n---\n")
        write(".cursor/skills/regular-skill/SKILL.md", "regular file, not a symlink\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Not a symlink") && it.message.contains("regular-skill") }, "got: $violations")
    }

    @Test
    fun whenCursorSkillHasNoSourceThenOrphanViolation() {
        validRepo()
        symlink(".cursor/skills/ghost/SKILL.md", "../../../.claude/skills/ghost/SKILL.md")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Orphaned skill mirror") && it.message.contains("ghost") }, "got: $violations")
    }

    @Test
    fun whenSkillSymlinksValidThenNoSkillViolations() {
        validRepo()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("skill") }, "unexpected: $violations")
    }
}
