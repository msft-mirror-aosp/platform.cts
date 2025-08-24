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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.Companion.resultCodeToString
import java.lang.reflect.Method
import java.util.concurrent.Callable

/** Helper class used by the device owner apps. */
class DeviceOwnerHelper private constructor() {
    init {
        throw UnsupportedOperationException("contains only static methods")
    }

    companion object {
        private val TAG: String = DeviceOwnerHelper::class.java.getSimpleName()

        /**
         * Executes a method requested by the test app.
         *
         * Typical usage:
         * <pre>`
         * @Override
         * public void onReceive(Context context, Intent intent) {
         * if (DeviceOwnerAdminReceiverHelper.runManagerMethod(this, context, intent)) return;
         * super.onReceive(context, intent);
         * }
         * `</pre> *
         *
         * @return whether the `intent` represented a method that was executed.
         */
        @JvmStatic
        fun runManagerMethod(
            receiver: BroadcastReceiver,
            context: Context,
            intent: Intent,
        ): Boolean {
            val action = intent.action
            Log.d(TAG, "runManagerMethod(): user=" + context.userId + ", action=" + action)

            if (action != Utils.ACTION_WRAPPED_MANAGER_CALL) {
                if (Utils.VERBOSE) {
                    Log.v(TAG, "ignoring, it's not " + Utils.ACTION_WRAPPED_MANAGER_CALL)
                }
                return false
            }

            try {
                val className = intent.getStringExtra(Utils.EXTRA_CLASS)
                val methodName = intent.getStringExtra(Utils.EXTRA_METHOD)
                val numberArgs = intent.getIntExtra(Utils.EXTRA_NUMBER_ARGS, 0)
                Log.d(
                    TAG,
                    ("runManagerMethod(): userId=" +
                        context.userId +
                        ", intent=" +
                        intent.action +
                        ", class=" +
                        className +
                        ", methodName=" +
                        methodName +
                        ", numberArgs=" +
                        numberArgs),
                )
                val args: Array<Any?>
                var parameterTypes: Array<Class<*>?>
                if (numberArgs > 0) {
                    args = arrayOfNulls(numberArgs)
                    parameterTypes = arrayOfNulls(numberArgs)
                    val extras = intent.extras!!
                    for (i in 0..<numberArgs) {
                        DataFormatter.getArg(extras, args, parameterTypes, i)
                    }
                    Log.d(
                        TAG,
                        ("converted args: " +
                            args.contentToString() +
                            " (with types " +
                            parameterTypes.contentToString() +
                            ")"),
                    )
                } else {
                    args = arrayOfNulls(size = 0)
                    parameterTypes = arrayOfNulls(size = 0)
                }
                val managerClass = Class.forName(className)
                val method: Method? = findMethod(managerClass, methodName!!, parameterTypes)
                if (method == null) {
                    sendError(
                        receiver,
                        IllegalArgumentException(
                            "Could not find method $methodName using reflection"
                        ),
                    )
                    return true
                }
                val manager =
                    if (managerClass == GenericManager::class.java) {
                        GenericManagerImpl(context)
                    } else {
                        context.getSystemService(managerClass)
                    }
                // Must handle in a separate thread as some APIs will fail when called from main's
                val result =
                    Utils.callOnHandlerThread<Any?>(Callable { method.invoke(manager, *args) })

                if (Utils.VERBOSE) {
                    // Some results - like network logging events - are quite large
                    Log.v(TAG, "runManagerMethod(): method returned $result")
                } else {
                    Log.v(TAG, "runManagerMethod(): method returned fine")
                }
                sendResult(receiver, result)
            } catch (e: Exception) {
                sendError(receiver, e)
            }

            return true
        }

        /**
         * Called by the device owner [DeviceAdminReceiver] to broadcasts an intent to the receivers
         * in the test case app.
         *
         * It must be used in place of standard APIs (such as
         * `LocalBroadcastManager.sendBroadcast()`) because on headless system user mode the test
         * app might be running in a different user (and this method will take care of IPC'ing the
         * intent over).
         */
        @JvmStatic
        fun sendBroadcastToTestAppReceivers(context: Context, intent: Intent) {
            if (forwardBroadcastToTestApp(context, intent)) return

            Log.d(TAG, ("Broadcasting " + intent.action + " locally on user " + context.userId))
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        /**
         * Forwards the intent to the test app.
         *
         * This method is needed in cases where the received of DPM callback must to some
         * processing; it should try to forward it first, as if it's running on headless system
         * user, the processing should be tone on the test user side.
         *
         * @return when `true`, the intent was forwarded and should not be processed locally.
         */
        @JvmStatic
        fun forwardBroadcastToTestApp(context: Context?, intent: Intent?): Boolean {
            if (!Utils.isHeadlessSystemUser) return false

            TestAppCallbacksReceiver.sendBroadcast(context, intent)
            return true
        }

        @Throws(NoSuchMethodException::class)
        private fun findMethod(
            clazz: Class<*>,
            methodName: String,
            parameterTypes: Array<Class<*>?>,
        ): Method? {
            // Handle some special cases first...

            // Methods that use CharSequence instead of String

            if (parameterTypes.size == 2) {
                when (methodName) {
                    "wipeData" ->
                        return clazz.getDeclaredMethod(
                            methodName,
                            *arrayOf(Int::class.javaPrimitiveType!!, CharSequence::class.java),
                        )
                    "setDeviceOwnerLockScreenInfo",
                    "setOrganizationName" ->
                        return clazz.getDeclaredMethod(
                            methodName,
                            *arrayOf(ComponentName::class.java, CharSequence::class.java),
                        )
                }
            }
            if (
                (methodName == "setStartUserSessionMessage" ||
                    methodName == "setEndUserSessionMessage")
            ) {
                return clazz.getDeclaredMethod(
                    methodName,
                    *arrayOf<Class<*>>(ComponentName::class.java, CharSequence::class.java),
                )
            }

            // Calls with null parameters (and hence the type cannot be inferred)
            val method: Method? = findMethodWithNullParameterCall(clazz, methodName, parameterTypes)
            if (method != null) return method

            // ...otherwise return exactly what as asked
            return clazz.getDeclaredMethod(methodName, *parameterTypes)
        }

        private fun findMethodWithNullParameterCall(
            clazz: Class<*>,
            methodName: String?,
            parameterTypes: Array<Class<*>?>?,
        ): Method? {
            if (parameterTypes == null) return null

            Log.d(
                TAG,
                ("findMethodWithNullParameterCall(): " +
                    clazz +
                    "." +
                    methodName +
                    "(" +
                    parameterTypes.contentToString() +
                    ")"),
            )

            var hasNullParameter = false
            for (i in parameterTypes.indices) {
                if (parameterTypes[i] == null) {
                    if (Utils.VERBOSE) {
                        Log.v(TAG, "Found null parameter at index $i of $methodName")
                    }
                    hasNullParameter = true
                    break
                }
            }
            if (!hasNullParameter) return null

            val methods: MutableList<Method> = ArrayList()
            for (method in clazz.declaredMethods) {
                if (method.name == methodName && method.parameterCount == parameterTypes.size) {
                    methods.add(method)
                }
            }
            if (Utils.VERBOSE) {
                Log.v(TAG, "Methods found: $methods")
            }

            return when (methods.size) {
                0 -> null
                1 -> methods.get(0)
                else -> findBestMethod(methods, parameterTypes)
            }
        }

        private fun findBestMethod(
            methods: MutableList<Method>,
            parameterTypes: Array<Class<*>?>,
        ): Method? {
            if (Utils.VERBOSE) {
                Log.v(TAG, "Found " + methods.size + " methods: " + methods)
            }
            var bestMethod: Method? = null

            _methods@ for (method in methods) {
                val methodParameters = method.parameters
                for (i in parameterTypes.indices) {
                    val expectedType = parameterTypes[i]
                    if (expectedType == null) continue

                    val actualType = methodParameters[i]!!.getType()
                    if (expectedType != actualType) {
                        if (Utils.VERBOSE) {
                            Log.v(
                                TAG,
                                ("Parameter at index " +
                                    i +
                                    " doesn't match (expecting " +
                                    expectedType +
                                    ", got " +
                                    actualType +
                                    "); rejecting " +
                                    method),
                            )
                        }
                        continue@_methods
                    }
                }
                // double check there isn't more than one
                if (bestMethod != null) {
                    Log.e(TAG, "found another method ($method), but will use $bestMethod")
                } else {
                    bestMethod = method
                }
            }
            if (Utils.VERBOSE) {
                Log.v(TAG, "Returning $bestMethod")
            }
            return bestMethod
        }

        private fun sendError(receiver: BroadcastReceiver, e: Exception?) {
            Log.e(TAG, "Exception handling wrapped DPC call", e)
            sendNoLog(receiver, TestAppSystemServiceFactory.RESULT_EXCEPTION, e)
        }

        private fun sendResult(receiver: BroadcastReceiver, result: Any?) {
            sendNoLog(receiver, TestAppSystemServiceFactory.RESULT_OK, result)
            if (Utils.VERBOSE) {
                Log.v(TAG, "Sent")
            }
        }

        private fun sendNoLog(receiver: BroadcastReceiver, code: Int, result: Any?) {
            if (Utils.VERBOSE) {
                Log.v(
                    TAG,
                    ("Sending " +
                        resultCodeToString(code) +
                        " (result='" +
                        result +
                        "') to " +
                        receiver +
                        " on " +
                        Thread.currentThread()),
                )
            }
            receiver.setResultCode(code)
            if (result != null) {
                val intent = Intent()
                DataFormatter.addArg(intent, arrayOf(result), 0)
                receiver.setResultExtras(intent.extras)
            }
        }
    }
}
