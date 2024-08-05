/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.wm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to maintain the legacy task cleanup behavior for ActivityManagerTestBase tests.
 *
 * <p>This annotation marks tests that should retain the original behavior of removing all tasks
 * except the home launcher during the setUp and tearDown phases in ActivityManagerTestBase.
 * This is a transitional measure to ensure no CTS tests break during the cleanup process.
 * Tests marked with this annotation will still invoke
 * removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME) in the setUp and tearDown methods.
 *
 * <p>Usage:
 * {@literal @KeepLegacyTaskCleanup}
 * public class SomeCtsTest extends ActivityManagerTestBase {
 *   // Test methods
 * }
 *
 * @deprecated This annotation is intended for transitional use to maintain legacy task cleanup
 *      behavior in tests inheriting from ActivityManagerTestBase. It will be removed after the
 *      cleanup process is complete.
 */
// TODO(b/355452977): Change the default cleanup behavior in ActivityManagerTestBase (ag/28627379)
//     once all the ActivityManagerTestBase tests are marked as @KeepLegacyTaskCleanup or
//     confirm to not rely on the legacy task cleanup behavior.
// TODO(b/355452977): Clean up all usages of this interface.
@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface KeepLegacyTaskCleanup {}
