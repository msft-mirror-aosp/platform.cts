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

import static android.app.Notification.SEMANTIC_STYLE_DANGER;
import static android.app.Notification.SEMANTIC_STYLE_UNSPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.Flags;
import android.app.Notification;
import android.app.Notification.BasicCompactContent;
import android.app.Notification.CompactIcon;
import android.app.Notification.CompactText;
import android.app.Notification.Metric;
import android.app.Notification.Metric.FixedText;
import android.app.Notification.Metric.MetricValue;
import android.app.Notification.Metric.TimeDifference;
import android.app.Notification.MetricStyle;
import android.app.Notification.ResolvedBasicCompactContent;
import android.app.Notification.ResolvedCompactContent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.LocalDate;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    Flags.FLAG_API_NOTIFICATION_CHIP,
    Flags.FLAG_API_METRIC_STYLE,
    Flags.FLAG_API_NOTIFICATION_SEMANTIC_STYLE
})
public class NotificationCompactContentTest {

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void basicCompactContent_nullIcon_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new BasicCompactContent(null, CompactText.none()));
    }

    @Test
    public void basicCompactContent_nullText_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new BasicCompactContent(CompactIcon.auto(), null));
    }

    @Test
    public void resolveCompactContent_basic_emptyText() {
        Notification n =
                getBuilderWithEverything()
                        .setCompactContent(new BasicCompactContent(CompactText.none()))
                        .build();

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        assertThat(resolved.getText()).isNull();
    }

    @Test
    public void resolveCompactContent_basic_shortCriticalText() {
        Notification n =
                getBuilderWithEverything()
                        .setShortCriticalText("Short")
                        .setCompactContent(
                                new BasicCompactContent(CompactText.useShortCriticalText()))
                        .build();

        FixedText resolvedText = resolveBasicCompactContentText(n, FixedText.class);

        assertThat(resolvedText.getValue().toString()).isEqualTo("Short");
    }

    @Test
    public void resolveCompactContent_basic_shortCriticalTextNull() {
        Notification n =
                getBuilderWithEverything()
                        .setShortCriticalText(null)
                        .setCompactContent(
                                new BasicCompactContent(CompactText.useShortCriticalText()))
                        .build();

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        assertThat(resolved.getText()).isNull();
    }

    @Test
    public void resolveCompactContent_basic_shortCriticalTextEmpty() {
        Notification n =
                getBuilderWithEverything()
                        .setShortCriticalText("")
                        .setCompactContent(
                                new BasicCompactContent(CompactText.useShortCriticalText()))
                        .build();

        FixedText resolvedText = resolveBasicCompactContentText(n, FixedText.class);

        assertThat(resolvedText.getValue().toString()).isEmpty();
    }

    @Test
    public void resolveCompactContent_basic_whenAsTimeRemaining() {
        Notification n =
                getBuilderWithEverything()
                        .setWhen(12345L)
                        .setCompactContent(
                                new BasicCompactContent(CompactText.useWhenAsTimeRemaining()))
                        .build();

        TimeDifference resolvedText = resolveBasicCompactContentText(n, TimeDifference.class);

        assertThat(resolvedText.isTimer()).isTrue();
        assertThat(resolvedText.getZeroTime()).isEqualTo(Instant.ofEpochMilli(12345L));
        assertThat(resolvedText.getFormat()).isEqualTo(TimeDifference.FORMAT_ADAPTIVE);
    }

    @Test
    public void resolveCompactContent_basic_whenAsChronometer() {
        Notification n =
                getBuilderWithEverything()
                        .setWhen(12345L)
                        .setCompactContent(
                                new BasicCompactContent(
                                        CompactText.useWhenAsChronometer(/* countdown= */ false)))
                        .build();

        TimeDifference resolvedText = resolveBasicCompactContentText(n, TimeDifference.class);

        assertThat(resolvedText.isStopwatch()).isTrue();
        assertThat(resolvedText.getZeroTime()).isEqualTo(Instant.ofEpochMilli(12345L));
        assertThat(resolvedText.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void resolveCompactContent_basic_whenAsCountdownChronometer() {
        Notification n =
                getBuilderWithEverything()
                        .setWhen(12345L)
                        .setCompactContent(
                                new BasicCompactContent(
                                        CompactText.useWhenAsChronometer(/* countdown= */ true)))
                        .build();

        TimeDifference resolvedText = resolveBasicCompactContentText(n, TimeDifference.class);

        assertThat(resolvedText.isTimer()).isTrue();
        assertThat(resolvedText.getZeroTime()).isEqualTo(Instant.ofEpochMilli(12345L));
        assertThat(resolvedText.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void resolveCompactContent_basic_styleMetric() {
        Notification n =
                getBuilderWithEverything()
                        .setStyle(
                                new MetricStyle()
                                        .addMetric(new Metric(new FixedText("0"), "label1"))
                                        .addMetric(new Metric(new FixedText("1"), "label1"))
                                        .addMetric(new Metric(new FixedText("2"), "label1")))
                        .setCompactContent(new BasicCompactContent(CompactText.useStyleMetric(2)))
                        .build();

        FixedText resolvedText = resolveBasicCompactContentText(n, FixedText.class);

        assertThat(resolvedText.getValue().toString()).isEqualTo("2");
    }

    @Test
    public void resolveCompactContent_basic_customMetric() {
        MetricValue customMetric = new Metric.FixedDate(LocalDate.of(2025, 9, 2));
        Notification n =
                getBuilderWithEverything()
                        .setCompactContent(
                                new BasicCompactContent(CompactText.fromMetricValue(customMetric)))
                        .build();

        MetricValue resolvedText = resolveBasicCompactContentText(n, MetricValue.class);

        assertThat(resolvedText).isEqualTo(customMetric);
    }

    @Test
    public void resolveCompactContent_basic_semanticStyle() {
        Notification n =
                getBuilderWithEverything()
                        .setShortCriticalText("Short")
                        .setCompactContent(
                                new BasicCompactContent(CompactText.useShortCriticalText())
                                        .setSemanticStyle(SEMANTIC_STYLE_DANGER))
                        .build();

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        assertThat(resolved.getSemanticStyle()).isEqualTo(SEMANTIC_STYLE_DANGER);
    }

    @Test
    public void resolveCompactContent_basic_defaultSemanticStyle() {
        Notification n =
                getBuilderWithEverything()
                        .setShortCriticalText("Short")
                        .setCompactContent(
                                new BasicCompactContent(CompactText.useShortCriticalText()))
                        .build();

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        assertThat(resolved.getSemanticStyle()).isEqualTo(SEMANTIC_STYLE_UNSPECIFIED);
    }

    @Test
    public void resolveCompactContent_basic_autoIcon() {
        Notification n =
                getBuilderWithEverything()
                        .setSmallIcon(R.drawable.ic_android)
                        .setCompactContent(
                                new BasicCompactContent(CompactIcon.auto(), CompactText.none()))
                        .build();

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        // NOTE: We don't validate the specific icon chosen since that is up to the platform.
        assertThat(resolved.getIcon()).isNotNull();
    }

    @Test
    public void resolveCompactContent_basic_smallIcon() {
        Notification n =
                getBuilderWithEverything()
                        .setSmallIcon(Icon.createWithResource("com.pkg", R.drawable.ic_android))
                        .setCompactContent(
                                new BasicCompactContent(
                                        CompactIcon.useSmallIcon(), CompactText.none()))
                        .build();

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        assertThat(resolved.getIcon().getIcon()).isNotNull();
        assertThat(resolved.getIcon().getIcon().getResPackage()).isEqualTo("com.pkg");
        assertThat(resolved.getIcon().getIcon().getResId()).isEqualTo(R.drawable.ic_android);
    }

    @Test
    public void resolveCompactContent_default_choosesShortCriticalText() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setShortCriticalText("Short")
                        .setStyle(
                                new MetricStyle()
                                        .addMetric(new Metric(new FixedText("1"), "L1"))
                                        .addMetric(new Metric(new FixedText("2"), "L2")))
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(12345L)
                        .setShowWhen(true)
                        .build();

        FixedText resolvedText = resolveBasicCompactContentText(n, FixedText.class);

        assertThat(resolvedText.getValue().toString()).isEqualTo("Short");
    }

    @Test
    public void resolveCompactContent_default_choosesShortCriticalTextEvenIfEmpty() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setShortCriticalText("")
                        .setStyle(
                                new MetricStyle()
                                        .addMetric(new Metric(new FixedText("1"), "L1"))
                                        .addMetric(new Metric(new FixedText("2"), "L2")))
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(12345L)
                        .setShowWhen(true)
                        .build();

        FixedText resolvedText = resolveBasicCompactContentText(n, FixedText.class);

        assertThat(resolvedText.getValue().toString()).isEmpty();
    }

    @Test
    public void resolveCompactContent_default_choosesCriticalMetric() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setStyle(
                                new MetricStyle()
                                        .addMetric(new Metric(new FixedText("1"), "L1"))
                                        .addMetric(new Metric(new FixedText("2"), "L2"))
                                        .setCriticalMetric(1))
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(12345L)
                        .setShowWhen(true)
                        .build();

        FixedText resolvedText = resolveBasicCompactContentText(n, FixedText.class);

        assertThat(resolvedText.getValue().toString()).isEqualTo("2");
    }

    @Test
    public void resolveCompactContent_default_choosesFirstMetric() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setStyle(
                                new MetricStyle()
                                        .addMetric(new Metric(new FixedText("1"), "L1"))
                                        .addMetric(new Metric(new FixedText("2"), "L2")))
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(12345L)
                        .setShowWhen(true)
                        .build();

        FixedText resolvedText = resolveBasicCompactContentText(n, FixedText.class);

        assertThat(resolvedText.getValue().toString()).isEqualTo("1");
    }

    @Test
    public void resolveCompactContent_default_choosesWhenAsChronometer() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setUsesChronometer(true)
                        .setChronometerCountDown(false)
                        .setWhen(12345L)
                        .setShowWhen(true)
                        .build();

        TimeDifference resolvedText = resolveBasicCompactContentText(n, TimeDifference.class);

        assertThat(resolvedText.isStopwatch()).isTrue();
        assertThat(resolvedText.getZeroTime()).isEqualTo(Instant.ofEpochMilli(12345L));
        assertThat(resolvedText.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void resolveCompactContent_default_choosesWhenAsChronometerCountdown() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(12345L)
                        .setShowWhen(true)
                        .build();

        TimeDifference resolvedText = resolveBasicCompactContentText(n, TimeDifference.class);

        assertThat(resolvedText.isTimer()).isTrue();
        assertThat(resolvedText.getZeroTime()).isEqualTo(Instant.ofEpochMilli(12345L));
        assertThat(resolvedText.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void resolveCompactContent_default_choosesWhenAsTimeRemaining() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setWhen(12345L)
                        .setShowWhen(true)
                        .build();

        TimeDifference resolvedText = resolveBasicCompactContentText(n, TimeDifference.class);

        assertThat(resolvedText.isTimer()).isTrue();
        assertThat(resolvedText.getZeroTime()).isEqualTo(Instant.ofEpochMilli(12345L));
        assertThat(resolvedText.getFormat()).isEqualTo(TimeDifference.FORMAT_ADAPTIVE);
    }

    @Test
    public void resolveCompactContent_default_choosesNothing() {
        Notification n =
                new Notification.Builder(mContext, "channel")
                        .setWhen(12345L)
                        .setShowWhen(false)
                        .build();

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        assertThat(resolved.getText()).isNull();
    }

    @Test
    public void resolveCompactContent_default_oldTargetSdk_choosesSmallIcon() {
        Notification n =
                getBuilderWithEverything()
                        .setSmallIcon(Icon.createWithResource("com.pkg", R.drawable.ic_android))
                        .build();
        ApplicationInfo appInfo = new ApplicationInfo(mContext.getApplicationInfo());
        appInfo.targetSdkVersion = Build.VERSION_CODES.BAKLAVA;
        Notification.addFieldsFromContext(appInfo, n);

        ResolvedBasicCompactContent resolved = resolveBasicCompactContent(n);

        assertThat(resolved.getIcon().getIcon()).isNotNull();
        assertThat(resolved.getIcon().getIcon().getResPackage()).isEqualTo("com.pkg");
        assertThat(resolved.getIcon().getIcon().getResId()).isEqualTo(R.drawable.ic_android);
    }

    /**
     * Returns a Builder prepped up to create a Notification with all the possible sources of data
     * for BasicCompactContent. This is to verify that, when specifying a particular source via
     * setCompactContent(), we're not taking data from the incorrect source just because "it's
     * there".
     *
     * <p>To verify assertions, callers should still override the particular field they want to
     * test, so as not to depend on the defaults set here.
     */
    private Notification.Builder getBuilderWithEverything() {
        return new Notification.Builder(mContext, "channel")
                .setSmallIcon(R.drawable.ic_android)
                .setShortCriticalText("SampleShortCriticalText")
                .setStyle(
                        new MetricStyle()
                                .addMetric(new Metric(new FixedText("SampleMetricText"), "Label")))
                .setWhen(System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true);
    }

    @NonNull
    private ResolvedBasicCompactContent resolveBasicCompactContent(Notification n) {
        ResolvedCompactContent resolved = n.resolveCompactContent(mContext);
        assertThat(resolved).isNotNull();
        assertThat(resolved).isInstanceOf(ResolvedBasicCompactContent.class);
        return (ResolvedBasicCompactContent) resolved;
    }

    @NonNull
    private <T extends Metric.MetricValue> T resolveBasicCompactContentText(
            Notification n, Class<T> metricValueType) {
        ResolvedBasicCompactContent resolvedContent = resolveBasicCompactContent(n);
        assertThat(resolvedContent.getText()).isNotNull();
        assertThat(resolvedContent.getText()).isInstanceOf(metricValueType);
        return metricValueType.cast(resolvedContent.getText());
    }
}
