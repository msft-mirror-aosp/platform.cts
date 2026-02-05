/**
 * Copyright (c) 2026, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.cts.testapp;

import android.content.ComponentName;
import android.service.personalcontext.cts.testapp.ITestAppComponentListener;

/**
 * Controller API for the test app harness.
 */
interface ITestAppController {
    void disableAllComponents();
    void registerComponent(in ComponentName componentName, in ITestAppComponentListener listener);
    void enableRegisteredComponents();

    List<ComponentName> getRefiners();
    List<ComponentName> getUnderstanders();
    List<ComponentName> getRenderers();

    void flush();
}