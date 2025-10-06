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

package android.app.appops.cts.appopssdksandboxloader

import android.app.Activity
import android.app.sdksandbox.LoadSdkException
import android.app.sdksandbox.SandboxedSdk
import android.app.sdksandbox.SdkSandboxManager
import android.os.Bundle
import android.os.OutcomeReceiver
import android.util.Log
import com.android.sandboxsdkappopuser.ISandboxSdkAppOpUser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LoadSdkSandboxActivity : Activity() {
    val intentExtra = "extra_op"
    val SDK_NAME_1 = "com.android.sandboxsdkappopuser"

    override fun onStart() {
        super.onStart()
        val sdkSandboxManager = getSystemService(SdkSandboxManager::class.java)!!
        val latch = CountDownLatch(1)
        sdkSandboxManager.loadSdk(
            SDK_NAME_1,
            Bundle(),
            Runnable::run,
            object : OutcomeReceiver<SandboxedSdk, LoadSdkException> {
                override fun onResult(result: SandboxedSdk?) {
                    val op = intent.getStringExtra(intentExtra)!!
                    ISandboxSdkAppOpUser.Stub.asInterface(result?.`interface`)
                        .noteProxyOpWithRootAsProxied(op, "attribution_tag")
                    latch.countDown()
                }

                override fun onError(error: LoadSdkException) {
                    Log.e(
                        LoadSdkSandboxActivity::class.simpleName,
                        "got error loading SDK ${error.message}"
                    )
                    super.onError(error)
                }
        }
        )
        latch.await(5, TimeUnit.SECONDS)
        finish()
    }
}
