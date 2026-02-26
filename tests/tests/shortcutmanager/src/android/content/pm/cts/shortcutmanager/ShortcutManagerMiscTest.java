/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.content.pm.cts.shortcutmanager;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getIconSize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@CddTest(requirement="3.8.1/C-4-1")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ShortcutManagerMiscTest extends ShortcutManagerCtsTestsBase {

    @Test
    public void testMiscApis() throws Exception {
        ShortcutManager manager = getTestContext().getSystemService(ShortcutManager.class);

        assertTrue(5 <= mMaxShortcuts && mMaxShortcuts <= 100);

        // during the test, this process always considered to be in the foreground.
        assertFalse(manager.isRateLimitingActive());

        final int iconDimension = getIconSize(getInstrumentation());
        assertEquals(iconDimension, manager.getIconMaxWidth());
        assertEquals(iconDimension, manager.getIconMaxHeight());
    }

    @Test
    public void testExcludedFromFields() throws Exception {
        final ShortcutInfo s1 = makeShortcut("s1");
        final ShortcutInfo s2 = makeShortcutExcludedFromLauncher("s2");
        assertFalse(s1.isExcludedFromSurfaces(ShortcutInfo.SURFACE_LAUNCHER));
        assertTrue(s2.isExcludedFromSurfaces(ShortcutInfo.SURFACE_LAUNCHER));
        assertEquals(0, s1.getExcludedFromSurfaces());
        assertEquals(ShortcutInfo.SURFACE_LAUNCHER, s2.getExcludedFromSurfaces());
    }
}
