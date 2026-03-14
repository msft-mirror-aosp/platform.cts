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

package android.media.audio.cts.audiopermissiontests

import android.content.Context
import android.provider.Settings.Global.getString
import android.provider.Settings.Global.putString
import android.util.Log

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity

import org.junit.rules.ExternalResource

/**
 * Rule for setting Settings {@code key} to {@code value}, returning to default at the end of the
 * statement. To be used as a class-rule, aborts on failure in teardown and populates an exception
 * variable which must be checked on setup.
 */
class SettingsRule(val key: String, val value: String) : ExternalResource() {

    private val mContext = InstrumentationRegistry.getInstrumentation().getContext()

    private var mSetupException : Throwable? = null

    private var mOldVal : String? = null

    fun checkSetup() = mSetupException?.let { throw it }

    override fun before() {
        try {
            Log.i("SettingsRule",  "setting $key to $value");
            runWithShellPermissionIdentity {
                mOldVal = getString(mContext.getContentResolver(), key)
                putString(mContext.getContentResolver(), key, value)
            }
        } catch (e: Throwable) {
            mSetupException = e
        }
    }

    override fun after() {
        try {
            runWithShellPermissionIdentity {
                putString(mContext.getContentResolver(), key, mOldVal)
            }
        } catch (e: Throwable) {
            Log.wtf("SettingsRule", "Unhandleable exception in teardown", e);
        }
    }
}
