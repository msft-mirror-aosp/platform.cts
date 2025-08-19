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

package android.provider.cts.contacts;

import static android.provider.Flags.FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Settings.AccountAttributes;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ContactsContract_AccountAttributesTest {
    // Using unique account name and type because these tests may break or be broken by
    // other tests running. No other tests should use the following accounts.
    private Account mAccount1;
    private Account mAccount2;

    private Account mSimAccount1;
    private static final Account ACCT_NOT_PRESENT =
            new Account(
                    "test for account attributes not signed in", StaticAccountAuthenticator.TYPE);
    private static final Account ACCT_WITH_TYPE_UNAUTHENTICATED =
            new Account("test for account attributes 3", "type.unauthenticated");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private ContentResolver mResolver;
    private AccountManager mAccountManager;

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mResolver = getContext().getContentResolver();
        mAccountManager = AccountManager.get(getContext());

        mAccount1 =
                new Account(
                        "test for account attributes 1" + System.currentTimeMillis(),
                        StaticAccountAuthenticator.TYPE);
        mAccount2 =
                new Account(
                        "test for account attributes 2 " + System.currentTimeMillis(),
                        StaticAccountAuthenticator.TYPE);

        mAccountManager.addAccountExplicitly(mAccount1, null, null);
        mAccountManager.addAccountExplicitly(mAccount2, null, null);

        mSimAccount1 =
                new Account(
                        "ContactsContract_AccountAttributesTest SIM name "
                                + System.currentTimeMillis(),
                        "ContactsContract_AccountAttributesTest SIM type");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    ContactsContract.SimContacts.addSimAccount(
                            mResolver,
                            mSimAccount1.name,
                            mSimAccount1.type,
                            0,
                            ContactsContract.SimAccount.ADN_EF_TYPE);
                });
        // Waiting a short while so that accounts are arrived on the device.
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @After
    public void tearDown() throws Exception {
        mAccountManager.removeAccount(mAccount1, null, null);
        mAccountManager.removeAccount(mAccount2, null, null);

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    ContactsContract.SimContacts.removeSimAccounts(mResolver, 0);
                });
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testSetAndGetAccountAttributes_invalidAccount() {
        // Expects exception when getting account attributes for invalid account.
        assertThrows(
                IllegalArgumentException.class,
                () -> getAccountAttributesInternal(mResolver, ACCT_NOT_PRESENT, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> getAccountAttributesInternal(mResolver, ACCT_NOT_PRESENT, "preload"));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testSetAndGetAccountAttributes_localAndSimAccount() {
        assertThat(getAccountAttributesInternal(mResolver, getLocalAccount(), null))
                .isEqualTo(AccountAttributes.ATTRIBUTE_DATA_ORIGIN_LOCAL);

        assertThat(getAccountAttributesInternal(mResolver, mSimAccount1, null))
                .isEqualTo(AccountAttributes.ATTRIBUTE_DATA_ORIGIN_SIM);
    }

    private Account getLocalAccount() {
        String accountName = ContactsContract.RawContacts.getLocalAccountName(mContext);
        String accountType = ContactsContract.RawContacts.getLocalAccountType(mContext);

        assertFalse(accountName != null ^ accountType != null);
        if (accountName == null) {
            return null;
        } else {
            return new Account(accountName, accountType);
        }
    }

    /** Verifies that updating attributes with no changes is accepted. */
    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testUpdateAccountAttributes_newAttributesSameAsPrevious_Ok() {
        long previousAttributes = getAccountAttributesInternal(mResolver, mAccount1, null);
        setAccountAttributesInternal(mResolver, mAccount1, null, previousAttributes);
        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null))
                .isEqualTo(previousAttributes);
    }

    /** Verifies that using an undefined attribute bit throws an IllegalArgumentException. */
    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testUpdateAccountAttributes_throwsExceptionOnUndefinedBit() {
        final long undefinedBit = 1L << 60; // Use a bit far outside the defined range.

        long previousAttributes = getAccountAttributesInternal(mResolver, mAccount1, null);
        // Expect an exception when trying to add an undefined bit.
        assertThrows(
                "Adding an undefined attribute bit should be rejected",
                IllegalArgumentException.class,
                () -> setAccountAttributesInternal(mResolver, mAccount1, null, undefinedBit));

        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null))
                .isEqualTo(previousAttributes);
    }

    /**
     * Verifies that an update resulting in a semantic conflict (e.g., multiple bits set in a
     * single-choice category) throws an IllegalStateException.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testUpdateAccountAttributes_throwsExceptionOnSemanticConflict() {
        // Conflict DATA_ORIGIN attributes.
        assertThrows(
                "Adding a conflicting DATA_ORIGIN should fail",
                IllegalStateException.class,
                () ->
                        setAccountAttributesInternal(
                                mResolver,
                                mAccount1,
                                null,
                                AccountAttributes.ATTRIBUTE_DATA_ORIGIN_SIM
                                        | AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD));

        // No conflict to have both SYNC_MODE.
        setAccountAttributesInternal(
                mResolver,
                mAccount1,
                null,
                AccountAttributes.ATTRIBUTE_SYNC_MODE_UP_SYNC
                        | AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC);
        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null))
                .isEqualTo(
                        AccountAttributes.ATTRIBUTE_SYNC_MODE_UP_SYNC
                                | AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC);
    }

    /**
     * Verifies that an update resulting in a semantic conflict (e.g., multiple bits set in a
     * single-choice category) throws an IllegalStateException.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testUpdateAccountAttributes_onNonAuthenticatedAccountTypes() {
        // Conflict DATA_ORIGIN attributes.
        assertThrows(
                "setAccountAttributes on account types not authenticated by this package should"
                        + " fail",
                SecurityException.class,
                () ->
                        setAccountAttributesInternal(
                                mResolver,
                                ACCT_WITH_TYPE_UNAUTHENTICATED,
                                null,
                                AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD));

        assertThrows(
                "resetAccountAttributes on account types not authenticated by this package should"
                        + " fail",
                SecurityException.class,
                () ->
                        resetAccountAttributesInternal(
                                mResolver, ACCT_WITH_TYPE_UNAUTHENTICATED, null));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testSetAndGetAccountAttributes() {
        // Initially the account attributes is 0.
        long initialAttributes1 = getAccountAttributesInternal(mResolver, mAccount1, null);
        long initialAttributes2 = getAccountAttributesInternal(mResolver, mAccount2, null);
        setAccountAttributesInternal(
                mResolver,
                mAccount1,
                null,
                AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD
                        | AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC);

        // ACCT_1's attributes should be updated.
        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null))
                .isEqualTo(
                        AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD
                                | AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC);

        // ACCT_2's attributes should  not be updated.
        assertThat(getAccountAttributesInternal(mResolver, mAccount2, null))
                .isEqualTo(initialAttributes2);

        // Set ACCT_1's attributes to 0
        setAccountAttributesInternal(mResolver, mAccount1, null, 0L);
        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null)).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_ACCOUNT_ATTRIBUTES_API_ENABLED})
    public void testResetAccountAttributes_revertsToDefault() {
        // Verify initial state is the default (cloud-based).
        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null))
                .isEqualTo(AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD);

        // Set a non-default attribute.
        setAccountAttributesInternal(
                mResolver, mAccount1, null, AccountAttributes.ATTRIBUTE_DATA_ORIGIN_LOCAL);

        // Verify the attribute was set.
        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null))
                .isEqualTo(AccountAttributes.ATTRIBUTE_DATA_ORIGIN_LOCAL);

        // Now, reset the attributes.
        resetAccountAttributesInternal(mResolver, mAccount1, null);

        // Verify the attributes have been reverted to the system-evaluated default.
        assertThat(getAccountAttributesInternal(mResolver, mAccount1, null))
                .isEqualTo(AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD);
    }

    private static long getAccountAttributesInternal(
            ContentResolver resolver, Account account, String dataSet) {
        return ContactsContract.Settings.getAccountAttributes(resolver, account, dataSet);
    }

    private static void setAccountAttributesInternal(
            ContentResolver resolver, Account account, String dataSet, long attributes) {
        ContactsContract.Settings.setAccountAttributes(resolver, account, dataSet, attributes);
    }

    private static void resetAccountAttributesInternal(
            ContentResolver resolver, Account account, String dataSet) {
        ContactsContract.Settings.resetAccountAttributes(resolver, account, dataSet);
    }

    private Context getContext() {
        return mContext;
    }
}
