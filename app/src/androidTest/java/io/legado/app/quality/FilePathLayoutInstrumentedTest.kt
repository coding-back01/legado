package io.legado.app.quality

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.legado.app.R
import io.legado.app.databinding.ItemPathPickerBinding
import io.legado.app.ui.file.utils.FilePickerIcon
import io.legado.app.utils.ConvertUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilePathLayoutInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val profile = requireNotNull(
        InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT)
    ) { "必须指定 $PROFILE_ARGUMENT" }

    @Test
    fun breadcrumbKeepsIconTextAndWholeRowInteraction() {
        val activity = launchFixture()
        instrumentation.runOnMainSync {
            val root = activity.findViewById<View>(R.id.text_view).parent as View
            val binding = ItemPathPickerBinding.bind(root)
            assertSame("View Binding 根节点漂移", root, binding.root)
            binding.textView.text = "root"
            binding.imageView.setImageDrawable(ConvertUtils.toDrawable(FilePickerIcon.getArrow()))
            root.measure(
                View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(dp(activity, 48), View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)

            val expectedTextColor = TextView(activity).currentTextColor
            assertEquals("路径文字颜色未保持主题默认 TextView 样式", expectedTextColor, binding.textView.currentTextColor)
            assertTrue(
                "路径文字颜色不可见",
                Color.alpha(binding.textView.currentTextColor) > 0,
            )
            assertEquals("箭头视口宽度变化", dp(activity, 20), binding.imageView.width)
            assertEquals("箭头视口未覆盖整行高度", root.height, binding.imageView.height)
            assertNotNull("动态箭头缺失", binding.imageView.drawable)
            assertTrue("箭头位于文字左侧或发生重叠", binding.imageView.left >= binding.textView.right)

            val rootArea = Rect(0, 0, root.width, root.height)
            val textArea = Rect().also(binding.textView::getHitRect)
            val iconArea = Rect().also(binding.imageView::getHitRect)
            assertTrue("整行点击区域未覆盖文字", rootArea.contains(textArea))
            assertTrue("整行点击区域未覆盖箭头", rootArea.contains(iconArea))
            assertFalse("文字子项不应截获整行点击", binding.textView.isClickable)
            assertFalse("箭头子项不应截获整行点击", binding.imageView.isClickable)
            var clicks = 0
            root.setOnClickListener { clicks += 1 }
            assertTrue("路径整行不可点击", root.performClick())
            assertEquals("路径整行点击次数错误", 1, clicks)
        }
        closeFixture()
    }

    private fun dp(activity: GoldenFixtureActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun launchFixture(): GoldenFixtureActivity {
        instrumentation.targetContext.startActivity(
            Intent().apply {
                setClassName(
                    instrumentation.targetContext.packageName,
                    GoldenFixtureActivity::class.java.name,
                )
                putExtra(GoldenFixtureActivity.EXTRA_FIXTURE, FIXTURE)
                putExtra(GoldenFixtureActivity.EXTRA_PROFILE, profile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            resumedFixture()?.let {
                instrumentation.waitForIdleSync()
                return it
            }
            SystemClock.sleep(100)
        }
        error("文件路径 fixture 未进入 RESUMED")
    }

    private fun closeFixture() {
        val activity = resumedFixture()
        instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
    }

    private fun resumedFixture(): GoldenFixtureActivity? {
        var activity: GoldenFixtureActivity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<GoldenFixtureActivity>()
                .firstOrNull()
        }
        return activity
    }

    companion object {
        private const val PROFILE_ARGUMENT = "legado.golden.profile"
        private const val FIXTURE = "file_path"
    }
}
