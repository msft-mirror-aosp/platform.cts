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

package android.compilation.cts;

import static org.junit.Assume.assumeTrue;

import android.compilation.cts.annotation.CtsTestCase;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests Pre-reboot Dexopt.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@CtsTestCase
public class PreRebootDexoptTest extends BaseHostJUnit4Test {
    private Utils mUtils;

    @Before
    public void setUp() throws Exception {
        mUtils = new Utils(getTestInformation());
    }

    @Test
    public void test() throws Exception {
        assumeTrue("true".equals(getDevice().getProperty("dalvik.vm.enable_pr_dexopt")));

        mUtils.assertCommandSucceeds("pm art pr-dexopt-job --test");
    }
}
