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

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TetheringManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.HardwarePropertiesManager
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.android.bedstead.dpmwrapper.DataFormatter.Companion.addArg
import com.android.bedstead.dpmwrapper.DataFormatter.Companion.getArg
import com.android.bedstead.dpmwrapper.Utils.Companion.handler
import com.android.bedstead.dpmwrapper.Utils.Companion.isHeadlessSystemUserMode
import com.android.bedstead.dpmwrapper.Utils.Companion.toString
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.ThrowingSupplier
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

// TODO(b/176993670): STOPSHIP - it currently uses ordered broadcasts and a Mockito spy to implement
// the IPC between users, but before S is shipped it should be changed to use the connected apps SDK
// or the new CTS infrastructure.
/**
 * Class used to create to provide a [DevicePolicyManager] implementation (and other managers that
 * must be run by the device owner user) that automatically funnels calls between the user running
 * the tests and the user that is the device owner.
 */
class TestAppSystemServiceFactory private constructor() {
    private class Result(receiver: BroadcastReceiver) {
        val code: Int
        val error: String?
        val extras: Bundle?
        val value: Any?

        init {
            var resultCode = receiver.resultCode
            var data = receiver.resultData
            extras = receiver.getResultExtras(true)
            var parsedValue: Any? = null
            try {
                if (extras != null && !extras.isEmpty()) {
                    val result = kotlin.arrayOfNulls<Any>(1)
                    val index = 0
                    getArg(extras, result, null, index)
                    parsedValue = result[index]
                }
            } catch (e: Exception) {
                Log.e(TAG, "error parsing extras (code=$resultCode, data=$data", e)
                data = "error parsing extras"
                resultCode = RESULT_EXCEPTION
            }
            code = resultCode
            error = data
            value = parsedValue
        }

        override fun toString(): String {
            return ("Result[code=" +
                resultCodeToString(code) +
                ", error=" +
                error +
                ", extras=" +
                extras +
                ", value=" +
                value +
                "]")
        }
    }

    internal abstract class ServiceManagerWrapper<T> {
        abstract fun getWrapper(context: Context, manager: T?, answer: Answer<*>): T?
    }

    init {
        throw UnsupportedOperationException("contains only static methods")
    }

    companion object {
        private val TAG: String = TestAppSystemServiceFactory::class.java.getSimpleName()

        private const val RESULT_NOT_SENT_TO_ANY_RECEIVER = 108
        const val RESULT_OK: Int = 42
        const val RESULT_EXCEPTION: Int = 666

        // Must be high enough to outlast long tests like NetworkLoggingTest, which waits up to
        // 6 minutes for network monitoring events.
        private val TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10)

        // Caches whether the package declares the required receiver (otherwise each test would be
        // querying package manager, which is expensive)
        private val sHasRequiredReceiver = HashMap<String?, Boolean?>()

