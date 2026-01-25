/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.virtualdevice.cts.computercontrol

import android.content.IntentSender
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.extensions.computercontrol.ComputerControlSession
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeNotNull

class TestAppAgentLauncher {
    private val context = InstrumentationRegistry.getInstrumentation().context

    fun launch(sessionName: String, packageName: String, className: String? = null): TestAppAgent {
        Log.d(TAG, "Requesting ComputerControlSession")
        val session = requestComputerControlSession(sessionName, listOf(packageName))
        assertNotNull(session)
        val testAppAgent = TestAppAgent(context, session!!, packageName, className)
        return testAppAgent
    }

    fun requestComputerControlSession(
        sessionName: String,
        packageNames: List<String>,
        onClose: (() -> Unit)? = null,
    ): ComputerControlSession? {
        val future = CompletableFuture<ComputerControlSession?>()
        val extension = ComputerControlExtensions.getInstance(context)
        assumeNotNull(extension)
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName(sessionName)
                .setTargetPackageNames(packageNames)
                .build()
        extension!!.requestSession(
            params,
            Executors.newSingleThreadExecutor(),
            object : ComputerControlSession.Callback {
                override fun onSessionPending(intentSender: IntentSender) {
                    Log.d(TAG, "Session Pending, starting IntentSender")
                }

                override fun onSessionCreated(session: ComputerControlSession) {
                    Log.d(TAG, "Session Created")
                    future.complete(session)
                }

                override fun onSessionCreationFailed(errorCode: Int) {
                    Log.d(TAG, "Session Creation Failed, errorCode: $errorCode")
                    future.complete(null)
                }

                override fun onSessionClosed() {
                    Log.d(TAG, "Session Closed")
                    onClose?.invoke()
                    future.complete(null)
                }
            },
        )
        return future.get(TestAppAgent.SESSION_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val TAG = "TestAppAgentLauncher"
    }
}
