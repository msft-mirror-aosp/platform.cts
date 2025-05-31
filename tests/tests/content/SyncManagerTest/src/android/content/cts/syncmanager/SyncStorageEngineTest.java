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

package android.content.cts.syncmanager;

import static com.google.common.truth.Truth.assertThat;

import android.accounts.Account;
import android.content.Context;
import android.content.SyncStatusInfo;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.util.AtomicFile;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.content.SyncStorageEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@AppModeFull(reason = "Sync manager not supported")
@RunWith(AndroidJUnit4.class)
public final class SyncStorageEngineTest {
    private static final String ACCOUNT_NAME = "account1";
    private static final String ACCOUNT_TYPE = "type1";
    private static final String PROVIDER = "auth1";
    private static final int USER_ID = 0;
    private static final String SYSTEM_DIRECTORY_NAME = "system";
    private static final String SYNC_DIRECTORY_NAME = "sync";

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void ignore_testMalformedAuthority() throws Exception {
        Looper.prepare();
        // Authority id is non integer. It should be discarded by SyncStorageEngine.
        byte[] accountsFileData =
                ("""
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <accounts>
                <listenForTickles user="0" enabled="false" />\
                <listenForTickles user="1" enabled="true" />\
                <authority id="nonint" user="0" account="account1" type="type1"\
                 authority="auth1" />
                </accounts>
                """)
                        .getBytes();

        File syncDir = getSyncDir();
        boolean ignored = syncDir.mkdirs();
        AtomicFile accountInfoFile = new AtomicFile(new File(syncDir, "accounts.xml"));
        FileOutputStream fos = accountInfoFile.startWrite();
        fos.write(accountsFileData);
        accountInfoFile.finishWrite(fos);

        SyncStorageEngine engine = SyncStorageEngine.newTestInstance(mContext);
        Account account = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
        SyncStorageEngine.EndPoint endPoint =
                new SyncStorageEngine.EndPoint(account, PROVIDER, USER_ID);
        SyncStatusInfo info = engine.getStatusByAuthority(endPoint);
        assertThat(info).isNull();
    }

    private File getSyncDir() {
        return new File(
                new File(mContext.getFilesDir(), SYSTEM_DIRECTORY_NAME), SYNC_DIRECTORY_NAME);
    }
}
