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
package com.android.bedstead.harrier.components

import android.Manifest.permission.INTERACT_ACROSS_USERS_FULL
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver
import java.util.function.Function

/**
 * A [DeviceStateComponent] that allows to register broadcast receivers.
 */
class BroadcastReceiversComponent : DeviceStateComponent {

    private val mContext: Context = context().instrumentedContext()
    private val mRegisteredBroadcastReceivers: MutableList<BlockingBroadcastReceiver> =
        mutableListOf()

    override fun teardownNonShareableState() {
        mRegisteredBroadcastReceivers.forEach {
            it.unregisterQuietly()
        }
        mRegisteredBroadcastReceivers.clear()
    }

    internal fun registerBroadcastReceiver(
        action: String,
        checker: Function<Intent, Boolean>?
    ): BlockingBroadcastReceiver {
        val broadcastReceiver = BlockingBroadcastReceiver(mContext, action, checker)
        broadcastReceiver.register()
        mRegisteredBroadcastReceivers.add(broadcastReceiver)

        return broadcastReceiver
    }

    internal fun registerBroadcastReceiver(
        intentFilter: IntentFilter,
        checker: Function<Intent, Boolean>?
    ): BlockingBroadcastReceiver {
        val broadcastReceiver = BlockingBroadcastReceiver(mContext, intentFilter, checker)
        broadcastReceiver.register()
        mRegisteredBroadcastReceivers.add(broadcastReceiver)

        return broadcastReceiver
    }

    internal fun registerBroadcastReceiverForUser(
        user: UserReference,
        action: String,
        checker: Function<Intent, Boolean>?
    ): BlockingBroadcastReceiver {
        permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            val broadcastReceiver = BlockingBroadcastReceiver(
                context().androidContextAsUser(user),
                action,
                checker
            )
            broadcastReceiver.register()
            mRegisteredBroadcastReceivers.add(broadcastReceiver)
            return broadcastReceiver
        }
    }

    internal fun registerBroadcastReceiverForUser(
        user: UserReference,
        intentFilter: IntentFilter,
        checker: Function<Intent, Boolean>?
    ): BlockingBroadcastReceiver {
        permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            val broadcastReceiver = BlockingBroadcastReceiver(
                context().androidContextAsUser(user),
                intentFilter,
                checker
            )
            broadcastReceiver.register()
            mRegisteredBroadcastReceivers.add(broadcastReceiver)
            return broadcastReceiver
        }
    }

    internal fun registerBroadcastReceiverForAllUsers(
        action: String,
        checker: Function<Intent, Boolean>?
    ): BlockingBroadcastReceiver {
        permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            val broadcastReceiver = BlockingBroadcastReceiver(mContext, action, checker)
            broadcastReceiver.registerForAllUsers()

            mRegisteredBroadcastReceivers.add(broadcastReceiver)
            return broadcastReceiver
        }
    }

    internal fun registerBroadcastReceiverForAllUsers(
        intentFilter: IntentFilter,
        checker: Function<Intent, Boolean>?
    ): BlockingBroadcastReceiver {
        permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            val broadcastReceiver = BlockingBroadcastReceiver(mContext, intentFilter, checker)
            broadcastReceiver.registerForAllUsers()

            mRegisteredBroadcastReceivers.add(broadcastReceiver)
            return broadcastReceiver
        }
    }
}

private fun DeviceState.component(): BroadcastReceiversComponent = getDependency(
    BroadcastReceiversComponent::class.java
)

/**
 * Create and register a [BlockingBroadcastReceiver] which will be unregistered after the
 * test has run.
 */
@JvmOverloads
fun DeviceState.registerBroadcastReceiver(
    action: String,
    checker: Function<Intent, Boolean>? = null
) = component().registerBroadcastReceiver(action, checker)

/**
 * Create and register a [BlockingBroadcastReceiver] which will be unregistered after the
 * test has run.
 */
@JvmOverloads
fun DeviceState.registerBroadcastReceiver(
    intentFilter: IntentFilter,
    checker: Function<Intent, Boolean>? = null
) = component().registerBroadcastReceiver(intentFilter, checker)

/**
 * Create and register a [BlockingBroadcastReceiver] which will be unregistered after the
 * test has run.
 */
@JvmOverloads
fun DeviceState.registerBroadcastReceiverForUser(
    user: UserReference,
    action: String,
    checker: Function<Intent, Boolean>? = null
) = component().registerBroadcastReceiverForUser(user, action, checker)

/**
 * Create and register a [BlockingBroadcastReceiver] which will be unregistered after the
 * test has run.
 */
@JvmOverloads
fun DeviceState.registerBroadcastReceiverForUser(
    user: UserReference,
    intentFilter: IntentFilter,
    checker: Function<Intent, Boolean>? = null
) = component().registerBroadcastReceiverForUser(user, intentFilter, checker)

/**
 * Create and register a [BlockingBroadcastReceiver] which will be unregistered after the
 * test has run.
 */
@JvmOverloads
fun DeviceState.registerBroadcastReceiverForAllUsers(
    action: String,
    checker: Function<Intent, Boolean>? = null
) = component().registerBroadcastReceiverForAllUsers(action, checker)

/**
 * Create and register a [BlockingBroadcastReceiver] which will be unregistered after the
 * test has run.
 */
@JvmOverloads
fun DeviceState.registerBroadcastReceiverForAllUsers(
    intentFilter: IntentFilter,
    checker: Function<Intent, Boolean>? = null
) = component().registerBroadcastReceiverForAllUsers(intentFilter, checker)
