/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.content.cts.contentresolver;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentResolver;
import android.content.SyncAdapterType;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Sync manager not supported")
@RunWith(AndroidJUnit4.class)
public final class ContentResolverSyncTest {
    private static final String TAG = ContentResolverSyncTest.class.getSimpleName();

    private static final String AUTHORITY = "android.content.cts.contentresolver.authority";

    private static final Account ACCOUNT =
            new Account(
                    MockAccountAuthenticator.ACCOUNT_NAME, MockAccountAuthenticator.ACCOUNT_TYPE);

    private static final int INITIAL_SYNC_TIMEOUT_MS = 60 * 1000;
    private static final int CANCEL_TIMEOUT_MS = 60 * 1000;
    private static final int LATCH_TIMEOUT_MS = 5000;

    private AccountManager mAccountManager;

    @Before
    public void setUp() {
        getMockSyncAdapter();
        mAccountManager =
                AccountManager.get(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void tearDown() throws Exception {
        getMockSyncAdapter().clearData();

        // Need to clean up created account
        removeAccount(mAccountManager);

        // Need to cancel any sync that was started.
        cancelSync(null);
    }

    private static MockSyncAdapter getMockSyncAdapter() {
        return MockSyncAdapter.getMockSyncAdapter();
    }

    private void addAccountExplicitly() {
        assertThat(
                        mAccountManager.addAccountExplicitly(
                                ACCOUNT, MockAccountAuthenticator.ACCOUNT_PASSWORD, null))
                .isTrue();
    }

    private boolean removeAccount(AccountManager am)
            throws IOException, AuthenticatorException, OperationCanceledException {
        AccountManagerFuture<Boolean> futureBoolean =
                am.removeAccount(ACCOUNT, null, null /* handler */);
        Boolean resultBoolean = futureBoolean.getResult();
        assertThat(futureBoolean.isDone()).isTrue();

        return resultBoolean;
    }

    private CountDownLatch setNewLatch(CountDownLatch latch) {
        getMockSyncAdapter().clearData();
        getMockSyncAdapter().setLatch(latch);
        return latch;
    }

    private void addAccountAndVerifyInitSync() {
        CountDownLatch latch = setNewLatch(new CountDownLatch(1));

        addAccountExplicitly();

        // Wait with timeout for the callback to do its work
        try {
            if (!latch.await(INITIAL_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                assertWithMessage("should not time out waiting on latch").fail();
            }
        } catch (InterruptedException e) {
            assertWithMessage("should not throw an InterruptedException").fail();
        }

        assertThat(getMockSyncAdapter().isStartSync()).isFalse();
        assertThat(getMockSyncAdapter().isCancelSync()).isFalse();
        assertThat(getMockSyncAdapter().isInitialized()).isTrue();
        assertThat(getMockSyncAdapter().getAccounts().get(0)).isEqualTo(ACCOUNT);
        assertThat(getMockSyncAdapter().getAuthority()).isEqualTo(AUTHORITY);
    }

    private void cancelSync(Account account) {
        CountDownLatch latch = setNewLatch(new CountDownLatch(1));

        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);

        ContentResolver.cancelSync(account, AUTHORITY);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            assertWithMessage("should not throw an InterruptedException").fail();
        }
        // Make sure the sync manager thinks the sync finished.

        final long timeout = SystemClock.uptimeMillis() + CANCEL_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < timeout) {
            if (!ContentResolver.isSyncActive(ACCOUNT, AUTHORITY)
                    && !ContentResolver.isSyncPending(ACCOUNT, AUTHORITY)) {
                break;
            }
            Log.i(TAG, "Waiting for sync to finish...");
            SystemClock.sleep(300);
        }
    }

    private void requestSync(Account account) {
        CountDownLatch latch = setNewLatch(new CountDownLatch(1));

        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);

