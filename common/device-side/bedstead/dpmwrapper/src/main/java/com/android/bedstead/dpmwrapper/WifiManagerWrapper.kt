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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.stubbing.Answer

internal class WifiManagerWrapper : ServiceManagerWrapper<WifiManager>() {
    companion object {
        private val TAG: String = WifiManagerWrapper::class.java.getSimpleName()

        private val sSpies = HashMap<Context?, WifiManager?>()
    }

    // Suppressing deprecation warnings since we are setting stub responses on deprecated methods.
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun getWrapper(
        context: Context,
        manager: WifiManager,
        answer: Answer<*>,
    ): WifiManager {
        val cachedSpy: WifiManager? = sSpies.get(context)
        if (cachedSpy != null) {
            Log.d(TAG, "get(): returning cached spy for user ${context.userId}")
            return cachedSpy
        }

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        val wifiManagerSpy =
            spy(manager) {
                // Used by WifiConfigCreator
                on { addNetwork(anyOrNull()) } doAnswer answer
                on { enableNetwork(any(), any()) } doAnswer answer
                on { removeNetwork(any()) } doAnswer answer
                on { configuredNetworks } doAnswer answer
                on { updateNetwork(anyOrNull()) } doAnswer answer
                on { saveConfiguration() } doAnswer answer
                on { isWifiEnabled } doAnswer answer
                on { setWifiEnabled(any()) } doAnswer answer

                // Used by WifiNetworkConfigurationWithoutFineLocationPermissionTest
                on { callerConfiguredNetworks } doAnswer answer
            }

        val identificationString = "WifiManagerWrapper#${System.identityHashCode(wifiManagerSpy)}"
        wifiManagerSpy.stub { on { toString() } doReturn identificationString }
        Log.d(TAG, "get(): created spy for user ${context.userId}: $identificationString")

        sSpies.put(context, wifiManagerSpy)
        Log.d(TAG, "get(): returning new spy for context $context and user ${context.userId}")

        return wifiManagerSpy
    }
}
