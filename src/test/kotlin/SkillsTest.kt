package ceres.tools

import ceres.testing.tempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class SkillsTest {
  private val root = tempDir()
  private val skills = Skills(root)

  private fun skill(name: String, md: String): Path =
    (root / name).createDirectories().also { (it / "SKILL.md").writeText(md) }

  private fun desc(md: String) = Skills(root).also { skill("s", md) }.find("s")!!.description

  @Test fun simpleAndQuotedDescriptions() {
    assertEquals("Does things", desc("---\nname: s\ndescription: Does things\n---\nbody"))
    assertEquals("quoted", desc("---\ndescription: \"quoted\"\n---\n"))
  }

  @Test fun multiLineDescriptions() {
    assertEquals("line one line two", desc("---\ndescription: >-\n  line one\n  line two\n---\n"))
    assertEquals("Does things", desc("---\ndescription: Does\n  things\n---\n"))
  }

  @Test fun missingOrBrokenFrontmatter() {
    assertEquals("(no description)", desc("just a body"))
    assertEquals("(no description)", desc("---\ndescription: never closed\n"))
    assertEquals("(no description)", desc("---\nname: only-name\n---\n"))
  }

  @Test fun descriptionIsCappedAt400() {
    assertEquals(400, desc("---\ndescription: ${"a".repeat(500)}\n---\n").length)
  }

  @Test fun listIsSortedAndSkipsNonSkills() {
    skill("zeta", "---\ndescription: z\n---\n")
    skill("alpha", "---\ndescription: a\n---\n")
    (root / "no-skill-md").createDirectories()
    (root / "stray.txt").writeText("x")
    assertEquals(listOf("alpha", "zeta"), skills.list().map { it.name })
    assertEquals("a", skills.find("alpha")?.description)
    assertNull(skills.find("nope"))
  }

  @Test fun missingRootIsEmpty() {
    assertTrue(Skills(root / "does-not-exist").list().isEmpty())
  }

  @Test fun filesListsExtrasButNotRootSkillMd() {
    val dir = skill("s", "---\ndescription: d\n---\n")
    (dir / "references").createDirectories(); (dir / "references" / "api.md").writeText("x")
    (dir / "scripts").createDirectories(); (dir / "scripts" / "run.sh").writeText("x")
    assertEquals(listOf("references/api.md", "scripts/run.sh"), skills.files(skills.find("s")!!))
  }

  @Test fun resolveStaysInsideSkillDir() {
    val dir = skill("s", "---\ndescription: d\n---\n")
    skill("other", "---\ndescription: o\n---\n")
    (dir / "ref.md").writeText("x")
    val s = skills.find("s")!!
    assertNotNull(skills.resolve(s, "ref.md"))
    assertNull(skills.resolve(s, "../other/SKILL.md"))
    assertNull(skills.resolve(s, "../../x"))
    assertNull(skills.resolve(s, "missing.md"))
    assertNull(skills.resolve(s, "."))
  }
}
