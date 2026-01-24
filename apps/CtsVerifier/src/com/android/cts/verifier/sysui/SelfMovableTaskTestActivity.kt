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

import android.Manifest
import android.app.ActivityOptions
import android.app.InfeasibleActivityOptionsException
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.android.cts.verifier.PassFailButtons
import com.android.cts.verifier.R

/**
 * This activity is to test the following CDD requirement (3.8.14/C-1-5):
 *
 *     If device implementations have the capability to display multiple activities at the same
 *     time, they MUST show tasks with |selfMovable| property (...) with a distinguishable
 *     persistent decoration (e.g. caption bar), and a method to close such tasks out of their
 *     persistent decorations.
 *
 * The test scenario is as follows:
 *
 *  1. If the CtsVerifier app does not hold the REPOSITION_SELF_WINDOWS permission, ask the tester
 *     to set the CtsVerifier app as the system's default browser to obtain the permission needed
 *     later on and prompt them to soft-restart the test (via in-test navigation). Otherwise,
 *     proceed further.
 *  2. Launch a task guaranteed to have the |selfMovable| property. If this is not possible, mark
 *     the test as passed and finish the test. Otherwise, proceed further.
 *  3. Ask the tester whether the new task has a persistent decoration that's clearly discernible
 *     from the app-controlled content. If the answer is no, ask the tester to fail the test.
 *     Otherwise, proceed further.
 *  4. After the tester recognized the persistent decoration, ask them to close the task without
 *     interacting with said persistent decoration and to mark the result using Pass/Fail buttons.
 *
 */
class SelfMovableTaskTestActivity : PassFailButtons.Activity() {

    private lateinit var titleTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var ackButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sysui_self_movable_test_activity)
        setPassFailButtonClickListeners()
        passButton.isEnabled = false

        titleTextView = findViewById(R.id.text_primary)
        messageTextView = findViewById(R.id.text_secondary)
        ackButton = findViewById(R.id.button_acknowledge)

        // Start the test scenario.
        verifyPermissions()
    }

    private fun verifyPermissions() {
        if (checkSelfPermission(Manifest.permission.REPOSITION_SELF_WINDOWS)
                != PackageManager.PERMISSION_GRANTED) {
            setTexts(
                R.string.sysui_smt_permission_prompt_title,
                R.string.sysui_smt_permission_prompt_content
            )
            ackButton.text = getString(R.string.sysui_smt_retry_button_text)
            ackButton.setOnClickListener { verifyPermissions() }

            // Softlock -- the tester should grant permissions and acknowledge the message.
            return
        }

        // Pass through to the next step.
        acknowledgeStep_launchMovableTaskOrPassTest()
    }

    private fun acknowledgeStep_launchMovableTaskOrPassTest() {
        setTexts(
            R.string.sysui_smt_launch_movable_task_info_dialog_title,
            R.string.sysui_smt_launch_movable_task_info_dialog_content
        )
        ackButton.text = getString(R.string.sysui_smt_acknowledge_button_text)

        // Softlock -- the tester should acknowledge the message.
        ackButton.setOnClickListener { launchMovableTaskOrPassTest() }
    }

    private fun launchMovableTaskOrPassTest() {
        val intent = Intent(this, SampleActivity::class.java).apply {
            component = ComponentName(this@SelfMovableTaskTestActivity, SampleActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }

        val activityOptions = ActivityOptions.makeBasic().apply {
            launchWindowingMode = WINDOWING_MODE_FREEFORM
            setMovableTaskRequired(true)
        }

        try {
            startActivity(intent, activityOptions.toBundle())
        } catch (e: InfeasibleActivityOptionsException) {
            setTexts(
                R.string.sysui_smt_launch_movable_task_fail_info_dialog_title,
                R.string.sysui_smt_launch_movable_task_fail_info_dialog_content
            )
            ackButton.setOnClickListener { setTestResultAndFinish(true) }

            // Softlock -- the tester should acknowledge the message and this will finish the test.
            return
        }

        // Pass through to the next step.
        askHumanAboutExistenceOfPersistentDecoration()
    }

    private fun askHumanAboutExistenceOfPersistentDecoration() {
        setTexts(
            R.string.sysui_smt_decoration_visibility_info_dialog_title,
            R.string.sysui_smt_decoration_visibility_info_dialog_content
        )
        ackButton.text = getString(R.string.sysui_smt_proceed_button_text)

        // Softlock -- the tester should acknowledge the message.
        ackButton.setOnClickListener { askHumanToCloseTestTask() }
    }

    private fun askHumanToCloseTestTask() {
        setTexts(
            R.string.sysui_smt_close_task_info_dialog_title,
            R.string.sysui_smt_close_task_info_dialog_content
        )
        ackButton.visibility = View.GONE
        passButton.isEnabled = true

        // Softlock -- the tester must either mark the test as passed or failed.
    }

    private fun setTexts(titleStringId: Int, messageStringId: Int) {
        titleTextView.text = getString(titleStringId)
        messageTextView.text = getString(messageStringId)
    }
}
