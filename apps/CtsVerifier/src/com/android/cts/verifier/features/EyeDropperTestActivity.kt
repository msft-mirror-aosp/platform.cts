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

package com.android.cts.verifier.features

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.android.cts.verifier.PassFailButtons
import com.android.cts.verifier.R

class EyeDropperTestActivity : PassFailButtons.Activity() {

    private lateinit var selectedColorView: View
    private lateinit var selectedColorHex: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.eyedropper_test)
        setPassFailButtonClickListeners()

        selectedColorView = findViewById(R.id.selected_color_view)
        selectedColorHex = findViewById(R.id.selected_color_hex)

        findViewById<Button>(R.id.launch_eyedropper_button).setOnClickListener {
            launchEyeDropper()
        }
    }

    private fun launchEyeDropper() {
        val intent = Intent(Intent.ACTION_OPEN_EYE_DROPPER)
        startActivityForResult(intent, REQUEST_CODE_EYE_DROPPER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_EYE_DROPPER && resultCode == RESULT_OK) {
            val color = data?.getIntExtra(Intent.EXTRA_COLOR, Color.BLACK) ?: Color.BLACK
            selectedColorView.setBackgroundColor(color)
            selectedColorHex.text = String.format("#%08X", color)
        }
    }

    companion object {
        private const val REQUEST_CODE_EYE_DROPPER = 1
    }
}
