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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.View;
import android.view.flags.Flags;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_SCROLL_TO_TOP)
public class ViewScrollToTopTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testViewDispatchScrollToTop_defaultReturnsFalse() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        TestView view = new TestView(activity);
                        assertFalse(view.dispatchScrollToTop(0));
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_callsChild() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        LinearLayout parent = new LinearLayout(activity);
                        parent.setOrientation(LinearLayout.VERTICAL);

                        TestView child = spy(new TestView(activity));
                        child.setLayoutParams(new LinearLayout.LayoutParams(100, 100));

                        parent.addView(child);
                        parent.measure(
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
                        parent.layout(0, 0, 100, 100);

                        doReturn(true).when(child).dispatchScrollToTop(anyInt());

                        assertTrue(parent.dispatchScrollToTop(50));
                        verify(child).dispatchScrollToTop(50);
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_nonConsumingChildPropagates() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        LinearLayout root = new LinearLayout(activity);
                        root.setOrientation(LinearLayout.VERTICAL);

                        // Child 1: Does not consume (returns false)
                        TestView child1 = spy(new TestView(activity));
                        child1.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        doReturn(false).when(child1).dispatchScrollToTop(anyInt());

                        // Child 2: Consumes (returns true)
                        TestView child2 = spy(new TestView(activity));
                        child2.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        doReturn(true).when(child2).dispatchScrollToTop(anyInt());

                        root.addView(child1);
                        root.addView(child2);

                        root.measure(
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
                        root.layout(0, 0, 100, 200);

                        // Dispatch at x=50 (intersects both).
                        // child1 is visited first (higher up), ignores it.
                        // child2 is visited second, consumes it.
                        assertTrue(root.dispatchScrollToTop(50));

                        verify(child1).dispatchScrollToTop(50);
                        verify(child2).dispatchScrollToTop(50);
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_consumedEventStopsPropagation() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        LinearLayout root = new LinearLayout(activity);
                        root.setOrientation(LinearLayout.VERTICAL);

                        // Child 1: Top-most, but returns false (Ignored)
                        TestView child1 = spy(new TestView(activity));
                        child1.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        doReturn(false).when(child1).dispatchScrollToTop(anyInt());

                        // Child 2: Middle, returns true (Consumed)
                        TestView child2 = spy(new TestView(activity));
                        child2.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        doReturn(true).when(child2).dispatchScrollToTop(anyInt());

                        // Child 3: Bottom, valid target, but should be shadowed by Child 2
                        TestView child3 = spy(new TestView(activity));
                        child3.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        doReturn(true).when(child3).dispatchScrollToTop(anyInt());

                        root.addView(child1);
                        root.addView(child2);
                        root.addView(child3);

                        root.measure(
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
                        root.layout(0, 0, 100, 300);

                        // Dispatch at x=50.
                        assertTrue(root.dispatchScrollToTop(50));

                        // 1. Child 1 is visited (Top visual) -> Returns False.
                        verify(child1).dispatchScrollToTop(50);

                        // 2. Child 2 is visited (Next) -> Returns True.
                        verify(child2).dispatchScrollToTop(50);

                        // 3. Child 3 should NEVER be visited because Child 2 consumed the event.
                        verify(child3, org.mockito.Mockito.never()).dispatchScrollToTop(anyInt());
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_coordinateTransformationWithMargin() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        final LinearLayout parent = new LinearLayout(activity);
                        parent.setOrientation(LinearLayout.VERTICAL);

                        final TestView child = spy(new TestView(activity));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(100, 100);
                        lp.leftMargin = 10;
                        child.setLayoutParams(lp);

                        parent.addView(child);
                        parent.measure(
                                View.MeasureSpec.makeMeasureSpec(110, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
                        parent.layout(0, 0, 110, 100);

                        doReturn(true).when(child).dispatchScrollToTop(anyInt());

                        // Dispatch at x=20.
                        // Child Left = 10. Local Coord = 20 - 10 = 10.
                        assertTrue(parent.dispatchScrollToTop(20));
                        verify(child).dispatchScrollToTop(10);
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_coordinateTransformationWithPadding() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        final LinearLayout parent = new LinearLayout(activity);
                        parent.setOrientation(LinearLayout.VERTICAL);
                        parent.setPadding(20, 0, 0, 0);

                        final TestView child = spy(new TestView(activity));
                        child.setLayoutParams(new LinearLayout.LayoutParams(100, 100));

                        parent.addView(child);
                        parent.measure(
                                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
                        parent.layout(0, 0, 120, 100);

                        doReturn(true).when(child).dispatchScrollToTop(anyInt());

                        // Dispatch at x=30.
                        // Padding=20. Child Left=20. Local Coord = 30 - 20 = 10.
                        assertTrue(parent.dispatchScrollToTop(30));
                        verify(child).dispatchScrollToTop(10);
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_coordinateTransformationWithTranslation() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        final LinearLayout parent = new LinearLayout(activity);
                        parent.setOrientation(LinearLayout.VERTICAL);

                        final TestView child = spy(new TestView(activity));
                        child.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        child.setTranslationX(20);

                        parent.addView(child);
                        parent.measure(
                                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
                        parent.layout(0, 0, 120, 100);

                        doReturn(true).when(child).dispatchScrollToTop(anyInt());
                        assertEquals(20f, child.getTranslationX(), 0.0);

                        // Dispatch at x=30.
                        // Child Left=0. Translation=20. Local Coord = 30 - 0 - 20 = 10.
                        assertTrue(parent.dispatchScrollToTop(30));
                        verify(child).dispatchScrollToTop(10);
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_coordinateTransformationWithRotation() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        final LinearLayout parent = new LinearLayout(activity);
                        parent.setOrientation(LinearLayout.VERTICAL);

                        final TestView child = spy(new TestView(activity));
                        child.setLayoutParams(new LinearLayout.LayoutParams(100, 100));

                        // Rotate 90 degrees clockwise around center (50, 50).
                        child.setPivotX(50);
                        child.setPivotY(50);
                        child.setRotation(90);

                        parent.addView(child);
                        parent.measure(
                                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
                        parent.layout(0, 0, 200, 200);

                        doReturn(true).when(child).dispatchScrollToTop(anyInt());

                        // Dispatch at x=50.
                        // Maps Parent(50, 0) -> Local(0, 50).
                        assertTrue(parent.dispatchScrollToTop(50));
                        verify(child).dispatchScrollToTop(0);
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_coordinateTransformationWithScale() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        final LinearLayout parent = new LinearLayout(activity);
                        parent.setOrientation(LinearLayout.VERTICAL);

                        final TestView child = spy(new TestView(activity));
                        child.setLayoutParams(new LinearLayout.LayoutParams(100, 100));

                        // Scale 2x around top-left (0,0). Visual width becomes 200.
                        child.setPivotX(0);
                        child.setPivotY(0);
                        child.setScaleX(2f);
                        child.setScaleY(2f);

                        parent.addView(child);
                        parent.measure(
                                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
                        parent.layout(0, 0, 200, 200);

                        doReturn(true).when(child).dispatchScrollToTop(anyInt());

                        // Dispatch at x=100.
                        // Scale=2.0. Local Coord = 100 / 2.0 = 50.
                        assertTrue(parent.dispatchScrollToTop(100));
                        verify(child).dispatchScrollToTop(50);
                    });
        }
    }

    @Test
    public void testViewGroupDispatchScrollToTop_withScrolledParent() {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        final LinearLayout parent = new LinearLayout(activity);
                        parent.setOrientation(LinearLayout.VERTICAL);

                        parent.measure(
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
                        parent.layout(0, 0, 100, 100);

                        // Parent Scrolled Y=50. Visual Top is now Content Y=50.
                        parent.scrollTo(0, 50);

                        final TestView child = spy(new TestView(activity));
                        child.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        child.setY(50); // Physically at top of viewport

                        parent.addView(child);
                        doReturn(true).when(child).dispatchScrollToTop(anyInt());

                        // Dispatch at x=10.
                        // Uses mScrollY(50) -> Hits child at Y=50.
                        // Local Coord = 10 - 0 = 10.
                        assertTrue(parent.dispatchScrollToTop(10));
                        verify(child).dispatchScrollToTop(10);
                    });
        }
    }

    public static class TestActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            setContentView(layout);
        }
    }

    public static class TestView extends View {
        public TestView(Context context) {
            super(context);
        }
    }
}
