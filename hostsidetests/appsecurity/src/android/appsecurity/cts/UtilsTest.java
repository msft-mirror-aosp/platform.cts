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
package android.appsecurity.cts;

import static android.appsecurity.cts.Utils.startUsersWith;

import static org.junit.Assert.assertThrows;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class UtilsTest {

    @Rule
    public final Expect expect = Expect.create();

    @Test
    public void testStartUsersWith_invalidArguments() {
        assertThrows(NullPointerException.class,
                // null
                () -> startUsersWith(42, null));
        assertThrows(IllegalArgumentException.class,
                // empty
                () -> startUsersWith(108, new int[0]));
        assertThrows(IllegalArgumentException.class,
                // not found
                () -> startUsersWith(108, new int[] {4, 8, 15, 16, 23, 42}));
        assertThrows(IllegalArgumentException.class,
                // duplicated values
                () -> startUsersWith(108, new int[] {42, 4, 8, 15, 16, 23, 42}));
    }

    @Test
    public void testStartUsersWith() {
        // Just 1 element
        expect.withMessage("startUsersWith(42, [42])").that(
                startUsersWith(42, new int[] { 42 })).asList().containsExactly(42).inOrder();

        // First is first
        expect.withMessage("startUsersWith(4, [4, 8, 15, 16, 23, 42])").that(
                startUsersWith(4, new int[] { 4, 8, 15, 16, 23, 42})).asList()
                .containsExactly(4, 8, 15, 16, 23, 42).inOrder();

        // First in the middle
        expect.withMessage("startUsersWith(15, [4, 8, 15, 16, 23, 42])").that(
                startUsersWith(15, new int[] { 4, 8, 15, 16, 23, 42})).asList()
                .containsExactly(15, 4, 8, 16, 23, 42).inOrder();

        // First is last
        expect.withMessage("startUsersWith(42, [4, 8, 15, 16, 23, 42])").that(
                startUsersWith(42, new int[] { 4, 8, 15, 16, 23, 42})).asList()
                .containsExactly(42, 4, 8, 15, 16, 23).inOrder();
    }
}
