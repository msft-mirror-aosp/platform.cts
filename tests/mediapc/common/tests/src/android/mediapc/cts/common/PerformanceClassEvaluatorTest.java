/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PerformanceClassEvaluatorTest {

   @Rule public final TestName testName = new TestName();

    @Test
    public void constructorTest_replacesNullWithEmpty() {
        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(new FakeTestName(null));
        assertThat(pce.getTestName()).isEqualTo("");
    }

    @Test
    public void constructorTest_replacesCurlyBraces() {
        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(new FakeTestName("{}"));
        assertThat(pce.getTestName()).isEqualTo("()");
    }

      private static final class FakeTestName extends TestName {
        private final String mMethodName;

        FakeTestName(String methodName) {
            mMethodName = methodName;
        }

        @Override
        public String getMethodName() {
            return mMethodName;
        }
    }
}
