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
import android.app.supervision.Policy
import android.app.supervision.SupervisionAppService
import androidx.annotation.CheckResult
import com.android.eventlib.Event
import com.android.eventlib.EventLogger
import com.android.eventlib.EventLogsQuery
import com.android.queryable.info.ServiceInfo
import com.android.queryable.queries.ServiceQuery
import com.android.queryable.queries.ServiceQueryHelper
import com.android.queryable.util.SerializableParcelWrapper
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.android.eventlib.events.supervisionappservice.PolicyQuery
import com.android.eventlib.events.supervisionappservice.PolicyQueryHelper

/** Event logged when [SupervisionAppService.onPolicyChanged]. */
class SupervisionAppServiceOnPolicyChangedEvent : Event() {

    // mService is initialized in the logger, so lateinit is appropriate here.
    protected lateinit var mService: ServiceInfo
    protected lateinit var mPolicy: SerializableParcelWrapper<Policy>

    /** Information about the [Service] which received the intent. */
    fun service(): ServiceInfo {
        return mService
    }

    /** Information about the [Policy] which was changed. */
    fun policy(): Policy {
        return mPolicy.get()
    }

    override fun toString(): String {
        return "SupervisionAppServiceOnPolicyChangedEvent{" +
            " service=$mService" +
            ", packageName='$mPackageName'" +
            ", timestamp=$mTimestamp" +
            ", policy=${mPolicy.get()}" +
            "}"
    }

    companion object {
        /** Begins a query for [SupervisionAppServiceOnPolicyChangedEvent] events. */
        @JvmStatic
        fun queryPackage(packageName: String): SupervisionAppServiceOnPolicyChangedEventQuery {
            return SupervisionAppServiceOnPolicyChangedEventQuery(packageName)
        }

        /** Begins logging a [SupervisionAppServiceOnPolicyChangedEvent]. */
        @JvmStatic
        fun logger(
            service: Service,
            serviceName: String,
            policy: Policy,
        ): SupervisionAppServiceOnPolicyChangedEventLogger {
            return SupervisionAppServiceOnPolicyChangedEventLogger(service, serviceName, policy)
        }
    }

    /** [EventLogsQuery] for [SupervisionAppServiceOnPolicyChangedEvent]. */
    class SupervisionAppServiceOnPolicyChangedEventQuery(packageName: String) :
        EventLogsQuery<
            SupervisionAppServiceOnPolicyChangedEvent,
            SupervisionAppServiceOnPolicyChangedEventQuery,
        >(SupervisionAppServiceOnPolicyChangedEvent::class.java, packageName) {

        private val mService: ServiceQueryHelper<SupervisionAppServiceOnPolicyChangedEventQuery> =
            ServiceQueryHelper(this)

        private val mPolicy: PolicyQueryHelper<SupervisionAppServiceOnPolicyChangedEventQuery> =
            PolicyQueryHelper(this)

        /** Query [Service]. */
        @CheckResult
        fun whereService(): ServiceQuery<SupervisionAppServiceOnPolicyChangedEventQuery> {
            return mService
        }

        /** Query [Policy]. */
        @CheckResult
        fun wherePolicy(): PolicyQuery<SupervisionAppServiceOnPolicyChangedEventQuery> {
            return mPolicy
        }


        override fun filter(event: SupervisionAppServiceOnPolicyChangedEvent): Boolean {
            // Note: This matches the filter logic from the original Java class.
            if (!mService.matches(event.mService)) {
                return false
            }

            if (!mPolicy.matches(event.mPolicy)) {
                return false
            }

            return true
        }

        override fun describeQuery(fieldName: String): String {
            return toStringBuilder(SupervisionAppServiceOnPolicyChangedEvent::class.java, this)
                .field("service", mService)
                .field("policy", mPolicy)
                .toString()
        }
    }

    /** [EventLogger] for [SupervisionAppServiceOnPolicyChangedEvent]. */
    class SupervisionAppServiceOnPolicyChangedEventLogger(
        service: Service,
        serviceName: String,
        policy: Policy,
    ) :
        EventLogger<SupervisionAppServiceOnPolicyChangedEvent>(
            service,
            SupervisionAppServiceOnPolicyChangedEvent(),
        ) {
        init {
            // Call the setService method from the constructor, mimicking the Java logic.
            setService(serviceName)
            setPolicy(policy)
        }

        /** Sets the [Service] which received this event. */
        @CanIgnoreReturnValue
        fun setService(serviceName: String): SupervisionAppServiceOnPolicyChangedEventLogger {
            mEvent.mService = ServiceInfo.builder().serviceClass(serviceName).build()
            return this
        }

        /** Sets the [Policy] which was changed. */
        @CanIgnoreReturnValue
        fun setPolicy(policy: Policy): SupervisionAppServiceOnPolicyChangedEventLogger {
            mEvent.mPolicy = SerializableParcelWrapper<Policy>(policy)
            return this
        }
    }
}