        ContentResolver.requestSync(account, AUTHORITY, extras);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            assertWithMessage("should not throw an InterruptedException").fail();
        }
    }

    private void setIsSyncable() {
        ContentResolver.setIsSyncable(ACCOUNT, AUTHORITY, 1);
    }

    /** Test a sync request */
    @Test
    public void testRequestSync() {
        // Prevent auto sync
        ContentResolver.setMasterSyncAutomatically(false);
        assertThat(ContentResolver.getMasterSyncAutomatically()).isFalse();

        addAccountAndVerifyInitSync();

        getMockSyncAdapter().clearData();

        setIsSyncable();
        cancelSync(ACCOUNT);

        getMockSyncAdapter().clearData();

        requestSync(ACCOUNT);

        assertThat(getMockSyncAdapter().isStartSync()).isTrue();
        assertThat(getMockSyncAdapter().isCancelSync()).isFalse();
        assertThat(getMockSyncAdapter().isInitialized()).isFalse();
        assertThat(getMockSyncAdapter().getAccounts().get(0)).isEqualTo(ACCOUNT);
        assertThat(getMockSyncAdapter().getAuthority()).isEqualTo(AUTHORITY);
    }

    /** Test a sync cancel */
    @Test
    public void testCancelSync() {
        // Prevent auto sync
        ContentResolver.setMasterSyncAutomatically(false);
        assertThat(ContentResolver.getMasterSyncAutomatically()).isFalse();

        addAccountAndVerifyInitSync();

        getMockSyncAdapter().clearData();

        setIsSyncable();
        requestSync(ACCOUNT);

        getMockSyncAdapter().clearData();

        cancelSync(ACCOUNT);

        assertThat(getMockSyncAdapter().isStartSync()).isFalse();
        assertThat(getMockSyncAdapter().isCancelSync()).isTrue();
        assertThat(getMockSyncAdapter().isInitialized()).isFalse();

        assertThat(ContentResolver.isSyncActive(ACCOUNT, AUTHORITY)).isFalse();
        assertThat(ContentResolver.isSyncPending(ACCOUNT, AUTHORITY)).isFalse();
    }

    /** Test if we can set and get the MasterSyncAutomatically switch */
    @Test
    public void testGetAndSetMasterSyncAutomatically() {
        ContentResolver.setMasterSyncAutomatically(true);
        assertThat(ContentResolver.getMasterSyncAutomatically()).isTrue();

        ContentResolver.setMasterSyncAutomatically(false);
        assertThat(ContentResolver.getMasterSyncAutomatically()).isFalse();
    }

    /** Test if we can set and get the SyncAutomatically switch for an account */
    @Test
    public void testGetAndSetSyncAutomatically() throws InterruptedException {
        // Prevent auto sync
        ContentResolver.setMasterSyncAutomatically(false);
        assertThat(ContentResolver.getMasterSyncAutomatically()).isFalse();

        ContentResolver.setSyncAutomatically(ACCOUNT, AUTHORITY, false);
        assertThat(ContentResolver.getSyncAutomatically(ACCOUNT, AUTHORITY)).isFalse();

        ContentResolver.setSyncAutomatically(ACCOUNT, AUTHORITY, true);
        assertThat(ContentResolver.getSyncAutomatically(ACCOUNT, AUTHORITY)).isTrue();
    }

    /** Test if we can set and get the IsSyncable switch for an account */
    @Test
    public void testGetAndSetIsSyncable() {
        // Prevent auto sync
        ContentResolver.setMasterSyncAutomatically(false);
        assertThat(ContentResolver.getMasterSyncAutomatically()).isFalse();

        addAccountExplicitly();

        ContentResolver.setIsSyncable(ACCOUNT, AUTHORITY, 2);
        assertThat(ContentResolver.getIsSyncable(ACCOUNT, AUTHORITY)).isGreaterThan(0);

        ContentResolver.setIsSyncable(ACCOUNT, AUTHORITY, 1);
        assertThat(ContentResolver.getIsSyncable(ACCOUNT, AUTHORITY)).isGreaterThan(0);

        ContentResolver.setIsSyncable(ACCOUNT, AUTHORITY, 0);
        assertThat(ContentResolver.getIsSyncable(ACCOUNT, AUTHORITY)).isEqualTo(0);

        ContentResolver.setIsSyncable(ACCOUNT, AUTHORITY, -1);
        assertThat(ContentResolver.getIsSyncable(ACCOUNT, AUTHORITY)).isLessThan(0);

        ContentResolver.setIsSyncable(ACCOUNT, AUTHORITY, -2);
        assertThat(ContentResolver.getIsSyncable(ACCOUNT, AUTHORITY)).isLessThan(0);
    }

    /** Test if we can get the sync adapter types */
    @Test
    public void testGetSyncAdapterTypes() {
        SyncAdapterType[] types = ContentResolver.getSyncAdapterTypes();
        assertThat(types).isNotNull();
        int length = types.length;
        assertThat(length).isGreaterThan(0);
        boolean found = false;
        for (SyncAdapterType type : types) {
            if (MockAccountAuthenticator.ACCOUNT_TYPE.equals(type.accountType)
                    && AUTHORITY.equals(type.authority)) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    /** Test if a badly formed sync request is throwing exceptions */
    @Test
    public void testStartSyncFailure() {
        assertThrows(
                "did not throw IllegalArgumentException when extras is null.",
                IllegalArgumentException.class,
                () -> ContentResolver.requestSync(null, null, null));
    }

    /** Test validate sync extra bundle */
    @Test
    public void testValidateSyncExtrasBundle() {
        Bundle extras = new Bundle();
        extras.putInt("Integer", 20);
        extras.putLong("Long", 10L);
        extras.putBoolean("Boolean", true);
        extras.putFloat("Float", 5.5f);
        extras.putDouble("Double", 2.5);
        extras.putString("String", MockAccountAuthenticator.ACCOUNT_NAME);
        extras.putCharSequence("CharSequence", null);

        ContentResolver.validateSyncExtrasBundle(extras);

        extras.putChar("Char", 'a'); // type Char is invalid
        assertThrows(
                "did not throw IllegalArgumentException when extras is invalid.",
                IllegalArgumentException.class,
                () -> ContentResolver.validateSyncExtrasBundle(extras));
    }

    /** Test to verify that a SyncAdapter is called on all the accounts accounts */
    @Test
    public void testCallMultipleAccounts() {
        // Prevent auto sync
        ContentResolver.setMasterSyncAutomatically(false);
        assertThat(ContentResolver.getMasterSyncAutomatically()).isFalse();

        addAccountAndVerifyInitSync();

        getMockSyncAdapter().clearData();

        setIsSyncable();
        cancelSync(ACCOUNT);

        getMockSyncAdapter().clearData();

        requestSync(null /* all accounts */);

        assertThat(getMockSyncAdapter().isStartSync()).isTrue();
        assertThat(getMockSyncAdapter().isCancelSync()).isFalse();
        assertThat(getMockSyncAdapter().isInitialized()).isFalse();
        assertThat(getMockSyncAdapter().getAccounts().get(0)).isEqualTo(ACCOUNT);
        assertThat(getMockSyncAdapter().getAuthority()).isEqualTo(AUTHORITY);
    }
}
