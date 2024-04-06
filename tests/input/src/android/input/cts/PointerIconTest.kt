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
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualMouseRelativeEvent
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon
import android.virtualdevice.cts.common.FakeAssociationRule
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.cts.input.DefaultPointerSpeedRule
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.inputeventmatchers.withMotionAction
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import kotlin.test.assertNotNull
import kotlin.test.fail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

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

    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenarioRule<CaptureEventActivity>(testName)
    @get:Rule
    val fakeAssociationRule = FakeAssociationRule()
    @get: Rule
    val defaultPointerSpeedRule = DefaultPointerSpeedRule()

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

        val actualScreenshot = getActualScreenshot()
        val expectedScreenshot = getGoldenImageBitmap(testName.methodName + "_expected.png")
        assertScreenshotDimensionsMatch(actualScreenshot, expectedScreenshot)

        assertScreenshotPixelsEqual(actualScreenshot, expectedScreenshot)
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

        val actualScreenshot = getActualScreenshot()
        val expectedScreenshot = getGoldenImageBitmap(testName.methodName + "_expected.png")
        assertScreenshotDimensionsMatch(actualScreenshot, expectedScreenshot)

        assertScreenshotPixelsEqual(actualScreenshot, expectedScreenshot)
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

        val actualScreenshot = getActualScreenshot()
        val expectedScreenshot = getGoldenImageBitmap(testName.methodName + "_expected.png")
        assertScreenshotDimensionsMatch(actualScreenshot, expectedScreenshot)

        assertScreenshotPixelsEqual(actualScreenshot, expectedScreenshot)
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

        val actualScreenshot = getActualScreenshot()
        val expectedScreenshot = getGoldenImageBitmap(testName.methodName + "_expected.png")
        assertScreenshotDimensionsMatch(actualScreenshot, expectedScreenshot)

        // Drop shadows drawn in the hardware can be device dependent. Test that screenshots are
        // similar enough within a threshold to account for these differences.
        assertScreenshotsSimilar(actualScreenshot, expectedScreenshot)
    }

    private fun assertScreenshotDimensionsMatch(
        actualScreenshot: Bitmap,
        expectedScreenshot: Bitmap
    ) {
        assertEquals(
            "Actual and expected screenshots should be the same width.",
            expectedScreenshot.width,
            actualScreenshot.width
        )
        assertEquals(
            "Actual and expected screenshots should be the same height.",
            expectedScreenshot.height,
            actualScreenshot.height
        )
    }

    private fun assertScreenshotsSimilar(actualScreenshot: Bitmap, expectedScreenshot: Bitmap) {
        if (isPixelDiffCountAboveThreshold(actualScreenshot, expectedScreenshot)) {
            saveBitmapToLosslessFile(actualScreenshot)
            fail("Screenshot mismatch.")
        }
    }

    // Use a count of pixel differences as opposed to a percentage difference per-pixel. A percent
    // threshold can make a test less flaky, but also provide a  less reliable signal: large parts
    // of the image can change or disappear and the test could still pass. Percentage thresholds can
    // also hide intentional changes not reflected in the golden.
    private fun isPixelDiffCountAboveThreshold(
        actualScreenshot: Bitmap,
        expectedScreenshot: Bitmap
    ): Boolean {
        var totalPixelsDiffCount = 0
        for (x in 0 until actualScreenshot.width) {
            for (y in 0 until actualScreenshot.height) {
                val actualPixel = actualScreenshot.getPixel(x, y)
                val expectedPixel = expectedScreenshot.getPixel(x, y)
                if ((Math.abs(Color.red(actualPixel) - Color.red(expectedPixel))
                            > MAX_PER_CHANNEL_DIFF) ||
                    (Math.abs(Color.green(actualPixel) - Color.green(expectedPixel))
                            > MAX_PER_CHANNEL_DIFF) ||
                    (Math.abs(Color.blue(actualPixel) - Color.blue(expectedPixel))
                            > MAX_PER_CHANNEL_DIFF) ||
                    (Math.abs(Color.alpha(actualPixel) - Color.alpha(expectedPixel))
                            > MAX_PER_CHANNEL_DIFF)) {
                    totalPixelsDiffCount++
                }
            }
        }
        val allowedDiffCount =
            actualScreenshot.width * actualScreenshot.height * SCREENSHOT_DIFF_PERCENT
        return totalPixelsDiffCount.toFloat() > allowedDiffCount
    }

    private fun getActualScreenshot(): Bitmap {
        val actualBitmap: Bitmap? = virtualDisplayRule.getScreenshot()
        assertNotNull(actualBitmap, "Screenshot is null.")
        return actualBitmap
    }

    private fun assertScreenshotPixelsEqual(actualScreenshot: Bitmap, expectedScreenshot: Bitmap) {
        val actualPixels = getBitmapPixels(actualScreenshot)
        val expectedPixels = getBitmapPixels(expectedScreenshot)
        if (!Arrays.equals(actualPixels, expectedPixels)) {
            saveBitmapToLosslessFile(actualScreenshot)
            fail("Screenshot mismatch.")
        }
    }

    private fun getBitmapPixels(bitmap: Bitmap): IntArray {
        val goldenPixels = IntArray(bitmap.getWidth() * bitmap.getHeight())
        bitmap.getPixels(
            goldenPixels,
            0,
            bitmap.getWidth(),
            0,
            0,
            bitmap.getWidth(),
            bitmap.getHeight()
        )
        return goldenPixels
    }

    private fun getGoldenImageBitmap(goldenImage: String): Bitmap {
        val assets: AssetManager = InstrumentationRegistry.getInstrumentation().targetContext.assets
        return BitmapFactory.decodeStream(assets.open(goldenImage))
    }

    private fun saveBitmapToLosslessFile(bitmap: Bitmap) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val actualScreenshot = File(dir, testName.methodName + "_actual.png")
        if (actualScreenshot.exists()) {
            Log.i(TAG, "Overwriting existing screenshot file: " + actualScreenshot.path)
            actualScreenshot.delete()
        }
        actualScreenshot.createNewFile()
        FileOutputStream(actualScreenshot).use { outputStream ->
            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                outputStream
            )
        }
        Log.d(TAG, "Actual screenshot saved to: $dir")
    }

    // We don't have a way to synchronously know when the requested pointer icon has been drawn
    // to the display, so wait some time (at least one display frame) for the icon to propagate.
    private fun waitForPointerIconUpdate() = Thread.sleep(100)

    companion object {
        const val TAG = "PointerIconTest"
        const val SCREENSHOT_DIFF_PERCENT = 0.01f // 1% total difference threshold
        const val MAX_PER_CHANNEL_DIFF = 0 // Default allow no difference per channel

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Iterable<Any> =
            listOf(PointerDevice.MOUSE, PointerDevice.DRAWING_TABLET)
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
            drawingTablet =
                UinputTouchDevice(
                    InstrumentationRegistry.getInstrumentation(),
                    display,
                    R.raw.test_drawing_tablet_register,
                    InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_STYLUS,
                    useDisplaySize = true,
                )
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
