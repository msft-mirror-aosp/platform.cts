/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.bedstead.multiuser.annotations

import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.REQUIRE_RUN_ON_PRECEDENCE
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.android.bedstead.multiuser.annotations.meta.EnsureHasProfileAnnotation
import com.android.bedstead.nene.types.OptionalBoolean

/**
 * Mark that a test method should run on a user which has a private profile.
 *
 * Your test configuration may be configured so that this test is only run on a user which has
 * a private profile. Otherwise, you can use DeviceState to ensure that the device enters
 * the correct state for the method.
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
@EnsureHasProfileAnnotation("android.os.usertype.profile.PRIVATE")
@RequirePrivateSpaceSupported
@UsesAnnotationExecutor(UsesAnnotationExecutor.MULTI_USER)
annotation class EnsureHasPrivateProfile(

    /**
     * Which user type the private profile should be attached to.
     */
    val forUser: UserType = UserType.INITIAL_USER,

    /**
     * Whether the instrumented test app should be installed in the private profile.
     */
    val installInstrumentedApp: OptionalBoolean = OptionalBoolean.ANY,

    /**
     * Should we ensure that we are switched to the parent of the profile.
     */
    val switchedToParentUser: OptionalBoolean = OptionalBoolean.ANY,

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
    // Must be before RequireRunOn to ensure users exist
    val priority: Int = ENSURE_HAS_PRIVATE_PROFILE_PRECEDENCE
) {
    companion object {
        const val ENSURE_HAS_PRIVATE_PROFILE_PRECEDENCE: Int = REQUIRE_RUN_ON_PRECEDENCE - 1
    }
}
