/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.app.contentrestriction.ClassifiableContent
import android.app.contentrestriction.ContentClassificationResult
import android.app.contentrestriction.ContentRestrictionAppService
import android.content.Intent
import com.android.eventlib.events.contentrestrictionappservice.ContentRestrictionAppServiceOnClassifyContentEvent
import com.android.eventlib.events.contentrestrictionappservice.ContentRestrictionAppServiceOnContentRestrictionDisabledEvent
import com.android.eventlib.events.contentrestrictionappservice.ContentRestrictionAppServiceOnContentRestrictionEnabledEvent
import com.android.eventlib.events.services.ServiceBoundEvent
import com.android.eventlib.events.services.ServiceCreatedEvent
import com.android.eventlib.events.services.ServiceDestroyedEvent
import com.android.eventlib.events.services.ServiceLowMemoryEvent
import com.android.eventlib.events.services.ServiceMemoryTrimmedEvent
import com.android.eventlib.events.services.ServiceReboundEvent
import com.android.eventlib.events.services.ServiceStartedEvent
import com.android.eventlib.events.services.ServiceTaskRemovedEvent
import com.android.eventlib.events.services.ServiceUnboundEvent
import java.io.FileDescriptor
import java.io.PrintWriter

/** Implementation of [ContentRestrictionAppService] which logs events in response to callbacks. */
open class EventLibContentRestrictionAppService : ContentRestrictionAppService() {
    private var mOverrideContentRestrictionAppServiceClassName: String? = null

    fun setOverrideContentRestrictionAppServiceClassName(overrideClassName: String) {
        mOverrideContentRestrictionAppServiceClassName = overrideClassName
    }

    /**
     * Get the class name for this [ContentRestrictionAppService].  This is useful for overriding
     * the class name in premade test apps.
     */
    fun getClassName(): String =
        mOverrideContentRestrictionAppServiceClassName
            ?: EventLibContentRestrictionAppService::class.java.name

    override fun onContentRestrictionEnabled() {
        ContentRestrictionAppServiceOnContentRestrictionEnabledEvent.logger(this, getClassName()).log()
        super.onContentRestrictionEnabled()
    }

    override fun onContentRestrictionDisabled() {
        ContentRestrictionAppServiceOnContentRestrictionDisabledEvent.logger(this, getClassName()).log()
        super.onContentRestrictionDisabled()
    }

    override fun onClassifyContent(content: ClassifiableContent): ContentClassificationResult? {
        ContentRestrictionAppServiceOnClassifyContentEvent.logger(this, getClassName(), content).log()
        return super.onClassifyContent(content)
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
        return super.onUnbind(intent)
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
