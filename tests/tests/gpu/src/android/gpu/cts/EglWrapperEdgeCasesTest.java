/*
 * Copyright 2025 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.opengl.EGL14;
import android.opengl.EGLDisplay;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test various edge cases of the EGL JNI wrapper.
 *
 * These cases are separated from the larger EGL14Test/EGL15Test because they exercise details of
 * display lifetime etc, which those tests otherwise want to handle in a uniform way in setup/teardown.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class EglWrapperEdgeCasesTest {

   @Test
   public void TestEglVersionArrayHandling() {
       EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
       assertNotEquals(EGL14.EGL_NO_DISPLAY, display);

       int error = EGL14.eglGetError();
       assertEquals(EGL14.EGL_SUCCESS, error);

       // use the SAME array for both the major and minor parts of the version.
       // some versions of the platform mishandle this on the JNI side and end up
       // clobbering one part of the version with the other.
       int[] version = new int[2];
       assertTrue(EGL14.eglInitialize(display, version, 0, version, 1));
       error = EGL14.eglGetError();
       assertEquals(EGL14.EGL_SUCCESS, error);

       EGL14.eglTerminate(display);

       // The version should be either 1.4 or 1.5
       assertEquals(1, version[0]);
       assertTrue("Minor version should be either 4 or 5 but is " + Integer.toString(version[1]),
               version[1] == 4 || version[1] == 5);
   }

}
