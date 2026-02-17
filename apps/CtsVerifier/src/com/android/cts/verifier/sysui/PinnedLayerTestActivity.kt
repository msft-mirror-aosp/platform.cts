/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.cts.verifier.sysui

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.android.cts.verifier.PassFailButtons
import com.android.cts.verifier.R

/**
 * Test that verifies UX security enforcements for [ActivityManager.AppTask.WINDOWING_LAYER_PINNED].
 *
 * The requirement enforces pinned windows to provide UI affordances:
 * 1. to be able to close the window.
 * 2. to be disabled pinned windows per an app instance.
 *
 * The test is split in several steps:
 * 1. The test verifies that the app hold necessary permissions. An app should have
 *    android.permission.USE_PINNED_WINDOWING_LAYER at install time and
 *    [AppOpsManager.OPSTR_PICTURE_IN_PICTURE].
 * 2. Verify there's a UI to close the pinned window.
 * 3. Verify there's a UI to get navigated to a place that allows to disable pinned windows per app.
 *
 * The test is considered passed if the tester can go through all 3 scenarios. In case a PiP
 * permission is missing the test is asked to enable it for the app.
 *
 * Some devices might not support an API and such devices should pass the test. For that we show a
 * [android.widget.Toast] to the tester when API denies the call. Additionally test steps
 * highlight to the tester to test API in different windowing modes and if it still fails then
 * the API is considered unsupported, therefore, they should mark it as passed.
 *
 * @see ActivityManager.AppTask.requestWindowingLayer
 */
class PinnedLayerTestActivity : PassFailButtons.Activity() {

    private lateinit var textPrimary: TextView
    private lateinit var textSecondary: TextView
    private lateinit var ackBtn: Button

    private val appOpsManager by lazy { getSystemService(AppOpsManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sysui_pinned_layer_test)
        setPassFailButtonClickListeners()
        setInfoResources(R.string.pinned_layer_test_title, R.string.pinned_layer_test_info, -1)

        textPrimary = findViewById(R.id.text_primary)
        textSecondary = findViewById(R.id.text_secondary)
        ackBtn = findViewById(R.id.button_acknowledge)

        checkPermissions()
    }

    private fun checkPermissions() {
        val hasPinnedPermission =
            checkSelfPermission("android.permission.USE_PINNED_WINDOWING_LAYER") ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPinnedPermission) {
            setStepDescription(
                R.string.pinned_layer_perm_fail_title,
                R.string.pinned_layer_perm_fail_desc,
                R.string.pinned_layer_acknowledge_button,
            )
            ackBtn.setOnClickListener { finish() }
            return
        }

        val mode =
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                packageName,
            )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            setStepDescription(
                R.string.pinned_layer_pip_missing_title,
                R.string.pinned_layer_pip_missing_desc,
                R.string.pinned_layer_acknowledge_button,
            )
            ackBtn.setOnClickListener { checkPermissions() }
            return
        }

        showCloseActionStep()
    }

    private fun showCloseActionStep() {
        setStepDescription(
            R.string.pinned_layer_close_affordance_title,
            R.string.pinned_layer_close_affordance_desc,
            R.string.pinned_layer_launch_sample_button,
        )
        ackBtn.setOnClickListener {
            launchPinnedTask()

            ackBtn.setText(R.string.pinned_layer_acknowledge_button)
            ackBtn.setOnClickListener { showDisableActionStep() }
        }
    }

    private fun showDisableActionStep() {
        setStepDescription(
            R.string.pinned_layer_disable_affordance_title,
            R.string.pinned_layer_disable_affordance_desc,
            R.string.pinned_layer_launch_sample_button,
        )
        ackBtn.setOnClickListener {
            launchPinnedTask()

            ackBtn.setText(R.string.pinned_layer_acknowledge_button)
            ackBtn.isVisible = false
        }
    }

    private fun launchPinnedTask() {
        val intent =
            Intent(this, PinnedSampleActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivity(intent)
    }

    private fun setStepDescription(
        @StringRes title: Int,
        @StringRes body: Int,
        @StringRes acknowledgeTitle: Int,
    ) {
        textPrimary.setText(title)
        textSecondary.setText(body)
        ackBtn.setText(acknowledgeTitle)
    }
}
