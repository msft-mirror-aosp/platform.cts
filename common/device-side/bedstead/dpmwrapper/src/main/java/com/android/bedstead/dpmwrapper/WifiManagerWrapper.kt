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

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.ServiceManagerWrapper
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.stubbing.Answer

internal class WifiManagerWrapper : ServiceManagerWrapper<WifiManager?>() {
    companion object {
        private val TAG: String = WifiManagerWrapper::class.java.getSimpleName()

        private val sSpies = HashMap<Context?, WifiManager?>()
    }

    @SuppressLint("MissingPermission")
    override fun getWrapper(
        context: Context,
        manager: WifiManager?,
        answer: Answer<*>,
    ): WifiManager? {
        val userId = context.userId
        val maybeSpy: WifiManager? = sSpies.get(context)
        if (maybeSpy != null) {
            Log.d(TAG, "get(): returning cached spy for user $userId")
            return maybeSpy
        }

        val spy = Mockito.spy(manager)
        val spyString = "WifiManagerWrapper#" + System.identityHashCode(spy)
        Log.d(TAG, "get(): created spy for user " + context.userId + ": " + spyString)

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        try {
            Mockito.doReturn(spyString).`when`<WifiManager?>(spy).toString()

            // Used by WifiConfigCreator
            Mockito.doAnswer(answer).`when`<WifiManager?>(spy).addNetwork(any())

            Mockito.doAnswer(answer)
                .`when`<WifiManager?>(spy)
                .enableNetwork(ArgumentMatchers.anyInt(), ArgumentMatchers.anyBoolean())
            Mockito.doAnswer(answer)
                .`when`<WifiManager?>(spy)
                .removeNetwork(ArgumentMatchers.anyInt())
            Mockito.doAnswer(answer).`when`<WifiManager?>(spy).configuredNetworks
            Mockito.doAnswer(answer).`when`<WifiManager?>(spy).updateNetwork(any())

            Mockito.doAnswer(answer).`when`<WifiManager?>(spy).saveConfiguration()
            Mockito.doAnswer(answer).`when`<WifiManager?>(spy).isWifiEnabled
            Mockito.doAnswer(answer).`when`<WifiManager?>(spy).isWifiEnabled =
                ArgumentMatchers.anyBoolean()

            // Used by WifiNetworkConfigurationWithoutFineLocationPermissionTest
            Mockito.doAnswer(answer).`when`<WifiManager?>(spy).callerConfiguredNetworks
        } catch (e: Exception) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e)
        }

        sSpies.put(context, spy)
        Log.d(TAG, ("get(): returning new spy for context " + context + " and user " + userId))

        return spy
    }
}
