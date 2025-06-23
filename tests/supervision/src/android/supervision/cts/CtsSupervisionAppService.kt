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

package android.supervision.cts

import android.app.supervision.SupervisionAppService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** SupervisionAppService used for CTS tests */
class CtsSupervisionAppService : SupervisionAppService() {
    override fun onSupervisionEnabled() {
        serviceReporter.reportMethodCalled(::onSupervisionEnabled.name)
    }

    override fun onSupervisionDisabled() {
        serviceReporter.reportMethodCalled(::onSupervisionDisabled.name)
    }
}

/** Reports on the method calls of the CtsSupervisionAppService. */
class ServiceReporter {
    private val channel = Channel<String>()

    fun reportMethodCalled(methodName: String) {
        runBlocking {
            channel.send(methodName)
        }
    }

    fun wasOnSupervisionEnabledCalled(): Boolean =
        wasMethodCalled(CtsSupervisionAppService::onSupervisionEnabled.name)

    fun wasOnSupervisionDisabledCalled(): Boolean =
        wasMethodCalled(CtsSupervisionAppService::onSupervisionDisabled.name)

    private fun wasMethodCalled(expectedMethodName: String): Boolean {
        val actualMethodName = runBlocking {
            withTimeout(TIMEOUT) {
                channel.receive()
            }
        }
        return expectedMethodName == actualMethodName
    }

    companion object {
        const val TIMEOUT = 5000L
    }
}

private val serviceReporter = ServiceReporter()

fun bindSupervisionAppService(action: (reporter: ServiceReporter) -> Unit) {
    val context = TestApis.context().instrumentedContext()
    val (connection, binder) = runBlocking {
        withTimeout(ServiceReporter.TIMEOUT) {
            bindService(context)
        }
    }
    assertThat(binder).isNotNull()
    action(serviceReporter)
    context.unbindService(connection)
}

private suspend fun bindService(context: Context): Pair<ServiceConnection, IBinder?> =
    suspendCancellableCoroutine { continuation ->
        val intent = Intent().apply {
            setPackage(context.packageName)
            setClassName(context.packageName, CtsSupervisionAppService::class.java.name)
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                continuation.resume(Pair(this, service)) { _, _, _, -> }
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
