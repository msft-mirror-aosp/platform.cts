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

package com.android.interactive.testrules;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.bedstead.nene.TestApis;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A {@link TestRule} that helps save the name of a test case in the context during test execution.
 */
public class TestNameSaver implements TestRule {

    public static final String INTERACTIVE_TEST_NAME = "INTERACTIVE_TEST_NAME";

    // packageName + className of a test class.
    private final String mPackageClass;

    public TestNameSaver(Class<?> testClass) {
        mPackageClass = testClass.getCanonicalName();
    }

    @Override
    public Statement apply(Statement base, Description description) {

        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                String testName = description.getMethodName();
                SharedPreferences sharedPref =
                        TestApis.context()
                                .instrumentedContext()
                                .getSharedPreferences(INTERACTIVE_TEST_NAME, Context.MODE_PRIVATE);
                boolean testNameUpdated =
                        sharedPref
                                .edit()
                                .putString(INTERACTIVE_TEST_NAME, mPackageClass + "#" + testName)
                                .commit();

                assertTrue(
                        "Failed to save the test name into the testing context.", testNameUpdated);

                base.evaluate();
            }
        };
    }
}
