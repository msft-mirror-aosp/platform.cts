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
package com.android.eventlib.events.contentrestrictionappservice

import com.android.eventlib.events.services.ServiceEvents
import com.android.eventlib.events.contentrestrictionappservice.ContentRestrictionAppServiceOnContentRestrictionEnabledEvent.ContentRestrictionAppServiceOnContentRestrictionEnabledEventQuery
import com.android.eventlib.events.contentrestrictionappservice.ContentRestrictionAppServiceOnContentRestrictionDisabledEvent.ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery
import com.android.eventlib.events.contentrestrictionappservice.ContentRestrictionAppServiceOnClassifyContentEvent.ContentRestrictionAppServiceOnClassifyContentEventQuery

/** Quick access to event queries about ContentRestrictionAppService. */
interface ContentRestrictionAppServiceEvents : ServiceEvents {
    /**
     * Query for when content restriction is enabled.
     *
     * Additional filters can be added to the returned object.
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun contentRestrictionEnabled(): ContentRestrictionAppServiceOnContentRestrictionEnabledEventQuery

    /**
     * Query for when content restriction is disabled.
     *
     * Additional filters can be added to the returned object.
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun contentRestrictionDisabled(): ContentRestrictionAppServiceOnContentRestrictionDisabledEventQuery

    /**
     * Query for when content is classified.
     *
     * Additional filters can be added to the returned object.
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun classifyContent(): ContentRestrictionAppServiceOnClassifyContentEventQuery
}
