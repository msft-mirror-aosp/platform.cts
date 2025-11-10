/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.cts.hint;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.stubs.R;
import android.app.stubs.shared.NotificationHelper;
import android.app.stubs.shared.TestNotificationListener;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.NotificationEvent;
import android.service.personalcontext.hint.NotificationHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/** Build/Install/Run: atest CtsPersonalContextTestCases:NotificationHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class NotificationHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    static final String STUB_PACKAGE_NAME = "android.personalcontext.cts";
    private static final String NOTIFICATION_CHANNEL_ID = "ContextHintTest";
    private static final NotificationChannel NOTIFICATION_CHANNEL =
            new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "ContextHintTest channel", IMPORTANCE_DEFAULT);

    private Context mContext;
    private TestNotificationListener mListener;
    private NotificationManager mNotificationManager;
    private NotificationHelper mNotificationHelper;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mNotificationHelper = new NotificationHelper(mContext);
        // clear the deck so that our getActiveNotifications results are predictable
        mNotificationManager.cancelAll();

        assertEquals(
                "Previous test left system in a bad state ",
                0,
                mNotificationManager.getActiveNotifications().length);

        // Ensure listener access isn't allowed before test runs (other tests could put
        // TestListener in an unexpected state)
        mNotificationHelper.disableListener(STUB_PACKAGE_NAME);
        mNotificationManager.createNotificationChannel(NOTIFICATION_CHANNEL);

        // Ensure that the tests are exempt from global service-related rate limits
        setEnableServiceNotificationRateLimit(false);
    }

    @After
    public void tearDown() throws Exception {
        setEnableServiceNotificationRateLimit(true);

        mNotificationManager.cancelAll();
        mNotificationHelper.disableListener(STUB_PACKAGE_NAME);
        mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.NotificationHint#getEventType",
                "android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent"
                        + "#getStatusBarNotification",
                "android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent"
                        + "#getNotificationChannel",
                "android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent"
                        + "#getRankingMap",
            })
    @Test
    public void testNotificationHintBundleUnbundle() throws Exception {
        mListener = mNotificationHelper.enableListener(STUB_PACKAGE_NAME);
        assertNotNull(mListener);

        // Send a notification so that we can get the StatusBarNotification and RankingMap objects.
        sendNotification(1, R.drawable.black);
        final StatusBarNotification notification =
                mNotificationHelper.findPostedNotification(
                        null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        final NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;

        final NotificationEvent.NotificationEnqueuedEvent enqueuedEvent =
                new NotificationEvent.NotificationEnqueuedEvent(
                        notification, NOTIFICATION_CHANNEL, rankingMap);
        final NotificationHint hint = new NotificationHint.Builder(enqueuedEvent).build();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(NotificationHint.class);
        final NotificationEvent outputEvent =
                ((NotificationHint) outputHint).getNotificationEvent();
        assertThat(outputEvent.getEventType()).isEqualTo(NotificationEvent.EVENT_TYPE_ENQUEUED);
        assertThat(outputEvent).isInstanceOf(NotificationEvent.NotificationEnqueuedEvent.class);

        final NotificationEvent.NotificationEnqueuedEvent outputEnqueuedEvent =
                (NotificationEvent.NotificationEnqueuedEvent) outputEvent;
        assertThat(outputEnqueuedEvent.getStatusBarNotification().getKey())
                .isEqualTo(notification.getKey());
        assertThat(outputEnqueuedEvent.getNotificationChannel().getId())
                .isEqualTo(NOTIFICATION_CHANNEL.getId());
        assertThat(outputEnqueuedEvent.getRankingMap()).isEqualTo(rankingMap);
    }

    private void setEnableServiceNotificationRateLimit(boolean enable) throws IOException {
        String command =
                "cmd activity fgs-notification-rate-limit " + (enable ? "enable" : "disable");

        mNotificationHelper.runCommand(command, InstrumentationRegistry.getInstrumentation());
    }

    private void sendNotification(final int id, final int icon) {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Telephony.Threads.CONTENT_URI);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setPackage(mContext.getPackageName());

        final PendingIntent pendingIntent =
                PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_MUTABLE);
        Notification.Builder nb =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(icon)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .setContentIntent(pendingIntent);

        final Notification notification = nb.build();
        mNotificationManager.notify(id, notification);

        assertNotNull(
                mNotificationHelper.findPostedNotification(
                        null, id, NotificationHelper.SEARCH_TYPE.APP));
    }

    /** Bundles then unbundles the given {@link ContextHint}. */
    public ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }
}
