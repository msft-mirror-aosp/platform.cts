/*
 * Copyright (C) 2021 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.bedstead.enterprise.annotations

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.android.bedstead.nene.packages.CommonPackages
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.queryable.annotations.Query
import com.google.auto.value.AutoAnnotation

/**
 * Mark that a test method should run within a work profile.
 *
 * Your test configuration should be such that this test is only run where a work profile is
 * created and the test is being run within that user.
 *
 * Optionally, you can guarantee that these methods do not run outside of a work
 * profile by using `Devicestate`.
 *
 * This annotation by default opts a test into multi-user presubmit. New tests should also be
 * annotated [Postsubmit] until they are shown to meet the multi-user presubmit requirements.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS
)
@Retention(
    AnnotationRetention.RUNTIME
)
@RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class RequireRunOnWorkProfile(

    val installInstrumentedAppInParent: OptionalBoolean = OptionalBoolean.ANY,
    /**
     * The key used to identify the profile owner.
     *
     * This can be used with [AdditionalQueryParameters] to modify the requirements for
     * the DPC.  */
    val dpcKey: String = DEFAULT_KEY,

    /**
     * Requirements for the Profile Owner.
     *
     * Defaults to the default version of RemoteDPC.
     */
    val dpc: Query = Query(),

    /**
     * Whether the profile owner's DPC should be returned by calls to `Devicestate#dpc()`.
     *
     * Only one device policy controller per test should be marked as primary.
     */
    val dpcIsPrimary: Boolean = false,

    /** Whether the work profile device will be in COPE mode.  */
    val isOrganizationOwned: Boolean = false,

    /**
     * Affiliation ids to be set for the profile owner.
     */
    val affiliationIds: Array<String> = [],

    /**
     * Should we ensure that we are switched to the parent of the profile.
     *
     * ANY will be treated as TRUE if no other annotation has forced a switch.
     */
    val switchedToParentUser: OptionalBoolean = OptionalBoolean.TRUE,

    /**
     * Priority sets the order that annotations will be resolved.
     *
     * Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     * If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [Int].
     */
    val priority: Int = AnnotationPriorityRunPrecedence.REQUIRE_RUN_ON_PRECEDENCE
) {
    companion object {
        const val DEFAULT_KEY: String = "profileOwner"
        const val PROFILE_TYPE: String = "android.os.usertype.profile.MANAGED"
    }
}

@AutoAnnotation
fun requireRunOnWorkProfile(dpc: Query = workaroundQuery()): RequireRunOnWorkProfile {
    return AutoAnnotation_RequireRunOnWorkProfileKt_requireRunOnWorkProfile(dpc)
}
