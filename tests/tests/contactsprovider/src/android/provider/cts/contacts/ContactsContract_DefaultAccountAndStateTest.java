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

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.provider.ContactsContract.RawContacts.DefaultAccountAndState;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;
import android.test.AndroidTestCase;

import androidx.test.filters.MediumTest;

@MediumTest
public class ContactsContract_DefaultAccountAndStateTest extends AndroidTestCase {
    private static final Account ACCT_1 = new Account("cp removal acct 1",
            StaticAccountAuthenticator.TYPE);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testDefaultContactsAccountClass_cloud() {
        DefaultAccountAndState defaultContactsAccount = new DefaultAccountAndState(
                DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD,
                ACCT_1);
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD,
                defaultContactsAccount.getState());
        assertEquals(ACCT_1, defaultContactsAccount.getCloudAccount());

        defaultContactsAccount = DefaultAccountAndState.ofCloud(ACCT_1);
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD,
                defaultContactsAccount.getState());
        assertEquals(ACCT_1, defaultContactsAccount.getCloudAccount());

        assertThrows(IllegalArgumentException.class, () ->
                new DefaultAccountAndState(
                        DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD, null));
    }

    public void testDefaultContactsAccountClass_local() {
        DefaultAccountAndState defaultContactsAccount = new DefaultAccountAndState(
                DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL,
                null);
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL,
                defaultContactsAccount.getState());
        assertNull(defaultContactsAccount.getCloudAccount());

        defaultContactsAccount = DefaultAccountAndState.ofLocal();
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL,
                defaultContactsAccount.getState());
        assertNull(defaultContactsAccount.getCloudAccount());

        assertThrows(IllegalArgumentException.class, () ->
                new DefaultAccountAndState(
                        DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL,
                        ACCT_1));
    }

    public void testDefaultContactsAccountClass_notSet() {
        DefaultAccountAndState defaultContactsAccount = new DefaultAccountAndState(
                DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_NOT_SET,
                null);
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_NOT_SET,
                defaultContactsAccount.getState());
        assertNull(defaultContactsAccount.getCloudAccount());

        defaultContactsAccount = DefaultAccountAndState.ofNotSet();
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_NOT_SET,
                defaultContactsAccount.getState());
        assertNull(defaultContactsAccount.getCloudAccount());

        assertThrows(IllegalArgumentException.class, () ->
                new DefaultAccountAndState(
                        DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_NOT_SET,
                        ACCT_1));
    }

    public void testDefaultContactsAccountClass_invalid() {
        assertThrows(IllegalArgumentException.class, () ->
                new DefaultAccountAndState(
                        /*state=*/ -1,
                        null));

        assertThrows(IllegalArgumentException.class, () ->
                new DefaultAccountAndState(
                        /*state=*/ -1,
                        ACCT_1));
    }
}
