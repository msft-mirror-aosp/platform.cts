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
import android.os.UserManager
import android.util.Log
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.ServiceManagerWrapper
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.stubbing.Answer

internal class UserManagerWrapper : ServiceManagerWrapper<UserManager?>() {
    companion object {
        private val TAG: String = UserManagerWrapper::class.java.getSimpleName()

        private val sSpies = HashMap<Context?, UserManager?>()
    }

    override fun getWrapper(
        context: Context,
        manager: UserManager?,
        answer: Answer<*>?,
    ): UserManager? {
        val userId = context.userId
        var spy: UserManager? = sSpies.get(context)
        if (spy != null) {
            Log.d(TAG, "get(): returning cached spy for user $userId")
            return spy
        }

        spy = Mockito.spy<UserManager?>(manager)
        val spyString = "UserManagerWrapper#" + System.identityHashCode(spy)
        Log.d(TAG, "get(): created spy for user " + context.userId + ": " + spyString)

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        try {
            Mockito.doReturn(spyString).`when`<UserManager?>(spy).toString()

            // Used by HardwarePropertiesManagerTest
            Mockito.doAnswer(answer)
                .`when`<UserManager?>(spy)
                .getApplicationRestrictions(ArgumentMatchers.any<String?>())
        } catch (e: Exception) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e)
        }

        sSpies.put(context, spy)
        Log.d(TAG, "get(): returning new spy for context $context and user $userId")

        return spy
    }
}
