/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.server.wm.settings;

import java.util.function.Supplier;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Test @Rule class that opens and closes an {@link AutoCloseable} around the test.
 */
class AutoCloseableRule implements TestRule {

    private final Supplier<AutoCloseable> mSupplier;

    public AutoCloseableRule(Supplier<AutoCloseable> supplier) {
        mSupplier = supplier;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (final AutoCloseable wrapper = mSupplier.get()) {
                    base.evaluate();
                }
            }
        };
    }
}
