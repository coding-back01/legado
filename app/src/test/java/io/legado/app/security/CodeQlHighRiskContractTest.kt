package io.legado.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodeQlHighRiskContractTest {

    @Test
    fun `PendingIntent 使用显式目标和不可变标志`() {
        val contextExtensions = repoFile(
            "app/src/main/java/io/legado/app/utils/ContextExtensions.kt"
        ).readText()

        assertFalse(contextExtensions.contains("FLAG_MUTABLE"))
        assertTrue(contextExtensions.contains("FLAG_IMMUTABLE"))
        assertTrue(
            contextExtensions.windowed(".setClass(this, T::class.java)".length)
                .count { it == ".setClass(this, T::class.java)" } >= 3
        )
    }

    @Test
    fun `上传文件名和大小只按文本写入 DOM`() {
        val uploadScript = repoFile(
            "app/src/main/assets/web/uploadBook/js/common.js"
        ).readText()

        assertFalse(uploadScript.contains(".html(name)"))
        assertFalse(uploadScript.contains(".html(size)"))
        assertTrue(uploadScript.contains(".text(name)"))
        assertTrue(uploadScript.contains(".text(size)"))
    }

    @Test
    fun `TXT 目录正则在编译和匹配时均受资源预算保护`() {
        val editDialog = repoFile(
            "app/src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleEditDialog.kt"
        ).readText()
        val textFile = repoFile(
            "app/src/main/java/io/legado/app/model/localBook/TextFile.kt"
        ).readText()

        assertTrue(editDialog.contains("RegexSafety.compile(tocRule.rule"))
        assertTrue(textFile.contains("RegexSafety.compile(tocRule.rule"))
        assertTrue(textFile.contains("RegexSafety.limitInput(blockContent)"))
        assertTrue(textFile.contains("RegexSafety.limitInput(content)"))
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
