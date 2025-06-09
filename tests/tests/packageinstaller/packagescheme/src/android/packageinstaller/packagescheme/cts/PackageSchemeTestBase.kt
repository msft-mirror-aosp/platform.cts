/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.packageinstaller.packagescheme.cts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern
import org.junit.After
import org.junit.Before

open class PackageSchemeTestBase {
    val LOG_TAG = PackageSchemeTestBase::class.java.simpleName
    val BASE_PATH = "/data/local/tmp/cts/packagescheme/"
    val TARGET_APP_PKG_NAME = "android.packageinstaller.emptytestapp.cts"
    val TARGET_APP_APK = BASE_PATH + "CtsEmptyTestApp.apk"
    val DEFAULT_TIMEOUT = 5000L

    var mScenario: ActivityScenario<TestActivity>? = null
    val mInstrumentation = InstrumentationRegistry.getInstrumentation()
    val mUiDevice = UiDevice.getInstance(mInstrumentation)
    var mButton: UiObject2? = null
    val mContext: Context = mInstrumentation.context
    var mPackageInstallerPackageName: String? = null

    class TestActivity : Activity() {
        val mLatch: CountDownLatch = CountDownLatch(1)
        var mResultCode = RESULT_OK

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val appInstallIntent: Intent? = intent.getExtra(Intent.EXTRA_INTENT) as Intent?
            startActivityForResult(appInstallIntent, 1)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            mResultCode = resultCode
            mLatch.countDown()
        }
    }

    @Before
    @After
    fun uninstall() {
        SystemUtil.runShellCommand("pm uninstall $TARGET_APP_PKG_NAME")
    }

    fun runTest(packageName: String, packageHasVisibility: Boolean, needTargetApp: Boolean) {
        SystemUtil.runShellCommand(
            "appops set $packageName android:request_install_packages allow"
        )

        if (packageHasVisibility) {
            SystemUtil.runShellCommand("appops set $packageName android:query_all_packages allow")
        }

        if (needTargetApp) {
            installTargetApp()
        }

        val intent = Intent(ApplicationProvider.getApplicationContext(), TestActivity::class.java)
        intent.putExtra(Intent.EXTRA_INTENT, getAppInstallIntent())

        var latch: CountDownLatch? = null
        mScenario = ActivityScenario.launch(intent)
        mScenario!!.onActivity {
            var button: UiObject2?
            val btnName: String
            // Need to scroll the screen to get to the buttons on some form factors
            // (e.g. on a watch).
            val scrollable = UiScrollable(UiSelector().scrollable(true))
            if (scrollable.exists()) {
                scrollable.flingToEnd(10)
            }
            if (packageHasVisibility && needTargetApp) {
                val buttonSelector = By.text(
                    Pattern.compile(
                        "Cancel",
                        Pattern.CASE_INSENSITIVE
                    )
                )
                button = mUiDevice.wait(
                    Until.findObject(getBySelector(buttonSelector)), DEFAULT_TIMEOUT)
                btnName = "Cancel"
            } else {
                val buttonSelector = By.text(
                    Pattern.compile(
                        "OK|Close",
                        Pattern.CASE_INSENSITIVE
                    )
                )
                button = mUiDevice.wait(
                    Until.findObject(getBySelector(buttonSelector)), DEFAULT_TIMEOUT)
                btnName = "OK or Close"
            }
            assertWithMessage("$btnName not found").that(button).isNotNull()
            button?.click()
            latch = it.mLatch
        }
        latch!!.await()
        mScenario!!.onActivity {
            val resultCode: Int = it.mResultCode
            if (packageHasVisibility && needTargetApp) {
                assertThat(resultCode).isNotEqualTo(Activity.RESULT_FIRST_USER)
            } else {
                assertThat(resultCode).isEqualTo(Activity.RESULT_FIRST_USER)
            }
        }
        mScenario!!.close()
    }

    private fun installTargetApp() {
        assertThat(
            SystemUtil.runShellCommand("pm install $TARGET_APP_APK").trim()
        ).isEqualTo("Success")
    }

    private fun getAppInstallIntent(): Intent {
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(Uri.parse("package:$TARGET_APP_PKG_NAME"))
    }

    private fun getBySelector(selector: BySelector): BySelector {
        return selector.pkg(getPackageInstallerPackageName()!!)
    }

    private fun getPackageInstallerPackageName(): String? {
        if (mPackageInstallerPackageName != null) {
            return mPackageInstallerPackageName
        }
        val intent = Intent(
            Intent.ACTION_INSTALL_PACKAGE
        ).setData(Uri.parse("content:"))
        val ri = mContext.packageManager.resolveActivity(intent, /* flags= */0)
        mPackageInstallerPackageName = ri?.activityInfo?.packageName
        Log.d(
            LOG_TAG,
            "sPackageInstallerPackageName = %mPackageInstallerPackageName"
        )
        return mPackageInstallerPackageName
    }
}
