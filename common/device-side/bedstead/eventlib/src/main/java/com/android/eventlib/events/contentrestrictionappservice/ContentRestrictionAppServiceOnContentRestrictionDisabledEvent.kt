/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.eventlib.events.contentrestrictionappservice

import android.app.Service
import androidx.annotation.CheckResult
import com.android.eventlib.Event
import com.android.eventlib.EventLogger
import com.android.eventlib.EventLogsQuery
import com.android.queryable.info.ServiceInfo
import com.android.queryable.queries.ServiceQuery
import com.android.queryable.queries.ServiceQueryHelper
import com.google.errorprone.annotations.CanIgnoreReturnValue

/** Event logged when [ContentRestrictionAppService.onContentRestrictionDisabled]. */
class ContentRestrictionAppServiceOnContentRestrictionDisabledEvent : Event() {

    // mService is initialized in the logger, so lateinit is appropriate here.
    protected lateinit var mService: ServiceInfo

    /** Information about the [Service] which received the intent. */
    fun service(): ServiceInfo {
        return mService
    }

    override fun toString(): String {
        return "ContentRestrictionAppServiceOnContentRestrictionDisabledEvent{" +
            ", service=$mService" +
            ", packageName='$mPackageName'" +
            ", timestamp=$mTimestamp" +
            "}"
    }

    companion object {
        /** Begins a query for [ContentRestrictionAppServiceOnContentRestrictionDisabledEvent] events. */
        @JvmStatic
        fun queryPackage(packageName: String): ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery {
            return ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery(packageName)
        }

        /** Begins logging a [ContentRestrictionAppServiceOnContentRestrictionDisabledEvent]. */
        @JvmStatic
        fun logger(
            service: Service,
            serviceName: String,
        ): ContentRestrictionAppServiceOnContentRestrictionDisabledEventLogger {
            return ContentRestrictionAppServiceOnContentRestrictionDisabledEventLogger(service, serviceName)
        }
    }

    /** [EventLogsQuery] for [ContentRestrictionAppServiceOnContentRestrictionDisabledEvent]. */
    class ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery(packageName: String) :
        EventLogsQuery<
            ContentRestrictionAppServiceOnContentRestrictionDisabledEvent,
            ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery,
        >(ContentRestrictionAppServiceOnContentRestrictionDisabledEvent::class.java, packageName) {

        private val mService:
            ServiceQueryHelper<ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery> =
            ServiceQueryHelper(this)

        /** Query [Service]. */
        @CheckResult
        fun whereService(): ServiceQuery<ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery> {
            return mService
        }

        override fun filter(event: ContentRestrictionAppServiceOnContentRestrictionDisabledEvent): Boolean {
            if (!mService.matches(event.mService)) {
                return false
            }
            return true
        }

        override fun describeQuery(fieldName: String): String {
            return toStringBuilder(ContentRestrictionAppServiceOnContentRestrictionDisabledEvent::class.java, this)
                .field("service", mService)
                .toString()
        }
    }

    /** [EventLogger] for [ContentRestrictionAppServiceOnContentRestrictionDisabledEvent]. */
    class ContentRestrictionAppServiceOnContentRestrictionDisabledEventLogger(
        service: Service,
        serviceName: String,
    ) :
        EventLogger<ContentRestrictionAppServiceOnContentRestrictionDisabledEvent>(
            service,
            ContentRestrictionAppServiceOnContentRestrictionDisabledEvent(),
        ) {
        init {
            // Call the setService method from the constructor, mimicking the Java logic.
            setService(serviceName)
        }

        /** Sets the [Service] which received this event. */
        @CanIgnoreReturnValue
        fun setService(serviceName: String): ContentRestrictionAppServiceOnContentRestrictionDisabledEventLogger {
            mEvent.mService = ServiceInfo.builder().serviceClass(serviceName).build()
            return this
        }
    }
}
