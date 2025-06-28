/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import static android.app.Activity.RESULT_OK;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.Intent.EXTRA_REMOTE_CALLBACK;
import static android.content.Intent.EXTRA_RETURN_RESULT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.OnUidImportanceListener;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.Flags;
import android.app.HomeVisibilityListener;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Instrumentation.ActivityResult;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.app.stubs.ActivityManagerRecentOneActivity;
import android.app.stubs.ActivityManagerRecentTwoActivity;
import android.app.stubs.MockApplicationActivity;
import android.app.stubs.MockService;
import android.app.stubs.RemoteActivity;
import android.app.stubs.ScreenOnActivity;
import android.app.stubs.TestHomeActivity;
import android.app.stubs.shared.CommandReceiver;
import android.app.stubs.shared.LocalForegroundService;
import android.app.tools.WatchUidRunner;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.NewUserRequest;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.annotations.RestrictedBuildTest;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.settings.SettingsSession;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AmMonitor;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.UserHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class ActivityManagerTest {
    private static final String TAG = ActivityManagerTest.class.getSimpleName();
    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final long WAITFOR_MSEC = 10000;
    // Long enough to cover devices with doubled hw multipliers. On most devices
    // this should be 10s as defined in ActivityManagerService#PROC_START_TIMEOUT
    private static final long WAITFOR_PROCSTAT_TIMEOUT_MSEC = 30000;
    private static final String SERVICE_NAME = "android.app.stubs.MockService";
    private static final long WAIT_TIME = 2000;
    // A secondary test activity from another APK.
    static final String SIMPLE_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    static final String SIMPLE_ACTIVITY = ".SimpleActivity";
    static final String SIMPLE_ACTIVITY_COMPONENT = STUB_PACKAGE_NAME + SIMPLE_ACTIVITY;
    static final String SIMPLE_ACTIVITY_IMMEDIATE_EXIT = ".SimpleActivityImmediateExit";
    static final String SIMPLE_ACTIVITY_CHAIN_EXIT = ".SimpleActivityChainExit";
    static final String SIMPLE_RECEIVER = ".SimpleReceiver";
    static final String SIMPLE_REMOTE_RECEIVER = ".SimpleRemoteReceiver";
    // The action sent back by the SIMPLE_APP after a restart.
    private static final String ACTIVITY_LAUNCHED_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";
    // The action sent to identify the time track info.
    private static final String ACTIVITY_TIME_TRACK_INFO = "com.android.cts.TIME_TRACK_INFO";

    private static final String DELAYED_PACKAGE_NAME = "com.android.delayed_start";
    private static final String DELAYED_ACTIVITY = ".DelayedActivity";

    private static final String PACKAGE_NAME_APP1 = "com.android.app1";
    private static final String PACKAGE_NAME_APP2 = "com.android.app2";
    private static final String PACKAGE_NAME_APP3 = "com.android.app3";
    private static final String PACKAGE_NAME_WEDGED_STARTUP = "com.android.wedged_start";

    private static final String CANT_SAVE_STATE_1_PACKAGE_NAME = "com.android.test.cantsavestate1";

    private static final String[] HELPER_PACKAGES = {
            PACKAGE_NAME_APP1,
            PACKAGE_NAME_APP2,
            PACKAGE_NAME_APP3,
            PACKAGE_NAME_WEDGED_STARTUP,
            CANT_SAVE_STATE_1_PACKAGE_NAME
    };

    private static final String MCC_TO_UPDATE = "987";
    private static final String MNC_TO_UPDATE = "654";

    // Return states of the ActivityReceiverFilter.
    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;
    public static final int RESULT_TIMEOUT = 3;

    private static final int PROCESS_CAPABILITY_ALL = PROCESS_CAPABILITY_FOREGROUND_LOCATION
            | PROCESS_CAPABILITY_FOREGROUND_CAMERA
            | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE
            | PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK
            | PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;

    private Context mTargetContext;
    private int mTestRunningUserId;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Intent mIntent;
    private List<Activity> mStartedActivityList;
    private int mErrorProcessID;
    private Instrumentation mInstrumentation;
    private HomeActivitySession mTestHomeSession;
    private boolean mAppStandbyEnabled;
    private boolean mAutomotiveDevice;
    private boolean mLeanbackOnly;
    private WindowManagerStateHelper mWmState;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation());

    private final UserHelper mUserHelper = new UserHelper();

    private boolean mIsWaitForFinishAttachApplicationEnabled;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        mTestRunningUserId = mTargetContext.getUserId();
        mActivityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        mStartedActivityList = new ArrayList<>();
        mErrorProcessID = -1;
        mAppStandbyEnabled = AppStandbyUtils.isAppStandbyEnabled();
        mAutomotiveDevice = mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        mLeanbackOnly = mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY);
        mWmState = new WindowManagerStateHelper();

        for (String pkg : HELPER_PACKAGES) {
            CtsAppTestUtils.clearBadProcess(pkg, mTestRunningUserId);
        }
        startSubActivity(ScreenOnActivity.class);
        AmUtils.waitForBroadcastBarrier();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestHomeSession != null) {
            mTestHomeSession.close();
        }
        if (mIntent != null) {
            mInstrumentation.getContext().stopService(mIntent);
        }
        for (int i = 0; i < mStartedActivityList.size(); i++) {
            mStartedActivityList.get(i).finish();
        }
        if (mErrorProcessID != -1) {
            android.os.Process.killProcess(mErrorProcessID);
        }

        // Ensure that there are no remaining component records of the test app package.
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(SIMPLE_PACKAGE_NAME));
    }

    @Test
    public void testGetRecentTasks() throws Exception {
        int maxNum = 0;
        int flags = 0;

        List<RecentTaskInfo> recentTaskList;
        // Test parameter: maxNum is set to 0
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertThat(recentTaskList).isNotNull();
        assertThat(recentTaskList).isEmpty();
        // Test parameter: maxNum is set to 50
        maxNum = 50;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertThat(recentTaskList).isNotNull();
        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);
        SystemClock.sleep(WAIT_TIME);
        // start recent2_activity
        startSubActivity(ActivityManagerRecentTwoActivity.class);
        SystemClock.sleep(WAIT_TIME);
        /*
         * assert both recent1_activity and recent2_activity exist in the recent
         * tasks list. Moreover,the index of the recent2_activity is smaller
         * than the index of recent1_activity
         */
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int indexRecentOne = getTaskInfoIndex(recentTaskList,
                ActivityManagerRecentOneActivity.class);
        int indexRecentTwo = getTaskInfoIndex(recentTaskList,
                ActivityManagerRecentTwoActivity.class);
        assertThat(indexRecentOne).isNotEqualTo(-1);
        assertThat(indexRecentTwo).isNotEqualTo(-1);
        assertThat(indexRecentTwo).isLessThan(indexRecentOne);

        try {
            mActivityManager.getRecentTasks(-1, 0);
            assertWithMessage("Should throw IllegalArgumentException").fail();
        } catch (IllegalArgumentException e) {
            // expected exception
        }
    }

    @Test
    public void testGetRecentTasksLimitedToCurrentAPK() throws Exception {
        int maxNum = 0;
        int flags = 0;

        // Check the number of tasks at this time.
        List<RecentTaskInfo>  recentTaskList;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesFirstRun = recentTaskList.size();

        // Start another activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityReceiverFilter receiver = new ActivityReceiverFilter(ACTIVITY_LAUNCHED_ACTION);
        mTargetContext.startActivity(intent);

        // Make sure the activity has really started.
        assertThat(receiver.waitForActivity()).isEqualTo(RESULT_PASS);
        receiver.close();

        // There shouldn't be any more tasks in this list at this time.
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesSecondRun = recentTaskList.size();
        assertThat(numberOfEntriesFirstRun).isEqualTo(numberOfEntriesSecondRun);

        // Tell the activity to finalize.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("finish", true);
        mTargetContext.startActivity(intent);
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private final String mActivityToFilter;
        private int result = RESULT_TIMEOUT;
        public long mTimeUsed = 0;
        private static final int TIMEOUT_IN_MS = 5000;

        // Create the filter with the intent to look for.
        public ActivityReceiverFilter(String activityToFilter) {
            mActivityToFilter = activityToFilter;
            IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mInstrumentation.getTargetContext().registerReceiver(this, filter,
                    Context.RECEIVER_EXPORTED);
        }

        // Turn off the filter.
        public void close() {
            mInstrumentation.getTargetContext().unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                synchronized(this) {
                   result = RESULT_PASS;
                   if (mActivityToFilter.equals(ACTIVITY_TIME_TRACK_INFO)) {
                       mTimeUsed = intent.getExtras().getLong(
                               ActivityOptions.EXTRA_USAGE_TIME_REPORT);
                   }
                   notifyAll();
                }
            }
        }

        public int waitForActivity() {
            AmUtils.waitForBroadcastBarrier();
            synchronized(this) {
                try {
                    wait(TIMEOUT_IN_MS);
                } catch (InterruptedException e) {
                }
            }
            return result;
        }
    }

    private <T extends Activity> void startSubActivity(Class<T> activityClass) {
        startSubActivity(activityClass, null);
    }

    private <T extends Activity> void startSubActivity(
            Class<T> activityClass,
            ActivityOptions activityOptions) {
        final Instrumentation.ActivityResult result = new ActivityResult(0, new Intent());
        final ActivityMonitor monitor = new ActivityMonitor(activityClass.getName(), result, false);
        mInstrumentation.addMonitor(monitor);
        launchActivity(activityClass, activityOptions);
        mStartedActivityList.add(monitor.waitForActivity());
    }

    private <T extends Activity> T launchActivity(
            Class<T> activityCls,
            ActivityOptions activityOptions) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        return launchStubActivityWithIntent(activityCls, intent, activityOptions);
    }

    private <T extends Activity> T launchStubActivityWithIntent(
            Class<T> activityCls, Intent intent, ActivityOptions activityOptions) {
        intent.setClassName(ActivityManagerTest.STUB_PACKAGE_NAME, activityCls.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        T activity = (T) mInstrumentation.startActivitySync(
                intent, activityOptions == null ? null : activityOptions.toBundle());
        mInstrumentation.waitForIdleSync();
        return activity;
    }

    private <T extends TaskInfo, S extends Activity> int getTaskInfoIndex(List<T> taskList,
            Class<S> activityClass) {
        int i = 0;
        for (TaskInfo ti : taskList) {
            if (ti.baseActivity.getClassName().equals(activityClass.getName())) {
                return i;
            }
            i++;
        }
        return -1;
    }

    @Test
    public void testGetRunningTasks() {
        // Test illegal parameter
        List<RunningTaskInfo> runningTaskList = mActivityManager.getRunningTasks(-1);
        assertThat(runningTaskList).isEmpty();

        runningTaskList = mActivityManager.getRunningTasks(0);
        assertThat(runningTaskList).isEmpty();

        runningTaskList = mActivityManager.getRunningTasks(20);
        assertThat(runningTaskList.size()).isAtMost(20);

        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);

        runningTaskList = mActivityManager.getRunningTasks(20);

        // assert only recent1_activity exists and is visible.
        int indexRecentOne = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentOneActivity.class);
        int indexRecentTwo = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentTwoActivity.class);
        assertThat(indexRecentOne).isNotEqualTo(-1);
        assertThat(indexRecentTwo).isEqualTo(-1);
        assertThat(runningTaskList.get(indexRecentOne).isVisible()).isTrue();

        // start recent2_activity in fullscreen to hide the recent1_activity.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        startSubActivity(ActivityManagerRecentTwoActivity.class, options);

        /*
         * assert both recent1_activity and recent2_activity exist in the
         * running tasks list. Moreover,the index of the recent2_activity is
         * smaller than the index of recent1_activity.
         */
        runningTaskList = mActivityManager.getRunningTasks(20);
        indexRecentOne = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentOneActivity.class);
        indexRecentTwo = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentTwoActivity.class);
        assertThat(indexRecentOne).isNotEqualTo(-1);
        assertThat(indexRecentTwo).isNotEqualTo(-1);
        assertThat(indexRecentTwo).isLessThan(indexRecentOne);

        boolean isRecentTwoActivityInMultiWindowMode = false;
        for (int i = mStartedActivityList.size() - 1; i >= 0; i--) {
            final Activity activity = mStartedActivityList.get(i);
            if (activity.getClass() == ActivityManagerRecentTwoActivity.class) {
                isRecentTwoActivityInMultiWindowMode = activity.isInMultiWindowMode();
                break;
            }
        }
        // Different form factors may force tasks to be multi-window (e.g. in freeform windowing
        // mode). If recent2_activity is in multi-windowing mode, it may not fully obscure
        // recent1_activity.
        if (!isRecentTwoActivityInMultiWindowMode) {
            assertThat(runningTaskList.get(indexRecentOne).isVisible()).isFalse();
        }
        assertThat(runningTaskList.get(indexRecentTwo).isVisible()).isTrue();
    }

    @Test
    public void testGetRunningServices() {
        // Test illegal parameter
        List<RunningServiceInfo> runningServiceInfo;
        runningServiceInfo = mActivityManager.getRunningServices(-1);
        assertThat(runningServiceInfo).isEmpty();

        runningServiceInfo = mActivityManager.getRunningServices(0);
        assertThat(runningServiceInfo).isEmpty();

        runningServiceInfo = mActivityManager.getRunningServices(5);
        assertThat(runningServiceInfo.size()).isAtMost(5);

        Intent intent = new Intent();
        intent.setClass(mInstrumentation.getTargetContext(), MockService.class);
        intent.putExtra(MockService.EXTRA_NO_STOP, true);
        mInstrumentation.getTargetContext().startService(intent);
        MockService.waitForStart(WAIT_TIME);

        runningServiceInfo = mActivityManager.getRunningServices(Integer.MAX_VALUE);
        boolean foundService = false;
        for (RunningServiceInfo rs : runningServiceInfo) {
            if (rs.service.getClassName().equals(SERVICE_NAME)) {
                foundService = true;
                break;
            }
        }
        assertThat(foundService).isTrue();
        MockService.prepareDestroy();
        mTargetContext.stopService(intent);
        boolean destroyed = MockService.waitForDestroy(WAIT_TIME);
        assertThat(destroyed).isTrue();
    }

    private void executeAndLogShellCommand(String cmd) throws IOException {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        final String output = uiDevice.executeShellCommand(cmd);
        Log.d(TAG, "executed[" + cmd + "]; output[" + output.trim() + "]");
    }

    private String executeShellCommand(String cmd) throws IOException {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        return uiDevice.executeShellCommand(cmd).trim();
    }

    private void setForcedAppStandby(String packageName, boolean enabled)
            throws IOException {
        String cmdBuilder =
                "appops set --user "
                        + mUserHelper.getUserId()
                        + ' '
                        + packageName
                        + " RUN_ANY_IN_BACKGROUND "
                        + (enabled ? "ignore" : "allow");
        executeAndLogShellCommand(cmdBuilder);
    }

    @Test
    public void testIsBackgroundRestricted() throws IOException {
        // This instrumentation runs in the target package's uid.
        final String targetPackage = mTargetContext.getPackageName();
        final ActivityManager am = mTargetContext.getSystemService(ActivityManager.class);
        setForcedAppStandby(targetPackage, true);
        assertThat(am.isBackgroundRestricted()).isTrue();
        setForcedAppStandby(targetPackage, false);
        assertThat(am.isBackgroundRestricted()).isFalse();
    }

    @FlakyTest(detail = "Known fail on cuttlefish b/275888802 and other devices b/255817314.")
    @Test
    public void testGetMemoryInfo() {
        // Advertised memory is required when VSR API is V.
        if (PropertyUtil.getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityManager.MemoryInfo outInfo = new ActivityManager.MemoryInfo();
            mActivityManager.getMemoryInfo(outInfo);
            assertWithMessage(
                            String.format(
                                    "lowmemory (%s) == (%d <= %d)",
                                    outInfo.lowMemory, outInfo.availMem, outInfo.threshold))
                    .that(outInfo.lowMemory == (outInfo.availMem <= outInfo.threshold))
                    .isTrue();
            assertWithMessage(
                            String.format(
                                    "totalMem (%d) <= advertisedMem (%d)",
                                    outInfo.totalMem, outInfo.advertisedMem))
                    .that(outInfo.totalMem <= outInfo.advertisedMem)
                    .isTrue();
        }
    }

    @Test
    public void testGetRunningAppProcesses() throws Exception {
        List<RunningAppProcessInfo> list = mActivityManager.getRunningAppProcesses();
        assertThat(list).isNotNull();
        final String SYSTEM_PROCESS = "system";
        boolean hasSystemProcess = false;
        // The package name is also the default name for the application process
        final String TEST_PROCESS = STUB_PACKAGE_NAME;
        boolean hasTestProcess = false;
        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals(SYSTEM_PROCESS)) {
                hasSystemProcess = true;

                // Make sure the importance is a sane value.
                assertThat(ra.importance >= RunningAppProcessInfo.IMPORTANCE_FOREGROUND).isTrue();
                assertThat(ra.importance < RunningAppProcessInfo.IMPORTANCE_GONE).isTrue();
            } else if (ra.processName.equals(TEST_PROCESS)) {
                hasTestProcess = true;
            }
        }

        // For security reasons the system process is not exposed.
        assertThat(hasSystemProcess).isFalse();
        assertThat(hasTestProcess).isTrue();

        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                assertWithMessage("should be no process named android.app.stubs:remote").fail();
            }
        }
        // start a new process
        // XXX would be a lot cleaner to bind instead of start.
        mIntent = new Intent("android.app.REMOTESERVICE");
        mIntent.setPackage("android.app.stubs");
        mInstrumentation.getTargetContext().startService(mIntent);
        SystemClock.sleep(WAITFOR_MSEC);

        List<RunningAppProcessInfo> listNew = mActivityManager.getRunningAppProcesses();
        mInstrumentation.getTargetContext().stopService(mIntent);

        for (RunningAppProcessInfo ra : listNew) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                return;
            }
        }
        assertWithMessage("android.app.stubs:remote process should be available").fail();
    }

    @Test
    public void testGetMyMemoryState() {
        final RunningAppProcessInfo ra = new RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(ra);

        assertThat(ra.uid).isEqualTo(android.os.Process.myUid());

        // When an instrumentation test is running, the importance is high.
        assertThat(ra.importance).isEqualTo(RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    }

    @Test
    public void testGetProcessInErrorState() throws Exception {
        List<ActivityManager.ProcessErrorStateInfo> errList;
        errList = mActivityManager.getProcessesInErrorState();
        assertThat(errList).isNull();

        // Setup the ANR monitor.
        final AmMonitor monitor = new AmMonitor(mInstrumentation, null);
        final ApplicationInfo app1Info = mTargetContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        final ApplicationInfo stubInfo = mTargetContext.getPackageManager().getApplicationInfo(
                STUB_PACKAGE_NAME, 0);
        final WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC, PROCESS_CAPABILITY_ALL);
        final String crashActivityName = "ActivityManagerStubCrashActivity";

        // Since this resource is explicitly closed in the `finally` block, a `try-with-resources`
        // statement was intentionally omitted.
        final SettingsSession<Integer> showOnFirstCrash =
                new SettingsSession<>(
                        Settings.Global.getUriFor(Settings.Global.SHOW_FIRST_CRASH_DIALOG),
                        Settings.Global::getInt,
                        Settings.Global::putInt);
        // Since this resource is explicitly closed in the `finally` block, a `try-with-resources`
        // statement was intentionally omitted.
        final SettingsSession<Integer> showBackground =
                new SettingsSession<>(
                        Settings.Secure.getUriFor(Settings.Secure.ANR_SHOW_BACKGROUND),
                        Settings.Secure::getInt,
                        Settings.Secure::putInt);
        try (AutoCloseable unused =
                CtsAppTestUtils.allowBackgroundActivityLaunch(PACKAGE_NAME_APP1)) {
            runWithShellPermissionIdentity(
                    () -> {
                        showOnFirstCrash.set(1);
                        showBackground.set(1);
                    });

            CommandReceiver.sendCommand(
                    mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1,
                    0,
                    null);
            uid1Watcher.waitFor(
                    WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_TOP,
                    Integer.valueOf(PROCESS_CAPABILITY_ALL));

            // Sleep a while to let things go through.
            SystemClock.sleep(WAIT_TIME);

            // Now tell it goto ANR.
            CommandReceiver.sendCommand(
                    mTargetContext,
                    CommandReceiver.COMMAND_SELF_INDUCED_ANR,
                    PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1,
                    0,
                    null);

            // Verify we got the ANR.
            assertThat(monitor.waitFor(AmMonitor.WAIT_FOR_EARLY_ANR, WAITFOR_MSEC)).isTrue();

            // Let it continue.
            monitor.sendCommand(AmMonitor.CMD_CONTINUE);

            // Now it should've reached the normal ANR process.
            assertThat(monitor.waitFor(AmMonitor.WAIT_FOR_ANR, WAITFOR_MSEC * 3)).isTrue();

            // Continue again, we need to see the ANR dialog in order to get the error
            // report.
            monitor.sendCommand(AmMonitor.CMD_CONTINUE);

            // Sleep a while to let things go through.
            SystemClock.sleep(WAIT_TIME);

            // We shouldn't be able to read the error state info of that.
            errList = mActivityManager.getProcessesInErrorState();
            assertThat(errList).isNull();

            // Shell should have the access.
            final List<ActivityManager.ProcessErrorStateInfo>[] holder = new List[1];
            runWithShellPermissionIdentity(
                    () -> {
                        holder[0] = mActivityManager.getProcessesInErrorState();
                    });
            assertThat(holder[0]).isNotNull();
            assertThat(holder[0]).hasSize(1);
            verifyProcessErrorStateInfo(
                    holder[0].get(0),
                    ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                    app1Info.uid,
                    PACKAGE_NAME_APP1);

            // Start a crashing activity in remote process with the same UID.
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(STUB_PACKAGE_NAME, STUB_PACKAGE_NAME + "." + crashActivityName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);

            // Wait for the crash.
            assertThat(monitor.waitFor(AmMonitor.WAIT_FOR_CRASHED, WAITFOR_MSEC)).isTrue();

            // Let it continue, we need to see the crash dialog in order to get the error
            // report.
            monitor.sendCommand(AmMonitor.CMD_CONTINUE);

            // Sleep a while to let things go through.
            SystemClock.sleep(WAIT_TIME);

            // We should be able to see this crash info.
            errList = mActivityManager.getProcessesInErrorState();
            assertThat(errList).isNotNull();
            assertThat(errList).hasSize(1);

            verifyProcessErrorStateInfo(
                    errList.get(0),
                    ActivityManager.ProcessErrorStateInfo.CRASHED,
                    stubInfo.uid,
                    STUB_PACKAGE_NAME + ":" + crashActivityName);

            // Shell should have the access to all of the crash info here.
            runWithShellPermissionIdentity(
                    () -> {
                        holder[0] = mActivityManager.getProcessesInErrorState();
                    });
            assertThat(holder[0]).isNotNull();
            assertThat(holder[0]).hasSize(2);
            // The return result is not sorted.
            final ActivityManager.ProcessErrorStateInfo t0 = holder[0].get(0);
            final ActivityManager.ProcessErrorStateInfo t1 = holder[0].get(1);
            final ActivityManager.ProcessErrorStateInfo info0 = t0.uid == stubInfo.uid ? t0 : t1;
            final ActivityManager.ProcessErrorStateInfo info1 = t1.uid == app1Info.uid ? t1 : t0;

            verifyProcessErrorStateInfo(
                    info0,
                    ActivityManager.ProcessErrorStateInfo.CRASHED,
                    stubInfo.uid,
                    STUB_PACKAGE_NAME + ":" + crashActivityName);
            verifyProcessErrorStateInfo(
                    info1,
                    ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                    app1Info.uid,
                    PACKAGE_NAME_APP1);
        } finally {
            runWithShellPermissionIdentity(
                    () -> {
                        showOnFirstCrash.close();
                        showBackground.close();
                    });
            monitor.finish();
            uid1Watcher.finish();
            runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(PACKAGE_NAME_APP1));
        }
    }

    private void verifyProcessErrorStateInfo(
            ActivityManager.ProcessErrorStateInfo info,
            int condition,
            int uid,
            String processName) {
        assertThat(info.condition).isEqualTo(condition);
        assertThat(info.uid).isEqualTo(uid);
        assertThat(info.processName).isEqualTo(processName);
    }

    @Test
    public void testGetDeviceConfigurationInfo() {
        ConfigurationInfo conInf = mActivityManager.getDeviceConfigurationInfo();
        assertThat(conInf).isNotNull();
    }

    @Test
    @Ignore("b/417216810 - flaky test")
    public void testUpdateMccMncConfiguration() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.i(TAG, "testUpdateMccMncConfiguration skipped: no telephony available");
            return;
        }

        Configuration originalConfig = mTargetContext.getResources().getConfiguration();
        String[] mccMncConfigToUpdate = new String[] {MCC_TO_UPDATE, MNC_TO_UPDATE};
        boolean success = ShellIdentityUtils.invokeMethodWithShellPermissions(mActivityManager,
                (am) -> am.updateMccMncConfiguration(mccMncConfigToUpdate[0],
                        mccMncConfigToUpdate[1]));

        if (success) {
            Configuration changedConfig = mTargetContext.getResources().getConfiguration();
            assertThat(Integer.toString(changedConfig.mnc)).isEqualTo(MNC_TO_UPDATE);
            assertThat(Integer.toString(changedConfig.mcc)).isEqualTo(MCC_TO_UPDATE);
        }

        // Set mcc mnc configs back in the end of the test if they were set to something else.
        ShellIdentityUtils.invokeMethodWithShellPermissions(mActivityManager,
                (am) -> am.updateMccMncConfiguration(Integer.toString(originalConfig.mcc),
                        Integer.toString(originalConfig.mnc)));
    }

    /**
     * Simple test for {@link ActivityManager#isUserAMonkey()} - verifies its false.
     *
     * <p>TODO: test positive case
     */
    @Test
    public void testIsUserAMonkey() {
        assertThat(ActivityManager.isUserAMonkey()).isFalse();
    }

    /**
     * Verify that {@link ActivityManager#isRunningInTestHarness()} is false.
     */
    @RestrictedBuildTest
    @Test
    public void testIsRunningInTestHarness() {
        assertWithMessage("isRunningInTestHarness must be false in production builds")
                .that(ActivityManager.isRunningInTestHarness())
                .isFalse();
    }

    /**
     * Go back to the home screen since running applications can interfere with application
     * lifetime tests.
     */
    private void launchHome() throws Exception {
        if (noHomeScreen()) {
            Log.d(TAG, "launchHome(): no home screen");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(intent);
        SystemClock.sleep(WAIT_TIME);
    }

    /**
     * Gets the value of com.android.internal.R.bool.config_noHomeScreen.
     * @return true if no home screen is supported, false otherwise.
     */
    private boolean noHomeScreen() {
        try {
            return mTargetContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier("config_noHomeScreen", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume there's a home screen.
            return false;
        }
    }

    /**
     * Verify that the TimeTrackingAPI works properly when starting and ending an activity.
     */
    @Test
    public void testTimeTrackingAPI_SimpleStartExit() throws Exception {
        createManagedHomeActivitySession();
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_IMMEDIATE_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO)
                .setPackage(context.getPackageName());
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context, 0, receiveIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE));

        // The application finished tracker.
        BlockingResultReceiver appEndReceiver = new BlockingResultReceiver();
        intent.putExtra(EXTRA_REMOTE_CALLBACK, appEndReceiver.getRemoteCallback());

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mTargetContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the receiver then.
        assertThat(appEndReceiver.getResult()).isEqualTo(RESULT_OK);

        if (isCurrentHomeActivityFocused()) {
            // At this time the timerReceiver should not fire, even though the activity has shut
            // down, because we are back to the home screen. Going to the home screen does not
            // qualify as the user leaving the activity's flow. The time tracking is considered
            // complete only when the user switches to another activity that is not part of the
            // tracked flow.
            assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_TIMEOUT);
            assertThat(timeReceiver.mTimeUsed).isEqualTo(0);
        } else {
            // If the system has not returned to the home screen, focus is returned to something
            // else that is considered a completion of the tracked activity flow, and hence time
            // tracking is triggered.
            assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_PASS);
        }

        // Issuing now another activity will trigger the timing information release.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the receiver then.
        assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_PASS);
        timeReceiver.close();
        assertThat(timeReceiver.mTimeUsed).isNotEqualTo(0);
    }

    @Test
    public void testHomeVisibilityListener() throws Exception {
        assumeFalse("With platforms that have no home screen, no need to test", noHomeScreen());
        assumeFalse("Skip test on TV devices", isAtvDevice());

        LinkedBlockingQueue<Boolean> currentHomeScreenVisibility = new LinkedBlockingQueue<>(2);
        HomeVisibilityListener homeVisibilityListener = new HomeVisibilityListener() {
            @Override
            public void onHomeVisibilityChanged(boolean isHomeActivityVisible) {
                currentHomeScreenVisibility.offer(isHomeActivityVisible);
            }
        };
        launchHome();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mActivityManager,
                (am) -> am.addHomeVisibilityListener(Runnable::run, homeVisibilityListener));

        try {
            // Make sure we got the first notification that the home screen is visible.
            assertThat(currentHomeScreenVisibility.poll(WAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
            // Launch a basic activity to obscure the home screen.
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);

            // Make sure the observer reports the home screen as no longer visible
            assertThat(currentHomeScreenVisibility.poll(WAIT_TIME, TimeUnit.MILLISECONDS))
                    .isFalse();
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mActivityManager,
                    (am) -> am.removeHomeVisibilityListener(homeVisibilityListener));
        }
    }

    /**
     * Verify that the TimeTrackingAPI works properly when switching away from the monitored task.
     */
    @Test
    public void testTimeTrackingAPI_SwitchAwayTriggers() throws Exception {
        createManagedHomeActivitySession();
        launchHome();

        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO)
                .setPackage(context.getPackageName());
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context, 0, receiveIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE));

        // The application started tracker.
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mTargetContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the receiver then.
        assertThat(appStartedReceiver.waitForActivity()).isEqualTo(RESULT_PASS);
        appStartedReceiver.close();

        // At this time the timerReceiver should not fire since our app is running.
        assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_TIMEOUT);
        assertThat(timeReceiver.mTimeUsed).isEqualTo(0);

        // Starting now another activity will put ours into the back hence releasing the timing.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity unused = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the receiver then.
        assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_PASS);
        timeReceiver.close();
        assertThat(timeReceiver.mTimeUsed).isNotEqualTo(0);
    }

    /**
     * Verify that the TimeTrackingAPI works properly when handling an activity chain gets started
     * and ended.
     */
    @Test
    public void testTimeTrackingAPI_ChainedActivityExit() throws Exception {
        createManagedHomeActivitySession();
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_CHAIN_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO)
                .setPackage(context.getPackageName());
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context, 0, receiveIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE));

        // The application finished tracker.
        BlockingResultReceiver appEndReceiver = new BlockingResultReceiver();
        intent.putExtra(EXTRA_REMOTE_CALLBACK, appEndReceiver.getRemoteCallback());

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mTargetContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the receiver then.
        assertThat(appEndReceiver.getResult()).isEqualTo(RESULT_OK);
        Log.e("SOSO", "Done waiting for activity exit");

        if (isCurrentHomeActivityFocused()) {
            // At this time the timerReceiver should not fire, even though the activity has shut
            // down, because we are back to the home screen. Going to the home screen does not
            // qualify as the user leaving the activity's flow. The time tracking is considered
            // complete only when the user switches to another activity that is not part of the
            // tracked flow.
            assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_TIMEOUT);
            assertThat(timeReceiver.mTimeUsed).isEqualTo(0);
        } else {
            // If the system has not returned to the home screen, focus is returned to something
            // else that is considered a completion of the tracked activity flow, and hence time
            // tracking is triggered.
            assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_PASS);
        }

        // Issue another activity so that the timing information gets released.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity unused = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the receiver then.
        assertThat(timeReceiver.waitForActivity()).isEqualTo(RESULT_PASS);
        timeReceiver.close();
        assertThat(timeReceiver.mTimeUsed).isNotEqualTo(0);
    }

    /**
     * Verify that after force-stopping a package which has a foreground task contains multiple
     * activities, the process of the package should not be alive (restarted).
     */
    @Test
    public void testForceStopPackageWontRestartProcess() throws Exception {
        // Ensure that there are no remaining component records of the test app package.
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(SIMPLE_PACKAGE_NAME));
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);
        // Start an activity of another APK.
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(intent);
        assertThat(appStartedReceiver.waitForActivity()).isEqualTo(RESULT_PASS);

        // Start a new activity in the same task. Here adds an action to make a different to intent
        // filter comparison so another same activity will be created.
        intent.setAction(Intent.ACTION_VIEW);
        mTargetContext.startActivity(intent);
        assertThat(appStartedReceiver.waitForActivity()).isEqualTo(RESULT_PASS);
        appStartedReceiver.close();

        // Wait for the first activity to stop so its ActivityRecord.haveState will be true. The
        // condition is required to keep the activity record when its process is died.
        SystemClock.sleep(WAIT_TIME);

        // The package name is also the default name for the activity process.
        final String testProcess = SIMPLE_PACKAGE_NAME;
        Predicate<RunningAppProcessInfo> processNamePredicate =
                runningApp -> testProcess.equals(runningApp.processName);

        List<RunningAppProcessInfo> runningApps = callWithShellPermissionIdentity(
                () -> mActivityManager.getRunningAppProcesses());
        assertWithMessage("Process " + testProcess + " should be found in running process list")
                .that(runningApps.stream().anyMatch(processNamePredicate))
                .isTrue();

        runningApps =
                callWithShellPermissionIdentity(
                        () -> {
                            mActivityManager.forceStopPackage(SIMPLE_PACKAGE_NAME);
                            // Wait awhile (process starting may be asynchronous) to verify if the
                            // process is
                            // started again unexpectedly.
                            SystemClock.sleep(WAIT_TIME);
                            return mActivityManager.getRunningAppProcesses();
                        });

        assertWithMessage("Process " + testProcess + " should not be alive after force-stop")
                .that(runningApps.stream().anyMatch(processNamePredicate))
                .isFalse();
    }

    /**
     * This test is to verify that devices are patched with the fix in b/119327603 for b/115384617.
     */
    @Test
    public void testIsAppForegroundRemoved() throws ClassNotFoundException {
       try {
           Class.forName("android.app.IActivityManager").getDeclaredMethod(
                   "isAppForeground", int.class);
            assertWithMessage("IActivityManager.isAppForeground() API should not be available.")
                    .fail();
       } catch (NoSuchMethodException e) {
           // Patched devices should throw this exception since isAppForeground is removed.
       }
    }

    /**
     * This test verifies the self-induced ANR by ActivityManager.appNotResponding().
     */
    @Test
    public void testAppNotResponding() throws Exception {
        // Setup the ANR monitor
        AmMonitor monitor = new AmMonitor(mInstrumentation,
                new String[]{AmMonitor.WAIT_FOR_CRASHED});

        // Now tell it goto ANR
        CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_SELF_INDUCED_ANR,
                PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

        try {

            // Verify we got the ANR
            assertThat(monitor.waitFor(AmMonitor.WAIT_FOR_EARLY_ANR, WAITFOR_MSEC)).isTrue();

            // Just kill the test app
            monitor.sendCommand(AmMonitor.CMD_KILL);
        } finally {
            // clean up
            monitor.finish();
            runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(PACKAGE_NAME_APP1));
        }
    }

    /** This test verifies that the PROC_START_TIMEOUT triggers an ANR. */
    @Test
    public void testAppNotRespondingOnStartup() {
        runWithShellPermissionIdentity(() -> {
            mIsWaitForFinishAttachApplicationEnabled = DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    "enable_wait_for_finish_attach_application",
                    false);
        });

        assumeTrue("App startup ANRs disabled", mIsWaitForFinishAttachApplicationEnabled);

        // Setup the ANR monitor
        AmMonitor monitor = new AmMonitor(mInstrumentation,
                new String[]{AmMonitor.WAIT_FOR_CRASHED});

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PACKAGE_NAME_WEDGED_STARTUP, MockApplicationActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mTargetContext.startActivity(intent);

        try {
            // Verify we got the ANR
            assertThat(monitor.waitFor(AmMonitor.WAIT_FOR_EARLY_ANR, WAITFOR_PROCSTAT_TIMEOUT_MSEC))
                    .isTrue();

            // Just kill the test app
            monitor.sendCommand(AmMonitor.CMD_KILL);
        } finally {
            // clean up
            monitor.finish();
            runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(PACKAGE_NAME_WEDGED_STARTUP));
        }
    }

    /*
     * Verifies the {@link android.app.ActivityManager#killProcessesWhenImperceptible}.
     */
    @Test
    public void testKillingPidsOnImperceptible() throws Exception {
        // Start remote service process
        final String remoteProcessName = STUB_PACKAGE_NAME + ":remote";
        Intent remoteIntent = new Intent("android.app.REMOTESERVICE");
        remoteIntent.setPackage(STUB_PACKAGE_NAME);
        mTargetContext.startService(remoteIntent);
        SystemClock.sleep(WAITFOR_MSEC);

        RunningAppProcessInfo remote = getRunningAppProcessInfo(remoteProcessName);
        assertThat(remote).isNotNull();

        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);
        boolean disabled = "0".equals(executeShellCommand("cmd deviceidle enabled light"));
        try {
            if (disabled) {
                executeAndLogShellCommand("cmd deviceidle enable light");
            }
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);
            assertThat(appStartedReceiver.waitForActivity()).isEqualTo(RESULT_PASS);

            RunningAppProcessInfo proc = getRunningAppProcessInfo(SIMPLE_PACKAGE_NAME);
            assertThat(proc).isNotNull();

            final String reason = "cts";
            assertThrows(
                    "Shouldn't have the permission",
                    SecurityException.class,
                    () ->
                            mActivityManager.killProcessesWhenImperceptible(
                                    new int[] {proc.pid}, reason));

            final long defaultWaitForKillTimeout = 5_000;

            // Keep the device awake
            toggleScreenOn(true);

            // Kill the remote process
            runWithShellPermissionIdentity(
                    () ->
                            mActivityManager.killProcessesWhenImperceptible(
                                    new int[] {remote.pid}, reason));

            // Kill the activity process
            runWithShellPermissionIdentity(
                    () ->
                            mActivityManager.killProcessesWhenImperceptible(
                                    new int[] {proc.pid}, reason));

            // The processes should be still alive because device isn't in idle
            assertThat(
                            waitUntilTrue(
                                    defaultWaitForKillTimeout,
                                    () -> isProcessGone(remote.pid, remoteProcessName)))
                    .isFalse();
            assertThat(
                            waitUntilTrue(
                                    defaultWaitForKillTimeout,
                                    () -> isProcessGone(proc.pid, SIMPLE_PACKAGE_NAME)))
                    .isFalse();

            if (isAtvDevice()) {
                // On operator tier devices of AndroidTv, Activity is put behind TvLauncher
                // after turnScreenOff by android.intent.category.HOME intent from
                // TvRecommendation.
                return;
            }

            // force device idle
            toggleScreenOn(false);
            triggerIdle(true);

            // Now the remote process should have been killed.
            assertThat(
                            waitUntilTrue(
                                    defaultWaitForKillTimeout,
                                    () -> isProcessGone(remote.pid, remoteProcessName)))
                    .isTrue();

            // The activity process should be still alive because it's is on the top (perceptible)
            assertThat(
                            waitUntilTrue(
                                    defaultWaitForKillTimeout,
                                    () -> isProcessGone(proc.pid, SIMPLE_PACKAGE_NAME)))
                    .isFalse();

            triggerIdle(false);
            // Toggle screen ON
            toggleScreenOn(true);

            // Now launch home
            executeAndLogShellCommand("input -d " + mUserHelper.getMainDisplayId()
                    + " keyevent KEYCODE_HOME");

            // force device idle again
            toggleScreenOn(false);
            triggerIdle(true);

            // Now the activity process should be gone.
            assertThat(
                            waitUntilTrue(
                                    defaultWaitForKillTimeout,
                                    () -> isProcessGone(proc.pid, SIMPLE_PACKAGE_NAME)))
                    .isTrue();

        } finally {
            // Clean up code
            triggerIdle(false);
            toggleScreenOn(true);
            appStartedReceiver.close();
            mTargetContext.stopService(remoteIntent);

            if (disabled) {
                executeAndLogShellCommand("cmd deviceidle disable light");
            }
            runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(SIMPLE_PACKAGE_NAME));
            executeAndLogShellCommand("am kill --user " + mTestRunningUserId
                    + " " + STUB_PACKAGE_NAME);
        }
    }

    /**
     * Verifies the system will kill app's child processes if they are using excessive cpu
     */
    @LargeTest
    @Test
    public void testKillingAppChildProcess() throws Exception {
        final long powerCheckInterval = 5 * 1000;
        final long processGoneTimeout = powerCheckInterval * 4;
        final int waitForSec = 10 * 1000;
        final String activityManagerConstants = "activity_manager_constants";

        final SettingsSession<String> amSettings = new SettingsSession<>(
                Settings.Global.getUriFor(activityManagerConstants),
                Settings.Global::getString, Settings.Global::putString);

        final ApplicationInfo ai = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final WatchUidRunner watcher = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);

        try (AutoCloseable unused =
                CtsAppTestUtils.allowBackgroundActivityLaunch(PACKAGE_NAME_APP1)) {
            // Shorten the power check intervals
            amSettings.set("power_check_interval=" + powerCheckInterval);

            // Keep the device awake
            toggleScreenOn(true);

            // Start an activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            CountDownLatch startLatch = startChildProcessInPackage(PACKAGE_NAME_APP1,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertWithMessage("Failed to spawn child process")
                    .that(startLatch.await(waitForSec, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Stop the activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_CACHED, null);

            // Wait for the system to kill that light weight child (it won't happen actually)
            CountDownLatch stopLatch = initWaitingForChildProcessGone();

            assertWithMessage("App's light weight child process shouldn't be gone")
                    .that(stopLatch.await(processGoneTimeout, TimeUnit.MILLISECONDS))
                    .isFalse();

            // Now kill the light weight child
            stopLatch = stopChildProcess();

            assertWithMessage("Failed to kill app's light weight child process")
                    .that(stopLatch.await(waitForSec, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Start an activity again
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn the cpu intensive child process
            startLatch = startChildProcessInPackage(PACKAGE_NAME_APP1,
                    new String[] {"/system/bin/sh", "-c",  "while true; do :; done"});

            // Wait for the start of the child process
            assertWithMessage("Failed to spawn child process")
                    .that(startLatch.await(waitForSec, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Stop the activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_CACHED, null);

            // Wait for the system to kill that heavy child due to excessive cpu usage,
            // as well as the parent process.
            watcher.waitFor(WatchUidRunner.CMD_GONE, processGoneTimeout);

        } finally {
            amSettings.close();

            runWithShellPermissionIdentity(() -> {
                // force stop test package, where the whole test process group will be killed.
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
            });

            watcher.finish();
        }
    }

    /** Verifies the system will trim app's child processes if there are too many */
    @LargeTest
    @Test
    @Ignore("b/428002594 - flaky test")
    public void testTrimAppChildProcess() throws Exception {
        final long powerCheckInterval = 5 * 1000;
        final long processGoneTimeout = powerCheckInterval * 4;
        final int waitForSec = 5 * 1000;
        final int maxPhantomProcessesNum = 2;
        final String namespaceActivityManager = "activity_manager";
        final String activityManagerConstants = "activity_manager_constants";
        final String maxPhantomProcesses = "max_phantom_processes";

        final SettingsSession<String> amSettings = new SettingsSession<>(
                Settings.Global.getUriFor(activityManagerConstants),
                Settings.Global::getString, Settings.Global::putString);
        final Bundle currentMax = new Bundle();
        final String keyCurrent = "current";

        ApplicationInfo ai = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final WatchUidRunner watcher1 = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);
        ai = mTargetContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP2, 0);
        final WatchUidRunner watcher2 = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);
        ai = mTargetContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP3, 0);
        final WatchUidRunner watcher3 = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);

        try (AutoCloseable unused =
                CtsAppTestUtils.allowBackgroundActivityLaunch(
                        PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, PACKAGE_NAME_APP3)) {
            // Shorten the power check intervals
            amSettings.set("power_check_interval=" + powerCheckInterval);

            // Reduce the maximum phantom processes allowance
            runWithShellPermissionIdentity(() -> {
                int current = DeviceConfig.getInt(namespaceActivityManager,
                        maxPhantomProcesses, -1);
                currentMax.putInt(keyCurrent, current);
                DeviceConfig.setProperty(namespaceActivityManager,
                        maxPhantomProcesses,
                        Integer.toString(maxPhantomProcessesNum), false);
            });

            // Keep the device awake
            toggleScreenOn(true);

            // Start an activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher1.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            CountDownLatch startLatch = startChildProcessInPackage(PACKAGE_NAME_APP1,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertWithMessage("Failed to spawn child process")
                    .that(startLatch.await(waitForSec, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Start an activity in another package
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP2, PACKAGE_NAME_APP2, 0, null);

            watcher2.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            startLatch = startChildProcessInPackage(PACKAGE_NAME_APP2,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertWithMessage("Failed to spawn child process")
                    .that(startLatch.await(waitForSec, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Finish the 1st activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher1.waitFor(WatchUidRunner.CMD_CACHED, null);

            // Wait for the system to kill that light weight child (it won't happen actually)
            CountDownLatch stopLatch = initWaitingForChildProcessGone();

            assertWithMessage("App's light weight child process shouldn't be gone")
                    .that(stopLatch.await(processGoneTimeout, TimeUnit.MILLISECONDS))
                    .isFalse();

            // Sleep a while
            SystemClock.sleep(powerCheckInterval);

            // Now start an activity in the 3rd party
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP3, PACKAGE_NAME_APP3, 0, null);

            watcher3.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            startLatch = startChildProcessInPackage(PACKAGE_NAME_APP3,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertWithMessage("Failed to spawn child process")
                    .that(startLatch.await(waitForSec, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Now the 1st child process should have been gone.
            stopLatch = initWaitingForChildProcessGone();

            assertWithMessage("1st App's child process should have been gone")
                    .that(stopLatch.await(processGoneTimeout, TimeUnit.MILLISECONDS))
                    .isTrue();

        } finally {
            amSettings.close();

            runWithShellPermissionIdentity(() -> {
                final int current = currentMax.getInt(keyCurrent);
                if (current < 0) {
                    // Hm, DeviceConfig doesn't have an API to delete a property,
                    // let's set it empty so the code will use the built-in default value.
                    DeviceConfig.setProperty(namespaceActivityManager,
                            maxPhantomProcesses, "", false);
                } else {
                    DeviceConfig.setProperty(namespaceActivityManager,
                            maxPhantomProcesses, Integer.toString(current), false);
                }
            });

            runWithShellPermissionIdentity(() -> {
                // force stop test package, where the whole test process group will be killed.
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP2);
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP3);
            });

            watcher1.finish();
            watcher2.finish();
            watcher3.finish();
        }
    }

    private CountDownLatch startChildProcessInPackage(String pkgName, String[] cmdline) {
        final CountDownLatch startLatch = new CountDownLatch(1);

        final IBinder binder =
                new Binder() {
                    @Override
                    protected boolean onTransact(
                            int code, @NonNull Parcel data, Parcel reply, int flags) {
                        if (code == CommandReceiver.RESULT_CHILD_PROCESS_STARTED) {
                            startLatch.countDown();
                            return true;
                        }
                        return false;
                    }
                };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        extras.putStringArray(CommandReceiver.EXTRA_CHILD_CMDLINE, cmdline);

        CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_CHILD_PROCESS,
                pkgName, pkgName, 0, extras);

        return startLatch;
    }

    private CountDownLatch stopChildProcess() {
        final CountDownLatch stopLatch = new CountDownLatch(1);

        final IBinder binder =
                new Binder() {
                    @Override
                    protected boolean onTransact(
                            int code, @NonNull Parcel data, Parcel reply, int flags) {
                        if (code == CommandReceiver.RESULT_CHILD_PROCESS_STOPPED) {
                            stopLatch.countDown();
                            return true;
                        }
                        return false;
                    }
                };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        extras.putLong(CommandReceiver.EXTRA_TIMEOUT, 10000);

        CommandReceiver.sendCommand(
                mTargetContext,
                CommandReceiver.COMMAND_STOP_CHILD_PROCESS,
                ActivityManagerTest.PACKAGE_NAME_APP1,
                ActivityManagerTest.PACKAGE_NAME_APP1,
                0,
                extras);

        return stopLatch;
    }

    private CountDownLatch initWaitingForChildProcessGone() {
        final CountDownLatch stopLatch = new CountDownLatch(1);

        final IBinder binder =
                new Binder() {
                    @Override
                    protected boolean onTransact(
                            int code, @NonNull Parcel data, Parcel reply, int flags) {
                        if (code == CommandReceiver.RESULT_CHILD_PROCESS_GONE) {
                            stopLatch.countDown();
                            return true;
                        }
                        return false;
                    }
                };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        extras.putLong(CommandReceiver.EXTRA_TIMEOUT, 20000L);

        CommandReceiver.sendCommand(
                mTargetContext,
                CommandReceiver.COMMAND_WAIT_FOR_CHILD_PROCESS_GONE,
                ActivityManagerTest.PACKAGE_NAME_APP1,
                ActivityManagerTest.PACKAGE_NAME_APP1,
                0,
                extras);

        return stopLatch;
    }

    @Test
    public void testTrimMemActivityFg() throws Exception {

        final int waitForSec = 5 * 1000;
        final ApplicationInfo ai1 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final WatchUidRunner watcher1 = new WatchUidRunner(mInstrumentation, ai1.uid, waitForSec);

        final ApplicationInfo ai2 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP2, 0);
        final WatchUidRunner watcher2 = new WatchUidRunner(mInstrumentation, ai2.uid, waitForSec);

        final ApplicationInfo ai3 = mTargetContext.getPackageManager()
                .getApplicationInfo(CANT_SAVE_STATE_1_PACKAGE_NAME, 0);
        final WatchUidRunner watcher3 = new WatchUidRunner(mInstrumentation, ai3.uid, waitForSec);

        final CountDownLatch[] latchHolder = new CountDownLatch[1];
        final int[] expectedLevel = new int[1];
        final Bundle extras = initWaitingForTrimLevel(level -> {
            if (level == expectedLevel[0]) {
                latchHolder[0].countDown();
            }
        });
        try (AutoCloseable unused =
                CtsAppTestUtils.allowBackgroundActivityLaunch(PACKAGE_NAME_APP1)) {
            // Override the memory pressure level, force it staying at normal.
            runShellCommand(mInstrumentation, "am memory-factor set NORMAL");

            // Keep the device awake
            toggleScreenOn(true);

            // Start an activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);

            watcher1.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_START_NO_FOREGROUND));

            latchHolder[0] = new CountDownLatch(1);
            expectedLevel[0] = TRIM_MEMORY_UI_HIDDEN;
            // Start another activity in package2
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            watcher2.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
            ComponentName app1 = new ComponentName(PACKAGE_NAME_APP1, SIMPLE_ACTIVITY_COMPONENT);
            mWmState.waitForValidState(app1);
            boolean resumed = mWmState.waitForActivityState(app1, WindowManagerState.STATE_RESUMED);
            if (!resumed) {
                watcher1.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE, null);
                assertWithMessage("Failed to wait for the trim memory event")
                        .that(latchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS))
                        .isTrue();
            } else {
                // On some form factors, like XR, multiple activities can be in the resumed state,
                // so PACKAGE_NAME_APP1 doesn't go into the background or the service state.
                // Therefore, there should not be any changes in the process state
                // or the memory trim level.
                assertWithMessage("The memory trim level shouldn't change")
                        .that(latchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS))
                        .isFalse();
            }
        } finally {
            runShellCommand(mInstrumentation, "am memory-factor reset");

            runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP2);
                mActivityManager.forceStopPackage(CANT_SAVE_STATE_1_PACKAGE_NAME);
            });

            watcher1.finish();
            watcher2.finish();
            watcher3.finish();
        }
    }

    @Test
    public void testServiceDoneLRUPosition() throws Exception {
        final String[] packageNames = {PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, PACKAGE_NAME_APP3};
        final String[] otherPackages = {PACKAGE_NAME_APP2, PACKAGE_NAME_APP3};
        final WatchUidRunner[] watchers = initWatchUidRunners(packageNames, WAITFOR_MSEC);
        final HandlerThread handlerThread = new HandlerThread("worker");
        final Messenger[] controllerHolder = new Messenger[1];
        final CountDownLatch[] countDownLatchHolder = new CountDownLatch[1];
        handlerThread.start();
        final Messenger messenger = new Messenger(new Handler(handlerThread.getLooper(), msg -> {
            final Bundle bundle = (Bundle) msg.obj;
            final IBinder binder = bundle.getBinder(CommandReceiver.EXTRA_MESSENGER);
            if (binder != null) {
                controllerHolder[0] = new Messenger(binder);
                countDownLatchHolder[0].countDown();
            }
            return true;
        }));

        try (AutoCloseable unused = CtsAppTestUtils.allowBackgroundActivityLaunch(otherPackages)) {
            // Make sure we could start a foreground service from background
            runShellCommand(mInstrumentation, "cmd deviceidle whitelist +" + PACKAGE_NAME_APP1);

            // Keep the device awake
            toggleScreenOn(true);

            // Start a FGS in app1
            final Bundle extras = new Bundle();
            countDownLatchHolder[0] = new CountDownLatch(1);
            extras.putBinder(CommandReceiver.EXTRA_MESSENGER, messenger.getBinder());
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);

            watchers[0].waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                    null);

            assertWithMessage("Failed to get the controller interface")
                    .that(countDownLatchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS))
                    .isTrue();

            final WatchUidRunner[] otherWatchers = {watchers[1], watchers[2]};
            // Start an activity in another package
            forBiEach(otherPackages, otherWatchers, (packageName, watcher) -> {
                CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                        packageName, packageName, 0, null);
                watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
            });

            // Stop both of these activities
            forBiEach(otherPackages, otherWatchers, (packageName, watcher) -> {
                CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                        packageName, packageName, 0, null);
                watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                        null);
                // Sleep a while before proceeding to next one to make sure the activity lifecycle
                // transitions have completed.
                SystemClock.sleep(1000);
            });

            // Launch home so we'd have cleared these the above test activities from recents.
            launchHome();

            // Now stop the foreground service, we'd have to do via the controller interface
            final Message msg = Message.obtain();
            try {
                msg.what = LocalForegroundService.COMMAND_STOP_SELF;
                controllerHolder[0].send(msg);
            } catch (RemoteException e) {
                assertWithMessage("Unable to stop test package").fail();
            }
            msg.recycle();
            watchers[0].waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                    null);

            verifyLruOrders(packageNames, true, (a, b) -> a > b, "%s should be newer than %s");
        } finally {
            handlerThread.quitSafely();

            runShellCommand(mInstrumentation, "cmd deviceidle whitelist -" + PACKAGE_NAME_APP1);

            // force stop test package, where the whole test process group will be killed.
            forEach(packageNames, packageName -> runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(packageName)));

            forEach(watchers, WatchUidRunner::finish);
        }
    }

    @Test
    public void testBroadcastReceiverLRUPosition() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeFalse("not testable in automotive device", mAutomotiveDevice);
        assumeFalse("not testable in leanback device", mLeanbackOnly);

        final String[] packageNames = {PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, PACKAGE_NAME_APP3};
        final WatchUidRunner[] watchers = initWatchUidRunners(packageNames, WAITFOR_MSEC * 2);

        try {
            try (AutoCloseable unused =
                    CtsAppTestUtils.allowBackgroundActivityLaunch(packageNames)) {
                mInstrumentation
                        .getUiAutomation()
                        .revokeRuntimePermission(
                                PACKAGE_NAME_APP1,
                                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                // Set the PACKAGE_NAME_APP1 into rare bucket
                runShellCommand(
                        mInstrumentation,
                        "am set-standby-bucket --user "
                                + mTestRunningUserId
                                + " "
                                + PACKAGE_NAME_APP1
                                + " rare");

                // Keep the device awake
                toggleScreenOn(true);

                // Start activities in these packages.
                forBiEach(
                        packageNames,
                        watchers,
                        (packageName, watcher) -> {
                            CommandReceiver.sendCommand(
                                    mTargetContext,
                                    CommandReceiver.COMMAND_START_ACTIVITY,
                                    packageName,
                                    packageName,
                                    0,
                                    null);
                            watcher.waitFor(
                                    WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
                        });

                // Stop all of these activities
                forBiEach(
                        packageNames,
                        watchers,
                        (packageName, watcher) -> {
                            CommandReceiver.sendCommand(
                                    mTargetContext,
                                    CommandReceiver.COMMAND_STOP_ACTIVITY,
                                    packageName,
                                    packageName,
                                    0,
                                    null);
                            watcher.waitFor(
                                    WatchUidRunner.CMD_PROCSTATE,
                                    WatchUidRunner.STATE_CACHED_EMPTY,
                                    null);
                            // Sleep a while before proceeding to next one to make sure the activity
                            // lifecycle
                            // transitions have completed.
                            SystemClock.sleep(1000);
                        });

                // Launch home so we'd have cleared these the above test activities from recents.
                launchHome();

                // Verify the LRU position.
                verifyLruOrders(packageNames, false, (a, b) -> a < b, "%s should be older than %s");
            } // Close the AutoCloseable and revoke the ability to launch from background.

            // Restrict the PACKAGE_NAME_APP1
            runShellCommand(mInstrumentation, "am set-standby-bucket --user " + mTestRunningUserId
                    + " " + PACKAGE_NAME_APP1 + " restricted");
            final boolean restricted =
                    waitUntilTrue(
                            WAITFOR_MSEC,
                            () -> {
                                try {
                                    final int bucket =
                                            AmUtils.getStandbyBucketAsUser(
                                                    PACKAGE_NAME_APP1, mTestRunningUserId);
                                    Log.i(
                                            TAG,
                                            "Standby bucket of "
                                                    + PACKAGE_NAME_APP1
                                                    + ": "
                                                    + bucket);
                                    return bucket == STANDBY_BUCKET_RESTRICTED;
                                } catch (Exception e) {
                                    Log.e(TAG, "Error querying the standby bucket", e);
                                    return false;
                                }
                            });
            assertWithMessage(PACKAGE_NAME_APP1 + " did not move to RESTRICTED bucket")
                    .that(restricted)
                    .isTrue();

            final CountDownLatch[] latch = new CountDownLatch[] {new CountDownLatch(1)};
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    latch[0].countDown();
                }
            };
            // Send a broadcast to PACKAGE_NAME_APP1
            CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                    CommandReceiver.COMMAND_EMPTY, PACKAGE_NAME_APP1, PACKAGE_NAME_APP1,
                    0, null, receiver);

            assertWithMessage("Failed to get the broadcast")
                    .that(latch[0].await(WAITFOR_MSEC * 2, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Now check the LRU position again, it should remain the same because it's restricted.
            verifyLruOrders(packageNames, false, (a, b) -> a < b, "%s should be older than %s");

            // Set the PACKAGE_NAME_APP1 into rare bucket again.
            runShellCommand(mInstrumentation, "am set-standby-bucket --user " + mTestRunningUserId
                    + " " + PACKAGE_NAME_APP1 + " rare");

            latch[0] = new CountDownLatch(1);
            // Send a broadcast to PACKAGE_NAME_APP1 again.
            CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                    CommandReceiver.COMMAND_EMPTY, PACKAGE_NAME_APP1, PACKAGE_NAME_APP1,
                    0, null, receiver);

            // Now its LRU position should have been bumped.
            verifyLruOrders(packageNames, true, (a, b) -> a > b, "%s should be newer than %s");
        } finally {
            runShellCommand(mInstrumentation, "am set-standby-bucket --user " + mTestRunningUserId
                    + " " + PACKAGE_NAME_APP1 + " rare");

            // force stop test package, where the whole test process group will be killed.
            forEach(packageNames, packageName -> runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(packageName)));

            forEach(watchers, WatchUidRunner::finish);
            mInstrumentation.getUiAutomation().grantRuntimePermission(PACKAGE_NAME_APP1,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
    }

    @Test
    public void testGetUidProcessState_checkAccess() throws Exception {
        boolean hasPermissionGrantChanged = false;
        if (!PermissionUtils.isPermissionGranted(STUB_PACKAGE_NAME,
                android.Manifest.permission.PACKAGE_USAGE_STATS)) {
            PermissionUtils.grantPermission(
                    STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            hasPermissionGrantChanged = true;
        }
        int newUserId = UserHandle.USER_NULL;
        try {
            // Verify that calling the API doesn't trigger any exceptions.
            mActivityManager.getUidProcessState(Process.myUid());

            assumeTrue(UserManager.supportsMultipleUsers());
            newUserId = createNewUser();
            assertThat(newUserId).isNotEqualTo(UserHandle.USER_NULL);
            startUser(newUserId);
            installExistingPackageAsUser(newUserId);
            final int uidFromNewUser = UserHandle.getUid(newUserId, Process.myUid());
            // Verify that calling the API for a uid on a different user results in an exception.
            assertThrows(SecurityException.class, () -> mActivityManager.getUidProcessState(
                    uidFromNewUser));

            // Verify that calling the API with shell identity (which has
            // INTERACT_ACROSS_USERS_FULL permission) for a uid on a different user works.
            runWithShellPermissionIdentity(() -> mActivityManager.getUidProcessState(
                    uidFromNewUser));
        } finally {
            if (newUserId != UserHandle.USER_NULL) {
                removeUser(newUserId);
            }
            if (hasPermissionGrantChanged) {
                PermissionUtils.revokePermission(
                        STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            }
        }
    }

    @Test
    public void testGetUidProcessCapabilities_checkAccess() throws Exception {
        boolean hasPermissionGrantChanged = false;
        if (!PermissionUtils.isPermissionGranted(STUB_PACKAGE_NAME,
                android.Manifest.permission.PACKAGE_USAGE_STATS)) {
            PermissionUtils.grantPermission(
                    STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            hasPermissionGrantChanged = true;
        }
        int newUserId = UserHandle.USER_NULL;
        try {
            // Verify that calling the API doesn't trigger any exceptions.
            mActivityManager.getUidProcessCapabilities(Process.myUid());

            assumeTrue(UserManager.supportsMultipleUsers());
            newUserId = createNewUser();
            assertThat(newUserId).isNotEqualTo(UserHandle.USER_NULL);
            startUser(newUserId);
            installExistingPackageAsUser(newUserId);
            final int uidFromNewUser = UserHandle.getUid(newUserId, Process.myUid());
            // Verify that calling the API for a uid on a different user results in an exception.
            assertThrows(SecurityException.class, () -> mActivityManager.getUidProcessState(
                    uidFromNewUser));

            // Verify that calling the API with shell identity (which has
            // INTERACT_ACROSS_USERS_FULL permission) for a uid on a different user works.
            runWithShellPermissionIdentity(() -> mActivityManager.getUidProcessState(
                    uidFromNewUser));
        } finally {
            if (newUserId != UserHandle.USER_NULL) {
                removeUser(newUserId);
            }
            if (hasPermissionGrantChanged) {
                PermissionUtils.revokePermission(
                        STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            }
        }
    }

    @Test
    public void testObserveForegroundProcess() throws Exception {
        final ParcelFileDescriptor[] pfds = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommandRw("am observe-foreground-process");
        final ParcelFileDescriptor stdOut = pfds[0];
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(stdOut)))) {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);
            final String result = reader.readLine();
            final int topPid = getRunningAppProcessInfo(SIMPLE_PACKAGE_NAME).pid;
            assertThat("New foreground process: " + topPid).isEqualTo(result);
        }
    }

    @Test
    public void testKillBackgroundProcess() throws Exception {
        final String otherPackage = PACKAGE_NAME_APP1;
        final ApplicationInfo ai1 = mTargetContext.getPackageManager()
                .getApplicationInfo(otherPackage, 0);
        final WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, Process.myUid(),
                WAITFOR_MSEC);
        final WatchUidRunner uid2Watcher = new WatchUidRunner(mInstrumentation, ai1.uid,
                WAITFOR_MSEC);
        try {
            launchHome();

            // Since we're running instrumentation, our proc state will stay above FGS.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE, null);

            // Start an activity in another process in our package, our proc state will goto TOP.
            final CountDownLatch remoteBinderDeathLatch1 = startRemoteActivityAndLinkToDeath(
                    new ComponentName(mTargetContext, RemoteActivity.class),
                    uid1Watcher);

            final CountDownLatch remoteBinderDeathLatch2 = startRemoteActivityAndLinkToDeath(
                    new ComponentName(otherPackage, STUB_PACKAGE_NAME + ".RemoteActivity"),
                    uid2Watcher);

            // Launch home again so our activity will be backgrounded.
            launchHome();

            // The uid goes back to FGS state,
            // but the process with the remote activity should have been in the background.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE, null);

            // And the test package should be in background too.
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY, null);

            // Now, try to kill the background process of our own, it should succeed.
            mActivityManager.killBackgroundProcesses(mTargetContext.getPackageName());

            assertWithMessage("We should be able to kill our own process")
                    .that(remoteBinderDeathLatch1.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Try to kill the background process of other app, it should fail.
            mActivityManager.killBackgroundProcesses(otherPackage);

            assertWithMessage("We should be able to kill the processes of other package")
                    .that(remoteBinderDeathLatch2.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS))
                    .isFalse();

            // Adopt the permission, we should be able to kill it now.
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    android.Manifest.permission.KILL_ALL_BACKGROUND_PROCESSES);
            mActivityManager.killBackgroundProcesses(otherPackage);

            assertWithMessage("We should be able to kill the processes of other package")
                    .that(remoteBinderDeathLatch2.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS))
                    .isTrue();
        } finally {
            uid1Watcher.finish();
            uid2Watcher.finish();
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSwitchToSystemUserIsRestrictedWhenItsNotAFullUser() {
        assumeHeadlessSystemUserMode();
        assumeFalse("Switch to Non-full headless SYSTEM user is only restricted when "
                        + "config_canSwitchToHeadlessSystemUser is disabled.",
                canSwitchToHeadlessSystemUser());

        runWithShellPermissionIdentity(
                () -> assertThat(mActivityManager.switchUser(UserHandle.SYSTEM)).isFalse());
    }

    @Test
    public void testSwitchToSystemUserIsAllowedWhenItsAFullUser() {
        assumeNonHeadlessSystemUserMode();

        runWithShellPermissionIdentity(
                () -> {
                    int currentUser = ActivityManager.getCurrentUser();
                    assertThat(mActivityManager.switchUser(UserHandle.SYSTEM)).isTrue();
                    assertThat(switchUser(currentUser)).isTrue();
                });
    }

    @Test
    public void testSwitchToHeadlessSystemUser_whenCanSwitchToHeadlessSystemUserEnabled() {
        assumeHeadlessSystemUserMode();
        assumeFalse(isAutomotive());

        assumeTrue("Switch to Non-full headless SYSTEM user is only allowed when "
                        + "config_canSwitchToHeadlessSystemUser is enabled.",
                canSwitchToHeadlessSystemUser());

        runWithShellPermissionIdentity(
                () -> {
                    int currentUser = ActivityManager.getCurrentUser();
                    assumeTrue(mActivityManager.switchUser(UserHandle.SYSTEM));
                    assertThat(switchUser(currentUser)).isTrue();
                });
    }

    @Test
    @Ignore("b/279787820: This is an internal API "
            + "that must be one way and thus cannot be verified.")
    public void testNoteForegroundResourceUse() {
        // Testing the method without permissions
        assertThrows(
                "Should not be able to call noteForegroundResourceUseBegin without permission",
                SecurityException.class,
                () -> mActivityManager.noteForegroundResourceUseBegin(1, 1, 1));

        assertThrows(
                "Should not be able to call noteForegroundResourceUseEnd without permission",
                SecurityException.class,
                () -> mActivityManager.noteForegroundResourceUseEnd(1, 1, 1));

        try {
            mInstrumentation.getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.LOG_FOREGROUND_RESOURCE_USE);
        } catch (Exception e) {
            assertWithMessage("Couldn't grant permission: " + e.getMessage()).fail();
        }

        // Testing invocation with permission granted
        try {
            mActivityManager.noteForegroundResourceUseBegin(1, 1, 1);
        } catch (SecurityException e) {
            assertWithMessage(
                            "Could not call noteForegroundResourceUseBegin with permission"
                                    + e.getMessage())
                    .fail();
        }
        try {
            mActivityManager.noteForegroundResourceUseEnd(1, 1, 1);
        } catch (SecurityException e) {
            assertWithMessage(
                            "Could not call noteForegroundResourceUseBegin with permission"
                                    + e.getMessage())
                    .fail();
        }
    }

    @Test
    public void testAddOnUidImportanceListener_legacy() throws Exception {
        final ApplicationInfo ai1 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final ApplicationInfo ai2 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP2, 0);
        final CountDownLatch[] latchHolder = new CountDownLatch[1];
        final int[] expectedUidHolder = new int[1];
        final OnUidImportanceListener listener =
                (uid, importance) -> {
                    if (uid == expectedUidHolder[0]) {
                        latchHolder[0].countDown();
                    }
                };
        try (AutoCloseable unused =
                CtsAppTestUtils.allowBackgroundActivityLaunch(PACKAGE_NAME_APP1)) {
            // If we didn't specify the target UID, we should be able to listen on all UID events.
            mActivityManager.addOnUidImportanceListener(listener,
                    RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai1.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            assertWithMessage("Failed to receive the UID importance changes")
                    .that(latchHolder[0].await(WAITFOR_MSEC * 2, TimeUnit.MILLISECONDS))
                    .isTrue();

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai2.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            assertWithMessage("Failed to receive the UID importance changes")
                    .that(latchHolder[0].await(WAITFOR_MSEC * 2, TimeUnit.MILLISECONDS))
                    .isTrue();
        } finally {
            mActivityManager.removeOnUidImportanceListener(listener);

            runWithShellPermissionIdentity(
                    () -> {
                        // force stop test package; the whole test process group will be killed.
                        mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                        mActivityManager.forceStopPackage(PACKAGE_NAME_APP2);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_UID_IMPORTANCE_LISTENER_FOR_UIDS)
    @Test
    public void testAddOnUidImportanceListener() throws Exception {
        final ApplicationInfo ai1 =
                mTargetContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final ApplicationInfo ai2 =
                mTargetContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP2, 0);
        final CountDownLatch[] latchHolder = new CountDownLatch[1];
        final int[] expectedUidHolder = new int[1];
        final OnUidImportanceListener listener =
                (uid, importance) -> {
                    if (uid == expectedUidHolder[0]) {
                        latchHolder[0].countDown();
                    }
                };
        try (AutoCloseable unused =
                CtsAppTestUtils.allowBackgroundActivityLaunch(PACKAGE_NAME_APP1)) {
            // Listen on the APP1's UID importance changes only.
            mActivityManager.addOnUidImportanceListener(listener,
                    RunningAppProcessInfo.IMPORTANCE_FOREGROUND, new int[] {ai1.uid});

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai1.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            assertWithMessage("Failed to receive the UID importance changes")
                    .that(latchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS))
                    .isTrue();

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai2.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            assertWithMessage("It should not receive the UID importance changes")
                    .that(latchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS))
                    .isFalse();
        } finally {
            mActivityManager.removeOnUidImportanceListener(listener);

            runWithShellPermissionIdentity(
                    () -> {
                        // force stop test package; the whole test process group will be killed.
                        mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                        mActivityManager.forceStopPackage(PACKAGE_NAME_APP2);
                    });
        }
    }

    @Test
    @RequiresFlagsEnabled(com.android.server.am.Flags.FLAG_EXPEDITE_ACTIVITY_LAUNCH_ON_COLD_START)
    public void testActivityStartIsEnqueuedImmediatelyAfterBindApplication() throws Exception {
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(DELAYED_PACKAGE_NAME, STUB_PACKAGE_NAME + DELAYED_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // The application finished tracker.
        BlockingResultReceiver appEndReceiver = new BlockingResultReceiver();
        intent.putExtra(EXTRA_REMOTE_CALLBACK, appEndReceiver.getRemoteCallback());

        // Run the activity.
        mTargetContext.startActivity(intent);

        // Wait until it finishes and end the receiver then.
        assertThat(appEndReceiver.getResult()).isEqualTo(RESULT_OK);
    }

    private boolean switchUser(int userId) throws IOException {
        String userSwitchCommand = "am switch-user -w " + userId;
        return runShellCommand(mInstrumentation, userSwitchCommand).isEmpty();
    }

    private CountDownLatch startRemoteActivityAndLinkToDeath(ComponentName activity,
            WatchUidRunner uidWatcher) throws Exception {
        final IBinder[] remoteBinderHolder = new IBinder[1];
        final CountDownLatch remoteBinderLatch = new CountDownLatch(1);
        final IBinder binder =
                new Binder() {
                    @Override
                    protected boolean onTransact(
                            int code, @NonNull Parcel data, Parcel reply, int flags) {
                        if (code == IBinder.FIRST_CALL_TRANSACTION) {
                            remoteBinderHolder[0] = data.readStrongBinder();
                            remoteBinderLatch.countDown();
                            return true;
                        }
                        return false;
                    }
                };
        final CountDownLatch remoteBinderDeathLatch = new CountDownLatch(1);
        final IBinder.DeathRecipient recipient = remoteBinderDeathLatch::countDown;
        final Intent intent = new Intent();
        intent.setComponent(activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Bundle extras = new Bundle();
        extras.putBinder(RemoteActivity.EXTRA_CALLBACK, binder);
        intent.putExtras(extras);
        mTargetContext.startActivity(intent);

        uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
        assertWithMessage("Failed to receive the callback from remote activity")
                .that(remoteBinderLatch.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(remoteBinderHolder[0]).isNotNull();
        remoteBinderHolder[0].linkToDeath(recipient, 0);

        // Finish the activity.
        final Parcel data = Parcel.obtain();
        try {
            remoteBinderHolder[0].transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in binder transaction", e);
        } finally {
            data.recycle();
        }

        // Sleep a while to let things go through.
        SystemClock.sleep(WAIT_TIME);
        return remoteBinderDeathLatch;
    }

    private int createNewUser() throws Exception {
        final UserManager userManager = mTargetContext.getSystemService(UserManager.class);
        return runWithShellPermissionIdentity(() -> {
            final NewUserRequest newUserRequest = new NewUserRequest.Builder()
                    .setName("test_user")
                    .setUserType(UserManager.USER_TYPE_FULL_SECONDARY)
                    .build();
            final UserHandle newUser = userManager.createUser(newUserRequest)
                    .getUser();
            return newUser == null ? UserHandle.USER_NULL : newUser.getIdentifier();
        });
    }

    private void startUser(int userId) throws Exception {
        final String cmd = "cmd activity start-user -w " + userId;
        final String output = executeShellCommand(cmd);
        if (output.startsWith("Error")) {
            assertWithMessage("Error starting the new user u" + userId + ": " + output).fail();
        }
        final String state = executeShellCommand("am get-started-user-state " + userId);
        if (!state.contains("RUNNING_UNLOCKED")) {
            assertWithMessage("Unexpected state for the new user u" + userId + ": " + state).fail();
        }
    }

    private void removeUser(int userId) throws Exception {
        final String cmd = "cmd package remove-user " + userId;
        final String output = executeShellCommand(cmd);
        if (output.startsWith("Error")) {
            assertWithMessage("Error removing the user u" + userId + ": " + output).fail();
        }
    }

    private void installExistingPackageAsUser(int userId) throws Exception {
        final String cmd =
                String.format(
                        "cmd package install-existing --user %d --wait %s",
                        userId, ActivityManagerTest.STUB_PACKAGE_NAME);
        executeShellCommand(cmd);
    }

    private int[] getLruPositions(String[] packageNames) throws Exception {
        final List<String> lru = getCachedAppsLru();
        assertWithMessage("Failed to get cached app list").that(lru).isNotEmpty();
        final int[] pos = new int[packageNames.length];
        for (int i = 0; i < packageNames.length; i++) {
            pos[i] = lru.indexOf(packageNames[i]);
        }
        return pos;
    }

    private void verifyLruOrders(
            String[] packageNames,
            boolean newest,
            BiPredicate<Integer, Integer> predicate,
            String msg)
            throws Exception {
        final List<String> lru = getCachedAppsLru();

        assertWithMessage("Failed to get cached app list").that(lru).isNotEmpty();
        final int[] pos = getLruPositions(packageNames);
        if (pos[0] != -1) {
            for (int i = 0; i < pos.length; i++) {
                if (i == 0 || pos[i] == -1) {
                    continue;
                }
                assertWithMessage(String.format(msg, packageNames[0], packageNames[i]))
                        .that(predicate.test(pos[0], pos[i]))
                        .isTrue();
            }
        } else if (newest) {
            for (int i = 0; i < pos.length; i++) {
                assertWithMessage(packageNames[i] + " should have gone").that(pos[i]).isEqualTo(-1);
            }
        }
    }

    private WatchUidRunner[] initWatchUidRunners(String[] packageNames, long waitFormMs)
            throws Exception {
        final WatchUidRunner[] watchers = new WatchUidRunner[packageNames.length];
        for (int i = 0; i < packageNames.length; i++) {
            final ApplicationInfo ai = mTargetContext.getPackageManager()
                    .getApplicationInfo(packageNames[i], 0);
            watchers[i] = new WatchUidRunner(mInstrumentation, ai.uid, waitFormMs);
        }
        return watchers;
    }

    private interface ConsumerWithException<T> {
        void accept(T t) throws Exception;
    }

    private interface BiConsumerWithException<T, U> {
        void accept(T t, U u) throws Exception;
    }

    private <T> void forEach(T[] items, ConsumerWithException<T> consumer) throws Exception {
        for (T item: items) {
            consumer.accept(item);
        }
    }

    private <T, U> void forBiEach(T[] itemsA, U[] itemsB, BiConsumerWithException<T, U> consumer)
            throws Exception {
        for (int i = 0; i < itemsA.length; i++) {
            consumer.accept(itemsA[i], itemsB[i]);
        }
    }

    private List<String> getCachedAppsLru() throws Exception {
        final List<String> lru = new ArrayList<>();
        final String output = runShellCommand(mInstrumentation, "dumpsys activity lru");
        final String[] lines = output.split("\n");
        for (String line: lines) {
            if (line == null || !line.contains(" cch")) {
                continue;
            }
            final int slash = line.lastIndexOf('/');
            if (slash == -1) {
                continue;
            }
            line = line.substring(0, slash);
            final int space = line.lastIndexOf(' ');
            if (space == -1) {
                continue;
            }
            line = line.substring(space + 1);
            final int colon = line.indexOf(':');
            if (colon == -1) {
                continue;
            }
            lru.add(0, line.substring(colon + 1));
        }
        return lru;
    }

    private Bundle initWaitingForTrimLevel(final Consumer<Integer> checker) {
        final IBinder binder =
                new Binder() {
                    @Override
                    protected boolean onTransact(
                            int code, @NonNull Parcel data, Parcel reply, int flags) {
                        if (code == IBinder.FIRST_CALL_TRANSACTION) {
                            final int level = data.readInt();
                            checker.accept(level);
                            return true;
                        }
                        return false;
                    }
                };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        return extras;
    }

    private RunningAppProcessInfo getRunningAppProcessInfo(String processName) {
        try {
            return callWithShellPermissionIdentity(
                    () ->
                            mActivityManager.getRunningAppProcesses().stream()
                                    .filter((ra) -> processName.equals(ra.processName))
                                    .findFirst()
                                    .orElse(null));
        } catch (Exception e) {
        }
        return null;
    }

    private boolean isProcessGone(int pid, String processName) {
        RunningAppProcessInfo info = getRunningAppProcessInfo(processName);
        return info == null || info.pid != pid;
    }

    // Copied from DeviceStatesTest
    /** Make sure the screen state. */
    private void toggleScreenOn(final boolean screenOn) throws Exception {
        if (screenOn) {
            executeAndLogShellCommand("input keyevent KEYCODE_WAKEUP");
            executeAndLogShellCommand("wm dismiss-keyguard");
        } else {
            executeAndLogShellCommand("input keyevent KEYCODE_SLEEP");
        }
        // Since the screen on/off intent is ordered, they will not be sent right now.
        SystemClock.sleep(2_000);
    }

    /**
     * Simulated for idle, and then perform idle maintenance now.
     */
    private void triggerIdle(boolean idle) throws Exception {
        if (idle) {
            executeAndLogShellCommand("cmd deviceidle force-idle light");
        } else {
            executeAndLogShellCommand("cmd deviceidle unforce");
        }
        // Wait a moment to let that happen before proceeding.
        SystemClock.sleep(2_000);
    }

    /** Return true if the given supplier says it's true */
    private boolean waitUntilTrue(long maxWait, Supplier<Boolean> supplier) {
        final long deadLine = SystemClock.uptimeMillis() + maxWait;
        boolean result;
        do {
            SystemClock.sleep(500);
        } while (!(result = supplier.get()) && SystemClock.uptimeMillis() < deadLine);
        return result;
    }

    private void createManagedHomeActivitySession()
            throws Exception {
        if (noHomeScreen()) return;
        ComponentName homeActivity = new ComponentName(
                STUB_PACKAGE_NAME, TestHomeActivity.class.getName());
        mTestHomeSession = new HomeActivitySession(homeActivity);
    }

    /**
     * HomeActivitySession is used to replace the default home component, so that you can use your
     * preferred home for testing within the session. The original default home will be restored
     * automatically afterward.
     */
    private final class HomeActivitySession {
        private final PackageManager mPackageManager;
        private ComponentName mOrigHome;
        private final ComponentName mSessionHome;

        HomeActivitySession(ComponentName sessionHome) throws Exception {
            mSessionHome = sessionHome;
            mPackageManager = mInstrumentation.getContext().getPackageManager();
            mOrigHome = getCurrentHomeComponent();

            runWithShellPermissionIdentity(
                    () -> mPackageManager.setComponentEnabledSetting(mSessionHome,
                            COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
            setDefaultHome(mSessionHome);
        }

        public void close() throws Exception {
            runWithShellPermissionIdentity(
                    () -> mPackageManager.setComponentEnabledSetting(mSessionHome,
                            COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));
            if (mOrigHome != null) {
                setDefaultHome(mOrigHome);
                mOrigHome = null;
            }
        }

        private void setDefaultHome(ComponentName componentName) throws Exception {
            executeShellCommand("cmd package set-home-activity --user "
                    + android.os.Process.myUserHandle().getIdentifier() + " "
                    + componentName.flattenToString());
        }
    }

    private boolean isAtvDevice() {
        final Context context = mInstrumentation.getTargetContext();
        return context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEVISION);
    }

    /**
     * Gets the value of {@link com.android.internal.R.bool#config_canSwitchToHeadlessSystemUser}.
     * @return {@code true} If headless system user is allowed to run in the foreground
     * even though it is not a full user.
     */
    private boolean canSwitchToHeadlessSystemUser() {
        try {
            return mTargetContext.getResources().getBoolean(Resources.getSystem()
                    .getIdentifier("config_canSwitchToHeadlessSystemUser", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume headless system user switch is disabled.
            Log.w(TAG, "Unable to read system property " + e.getMessage());
            return false;
        }
    }

    private void assumeHeadlessSystemUserMode() {
        assumeTrue("System user is a FULL user in non-headless system user mode.",
                UserManager.isHeadlessSystemUserMode());
    }

    private void assumeNonHeadlessSystemUserMode() {
        assumeFalse("System user is not a FULL user in headless system user mode.",
                UserManager.isHeadlessSystemUserMode());
    }

    private boolean isCurrentHomeActivityFocused() {
        if (noHomeScreen()) {
            return false;
        }
        ComponentName homeActivity = getCurrentHomeComponent();
        mWmState.waitForValidState(homeActivity);
        return mWmState.waitForFocusedActivity(homeActivity);
    }

    private ComponentName getCurrentHomeComponent() {
        final Intent intent = new Intent(ACTION_MAIN);
        intent.addCategory(CATEGORY_HOME);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        final ResolveInfo resolveInfo =
                mInstrumentation
                        .getContext()
                        .getPackageManager()
                        .resolveActivity(intent, MATCH_DEFAULT_ONLY);
        if (resolveInfo == null) {
            throw new AssertionError("Home activity not found");
        }
        return new ComponentName(
                resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
    }

    private boolean isAutomotive() {
        PackageManager pm = mTargetContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private static final class BlockingResultReceiver {
        private final BlockingQueue<Integer> mBlockingQueue = new LinkedBlockingQueue<>();
        private final RemoteCallback mRemoteCallback;

        BlockingResultReceiver() {
            mRemoteCallback = new RemoteCallback(bundle -> {
                final int result = bundle.getInt(EXTRA_RETURN_RESULT, RESULT_FAIL);
                mBlockingQueue.offer(result);
            });
        }

        public RemoteCallback getRemoteCallback() {
            return mRemoteCallback;
        }

        public int getResult() throws InterruptedException {
            final Integer result = mBlockingQueue.poll(WAITFOR_MSEC * 2, TimeUnit.MILLISECONDS);
            return result == null ? RESULT_TIMEOUT : result;
        }
    }
}