        /** Gets the proper [DevicePolicyManager] instance to be used by the test. */
        @JvmStatic
        fun getDevicePolicyManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
            forDeviceOwner: Boolean,
            isSingleUserMode: Boolean,
        ): DevicePolicyManager? {
            return getSystemService<DevicePolicyManager>(
                context,
                DevicePolicyManager::class.java,
                receiverClass,
                forDeviceOwner,
                isSingleUserMode,
            )
        }

        /** Gets the proper [DevicePolicyManager] instance to be used by the test. */
        @JvmStatic
        fun getDevicePolicyManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
            forDeviceOwner: Boolean,
        ): DevicePolicyManager? {
            return getDevicePolicyManager(
                context,
                receiverClass,
                forDeviceOwner,
                isSingleUser(context),
            )
        }

        /** Gets the proper [WifiManager] instance to be used by device owner tests. */
        @JvmStatic
        fun getWifiManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
            isSingleUserMode: Boolean,
        ): WifiManager? {
            return getSystemService<WifiManager>(
                context,
                WifiManager::class.java,
                receiverClass,
                true,
                isSingleUserMode,
            )
        }

        /** Gets the proper [WifiManager] instance to be used by device owner tests. */
        @JvmStatic
        fun getWifiManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
        ): WifiManager? {
            return getSystemService<WifiManager>(
                context,
                WifiManager::class.java,
                receiverClass,
                true,
                isSingleUser(context),
            )
        }

        /** Gets the proper [TetheringManager] instance to be used by device owner tests. */
        @JvmStatic
        fun getTetheringManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
        ): TetheringManager? {
            return getSystemService<TetheringManager>(
                context,
                TetheringManager::class.java,
                receiverClass,
                true,
                isSingleUser(context),
            )
        }

        @SuppressLint("MissingPermission")
        private fun isSingleUser(context: Context): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                SystemUtil.runWithShellPermissionIdentity(
                    ThrowingSupplier {
                        (context
                            .getSystemService(DevicePolicyManager::class.java)
                            .getHeadlessDeviceOwnerMode() ==
                            DeviceAdminInfo.HEADLESS_DEVICE_OWNER_MODE_SINGLE_USER)
                    }
                )
        }

        /**
         * Gets the proper [HardwarePropertiesManager] instance to be used by device owner tests.
         */
        @JvmStatic
        fun getHardwarePropertiesManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
        ): HardwarePropertiesManager? {
            return getSystemService<HardwarePropertiesManager>(
                context,
                HardwarePropertiesManager::class.java,
                receiverClass,
                true,
                isSingleUser(context),
            )
        }

        /** Gets the proper [UserManager] instance to be used by device owner tests. */
        @JvmStatic
        fun getUserManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
        ): UserManager? {
            return getSystemService<UserManager>(
                context,
                UserManager::class.java,
                receiverClass,
                true,
                isSingleUser(context),
            )
        }

        /** Gets the proper [GenericManager] instance to be used by the test. */
        @JvmStatic
        fun getGenericManager(
            context: Context,
            receiverClass: Class<out BroadcastReceiver?>,
        ): GenericManager? {
            return getSystemService<GenericManager>(
                context,
                GenericManager::class.java,
                receiverClass,
                true,
                isSingleUser(context),
            )
        }

        private fun assertHasRequiredReceiver(context: Context) {
            if (!isHeadlessSystemUserMode) return

            val packageName = context.getPackageName()
            val hasIt: Boolean? = sHasRequiredReceiver.get(packageName)
            if (hasIt != null && hasIt) {
                return
            }
            val pm = context.getPackageManager()
            TestAppCallbacksReceiver::class.java
            val packageInfo: PackageInfo
            try {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_RECEIVERS)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.wtf(TAG, "Could not get receivers for $packageName")
                return
            }

            val numberReceivers =
                (if (packageInfo.receivers == null) 0 else packageInfo.receivers!!.size)
            Log.d(
                TAG,
                ("assertHasRequiredReceiver(" +
                    packageName +
                    "): userId=" +
                    context.userId +
                    ", info=" +
                    packageInfo +
                    ", receivers=" +
                    numberReceivers),
            )

            if (packageInfo.receivers != null) {
                for (receiver in packageInfo.receivers) {
                    Log.v(TAG, "checking receiver $receiver")
                    var receiverClass: Class<*>? = null
                    try {
                        receiverClass = Class.forName(receiver.name)
                    } catch (e: ClassNotFoundException) {
                        Log.e(TAG, "Invalid receiver class on manifest: " + receiver.name)
                        continue
                    }
                    if (TestAppCallbacksReceiver::class.java.isAssignableFrom(receiverClass)) {
                        Log.d(TAG, "Found " + receiverClass.name + " on " + packageName)
                        sHasRequiredReceiver.put(packageName, true)
                        return
                    }
                }
            }
            if (numberReceivers == 0) {
                // This is happening sometimes on headless system user; most likely it's a
                // permission
                // issue querying pm, but given that the DpmWrapper is temporary and this check is
                // more
                // of a validation to avoid other issues, it's ok to just log...
                Log.wtf(TAG, "Package $packageName has no receivers")
                return
            }
            fail(
                ("Package " +
                    packageName +
                    " has " +
                    numberReceivers +
                    " receivers, but not extends " +
                    TestAppCallbacksReceiver::class.java.getName() +
                    " - did you add one to the manifest?")
            )
        }

        @SuppressLint("MissingPermission")
        private fun <T> getSystemService(
            context: Context,
            serviceClass: Class<T>,
            receiverClass: Class<out BroadcastReceiver?>,
            forDeviceOwner: Boolean,
            isSingleUserMode: Boolean,
        ): T? {
            var wrapper: ServiceManagerWrapper<T?>? = null
            val wrappedClass: Class<*>?

            var manager: T? = null
            var managerCanBeNull = false

            if (serviceClass == DevicePolicyManager::class.java) {
                wrappedClass = DevicePolicyManager::class.java
                @Suppress("UNCHECKED_CAST")
                val safeCastWrapper = DevicePolicyManagerWrapper() as ServiceManagerWrapper<T?>
                wrapper = safeCastWrapper
            } else if (serviceClass == WifiManager::class.java) {
                @Suppress("UNCHECKED_CAST")
                val safeCastWrapper = WifiManagerWrapper() as ServiceManagerWrapper<T?>
                wrapper = safeCastWrapper
                wrappedClass = WifiManager::class.java
                managerCanBeNull = true
            } else if (serviceClass == TetheringManager::class.java) {
                @Suppress("UNCHECKED_CAST")
                val safeCastWrapper = TetheringManagerWrapper() as ServiceManagerWrapper<T?>
                wrapper = safeCastWrapper
                wrappedClass = TetheringManager::class.java
                managerCanBeNull = true
            } else if (serviceClass == HardwarePropertiesManager::class.java) {
                @Suppress("UNCHECKED_CAST")
                val safeCastWrapper =
                    HardwarePropertiesManagerWrapper() as ServiceManagerWrapper<T?>
                wrapper = safeCastWrapper
                wrappedClass = HardwarePropertiesManager::class.java
            } else if (serviceClass == UserManager::class.java) {
                @Suppress("UNCHECKED_CAST")
                val safeCastWrapper = UserManagerWrapper() as ServiceManagerWrapper<T?>
                wrapper = safeCastWrapper
                wrappedClass = UserManager::class.java
            } else if (serviceClass == GenericManager::class.java) {
                @Suppress("UNCHECKED_CAST")
                val safeCastWrapper = GenericManagerWrapper() as ServiceManagerWrapper<T?>
                @Suppress("UNCHECKED_CAST") val safeCastManager = GenericManagerImpl(context) as T?
                wrapper = safeCastWrapper
                wrappedClass = GenericManager::class.java
                manager = safeCastManager
            } else {
                throw IllegalArgumentException("invalid service class: $serviceClass")
            }
            if (manager == null) {
                @Suppress("UNCHECKED_CAST")
                manager = context.getSystemService(wrappedClass) as T?
            }

            if (manager == null) {
                if (managerCanBeNull) {
                    Log.i(TAG, "Manager of" + serviceClass + "is null")
                    return null
                }
                fail("Could not get a manager of type $serviceClass")
            }

            if (!forDeviceOwner) return manager

            assertHasRequiredReceiver(context)

            val userId = context.userId
            if (userId == UserHandle.USER_SYSTEM || !isHeadlessSystemUserMode || isSingleUserMode) {
                Log.i(TAG, "get(): returning 'pure' DevicePolicyManager for user $userId")
                return manager
            }

            val receiverClassName = receiverClass.name
            val wrappedClassName = wrappedClass.name
            if (Utils.Companion.VERBOSE) {
                Log.v(
                    TAG,
                    ("get(): receiverClassName: " +
                        receiverClassName +
                        ", wrappedClassName: " +
                        wrappedClassName),
                )
            }

            val answer: Answer<*> = Answer { inv: InvocationOnMock? ->
                val args = inv!!.getArguments()
                if (Utils.Companion.VERBOSE) {
                    Log.v(TAG, "spying " + inv + " method: " + inv!!.getMethod())
                } else {
                    Log.i(TAG, "spying " + inv!!.getMethod())
                }
                val methodName = inv!!.getMethod().name
                val intent =
                    Intent(Utils.Companion.ACTION_WRAPPED_MANAGER_CALL)
                        .setClassName(context, receiverClassName)
                        .putExtra(Utils.Companion.EXTRA_CLASS, wrappedClassName)
                        .putExtra(Utils.Companion.EXTRA_METHOD, methodName)
                        .putExtra(Utils.Companion.EXTRA_NUMBER_ARGS, args.size)
                for (i in args.indices) {
                    addArg(intent, args, i)
                }

                val latch = CountDownLatch(1)
                val resultRef = AtomicReference<Result>()
                val myReceiver: BroadcastReceiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val action = intent.action
                            if (Utils.Companion.VERBOSE) {
                                Log.v(
                                    TAG,
                                    ("spy received intent " +
                                        action +
                                        " for user " +
                                        context.userId),
                                )
                            }
                            val result = Result(this)
                            if (Utils.Companion.VERBOSE) Log.v(TAG, "result:" + result)
                            resultRef.set(result)
                            latch.countDown()
                        }
                    }
                if (Utils.Companion.VERBOSE) {
                    Log.v(
                        TAG,
                        ("Sending ordered broadcast (" +
                            toString(intent) +
                            ") from user " +
                            userId +
                            " to user " +
                            UserHandle.SYSTEM),
                    )
                }

                // NOTE: method below used to be wrapped under runWithShellPermissionIdentity() to
                // get
                // INTERACT_ACROSS_USERS permission, but that's not needed anymore (as the
                // permission
                // is granted by the test. Besides, this class is now also used by DO apps that are
                // not
                // instrumented, so it was removed
                if (
                    context.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS) !=
                        PackageManager.PERMISSION_GRANTED
                ) {
                    fail(
                        ("Package " +
                            context.getPackageName() +
                            " doesn't have " +
                            Manifest.permission.INTERACT_ACROSS_USERS +
                            " - did you add it to the manifest and called " +
                            "grantDpmWrapper() (for user " +
                            userId +
                            ") in the host-side test?")
                    )
                }
                context.sendOrderedBroadcastAsUser(
                    intent,
                    UserHandle.SYSTEM,
                    null,
                    myReceiver,
                    handler,
                    RESULT_NOT_SENT_TO_ANY_RECEIVER,
                    null,
                    null,
                )

                if (Utils.Companion.VERBOSE) {
                    Log.d(
                        TAG,
                        ("Waiting up to " +
                            TIMEOUT_MS +
                            "ms for response on " +
                            Thread.currentThread()),
                    )
                }
                if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    fail("Ordered broadcast for %s() not received in %dms", methodName, TIMEOUT_MS)
                }

                val result = resultRef.get()
                Log.d(
                    TAG,
                    ("Received result on user " +
                        userId +
                        ". Code: " +
                        resultCodeToString(result.code)),
                )

                if (Utils.Companion.VERBOSE) {
                    // Some results - like network logging events - are quite large
                    Log.v(TAG, "Result: $result")
                }
                when (result.code) {
                    RESULT_OK -> return@Answer result.value
                    RESULT_EXCEPTION -> {
                        val e = result.value as Exception?
                        if (e is InvocationTargetException) {
                            throw e.cause ?: e
                        } else {
                            throw Exception()
                        }
                    }
                    RESULT_NOT_SENT_TO_ANY_RECEIVER -> {
                        fail(
                            ("Didn't receive result from ordered broadcast - did you override " +
                                receiverClassName +
                                ".onReceive() to call " +
                                "DeviceOwnerHelper.runManagerMethod()? Did you add " +
                                Utils.Companion.ACTION_WRAPPED_MANAGER_CALL +
                                " to its intent filter / manifest?")
                        )
                        return@Answer null
                    }
                    else -> {
                        fail("Received invalid result for method %s: %s", methodName, result)
                        return@Answer null
                    }
                }
            }

            val spy = wrapper.getWrapper(context, manager, answer)

            return spy
        }

        fun resultCodeToString(code: Int): String {
            // Can't use DebugUtils.constantToString() because some values are private
            return when (code) {
                RESULT_NOT_SENT_TO_ANY_RECEIVER -> "RESULT_NOT_SENT_TO_ANY_RECEIVER"
                RESULT_OK -> "RESULT_OK"
                RESULT_EXCEPTION -> "RESULT_EXCEPTION"
                else -> "RESULT_UNKNOWN:$code"
            }
        }

        private fun fail(template: String?, vararg args: Any?) {
            throw AssertionError(String.format(Locale.ENGLISH, template!!, *args))
        }
    }
}
