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
package com.android.eventlib.premade

import android.app.supervision.Policy
import android.app.supervision.SupervisionAppService
import android.content.Intent
import com.android.eventlib.events.services.ServiceBoundEvent
import com.android.eventlib.events.services.ServiceCreatedEvent
import com.android.eventlib.events.services.ServiceDestroyedEvent
import com.android.eventlib.events.services.ServiceLowMemoryEvent
import com.android.eventlib.events.services.ServiceMemoryTrimmedEvent
import com.android.eventlib.events.services.ServiceReboundEvent
import com.android.eventlib.events.services.ServiceStartedEvent
import com.android.eventlib.events.services.ServiceTaskRemovedEvent
import com.android.eventlib.events.services.ServiceUnboundEvent
import com.android.eventlib.events.supervisionappservice.SupervisionAppServiceOnPolicyChangedEvent
import com.android.eventlib.events.supervisionappservice.SupervisionAppServiceOnSupervisionDisabledEvent
import com.android.eventlib.events.supervisionappservice.SupervisionAppServiceOnSupervisionEnabledEvent
import java.io.FileDescriptor
import java.io.PrintWriter

/** Implementation of [SupervisionAppService] which logs events in response to callbacks. */
open class EventLibSupervisionAppService : SupervisionAppService() {
    private var mOverrideSupervisionAppServiceClassName: String? = null
    private val mLock = Object()
    private var waitOnUnbind = false

    fun setOverrideSupervisionAppServiceClassName(overrideSupervisionAppServiceClassName: String) {
        mOverrideSupervisionAppServiceClassName = overrideSupervisionAppServiceClassName
    }

    /**
     * Get the class name for this [SupervisionAppService].
     *
     * This will account for the name being overridden.
     */
    fun getClassName(): String =
        mOverrideSupervisionAppServiceClassName ?: EventLibSupervisionAppService::class.java.name

    override fun onSupervisionEnabled() {
        SupervisionAppServiceOnSupervisionEnabledEvent.logger(this, getClassName()).log()
        super.onSupervisionEnabled()
    }

    override fun onSupervisionDisabled() {
        synchronized(mLock) {
            waitOnUnbind = true
        }
        SupervisionAppServiceOnSupervisionDisabledEvent.logger(this, getClassName()).log()
        super.onSupervisionDisabled()
    }

    override fun onPolicyChanged(policy: Policy) {
        SupervisionAppServiceOnPolicyChangedEvent.logger(this, getClassName(), policy).log()
        super.onPolicyChanged(policy)
    }

    override fun onServiceBound(intent: Intent?) {
        ServiceBoundEvent.logger(this, getClassName(), intent).log()
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<String?>) {
        super.dump(fd, writer, args)
    }

    override fun onCreate() {
        ServiceCreatedEvent.logger(this, getClassName()).log()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceStartedEvent.logger(this, getClassName(), intent, flags, startId).log()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        ServiceDestroyedEvent.logger(this, getClassName()).log()
        super.onDestroy()
    }

    override fun onLowMemory() {
        ServiceLowMemoryEvent.logger(this, getClassName()).log()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        ServiceMemoryTrimmedEvent.logger(this, getClassName(), level).log()
        super.onTrimMemory(level)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceUnboundEvent.logger(this, getClassName(), intent).log()
        val onUnbind = super.onUnbind(intent)
        if (waitOnUnbind) {
            synchronized(mLock) {
                waitOnUnbind = false
                // Wait a bit to prevent the process from being killed prematurely.
                mLock.wait(2500)
            }
        }
        return onUnbind
    }

    override fun onRebind(intent: Intent?) {
        ServiceReboundEvent.logger(this, getClassName(), intent).log()
        super.onRebind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ServiceTaskRemovedEvent.logger(this, getClassName(), rootIntent).log()
        super.onTaskRemoved(rootIntent)
    }
}
