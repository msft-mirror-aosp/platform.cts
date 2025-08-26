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

package android.cts.compatchanges.host;

import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

public class OwnedPhotosHostTest extends CompatChangeGatingTestCase {
    protected static final String TEST_APK = "CtsOwnedPhotosTestsApp.apk";
    protected static final String TEST_PKG = "android.cts.compatchanges.device";
    protected static final String TEST_CLASS_NAME = ".OwnedPhotosTest";
    protected static final long OWNED_PHOTOS_CHANGE_ID = 310703690L;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
    }

    public void testIsChangeEnabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "verifyCompatChangeIsEnabled",
                /*enabledChanges*/ ImmutableSet.of(OWNED_PHOTOS_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testIsChangeDisabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "verifyCompatChangeIsDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(OWNED_PHOTOS_CHANGE_ID));
    }

    public void testRevokeOwnershipWhenOwnedPhotosEnabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testRevokeOwnershipWhenOwnedPhotosEnabled",
                /*enabledChanges*/ ImmutableSet.of(OWNED_PHOTOS_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testRevokeOwnershipWhenOwnedPhotosDisabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testRevokeOwnershipWhenOwnedPhotosDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(OWNED_PHOTOS_CHANGE_ID));
    }

    public void testRenameOperationInSharedStorageForOwnedPhotos() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testRenameOperationInSharedStorageForOwnedPhotos",
                /*enabledChanges*/ ImmutableSet.of(OWNED_PHOTOS_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testRenameOperationInMediaDirectoryForOwnedPhotos() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testRenameOperationInMediaDirectoryForOwnedPhotos",
                /*enabledChanges*/ ImmutableSet.of(OWNED_PHOTOS_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }
}
