package android.companion.cts.core

import android.Manifest
import android.annotation.CallSuper
import android.companion.CompanionDeviceManager
import android.companion.cts.common.AppHelper
import android.companion.cts.common.TestBase
import kotlin.test.assertTrue

open class CoreTestBase : TestBase() {
    var nearbyPerms = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )

    protected val testApp = AppHelper(
        instrumentation,
        userId,
        TEST_APP_PACKAGE_NAME,
        TEST_APP_APK_PATH
    )

    @CallSuper
    override fun setUp() {
        super.setUp()

        // Make sure test app is installed.
        with(testApp) {
            if (!isInstalled()) install()
            assertTrue("Test app $packageName is not installed") { isInstalled() }
        }
    }

    protected val NO_OP_LISTENER: CompanionDeviceManager.OnAssociationsChangedListener =
        CompanionDeviceManager.OnAssociationsChangedListener { }

    protected val NO_OP_CALLBACK: CompanionDeviceManager.Callback =
        object : CompanionDeviceManager.Callback() {
            override fun onFailure(error: CharSequence?) = Unit
        }
}
