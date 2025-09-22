/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.eventlib.events.supervisionappservice

import android.app.Service
import androidx.annotation.CheckResult
import com.android.eventlib.Event
import com.android.eventlib.EventLogger
import com.android.eventlib.EventLogsQuery
import com.android.queryable.info.ServiceInfo
import com.android.queryable.queries.ServiceQuery
import com.android.queryable.queries.ServiceQueryHelper
import com.google.errorprone.annotations.CanIgnoreReturnValue

/** Event logged when [SupervisionAppService.onSupervisionDisabled]. */
class SupervisionAppServiceOnSupervisionDisabledEvent : Event() {

    // mService is initialized in the logger, so lateinit is appropriate here.
    protected lateinit var mService: ServiceInfo

    /** Information about the [Service] which received the intent. */
    fun service(): ServiceInfo {
        return mService
    }

    override fun toString(): String {
        return "SupervisionAppServiceOnSupervisionDisabledEvent{" +
            ", service=$mService" +
            ", packageName='$mPackageName'" +
            ", timestamp=$mTimestamp" +
            "}"
    }

    companion object {
        /** Begins a query for [SupervisionAppServiceOnSupervisionDisabledEvent] events. */
        @JvmStatic
        fun queryPackage(
            packageName: String
        ): SupervisionAppServiceOnSupervisionDisabledEventQuery {
            return SupervisionAppServiceOnSupervisionDisabledEventQuery(packageName)
        }

        /** Begins logging a [SupervisionAppServiceOnSupervisionDisabledEvent]. */
        @JvmStatic
        fun logger(
            service: Service,
            serviceName: String,
        ): SupervisionAppServiceOnSupervisionDisabledEventLogger {
            return SupervisionAppServiceOnSupervisionDisabledEventLogger(service, serviceName)
        }
    }

    /** [EventLogsQuery] for [SupervisionAppServiceOnSupervisionDisabledEvent]. */
    class SupervisionAppServiceOnSupervisionDisabledEventQuery(packageName: String) :
        EventLogsQuery<
            SupervisionAppServiceOnSupervisionDisabledEvent,
            SupervisionAppServiceOnSupervisionDisabledEventQuery,
        >(SupervisionAppServiceOnSupervisionDisabledEvent::class.java, packageName) {

        private val mService:
            ServiceQueryHelper<SupervisionAppServiceOnSupervisionDisabledEventQuery> =
            ServiceQueryHelper(this)

        /** Query [Service]. */
        @CheckResult
        fun whereService(): ServiceQuery<SupervisionAppServiceOnSupervisionDisabledEventQuery> {
            return mService
        }

        override fun filter(event: SupervisionAppServiceOnSupervisionDisabledEvent): Boolean {
            // Note: This matches the filter logic from the original Java class.
            if (!mService.matches(event.mService)) {
                return false
            }
            return true
        }

        override fun describeQuery(fieldName: String): String {
            return toStringBuilder(
                    SupervisionAppServiceOnSupervisionDisabledEvent::class.java,
                    this,
                )
                .field("service", mService)
                .toString()
        }
    }

    /** [EventLogger] for [SupervisionAppServiceOnSupervisionDisabledEvent]. */
    class SupervisionAppServiceOnSupervisionDisabledEventLogger(
        service: Service,
        serviceName: String,
    ) :
        EventLogger<SupervisionAppServiceOnSupervisionDisabledEvent>(
            service,
            SupervisionAppServiceOnSupervisionDisabledEvent(),
        ) {
        init {
            // Call the setService method from the constructor, mimicking the Java logic.
            setService(serviceName)
        }

        /** Sets the [Service] which received this event. */
        @CanIgnoreReturnValue
        fun setService(serviceName: String): SupervisionAppServiceOnSupervisionDisabledEventLogger {
            mEvent.mService = ServiceInfo.builder().serviceClass(serviceName).build()
            return this
        }
    }
}
