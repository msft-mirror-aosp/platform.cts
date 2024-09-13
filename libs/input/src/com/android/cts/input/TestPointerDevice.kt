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

package com.android.cts.input

import android.Manifest.permission.CREATE_VIRTUAL_DEVICE
import android.Manifest.permission.INJECT_EVENTS
import android.companion.AssociationInfo
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceManager.VirtualDevice
import android.companion.virtual.VirtualDeviceParams
import android.content.Context
import android.graphics.Point
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualMouseRelativeEvent
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity

enum class TestPointerDevice {

    MOUSE {
        private lateinit var virtualDevice: VirtualDevice
        private lateinit var virtualMouse: VirtualMouse

        override fun setUp(
            context: Context,
            display: Display,
            associationInfo: AssociationInfo
        ) {
            val virtualDeviceManager =
                context.getSystemService(VirtualDeviceManager::class.java)!!
            runWithShellPermissionIdentity({
                virtualDevice =
                    virtualDeviceManager.createVirtualDevice(associationInfo.id,
                        VirtualDeviceParams.Builder().build())
                virtualMouse =
                    virtualDevice.createVirtualMouse(
                        VirtualMouseConfig.Builder()
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
            associationInfo: AssociationInfo,
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
                physicalLocation = pointer,
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
        associationInfo: AssociationInfo,
    )

    abstract fun hoverMove(dx: Int, dy: Int)
    abstract fun tearDown()

    companion object {
        const val TEST_VENDOR_ID = 0x18d1
        const val TEST_PRODUCT_ID = 0xabcd
    }
}
