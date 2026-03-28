/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext.cts;

import android.content.Context;
import android.service.personalcontext.PersonalContextManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class EnablePersonalContextTestRule implements TestRule {
    private final Context mContext;

    public EnablePersonalContextTestRule() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final PersonalContextManager personalContextManager =
                        mContext.getSystemService(PersonalContextManager.class);
                final boolean wasEnabled = personalContextManager.isEnabled();
                try {
                    personalContextManager.setEnabled(true);
                    base.evaluate();
                } finally {
                    personalContextManager.setEnabled(wasEnabled);
                }
            }
        };
    }
}
