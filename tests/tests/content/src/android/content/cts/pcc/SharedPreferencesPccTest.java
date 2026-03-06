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

package android.content.cts.pcc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PCC variant of SharedPreferences tests. Re-implements core CTS logic using public APIs to verify
 * storage behavior within the Private Compute Core sandbox environment.
 */
@RunWith(AndroidJUnit4.class)
public class SharedPreferencesPccTest {
    private Context mContext;
    private static final String PREF_NAME = "pcc_storage_test";

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Start each test with a clean state
        mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void testBasicCrud() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = "pcc_test_key";
        String value = "pcc_test_value";

        // Verify write (using commit for synchronous validation)
        assertTrue(
                "Failed to write to SharedPreferences in PCC sandbox",
                prefs.edit().putString(key, value).commit());

        // Verify read
        assertTrue(prefs.contains(key));
        assertEquals("Value mismatch in PCC sandboxed storage", value, prefs.getString(key, null));
    }

    @Test
    public void testClear() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("key1", "val1").putString("key2", "val2").commit();

        assertTrue(prefs.edit().clear().commit());
        assertFalse("Data persisted after clear() in PCC sandbox", prefs.contains("key1"));
        assertEquals(0, prefs.getAll().size());
    }

    @Test
    public void testListenerFires() throws Exception {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        final CountDownLatch latch = new CountDownLatch(1);
        final String targetKey = "trigger_key";

        SharedPreferences.OnSharedPreferenceChangeListener listener =
                (p, key) -> {
                    if (targetKey.equals(key)) {
                        latch.countDown();
                    }
                };

        prefs.registerOnSharedPreferenceChangeListener(listener);
        try {
            prefs.edit().putString(targetKey, "event").apply();
            assertTrue(
                    "Listener was not notified of change in PCC sandbox",
                    latch.await(5, TimeUnit.SECONDS));
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}
