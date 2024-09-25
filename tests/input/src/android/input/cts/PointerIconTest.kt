/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.input.cts

import android.Manifest.permission.CREATE_VIRTUAL_DEVICE
import android.Manifest.permission.INJECT_EVENTS
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceManager.VirtualDevice
import android.companion.virtual.VirtualDeviceParams
import android.content.Context
import android.cts.input.EventVerifier
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualMouseRelativeEvent
import android.os.Environment
import android.os.SystemProperties
import android.view.Display
import android.view.MotionEvent
import android.view.PointerIcon
import android.virtualdevice.cts.common.FakeAssociationRule
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.cts.input.DefaultPointerSpeedRule
import com.android.cts.input.UinputDrawingTablet
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.inputeventmatchers.withMotionAction
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import platform.test.screenshot.GoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.ScreenshotTestRule
import platform.test.screenshot.assertAgainstGolden
import platform.test.screenshot.matchers.AlmostPerfectMatcher
import platform.test.screenshot.matchers.BitmapMatcher
import platform.test.screenshot.matchers.PixelPerfectMatcher

/**
 * End-to-end tests for the [PointerIcon] pipeline.
 *
 * These are screenshot tests for used to verify functionality of the pointer icon pipeline.
 * We use a virtual display to launch the test activity, and use a [VirtualMouse] to move the mouse
 * and get the mouse pointer to show up. We then request the pointer icon to be set using the view
 * APIs and take a screenshot of the display to ensure the icon shows up correctly. We use the
 * virtual display to be able to precisely compare the screenshots across devices of various form
 * factors and sizes.
 */
@MediumTest
@RunWith(Parameterized::class)
class PointerIconTest {
    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: EventVerifier
    private lateinit var exactScreenshotMatcher: BitmapMatcher
    private lateinit var similarScreenshotMatcher: BitmapMatcher

    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenarioRule<CaptureEventActivity>(testName)
    @get:Rule
    val fakeAssociationRule = FakeAssociationRule()
    @get:Rule
    val defaultPointerSpeedRule = DefaultPointerSpeedRule()
    @get:Rule
    val screenshotRule = ScreenshotTestRule(GoldenPathManager(
        InstrumentationRegistry.getInstrumentation().getContext(),
        ASSETS_PATH,
        TEST_OUTPUT_PATH,
        PathConfig()
    ), disableIconPool = false)

    @Parameter(0)
    lateinit var device: PointerDevice

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        activity = virtualDisplayRule.activity
        activity.runOnUiThread {
            activity.actionBar?.hide()
            activity.window.decorView.rootView.setBackgroundColor(Color.WHITE)
        }

        device.setUp(context, virtualDisplayRule.virtualDisplay.display, fakeAssociationRule)

        verifier = EventVerifier(activity::getInputEvent)

        exactScreenshotMatcher = PixelPerfectMatcher()
        similarScreenshotMatcher =
            AlmostPerfectMatcher(acceptableThreshold = SCREENSHOT_DIFF_PERCENT)
    }

    @After
    fun tearDown() {
        device.tearDown()
    }

    @Test
    fun testCreateBitmapIcon() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(Color.RED)
        }

        val view = activity.window.decorView.rootView
        view.pointerIcon = PointerIcon.create(bitmap, 50f, 50f)

        device.hoverMove(1, 1)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_HOVER_ENTER))
        waitForPointerIconUpdate()

        assertScreenshotsMatch()
    }

    @Test
    fun testLoadBitmapIcon() {
        val view = activity.window.decorView.rootView
        view.pointerIcon =
            PointerIcon.load(InstrumentationRegistry.getInstrumentation().targetContext.resources,
                R.drawable.pointer_arrow_bitmap_icon)

        device.hoverMove(1, 1)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_HOVER_ENTER))
        waitForPointerIconUpdate()

        assertScreenshotsMatch()
    }

    @Test
    fun testLoadVectorIconNoShadow() {
        // Skip test if Vector support not enabled.
        assumeTrue(android.view.flags.Flags.enableVectorCursors())

        val view = activity.window.decorView.rootView
        val pointer =
            PointerIcon.load(
                InstrumentationRegistry.getInstrumentation().targetContext.resources,
                R.drawable.pointer_arrow_vector_icon
            )
        // Turn off vector drop shadow for this test to confirm position of the pointer, ignoring
        // any differences in the shadow, which can change based on the hardware running the test.
        pointer.setDrawNativeDropShadow(false)
        view.pointerIcon = pointer

        device.hoverMove(1, 1)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_HOVER_ENTER))
        waitForPointerIconUpdate()

        assertScreenshotsMatch()
    }

    @Test
    fun testLoadVectorIcon() {
        // Skip test if Vector support not enabled.
        assumeTrue(android.view.flags.Flags.enableVectorCursors())

        val view = activity.window.decorView.rootView
        view.pointerIcon =
            PointerIcon.load(InstrumentationRegistry.getInstrumentation().targetContext.resources,
                R.drawable.pointer_arrow_vector_icon)

        device.hoverMove(1, 1)
        waitForPointerIconUpdate()

        // Drop shadows drawn in the hardware can be device dependent. Test that screenshots are
        // similar enough within a threshold to account for these differences.
        assertScreenshotsSimilar()
    }

    private fun getActualScreenshot(): Bitmap {
        val actualBitmap: Bitmap? = virtualDisplayRule.getScreenshot()
        assertNotNull(actualBitmap, "Screenshot is null.")
        return actualBitmap
    }

    private fun assertScreenshotsMatch() {
        getActualScreenshot().assertAgainstGolden(
            screenshotRule,
            getParameterizedExpectedScreenshotName(),
            exactScreenshotMatcher
        )
    }

    private fun assertScreenshotsSimilar() {
        getActualScreenshot().assertAgainstGolden(
            screenshotRule,
            getParameterizedExpectedScreenshotName(),
            similarScreenshotMatcher
        )
    }

    private fun getParameterizedExpectedScreenshotName(): String {
        // Replace illegal characters '[' and ']' in expected screenshot name with underscores.
        return "${testName.methodName}expected".replace("""\[|\]""".toRegex(), "_")
    }

    // We don't have a way to synchronously know when the requested pointer icon has been drawn
    // to the display, so wait some time (at least one display frame) for the icon to propagate.
    private fun waitForPointerIconUpdate() = Thread.sleep(500L * HW_TIMEOUT_MULTIPLIER)

    companion object {
        const val SCREENSHOT_DIFF_PERCENT = 0.01 // 1% total difference threshold
        const val ASSETS_PATH = "tests/input/assets"
        val TEST_OUTPUT_PATH = Environment.getExternalStorageDirectory().absolutePath +
                "/CtsInputTestCases/" +
                PointerIconTest::class.java.simpleName
        val HW_TIMEOUT_MULTIPLIER = SystemProperties.getInt("ro.hw_timeout_multiplier", 1);

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Iterable<Any> =
            // NOTE: PointerIconTest for MOUSE is temporarily ignored due to b/369000028.
            listOf(PointerDevice.DRAWING_TABLET)
    }
}

