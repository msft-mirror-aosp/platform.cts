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

package android.bluetooth.cts

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.CountDownLatch

private const val TAG = "BluetoothScanReceiver"

class BluetoothScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scanResults =
            intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                ScanResult::class.java,
            )
        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        Log.i(
            TAG,
            "Received scan results:$intent\n" +
                "ScanResults = $scanResults\n" +
                "Callback Type = $callbackType\n" +
                "Error Code = $errorCode",
        )
        countDownLatch?.let {
            it.countDown()
            countDownLatch = null
        }
    }

    companion object {
        private var countDownLatch: CountDownLatch? = null

        @JvmStatic fun createCountDownLatch() = CountDownLatch(1).also { countDownLatch = it }
    }
}
