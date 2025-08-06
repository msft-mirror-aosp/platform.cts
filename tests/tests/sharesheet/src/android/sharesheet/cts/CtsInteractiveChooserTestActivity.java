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

package android.sharesheet.cts;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.service.chooser.ChooserManager;
import android.service.chooser.ChooserSession;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class CtsInteractiveChooserTestActivity extends Activity {
    private static final String TAG = "CtsInteractiveChooserTestActivity";
    // Special CTS mime type
    private static final String CTS_DATA_TYPE = "test/cts_interactive";
    // Special CTS mime type
    private static final String CTS_ALT_DATA_TYPE = "test/cts_alternate_interactive";
    private static final String TEST_CATEGORY = "android.sharesheet.cts.TEST_CATEGORY";

    public static final String PARAM_ACTIVITY_CONTROLLER_CALLBACK = "controller-callback";
    public static final String PARAM_ORIENTATION = "orientation";

    private Button mLaunchChooser;
    private ViewGroup mChooserActionRow;
    private boolean mIsTargetEnabled = true;

    private final InteractiveTestActivityReportBuilder mReportBuilder =
            new InteractiveTestActivityReportBuilder();

    @Nullable
    private ChooserSession mChooserSession;

    private final ChooserSession.StateListener mChooserSessionStateListener =
            new ChooserSession.StateListener() {
                @Override
                public void onStateChanged(int state) {
                    onChooserStateChanged(state);
                }

                @Override
                public void onBoundsChanged(@NonNull Rect bounds) {
                    onChooserBoundsChanged(bounds);
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int orientation =
                getIntent().getIntExtra(PARAM_ORIENTATION, SCREEN_ORIENTATION_UNSPECIFIED);
        android.util.Log.d(TAG, "using orientation: " + orientation);
        setRequestedOrientation(orientation);
        Bundle extras = getIntent().getExtras();
        IBinder binder =
                extras == null ? null : extras.getBinder(PARAM_ACTIVITY_CONTROLLER_CALLBACK);
        if (binder instanceof InteractiveTestActivityControllerCallback controllerCallback) {
            controllerCallback.setTestActivityController(this::createTestReport);
        } else {
            Log.e(TAG, "Controller callback was not provided");
            finish();
            return;
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        setContentView(R.layout.activity_interactive_chooser_test);

        View contentView = findViewById(R.id.content_view);
        if (contentView != null) {
            contentView.setOnApplyWindowInsetsListener(
                    (v, insets) -> {
                        Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars());
                        contentView.setPadding(
                                systemInsets.left,
                                systemInsets.top,
                                systemInsets.right,
                                systemInsets.bottom);
                        return WindowInsets.CONSUMED;
                    });
        }
        mLaunchChooser = findViewById(R.id.launch_chooser);
        mLaunchChooser.setOnClickListener((v) -> launchChooser());

        mChooserActionRow = findViewById(R.id.chooser_action_row);

        Button closeChooser = findViewById(R.id.close_chooser);
        closeChooser.setOnClickListener((v) -> closeChooser());

        Button updateChooser = findViewById(R.id.update_chooser);
        updateChooser.setOnClickListener((v) -> updateChooser());

        Button unsubscribe = findViewById(R.id.unsubscribe);
        unsubscribe.setOnClickListener((v) -> removeSessionStateListener());

        Button disableButton = findViewById(R.id.target_status);
        disableButton.setOnClickListener((v) -> toggleTargetEnableStatus((Button) v));

        onSessionActiveStateChanged(mChooserSession != null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mChooserSession != null) {
            mChooserSession.endSession();
            mChooserSession = null;
            mReportBuilder.setSession(null);
        }
    }

    private void onChooserStateChanged(int state) {
        Log.d(TAG, "Chooser session state changed; state: " + state);
        mReportBuilder.addReportedState(state);
        if (state == ChooserSession.STATE_STARTED) {
            onSessionActiveStateChanged(true);
        } else if (state == ChooserSession.STATE_CLOSED) {
            mChooserSession = null;
            mReportBuilder.setSession(null);
            onSessionActiveStateChanged(false);
        }
    }

    private void onChooserBoundsChanged(Rect bounds) {
        Log.d(TAG, "Chooser bounds changed; bounds: " + bounds);
        mReportBuilder.addReportedBound(bounds);
    }

    private InteractiveTestActivityReport createTestReport() {
        WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
        mReportBuilder.setWindowInsets(
                windowMetrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()));
        mReportBuilder.setWindowHeight(windowMetrics.getBounds().height());
        return mReportBuilder.build();
    }

    private void launchChooser() {
        Log.d(TAG, "Launch Chooser button clicked");
        maybeStartNewSession();
        onSessionActiveStateChanged(true);
    }

    private void closeChooser() {
        Log.d(TAG, "Close Chooser button clicked");
        if (mChooserSession == null) {
            return;
        }
        mChooserSession.endSession();
        mChooserSession = null;
        mReportBuilder.setSession(null);
        onSessionActiveStateChanged(false);
    }

    private void updateChooser() {
        Log.d(TAG, "Update Chooser button clicked");
        if (mChooserSession == null) {
            return;
        }
        Intent chooserIntent = Intent.createChooser(createTargetIntent(CTS_ALT_DATA_TYPE), null);
        mChooserSession.updateIntent(chooserIntent);
    }

    private void removeSessionStateListener() {
        Log.d(TAG, "Unsubscribe button clicked");
        if (mChooserSession == null) {
            return;
        }
        mChooserSession.removeStateListener(mChooserSessionStateListener);
    }

    private void toggleTargetEnableStatus(Button button) {
        Log.d(TAG, "Toggle target state button clicked");
        if (mChooserSession == null) {
            return;
        }
        mIsTargetEnabled = !mIsTargetEnabled;
        mChooserSession.setTargetsEnabled(mIsTargetEnabled);
        button.setText(mIsTargetEnabled ? "Disable" : "Enable");
    }

    private void maybeStartNewSession() {
        if (mChooserSession != null) {
            return;
        }
        Log.d(TAG, "Staring new Chooser session");
        Intent chooserIntent = Intent.createChooser(createTargetIntent(CTS_DATA_TYPE), null);
        mChooserSession =
                Objects.requireNonNull(getSystemService(ChooserManager.class))
                        .startSession(this, chooserIntent);
        mChooserSession.addStateListener(getMainExecutor(), mChooserSessionStateListener);
        mReportBuilder.setSession(mChooserSession);
    }

    private void onSessionActiveStateChanged(boolean isActive) {
        mLaunchChooser.setVisibility(isActive ? View.GONE : View.VISIBLE);
        mChooserActionRow.setVisibility(isActive ? View.VISIBLE : View.GONE);
    }

    private Intent createTargetIntent(String type) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(type);
        intent.addCategory(TEST_CATEGORY);
        return intent;
    }
}
