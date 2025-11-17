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
package android.app.notification.current.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.app.Notification;
import android.app.Notification.ProjectedExtender;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RequiresFlagsEnabled(Flags.FLAG_API_PROJECTED_EXTENDER)
public class NotificationProjectedExtenderTest {

    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testProjectedExtender_setGetContentIntent() {
        ProjectedExtender projectedExtender = new ProjectedExtender();
        PendingIntent intent = createIntent("test");
        projectedExtender.setContentIntent(intent);
        assertThat(projectedExtender.getContentIntent()).isEqualTo(intent);
    }

    @Test
    public void testProjectedExtender_setsExtras() {
        PendingIntent contentIntent = createIntent("content");

        ProjectedExtender projectedExtender = new ProjectedExtender().setContentIntent(contentIntent);
        Notification.Builder builder =
                new Notification.Builder(mContext, "test channel")
                        .setSmallIcon(0)
                        .setContentTitle("title")
                        .setContentText("text");

        builder = projectedExtender.extend(builder);
        Notification notification = builder.build();

        Bundle projectedExtensions =
                notification.extras.getBundle(ProjectedExtender.EXTRA_PROJECTED_EXTENDER);
        assertThat(projectedExtensions).isNotNull();
        assertThat(projectedExtensions.getParcelable(
                ProjectedExtender.KEY_CONTENT_INTENT, PendingIntent.class)).isEqualTo(contentIntent);
    }

    @Test
    public void testProjectedExtender_fromNotification() {
        PendingIntent contentIntent = createIntent("content");

        ProjectedExtender projectedExtender = new ProjectedExtender().setContentIntent(contentIntent);
        Notification.Builder builder =
                new Notification.Builder(mContext, "test channel")
                        .setSmallIcon(0)
                        .setContentTitle("title")
                        .setContentText("text");

        builder = projectedExtender.extend(builder);
        Notification notification = builder.build();

        ProjectedExtender recoveredExtender = new ProjectedExtender(notification);
        assertThat(recoveredExtender.getContentIntent()).isEqualTo(contentIntent);
    }

    @Test
    public void testProjectedExtender_emptyConstructor() {
        ProjectedExtender projectedExtender = new ProjectedExtender();
        Notification notification = new Notification.Builder(mContext, "test channel")
                .extend(projectedExtender).build();
        assertThat(notification.extras.getBundle(ProjectedExtender.EXTRA_PROJECTED_EXTENDER)
                .isEmpty()).isTrue();
    }

    @Test
    public void testProjectedExtender_parcel() {
        PendingIntent contentIntent = createIntent("content");
        ProjectedExtender projectedExtender = new ProjectedExtender().setContentIntent(contentIntent);
        Notification notification =
                new Notification.Builder(mContext, "test channel")
                        .setSmallIcon(0)
                        .setContentTitle("title")
                        .setContentText("text")
                        .extend(projectedExtender)
                        .build();

        Notification unparceledNotification = writeAndReadToParcel(notification);
        ProjectedExtender unparceledExtender = new ProjectedExtender(unparceledNotification);

        assertThat(unparceledExtender.getContentIntent()).isEqualTo(contentIntent);
    }

    private PendingIntent createIntent(String actionName) {
        return PendingIntent.getActivity(mContext, 0, new Intent(actionName),
                PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification writeAndReadToParcel(Notification notification) {
        Parcel parcel = Parcel.obtain();
        notification.writeToParcel(parcel, /* flags */ 0);
        parcel.setDataPosition(0);
        Notification newNotification = Notification.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return newNotification;
    }
}
