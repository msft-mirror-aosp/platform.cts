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

package android.provider.cts.contacts;

import static android.provider.Flags.FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract.RawContacts.DefaultAccount;
import android.provider.ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState;
import android.provider.ContactsContract.SimAccount;
import android.provider.ContactsContract.SimContacts;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ContactsContract_DefaultAccountTest {
    // Using unique account name and type because these tests may break or be broken by
    // other tests running. No other tests should use the following accounts.
    private static final Account ACCT_1 = new Account("test for default account1",
            StaticAccountAuthenticator.TYPE);
    private static final Account ACCT_2 = new Account("test for default account2",
            StaticAccountAuthenticator.TYPE);
    private static final Account ACCT_NOT_PRESENT = new Account("test for account not signed in",
            StaticAccountAuthenticator.TYPE);
    private static final String SIM_ACCT_NAME = "sim account name for default account test";
    private static final String SIM_ACCT_TYPE = "sim account type for default account test";
    private static final Account SIM_ACCT = new Account(SIM_ACCT_NAME, SIM_ACCT_TYPE);
    private static final int SIM_SLOT_0 = 0;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private Context mContext;
    private ContentResolver mResolver;
    private AccountManager mAccountManager;

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mResolver = getContext().getContentResolver();
        mAccountManager = AccountManager.get(getContext());

        mAccountManager.addAccountExplicitly(ACCT_1, null, null);
        mAccountManager.addAccountExplicitly(ACCT_2, null, null);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME, SIM_ACCT_TYPE, SIM_SLOT_0,
                    SimAccount.ADN_EF_TYPE);
        });
    }

    @After
    public void tearDown() throws Exception {
        mAccountManager.removeAccount(ACCT_1, null, null);
        mAccountManager.removeAccount(ACCT_2, null, null);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_0);
        });

        SystemUtil.runWithShellPermissionIdentity(() -> {
            setDefaultAccountForNewContacts(DefaultAccountAndState.ofNotSet());
        });
    }

    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @Test
    public void testInitialDefaultAccountState() {
        DefaultAccountAndState defaultAccountAndState = getDefaultAccountForNewContacts();
        assertEquals(DefaultAccountAndState.ofNotSet(), defaultAccountAndState);
    }


    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccountInLocalState() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            setDefaultAccountForNewContacts(DefaultAccountAndState.ofLocal());
        });
        assertEquals(DefaultAccountAndState.ofLocal(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_cloud_accountIsNotSignedIn() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThrows(IllegalArgumentException.class, () -> setDefaultAccountForNewContacts(
                    DefaultAccountAndState.ofCloud(ACCT_NOT_PRESENT)));
        });
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_cloud_accountIsSignedIn() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofCloud(ACCT_1)));
        assertEquals(DefaultAccountAndState.ofCloud(ACCT_1), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_cloud_invalidCloudAccounts() {
        // SIM_ACCT is not a SIM account, which cannot be set as default account with the state SIM.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThrows(IllegalArgumentException.class, () -> setDefaultAccountForNewContacts(
                    DefaultAccountAndState.ofCloud(SIM_ACCT)));
        });
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_sim() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            setDefaultAccountForNewContacts(DefaultAccountAndState.ofSim(SIM_ACCT));
        });
        assertEquals(DefaultAccountAndState.ofSim(SIM_ACCT), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_sim_invalidSimAccount() {
        // ACCT_1 is not a SIM account, which cannot be set as default account with the state SIM.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThrows(IllegalArgumentException.class,
                    () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofSim(ACCT_1)));
        });
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_getCloudEligibleAccounts() {
        HashSet<String> eligibleCloudAccountTypes = new HashSet<>(
                Arrays.asList(configuredEligibleCloudAccountTypes()));

        Set<Account> accountsOtherThanEligibleCloudAccounts = new HashSet<>(
                Arrays.asList(mAccountManager.getAccounts()));

        SystemUtil.runWithShellPermissionIdentity(() -> {
            List<Account> eligibleCloudAccounts = getEligibleCloudAccounts();

            // Check that very eligible cloud account must be with the eligible cloud account type.
            for (Account account : eligibleCloudAccounts) {
                assertTrue(eligibleCloudAccountTypes.contains(account.type));
                accountsOtherThanEligibleCloudAccounts.remove(account);
            }

            // Check that no account other than those returned by getEligibleCloudAccounts
            // come with an eligible cloud account type.
            for (Account account : accountsOtherThanEligibleCloudAccounts) {
                assertFalse(eligibleCloudAccountTypes.contains(account.type));
            }
        });
    }

    private DefaultAccountAndState getDefaultAccountForNewContacts() {
        return DefaultAccount.getDefaultAccountForNewContacts(mResolver);
    }

    private void setDefaultAccountForNewContacts(DefaultAccountAndState defaultAccountAndState) {
        DefaultAccount.setDefaultAccountForNewContacts(mResolver, defaultAccountAndState);
    }

    private String[] configuredEligibleCloudAccountTypes() {
        // Get com.android.internal.R.array.config_rawContactsEligibleDefaultAccountTypes
        int resId = getContext().getResources().getIdentifier(
                "config_rawContactsEligibleDefaultAccountTypes", "array", "android");
        return getContext().getResources().getStringArray(resId);
    }

    private List<Account> getEligibleCloudAccounts() {
        return DefaultAccount.getEligibleCloudAccounts(mResolver);
    }

    private Context getContext() {
        return mContext;
    }
}
