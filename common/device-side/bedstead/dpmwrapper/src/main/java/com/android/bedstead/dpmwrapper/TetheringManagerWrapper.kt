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
package com.android.bedstead.dpmwrapper

import android.content.Context
import android.net.TetheringManager
import android.util.Log
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.ServiceManagerWrapper
import java.util.concurrent.Executor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.stubbing.Answer

internal class TetheringManagerWrapper : ServiceManagerWrapper<TetheringManager?>() {
    companion object {
        private val TAG: String = TetheringManagerWrapper::class.java.getSimpleName()

        private val sSpies = HashMap<Context?, TetheringManager?>()
    }

    override fun getWrapper(
        context: Context,
        manager: TetheringManager?,
        answer: Answer<*>,
    ): TetheringManager? {
        val userId = context.userId
        var spy: TetheringManager? = sSpies.get(context)
        if (spy != null) {
            Log.d(TAG, "get(): returning cached spy for user $userId")
            return spy
        }

        spy = Mockito.spy<TetheringManager?>(manager)
        val spyString = "TetheringManagerWrapper#" + System.identityHashCode(spy)
        Log.d(TAG, "get(): created spy for user " + context.userId + ": " + spyString)

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        try {
            Mockito.doReturn(spyString).`when`<TetheringManager?>(spy).toString()

            // Used by TetheringTest
            Mockito.doAnswer(answer)
                .`when`<TetheringManager?>(spy)
                .startTethering(any<TetheringManager.TetheringRequest>(), any(), any())
            Mockito.doAnswer(answer)
                .`when`<TetheringManager?>(spy)
                .startTethering(
                    any<Int>(),
                    any<Executor>(),
                    any<TetheringManager.StartTetheringCallback>(),
                )
        } catch (e: Exception) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e)
        }

        sSpies.put(context, spy)
        Log.d(TAG, "get(): returning new spy for context $context and user $userId")

        return spy
    }
}
