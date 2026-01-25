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

package android.computercontrol.testapp2

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = View(this)
        setContentView(view)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        view.setOnClickListener {
            val intent = Intent(this, SecureContentActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d(TAG, "Clicked, launching SecureContentActivity.")
            startActivity(intent)
        }
        view.setOnLongClickListener {
            val intent = packageManager.getLaunchIntentForPackage("android.computercontrol.testapp")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Log.d(TAG, "Long clicked, launching testapp.")
                startActivity(intent)
            } else {
                Log.w(TAG, "Could not find launch intent for testapp")
            }
            true
        }
    }

    companion object {
        private const val TAG = "ComputerControlTestApp2.MainActivity"
    }
}
