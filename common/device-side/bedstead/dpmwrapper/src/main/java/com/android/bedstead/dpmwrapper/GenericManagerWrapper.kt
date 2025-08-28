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
import android.util.Log
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.ServiceManagerWrapper
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.stubbing.Answer

internal class GenericManagerWrapper : ServiceManagerWrapper<GenericManager>() {
    companion object {
        private val TAG: String = GenericManagerWrapper::class.java.getSimpleName()

        private val sMocks = HashMap<Context?, GenericManager?>()
    }

    override fun getWrapper(
        context: Context,
        manager: GenericManager,
        answer: Answer<*>,
    ): GenericManager {
        val userId = context.userId
        val cachedMock: GenericManager? = sMocks.get(context)
        if (cachedMock != null) {
            Log.d(TAG, "get(): returning cached mock for user $userId")
            return cachedMock
        }

        // TODO(b/176993670): given that GenericManager is an interface, we could dynamically mock
        // all methods (for example, using Java's DynamicProxy), but given that DpmWrapper will
        // eventually go away, it's not worth the effort
        val genericManagerMock =
            mock<GenericManager> { on { getSecureIntSettings(anyOrNull()) } doAnswer answer }

        val identificationString =
            "GenericManagerWrapper#${System.identityHashCode(genericManagerMock)}"
        genericManagerMock.stub { on { toString() } doReturn identificationString }
        Log.d(TAG, "get(): created mock for user ${context.userId}: $identificationString")

        sMocks.put(context, genericManagerMock)
        Log.d(TAG, "get(): returning new mock for context $context and user $userId")

        return genericManagerMock
    }
}
