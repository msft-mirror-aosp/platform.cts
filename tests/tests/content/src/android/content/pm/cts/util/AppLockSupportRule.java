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

package android.content.pm.cts.util;

import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A {@link TestRule} that skips tests annotated with {@link RequiresAppLockSupported} on devices
 * that do not support the App Lock feature.
 */
public final class AppLockSupportRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (description.getAnnotation(RequiresAppLockSupported.class) != null
                        || description.getTestClass().getAnnotation(RequiresAppLockSupported.class)
                                != null) {
                    Context context =
                            InstrumentationRegistry.getInstrumentation().getTargetContext();
                    assume().withMessage("Skipping test: App Lock is not supported on this device.")
                            .that(PackageTestUtils.shouldTestAppLock(context))
                            .isTrue();
                }
                base.evaluate();
            }
        };
    }
}
