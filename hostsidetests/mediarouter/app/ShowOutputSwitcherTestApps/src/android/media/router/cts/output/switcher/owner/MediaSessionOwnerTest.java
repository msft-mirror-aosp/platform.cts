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

package android.media.router.cts.output.switcher.owner;

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaRouter2;
import android.media.router.cts.output.switcher.creator.IMediaSessionCreator;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.IBinder;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class MediaSessionOwnerTest {

    private static final String CREATOR_PACKAGE_NAME =
            "android.media.router.cts.output.switcher.creator";
    private static final String CREATOR_SERVICE_NAME =
            CREATOR_PACKAGE_NAME + ".MediaSessionCreatorService";

    private Context mContext;
    private IMediaSessionCreator mCreatorService;
    private ActivityScenario<MediaSessionOwnerActivity> mActivityScenario;
    private final CompletableFuture<IBinder> mServiceBinderFuture = new CompletableFuture<>();

    private final ServiceConnection mServiceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mServiceBinderFuture.complete(service);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mCreatorService = null;
                }
            };

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        // Launch the activity so that the test gains foreground importance.
        mActivityScenario = ActivityScenario.launch(MediaSessionOwnerActivity.class);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(new ComponentName(CREATOR_PACKAGE_NAME, CREATOR_SERVICE_NAME));
        boolean bound =
                mContext.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        assertTrue("Failed to bind to creator service", bound);
        IBinder binder = mServiceBinderFuture.get(5, TimeUnit.SECONDS);
        mCreatorService = IMediaSessionCreator.Stub.asInterface(binder);
    }

    @After
    public void tearDown() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
        if (mCreatorService != null) {
            mContext.unbindService(mServiceConnection);
        }
        InstrumentationRegistry.getInstrumentation()
                .getContext()
                .sendBroadcast(
                        new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
    }

    @Test
    public void testShowSystemOutputSwitcherWithOverriddenOwner() throws Exception {
        assertNotNull("Creator service not bound", mCreatorService);
        MediaSession.Token token = mCreatorService.createSession(mContext.getPackageName());
        assertNotNull("Failed to create session and get token", token);
        MediaController controller = new MediaController(mContext, token);
        assertEquals(
                "Test app is not the owner of the session",
                mContext.getPackageName(),
                controller.getPackageName());

        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        boolean result = router.showSystemOutputSwitcher(token);
        assertTrue(
                "showSystemOutputSwitcher should return true when called from foreground owner",
                result);
    }
}
