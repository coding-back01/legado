package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LauncherIconContractTest {

    private val root = requireNotNull(
        sequenceOf(File(".."), File(".")).firstOrNull {
            it.resolve("app/src/main/AndroidManifest.xml").isFile
        }
    ) { "找不到仓库根目录" }

    @Test
    fun `七个 launcher 入口与旧字符串值保持一一对应`() {
        val manifest = xml("app/src/main/AndroidManifest.xml")
        val launcherActivities = manifest.getElementsByTagName("activity")
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .filter { activity ->
                        activity.getElementsByTagName("category").let { categories ->
                            (0 until categories.length).any { index ->
                                (categories.item(index) as Element).android("name") ==
                                        "android.intent.category.LAUNCHER"
                            }
                        }
                    }
                    .associate { it.android("name") to it.android("icon") }
            }

        assertEquals(
            linkedMapOf(
                ".ui.welcome.WelcomeActivity" to "",
                ".ui.welcome.Launcher1" to "@mipmap/launcher1",
                ".ui.welcome.Launcher2" to "@mipmap/launcher2",
                ".ui.welcome.Launcher3" to "@mipmap/launcher3",
                ".ui.welcome.Launcher4" to "@mipmap/launcher4",
                ".ui.welcome.Launcher5" to "@mipmap/launcher5",
                ".ui.welcome.Launcher6" to "@mipmap/launcher6",
            ),
            launcherActivities,
        )

        assertEquals(
            listOf(
                "ic_launcher",
                "launcher1",
                "launcher2",
                "launcher3",
                "launcher4",
                "launcher5",
                "launcher6",
            ),
            arrayItems("icons"),
        )

        val preference = root.resolve("app/src/main/res/xml/pref_config_theme.xml").readText()
        assertTrue(preference.contains("android:defaultValue=\"ic_launcher\""))
        assertTrue(preference.contains("android:entryValues=\"@array/icons\""))
    }

    @Test
    fun `图标资源使用与旧字符串数组平行的类型化数组`() {
        assertEquals(
            listOf(
                "@mipmap/ic_launcher",
                "@mipmap/launcher1",
                "@mipmap/launcher2",
                "@mipmap/launcher3",
                "@mipmap/launcher4",
                "@mipmap/launcher5",
                "@mipmap/launcher6",
            ),
            arrayItems("launcher_icon_resources"),
        )

        val preference = root.resolve("app/src/main/res/xml/pref_config_theme.xml").readText()
        val implementation = root.resolve(
            "app/src/main/java/io/legado/app/lib/prefs/IconListPreference.kt"
        ).readText()
        assertTrue(preference.contains("app:icons=\"@array/launcher_icon_resources\""))
        assertFalse(implementation.contains(".getIdentifier("))
    }

    @Test
    fun `未知旧值回退且配置重建保留类型化图标`() {
        val implementation = root.resolve(
            "app/src/main/java/io/legado/app/lib/prefs/IconListPreference.kt"
        ).readText()
        assertTrue(implementation.contains("resolveIconIndex"))
        assertTrue(implementation.contains("putIntArray(ARG_ICON_RESOURCE_IDS"))
        assertTrue(implementation.contains("getIntArray(ARG_ICON_RESOURCE_IDS"))
        assertFalse(implementation.contains("putCharSequenceArray(\"iconNames\""))
        assertFalse(implementation.contains("getCharSequenceArray(\"iconNames\""))
    }

    private fun arrayItems(name: String): List<String> {
        val resources = xml("app/src/main/res/values/array_values.xml")
        val arrays = resources.getElementsByTagName("string-array").asElements() +
                resources.getElementsByTagName("array").asElements()
        val array = arrays.singleOrNull { it.getAttribute("name") == name }
            ?: return emptyList()
        return array.getElementsByTagName("item").asElements().map { it.textContent.trim() }
    }

    private fun xml(path: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(root.resolve(path))

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).map { item(it) as Element }

    private fun Element.android(name: String): String =
        getAttribute("android:$name")
}
