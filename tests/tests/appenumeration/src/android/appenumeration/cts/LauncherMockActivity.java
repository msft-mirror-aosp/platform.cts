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

package android.appenumeration.cts;

import android.app.Activity;

/**
 * A mock activity used to satisfy technical eligibility requirements for the Home role
 * ({@link android.app.role.RoleManager#ROLE_HOME}).
 *
 * <p>By declaring {@link android.content.Intent#CATEGORY_HOME} in the manifest, the
 * test package can be granted the Home role. This is required to receive sensitive
 * events and callbacks which the system restricts to the active launcher.</p>
 */
public class LauncherMockActivity extends Activity {
}
