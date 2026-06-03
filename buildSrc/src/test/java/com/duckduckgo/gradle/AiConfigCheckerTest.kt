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

    /** A fully consistent repo: every rule/skill indexed, every reference resolves, symlinks valid. */
    private fun validRepo() {
        write(".cursor/rules/architecture.mdc", "---\nalwaysApply: true\n---\n# Arch\nSee `app/build.gradle` and module `:di`.\n")
        write(".cursor/rules/pixels.mdc", "# Pixels\n")
        symlink(".claude/rules/architecture.md", "../../.cursor/rules/architecture.mdc")
        symlink(".claude/rules/pixels.md", "../../.cursor/rules/pixels.mdc")
        write("app/build.gradle", "// app\n")
        write("di/build.gradle", "// di module\n")
        write(".claude/skills/my-skill/SKILL.md", "---\nname: my-skill\ndescription: x\n---\n")
        write(
            "AGENTS.md",
            """
            |# AGENTS
            |
            || File | Covers |
            ||---|---|
            || `.cursor/rules/architecture.mdc` | arch |
            || `.cursor/rules/pixels.mdc` | pixels |
            |
            |## Skills
            |
            || Skill | Purpose |
            ||---|---|
            || `.claude/skills/my-skill/` | does things |
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
        val agents = File(repo, "AGENTS.md")
        agents.writeText(agents.readText() + "\n| `.cursor/rules/ghost.mdc` | ghost |\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Indexed rule missing") && it.message.contains("ghost.mdc") }, "got: $violations")
    }

    @Test
    fun whenAllSkillsIndexedAndHaveSkillMdThenNoSkillViolations() {
        validRepo()
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.none { it.message.contains("Skill") }, "unexpected: $violations")
    }

    @Test
    fun whenSkillMissingSkillMdThenViolation() {
        validRepo()
        File(repo, ".claude/skills/broken").mkdirs() // dir but no SKILL.md
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Skill missing SKILL.md") && it.message.contains("broken") }, "got: $violations")
    }

    @Test
    fun whenSkillNotIndexedThenViolation() {
        validRepo()
        write(".claude/skills/unlisted/SKILL.md", "---\nname: unlisted\n---\n") // valid skill, absent from table
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Skill not indexed") && it.message.contains("unlisted") }, "got: $violations")
    }

    @Test
    fun whenTableListsMissingSkillThenViolation() {
        validRepo()
        val agents = File(repo, "AGENTS.md")
        agents.writeText(agents.readText() + "\n| `.claude/skills/ghost-skill/` | ghost |\n")
        val violations = AiConfigChecker(repo).check()
        assertTrue(violations.any { it.message.contains("Indexed skill missing") && it.message.contains("ghost-skill") }, "got: $violations")
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
}
