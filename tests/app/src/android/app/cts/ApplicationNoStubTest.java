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

package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentCallbacks2;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

/**
 * Test {@link Application} that doesn't depend on the stub app, separated out for Ravenwood.
 *
 * @see ApplicationNoStubTest
 */
public class ApplicationNoStubTest {
    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    @Test
    public void testAppPackageName() {
        var context = sInstrumentation.getTargetContext();
        var app = context.getApplicationContext();

        assertEquals(context.getPackageName(), app.getPackageName());
    }

    @Test
    public void testOnTrimMemory() {
        final int level = 2;
        Application app = new Application();
        ComponentCallbacks2 mockCallBack2 = mock(ComponentCallbacks2.class, CALLS_REAL_METHODS);
        app.registerComponentCallbacks(mockCallBack2);

        app.onTrimMemory(level);

        verify(mockCallBack2).onTrimMemory(level);
    }
}
