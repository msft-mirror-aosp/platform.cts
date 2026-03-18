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
import android.app.contentrestriction.ClassifiableContent
import androidx.annotation.CheckResult
import com.android.eventlib.Event
import com.android.eventlib.EventLogger
import com.android.eventlib.EventLogsQuery
import com.android.queryable.info.ServiceInfo
import com.android.queryable.queries.ServiceQuery
import com.android.queryable.queries.ServiceQueryHelper
import com.google.errorprone.annotations.CanIgnoreReturnValue

/** Event logged when [ContentRestrictionAppService.onClassifyContent]. */
class ContentRestrictionAppServiceOnClassifyContentEvent : Event() {

    // mService is initialized in the logger, so lateinit is appropriate here.
    protected lateinit var mService: ServiceInfo
    var classifiableContent: ClassifiableContent? = null
        protected set

    /** Information about the [Service] which received the intent. */
    fun service(): ServiceInfo {
        return mService
    }

    override fun toString(): String {
        return "ContentRestrictionAppServiceOnClassifyContentEvent{" +
            ", service=$mService" +
            ", classifiableContent=$classifiableContent" +
            ", packageName='$mPackageName'" +
            ", timestamp=$mTimestamp" +
            "}"
    }

    companion object {
        /** Begins a query for [ContentRestrictionAppServiceOnClassifyContentEvent] events. */
        @JvmStatic
        fun queryPackage(packageName: String): ContentRestrictionAppServiceOnClassifyContentEventQuery {
            return ContentRestrictionAppServiceOnClassifyContentEventQuery(packageName)
        }

        /** Begins logging a [ContentRestrictionAppServiceOnClassifyContentEvent]. */
        @JvmStatic
        fun logger(
            service: Service,
            serviceName: String,
            classifiableContent: ClassifiableContent
        ): ContentRestrictionAppServiceOnClassifyContentEventLogger {
            return ContentRestrictionAppServiceOnClassifyContentEventLogger(service, serviceName, classifiableContent)
        }
    }

    /** [EventLogsQuery] for [ContentRestrictionAppServiceOnClassifyContentEvent]. */
    class ContentRestrictionAppServiceOnClassifyContentEventQuery(packageName: String) :
        EventLogsQuery<
            ContentRestrictionAppServiceOnClassifyContentEvent,
            ContentRestrictionAppServiceOnClassifyContentEventQuery,
        >(ContentRestrictionAppServiceOnClassifyContentEvent::class.java, packageName) {

        private val mService:
            ServiceQueryHelper<ContentRestrictionAppServiceOnClassifyContentEventQuery> =
            ServiceQueryHelper(this)

        /** Query [Service]. */
        @CheckResult
        fun whereService(): ServiceQuery<ContentRestrictionAppServiceOnClassifyContentEventQuery> {
            return mService
        }

        override fun filter(event: ContentRestrictionAppServiceOnClassifyContentEvent): Boolean {
            if (!mService.matches(event.mService)) {
                return false
            }
            return true
        }

        override fun describeQuery(fieldName: String): String {
            return toStringBuilder(ContentRestrictionAppServiceOnClassifyContentEvent::class.java, this)
                .field("service", mService)
                .toString()
        }
    }

    /** [EventLogger] for [ContentRestrictionAppServiceOnClassifyContentEvent]. */
    class ContentRestrictionAppServiceOnClassifyContentEventLogger(
        service: Service,
        serviceName: String,
        classifiableContent: ClassifiableContent
    ) :
        EventLogger<ContentRestrictionAppServiceOnClassifyContentEvent>(
            service,
            ContentRestrictionAppServiceOnClassifyContentEvent(),
        ) {
        init {
            // Call the setService method from the constructor, mimicking the Java logic.
            setService(serviceName)
            setClassifiableContent(classifiableContent)
        }

        /** Sets the [Service] which received this event. */
        @CanIgnoreReturnValue
        fun setService(serviceName: String): ContentRestrictionAppServiceOnClassifyContentEventLogger {
            mEvent.mService = ServiceInfo.builder().serviceClass(serviceName).build()
            return this
        }

        @CanIgnoreReturnValue
        fun setClassifiableContent(classifiableContent: ClassifiableContent): ContentRestrictionAppServiceOnClassifyContentEventLogger {
            mEvent.classifiableContent = classifiableContent
            return this
        }
    }
}