enum class PointerDevice {

    MOUSE {
        private lateinit var virtualDevice: VirtualDevice
        private lateinit var virtualMouse: VirtualMouse

        override fun setUp(
            context: Context,
            display: Display,
            fakeAssociationRule: FakeAssociationRule
        ) {
            val virtualDeviceManager =
                context.getSystemService(VirtualDeviceManager::class.java)!!
            runWithShellPermissionIdentity({
                virtualDevice =
                    virtualDeviceManager.createVirtualDevice(fakeAssociationRule.associationInfo.id,
                        VirtualDeviceParams.Builder().build())
                virtualMouse =
                    virtualDevice.createVirtualMouse(VirtualMouseConfig.Builder()
                            .setVendorId(TEST_VENDOR_ID)
                            .setProductId(TEST_PRODUCT_ID)
                            .setInputDeviceName("Pointer Icon Test Mouse")
                            .setAssociatedDisplayId(display.displayId).build())
            }, CREATE_VIRTUAL_DEVICE, INJECT_EVENTS)
        }

        override fun hoverMove(dx: Int, dy: Int) {
            runWithShellPermissionIdentity({
                virtualMouse.sendRelativeEvent(
                    VirtualMouseRelativeEvent.Builder()
                            .setRelativeX(dx.toFloat())
                            .setRelativeY(dy.toFloat())
                            .build()
                )
            }, CREATE_VIRTUAL_DEVICE)
        }

        override fun tearDown() {
            runWithShellPermissionIdentity({
                if (this::virtualMouse.isInitialized) {
                    virtualMouse.close()
                }
                if (this::virtualDevice.isInitialized) {
                    virtualDevice.close()
                }
            }, CREATE_VIRTUAL_DEVICE)
        }

        override fun toString(): String = "MOUSE"
    },

    DRAWING_TABLET {
        private lateinit var drawingTablet: UinputTouchDevice
        private lateinit var pointer: Point

        @Suppress("DEPRECATION")
        override fun setUp(
            context: Context,
            display: Display,
            fakeAssociationRule: FakeAssociationRule
        ) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            drawingTablet = UinputDrawingTablet(instrumentation, display)
            // Start with the pointer in the middle of the display.
            pointer = Point((display.width - 1) / 2, (display.height - 1) / 2)
        }

        override fun hoverMove(dx: Int, dy: Int) {
            pointer.offset(dx, dy)
            drawingTablet.sendBtn(UinputTouchDevice.BTN_TOOL_PEN, isDown = true)
            drawingTablet.sendDown(
                id = 0,
                location = pointer,
                toolType = UinputTouchDevice.MT_TOOL_PEN
            )
            drawingTablet.sync()
        }

        override fun tearDown() {
            if (this::drawingTablet.isInitialized) {
                drawingTablet.close()
            }
        }

        override fun toString(): String = "DRAWING_TABLET"
    };

    abstract fun setUp(
        context: Context,
        display: Display,
        fakeAssociationRule: FakeAssociationRule
    )

    abstract fun hoverMove(dx: Int, dy: Int)
    abstract fun tearDown()

    companion object {
        const val TEST_VENDOR_ID = 0x18d1
        const val TEST_PRODUCT_ID = 0xabcd
    }
}
