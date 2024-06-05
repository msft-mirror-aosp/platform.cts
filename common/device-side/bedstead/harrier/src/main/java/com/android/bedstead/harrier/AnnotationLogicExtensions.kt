/*
 * Copyright (C) 2024 The Android Open Source Project
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
// receiver parameters are also used for limiting visibility of the function
@file:Suppress("UnusedReceiverParameter")

package com.android.bedstead.harrier

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn
import com.android.bedstead.harrier.annotations.EnsureUnlocked
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFactoryResetProtectionPolicySupported
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireLowRamDevice
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionSupported
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionUnsupported
import com.android.bedstead.harrier.annotations.RequireUsbDataSignalingCanBeDisabled
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.TestApis.devicePolicy
import org.hamcrest.CoreMatchers
import org.junit.Assume
import org.junit.Assume.assumeTrue

fun RequireResourcesBooleanValue.logic() {
    Assume.assumeThat(
        "resource with configName: $configName",
        TestApis.resources().system().getBoolean(configName),
        CoreMatchers.`is`(requiredValue)
    )
}

fun RequireFactoryResetProtectionPolicySupported.logic() {
    checkFailOrSkip(
        "Requires factory reset protection policy to be supported",
        devicePolicy().isFactoryResetProtectionPolicySupported(),
        FailureMode.FAIL
    )
}

fun RequireStorageEncryptionSupported.logic() {
    checkFailOrSkip(
        "Requires storage encryption to be supported.",
        devicePolicy().getStorageEncryptionStatus() !=
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED,
        FailureMode.SKIP
    )
}

fun RequireStorageEncryptionUnsupported.logic() {
    checkFailOrSkip(
        "Requires storage encryption to not be supported.",
        devicePolicy().getStorageEncryptionStatus() ==
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED,
        FailureMode.SKIP
    )
}

fun RequireFeature.logic() {
    checkFailOrSkip(
        "Device must have feature $value",
        TestApis.packages().features().contains(value),
        failureMode
    )
}

fun RequireDoesNotHaveFeature.logic() {
    checkFailOrSkip(
        "Device must not have feature $value",
        !TestApis.packages().features().contains(value),
        failureMode
    )
}

fun RequireLowRamDevice.logic() {
    checkFailOrSkip(
        reason,
        TestApis.context().instrumentedContext()
                .getSystemService(ActivityManager::class.java)!!
                .isLowRamDevice,
        failureMode
    )
}

fun RequireNotLowRamDevice.logic() {
    checkFailOrSkip(
        reason,
        !TestApis.context().instrumentedContext()
                .getSystemService(ActivityManager::class.java)!!
                .isLowRamDevice,
        failureMode
    )
}

fun EnsureScreenIsOn.logic() {
    TestApis.device().wakeUp()
}

fun EnsureUnlocked.logic() {
    TestApis.device().unlock()
}

fun RequireUsbDataSignalingCanBeDisabled.logic() {
    assumeTrue(
        "device must be able to control usb data signaling",
        devicePolicy().canUsbDataSignalingBeDisabled()
    )
}
