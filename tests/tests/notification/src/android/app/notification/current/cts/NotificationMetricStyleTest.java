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

import static org.junit.Assert.assertThrows;

import android.app.Flags;
import android.app.Notification;
import android.app.Notification.Metric;
import android.app.Notification.Metric.FixedDate;
import android.app.Notification.Metric.FixedFloat;
import android.app.Notification.Metric.FixedInt;
import android.app.Notification.Metric.FixedString;
import android.app.Notification.Metric.FixedTime;
import android.app.Notification.Metric.TimeDifference;
import android.app.Notification.MetricStyle;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_API_METRIC_STYLE)
public class NotificationMetricStyleTest {

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void builderBuild_setsMetricStyle() {
        Metric metric = new Metric(new FixedInt(1979), "Steps");
        Notification n =
                new Notification.Builder(mContext)
                        .setSmallIcon(R.drawable.ic_android)
                        .setStyle(new MetricStyle().addMetric(metric))
                        .build();

        Notification.Builder recovered = Notification.Builder.recoverBuilder(mContext, n);

        assertThat(recovered.getStyle()).isInstanceOf(MetricStyle.class);
        assertThat(((MetricStyle) recovered.getStyle()).getMetrics()).containsExactly(metric);
    }

    @Test
    public void builderBuild_noMetrics_throws() {
        Notification.Builder builder =
                new Notification.Builder(mContext)
                        .setSmallIcon(R.drawable.ic_android)
                        .setStyle(new MetricStyle());

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void addMetric_adds() {
        MetricStyle style = new MetricStyle();
        style.addMetric(new Metric(new FixedString("Blah"), "Meh"));
        style.addMetric(new Metric(new FixedInt(42), "Steps"));
        style.addMetric(new Metric(new FixedDate(LocalDate.of(2017, 4, 1)), "X"));

        assertThat(style.getMetrics())
                .containsExactly(
                        new Metric(new FixedString("Blah"), "Meh"),
                        new Metric(new FixedInt(42), "Steps"),
                        new Metric(new FixedDate(LocalDate.of(2017, 4, 1)), "X"))
                .inOrder();
    }

    @Test
    public void addMetric_null_throws() {
        MetricStyle style = new MetricStyle();
        assertThrows(NullPointerException.class, () -> style.addMetric(null));
    }

    @Test
    public void setMetrics_replaces() {
        MetricStyle style = new MetricStyle();
        style.addMetric(new Metric(new FixedString("Will be discarded"), "A"));
        style.addMetric(new Metric(new FixedString("And this too"), "B"));

        style.setMetrics(List.of(new Metric(new FixedInt(10), "X")));

        assertThat(style.getMetrics()).containsExactly(new Metric(new FixedInt(10), "X"));
    }

    @Test
    public void getMetrics_immutable() {
        MetricStyle style = new MetricStyle();

        assertThrows(
                UnsupportedOperationException.class,
                () -> style.getMetrics().add(new Metric(new FixedInt(10), "X")));
    }

    @Test
    public void metricStyleEqualsAndHash_sameStyle_isEqual() {
        MetricStyle style1 =
                new MetricStyle()
                        .addMetric(
                                new Metric(
                                        TimeDifference.forPausedTimer(
                                                Duration.ofSeconds(30),
                                                TimeDifference.FORMAT_ADAPTIVE),
                                        "Timer"))
                        .addMetric(new Metric(new FixedString("Gibbous"), "Moon"))
                        .addMetric(new Metric(new FixedTime(LocalTime.of(19, 30)), "Event"));

        MetricStyle style2 =
                new MetricStyle()
                        .setMetrics(
                                List.of(
                                        new Metric(
                                                TimeDifference.forPausedTimer(
                                                        Duration.ofSeconds(30),
                                                        TimeDifference.FORMAT_ADAPTIVE),
                                                "Timer"),
                                        new Metric(new FixedString("Gibbous"), "Moon"),
                                        new Metric(new FixedTime(LocalTime.of(19, 30)), "Event")));

        assertThat(style1).isEqualTo(style2);
        assertThat(style1.hashCode()).isEqualTo(style2.hashCode());
    }

    @Test
    public void metricStyleEqualsAndHash_differentStyle_isDifferent() {
        MetricStyle style1 =
                new MetricStyle()
                        .addMetric(
                                new Metric(
                                        TimeDifference.forPausedTimer(
                                                Duration.ofSeconds(30),
                                                TimeDifference.FORMAT_ADAPTIVE),
                                        "Timer"))
                        .addMetric(new Metric(new FixedString("Gibbous"), "Moon"));

        MetricStyle style2 =
                new MetricStyle()
                        .setMetrics(
                                List.of(
                                        new Metric(
                                                TimeDifference.forPausedTimer(
                                                        Duration.ofSeconds(30),
                                                        TimeDifference.FORMAT_ADAPTIVE),
                                                "A *different* timer"),
                                        new Metric(new FixedString("Gibbous"), "Moon")));

        assertThat(style1).isNotEqualTo(style2);
        assertThat(style1.hashCode()).isNotEqualTo(style2.hashCode());
    }

    @Test
    public void newMetric_constructs() {
        Metric metric = new Metric(new FixedString("str"), "Port");

        assertThat(metric.getValue()).isEqualTo(new FixedString("str"));
        assertThat(metric.getLabel()).isEqualTo("Port");
    }

    @Test
    public void equalsAndHash_sameMetric_isEqual() {
        Metric metric1 = new Metric(new FixedFloat(23.5f, "°C", 0, 1), "Temp");
        Metric metric2 = new Metric(new FixedFloat(23.5f, "°C", 0, 1), "Temp");

        assertThat(metric1).isEqualTo(metric2);
        assertThat(metric1.hashCode()).isEqualTo(metric2.hashCode());
    }

    @Test
    public void equalsAndHash_differentMetric_isDifferent() {
        Metric metric1 = new Metric(new FixedInt(23, "m"), "Distance");
        Metric metric2 = new Metric(new FixedInt(24, "m"), "Distance");

        assertThat(metric1).isNotEqualTo(metric2);
        assertThat(metric1.hashCode()).isNotEqualTo(metric2.hashCode());
    }

    @Test
    public void newMetric_nullValue_throws() {
        assertThrows(NullPointerException.class, () -> new Metric(null, "X"));
    }

    @Test
    public void newMetric_nullLabel_throws() {
        assertThrows(NullPointerException.class, () -> new Metric(new FixedInt(10), null));
    }

    @Test
    public void newMetric_emptyLabel_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Metric(new FixedInt(10), ""));
    }

    @Test
    public void newMetric_blankLabel_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Metric(new FixedInt(10), "   "));
    }

    @Test
    public void newTimeDifference_forTimer_constructs() {
        TimeDifference timeDifference =
                TimeDifference.forTimer(
                        Instant.ofEpochMilli(100), TimeDifference.FORMAT_CHRONOMETER);

        assertThat(timeDifference.getZeroTime()).isEqualTo(Instant.ofEpochMilli(100));
        assertThat(timeDifference.getZeroElapsedRealtime()).isNull();
        assertThat(timeDifference.getPausedDuration()).isNull();
        assertThat(timeDifference.isTimer()).isTrue();
        assertThat(timeDifference.isStopwatch()).isFalse();
        assertThat(timeDifference.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void newTimeDifference_forElapsedRealtimeTimer_constructs() {
        TimeDifference timeDifference =
                TimeDifference.forTimer(20_000, TimeDifference.FORMAT_CHRONOMETER);

        assertThat(timeDifference.getZeroTime()).isNull();
        assertThat(timeDifference.getZeroElapsedRealtime()).isEqualTo(20_000);
        assertThat(timeDifference.getPausedDuration()).isNull();
        assertThat(timeDifference.isTimer()).isTrue();
        assertThat(timeDifference.isStopwatch()).isFalse();
        assertThat(timeDifference.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void newTimeDifference_forStopwatch_constructs() {
        TimeDifference timeDifference =
                TimeDifference.forStopwatch(
                        Instant.ofEpochMilli(200), TimeDifference.FORMAT_AUTOMATIC);

        assertThat(timeDifference.getZeroTime()).isEqualTo(Instant.ofEpochMilli(200));
        assertThat(timeDifference.getZeroElapsedRealtime()).isNull();
        assertThat(timeDifference.getPausedDuration()).isNull();
        assertThat(timeDifference.isStopwatch()).isTrue();
        assertThat(timeDifference.isTimer()).isFalse();
        assertThat(timeDifference.getFormat()).isEqualTo(TimeDifference.FORMAT_AUTOMATIC);
    }

    @Test
    public void newTimeDifference_forElapsedRealtimeStopwatch_constructs() {
        TimeDifference timeDifference =
                TimeDifference.forStopwatch(30_000, TimeDifference.FORMAT_AUTOMATIC);

        assertThat(timeDifference.getZeroTime()).isNull();
        assertThat(timeDifference.getZeroElapsedRealtime()).isEqualTo(30_000);
        assertThat(timeDifference.getPausedDuration()).isNull();
        assertThat(timeDifference.isStopwatch()).isTrue();
        assertThat(timeDifference.isTimer()).isFalse();
        assertThat(timeDifference.getFormat()).isEqualTo(TimeDifference.FORMAT_AUTOMATIC);
    }

    @Test
    public void newTimeDifference_forPausedTimer_constructs() {
        TimeDifference timeDifference =
                TimeDifference.forPausedTimer(
                        Duration.ofSeconds(90), TimeDifference.FORMAT_CHRONOMETER);

        assertThat(timeDifference.getZeroTime()).isNull();
        assertThat(timeDifference.getZeroElapsedRealtime()).isNull();
        assertThat(timeDifference.getPausedDuration()).isEqualTo(Duration.ofSeconds(90));
        assertThat(timeDifference.isTimer()).isTrue();
        assertThat(timeDifference.isStopwatch()).isFalse();
        assertThat(timeDifference.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void newTimeDifference_forPausedStopwatch_constructs() {
        TimeDifference timeDifference =
                TimeDifference.forPausedStopwatch(
                        Duration.ofMinutes(2), TimeDifference.FORMAT_CHRONOMETER);

        assertThat(timeDifference.getZeroTime()).isNull();
        assertThat(timeDifference.getZeroElapsedRealtime()).isNull();
        assertThat(timeDifference.getPausedDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(timeDifference.isStopwatch()).isTrue();
        assertThat(timeDifference.isTimer()).isFalse();
        assertThat(timeDifference.getFormat()).isEqualTo(TimeDifference.FORMAT_CHRONOMETER);
    }

    @Test
    public void newTimeDifference_invalidFormat_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TimeDifference.forTimer(Instant.now(), /* format= */ -10));
        assertThrows(
                IllegalArgumentException.class,
                () -> TimeDifference.forStopwatch(Instant.now(), /* format= */ 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> TimeDifference.forPausedTimer(Duration.ofMinutes(2), /* format= */ -20));
        assertThrows(
                IllegalArgumentException.class,
                () -> TimeDifference.forPausedStopwatch(Duration.ofMinutes(2), /* format= */ 77));
    }

    @Test
    public void newTimeDifference_nullInput_throws() {
        assertThrows(
                NullPointerException.class,
                () -> TimeDifference.forTimer(null, TimeDifference.FORMAT_ADAPTIVE));
        assertThrows(
                NullPointerException.class,
                () -> TimeDifference.forPausedTimer(null, TimeDifference.FORMAT_ADAPTIVE));
        assertThrows(
                NullPointerException.class,
                () -> TimeDifference.forStopwatch(null, TimeDifference.FORMAT_ADAPTIVE));
        assertThrows(
                NullPointerException.class,
                () -> TimeDifference.forPausedStopwatch(null, TimeDifference.FORMAT_ADAPTIVE));
    }

    @Test
    public void newFixedDate_constructs() {
        FixedDate fixedDate = new FixedDate(LocalDate.of(2021, 10, 31), FixedDate.FORMAT_LONG_DATE);

        assertThat(fixedDate.getValue()).isEqualTo(LocalDate.of(2021, 10, 31));
        assertThat(fixedDate.getFormat()).isEqualTo(FixedDate.FORMAT_LONG_DATE);

        FixedDate defaults = new FixedDate(LocalDate.of(2021, 10, 31));

        assertThat(defaults.getValue()).isEqualTo(LocalDate.of(2021, 10, 31));
        assertThat(defaults.getFormat()).isEqualTo(FixedDate.FORMAT_AUTOMATIC);
    }

    @Test
    public void newFixedDate_nullDate_throws() {
        assertThrows(NullPointerException.class, () -> new FixedDate(null));
        assertThrows(
                NullPointerException.class, () -> new FixedDate(null, FixedDate.FORMAT_AUTOMATIC));
    }

    @Test
    public void newFixedTime_constructs() {
        FixedTime fixedTime = new FixedTime(LocalTime.of(21, 15));

        assertThat(fixedTime.getValue()).isEqualTo(LocalTime.of(21, 15));
    }

    @Test
    public void newFixedTime_nullTime_throws() {
        assertThrows(NullPointerException.class, () -> new FixedTime(null));
    }

    @Test
    public void newFixedInt_constructs() {
        FixedInt fixedInt = new FixedInt(33, "orientales");

        assertThat(fixedInt.getValue()).isEqualTo(33);
        assertThat(fixedInt.getUnit()).isEqualTo("orientales");
    }

    @Test
    public void newFixedFloat_constructs() {
        FixedFloat fixedFloat = new FixedFloat(33.33f, "degrees", 1, 2);

        assertThat(fixedFloat.getValue()).isEqualTo(33.33f);
        assertThat(fixedFloat.getUnit()).isEqualTo("degrees");
        assertThat(fixedFloat.getMinFractionDigits()).isEqualTo(1);
        assertThat(fixedFloat.getMaxFractionDigits()).isEqualTo(2);

        FixedFloat defaults = new FixedFloat(44.44f);

        assertThat(defaults.getValue()).isEqualTo(44.44f);
        assertThat(defaults.getUnit()).isNull();
        assertThat(defaults.getMinFractionDigits())
                .isEqualTo(FixedFloat.DEFAULT_MIN_FRACTION_DIGITS);
        assertThat(defaults.getMaxFractionDigits())
                .isEqualTo(FixedFloat.DEFAULT_MAX_FRACTION_DIGITS);
    }

    @Test
    public void newFixedFloat_invalidFractionDigits_throws() {
        // invalid (negative) min and max
        assertThrows(IllegalArgumentException.class, () -> new FixedFloat(1f, null, -10, -5));
        // invalid (negative) min, valid max
        assertThrows(IllegalArgumentException.class, () -> new FixedFloat(1f, null, -1, 3));
        // too large min, too large max
        assertThrows(IllegalArgumentException.class, () -> new FixedFloat(1f, null, 10, 15));
        // valid min, too large max
        assertThrows(IllegalArgumentException.class, () -> new FixedFloat(1f, null, 2, 8));
        // max lower than min
        assertThrows(IllegalArgumentException.class, () -> new FixedFloat(1f, null, 3, 1));
    }

    @Test
    public void newFixedString_constructs() {
        FixedString fixedString = new FixedString("120/80", "mmHg");
        assertThat(fixedString.getValue()).isEqualTo("120/80");
        assertThat(fixedString.getUnit()).isEqualTo("mmHg");

        FixedString defaults = new FixedString("Hello!");
        assertThat(defaults.getValue()).isEqualTo("Hello!");
        assertThat(defaults.getUnit()).isNull();
    }

    @Test
    public void newFixedString_nullString_throws() {
        assertThrows(NullPointerException.class, () -> new FixedString(null));
    }
}
