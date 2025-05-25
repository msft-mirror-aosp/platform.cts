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

package android.content.cts;

import static android.Manifest.permission.READ_WALLPAPER_INTERNAL;
import static android.content.cts.contenturitestapp.IContentUriTestService.PKG_ACCESS_TYPE_GENERAL;
import static android.content.cts.contenturitestapp.IContentUriTestService.PKG_ACCESS_TYPE_GRANT;
import static android.content.cts.contenturitestapp.IContentUriTestService.PKG_ACCESS_TYPE_NONE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.app.Instrumentation;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextParams;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.cts.contenturitestapp.IContentUriTestService;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.WindowManager;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.IBinderPermissionTestService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@AppModeFull // TODO(Instant) Figure out which APIs should work.
@RunWith(JUnit4.class)
public class ContextTest {
    private static final String TAG = "ContextTest";
    private static final String ACTUAL_RESULT = "ResultSetByReceiver";

    private static final String INITIAL_RESULT = "InitialResult";

    private static final String VALUE_ADDED = "ValueAdded";
    private static final String KEY_ADDED = "AddedByReceiver";

    private static final String VALUE_REMOVED = "ValueWillBeRemove";
    private static final String KEY_REMOVED = "ToBeRemoved";

    private static final String VALUE_KEPT = "ValueKept";
    private static final String KEY_KEPT = "ToBeKept";

    private static final String MOCK_STICKY_ACTION = "android.content.cts.ContextTest."
            + "STICKY_BROADCAST_RESULT";

    private static final String ACTION_BROADCAST_TESTORDER =
            "android.content.cts.ContextTest.BROADCAST_TESTORDER";
    private final static String MOCK_ACTION1 = ACTION_BROADCAST_TESTORDER + "1";
    private final static String MOCK_ACTION2 = ACTION_BROADCAST_TESTORDER + "2";

    // Note: keep these constants in sync with the permissions used by BinderPermissionTestService.
    //
    // A permission that's granted to this test package.
    private static final String GRANTED_PERMISSION = "android.permission.USE_CREDENTIALS";
    // A permission that's not granted to this test package.
    private static final String NOT_GRANTED_PERMISSION = "android.permission.HARDWARE_TEST";

    private static final int BROADCAST_TIMEOUT = 15000;
    private static final int SERVICE_TIMEOUT = 15000;
    private static final int ROOT_UID = 0;

    /* TestService for testCheckContentUriPermissionFull tests. */
    private static final String PKG_TEST_SERVICE = "android.content.cts.contenturitestapp";
    private static final String CLS_TEST_SERVICE = PKG_TEST_SERVICE + ".TestService";
    private static final ComponentName COMPONENT_CONTENT_URI_TEST_SERVICE =
            new ComponentName(PKG_TEST_SERVICE, CLS_TEST_SERVICE);
    private static final Uri URI =
            new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority("ctstest").build();
    private static final Uri URI1 =
            new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority("ctstest1").build();
    private static final Uri URI2 =
            new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority("ctstest2").build();
    private static final String ANDROID_SHELL = "com.android.shell";
    private static final String TEST_SHARED_PREFERENCE_NAME = "test";
    private static final String TEST_DB_NAME = "test.db";

    private IContentUriTestService mContentUriTestService;
    private ServiceConnection mContentUriServiceConnection;

    private Object mLockObj;

    private ArrayList<BroadcastReceiver> mRegisteredReceiverList;

    private boolean mWallpaperChanged;
    private BitmapDrawable mOriginalWallpaper = null;
    private volatile IBinderPermissionTestService mBinderPermissionTestService;
    private ServiceConnection mBinderPermissionTestConnection;

    protected Context mContext;
    /**
     * Shell command to broadcast {@link ResultReceiver#MOCK_ACTION} as an external app.
     */
    private String mExternalAppBroadcastCommand;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * Returns the Context object that's being tested.
     */
    protected Context getContextUnderTest() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    public Context getContext() {
        return mContext;
    }

    @Before
    public final void setUp() throws Exception {
        mContext = getContextUnderTest();
        mContext.setTheme(R.style.Test_Theme);

        mLockObj = new Object();

        mRegisteredReceiverList = new ArrayList<>();
        mExternalAppBroadcastCommand = "am broadcast --user " + mContext.getUserId()
                + " -a " + ResultReceiver.MOCK_ACTION + " -f " + Intent.FLAG_RECEIVER_FOREGROUND;
    }

    @After
    public final void tearDown() throws Exception {
        if (mOriginalWallpaper != null && mWallpaperChanged) {
            mContext.setWallpaper(mOriginalWallpaper.getBitmap());
        }

        for (BroadcastReceiver receiver : mRegisteredReceiverList) {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testGetString() {
        String testString = mContext.getString(R.string.context_test_string1);
        assertThat(testString).isEqualTo("This is %s string.");

        testString = mContext.getString(R.string.context_test_string1, "expected");
        assertThat(testString).isEqualTo("This is expected string.");

        testString = mContext.getString(R.string.context_test_string2);
        assertThat(testString).isEqualTo("This is test string.");

        // Test wrong resource id
        assertThrows(
                "Wrong resource id should not be accepted.",
                NotFoundException.class,
                () -> mContext.getString(0, "expected"));

        // Test wrong resource id
        assertThrows(
                "Wrong resource id should not be accepted.",
                NotFoundException.class,
                () -> mContext.getString(0));
    }

    @Test
    public void testGetText() {
        CharSequence testCharSequence = mContext.getText(R.string.context_test_string2);
        assertThat(testCharSequence.toString()).isEqualTo("This is test string.");

        // Test wrong resource id
        assertThrows(
                "Wrong resource id should not be accepted.",
                NotFoundException.class,
                () -> mContext.getText(0));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testCreateAttributionContext() {
        final String tag = "testCreateAttributionContext";
        final Context attrib = mContext.createAttributionContext(tag);
        assertThat(attrib.getAttributionTag()).isEqualTo(tag);
        assertThat(mContext.getAttributionTag()).isNull();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testCreateAttributionContextFromParams() {
        final ContextParams params = new ContextParams.Builder()
                .setAttributionTag("foo")
                .setNextAttributionSource(new AttributionSource.Builder(1)
                        .setPackageName("bar")
                        .setAttributionTag("baz")
                        .build())
                .build();
        final Context attributionContext = getContext().createContext(params);

        assertThat(attributionContext.getParams()).isEqualTo(params);
        assertThat(attributionContext.getAttributionSource().getNext())
                .isEqualTo(params.getNextAttributionSource());
        assertThat(attributionContext.getAttributionSource().getAttributionTag())
                .isEqualTo(params.getAttributionTag());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testContextParams() {
        final int uid = 1;
        final ContextParams params =
                new ContextParams.Builder()
                        .setAttributionTag("foo")
                        .setNextAttributionSource(
                                new AttributionSource.Builder(uid)
                                        .setPackageName("bar")
                                        .setAttributionTag("baz")
                                        .build())
                        .build();

        assertThat(params.getAttributionTag()).isEqualTo("foo");
        assertThat(params.getNextAttributionSource().getUid()).isEqualTo(uid);
        assertThat(params.getNextAttributionSource().getPackageName()).isEqualTo("bar");
        assertThat(params.getNextAttributionSource().getAttributionTag()).isEqualTo("baz");
    }

    // TODO: Add `buildFakeAttributionSource()` and `validateContextParams()` methods back, later
    //  when Android R (sdk version 30) is no longer supported

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @ApiTest(apis = {"android.content.AttributionSource.Builder#setNext"})
    public void testAttributionSourceSetNext() {
        final int uid = 1;
        final int nextUid = 2;
        final AttributionSource next =
                new AttributionSource.Builder(nextUid)
                        .setPackageName("nextBar")
                        .setAttributionTag("nextBaz")
                        .build();
        final ContextParams params =
                new ContextParams.Builder()
                        .setAttributionTag("foo")
                        .setNextAttributionSource(
                                new AttributionSource.Builder(uid)
                                        .setPackageName("bar")
                                        .setAttributionTag("baz")
                                        .setNext(next)
                                        .build())
                        .build();
        // Setting a 'next' should not affect prev.
        assertThat(params.getAttributionTag()).isEqualTo("foo");
        AttributionSource nextAttributionSource = params.getNextAttributionSource();
        assertThat(nextAttributionSource.getUid()).isEqualTo(uid);
        assertThat(nextAttributionSource.getPackageName()).isEqualTo("bar");
        assertThat(nextAttributionSource.getAttributionTag()).isEqualTo("baz");

        final AttributionSource check = nextAttributionSource.getNext();
        assertThat(check.getUid()).isEqualTo(nextUid);
        assertThat(check.getPackageName()).isEqualTo("nextBar");
        assertThat(check.getAttributionTag()).isEqualTo("nextBaz");
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @ApiTest(apis = {"android.content.AttributionSource.Builder#setNextAttributionSource"})
    public void testAttributionSourceSetNextAttributionSource() {
        final int uid = 1;
        final int nextUid = 2;
        final AttributionSource next =
                new AttributionSource.Builder(nextUid)
                        .setPackageName("nextBar")
                        .setAttributionTag("nextBaz")
                        .build();
        final ContextParams params =
                new ContextParams.Builder()
                        .setAttributionTag("foo")
                        .setNextAttributionSource(
                                new AttributionSource.Builder(uid)
                                        .setPackageName("bar")
                                        .setAttributionTag("baz")
                                        .setNextAttributionSource(next)
                                        .build())
                        .build();
        // Setting a 'next' should not affect prev.
        assertThat(params.getAttributionTag()).isEqualTo("foo");
        AttributionSource nextAttributionSource = params.getNextAttributionSource();
        assertThat(nextAttributionSource.getUid()).isEqualTo(uid);
        assertThat(nextAttributionSource.getPackageName()).isEqualTo("bar");
        assertThat(nextAttributionSource.getAttributionTag()).isEqualTo("baz");

        final AttributionSource check = nextAttributionSource.getNext();
        assertThat(check.getUid()).isEqualTo(nextUid);
        assertThat(check.getPackageName()).isEqualTo("nextBar");
        assertThat(check.getAttributionTag()).isEqualTo("nextBaz");
    }

    @Test
    public void testContextParams_Inherit() {
        final ContextParams orig = new ContextParams.Builder()
                .setAttributionTag("foo").build();
        {
            final ContextParams params = new ContextParams.Builder(orig).build();
            assertThat(params.getAttributionTag()).isEqualTo("foo");
        }
        {
            final ContextParams params = new ContextParams.Builder(orig)
                    .setAttributionTag("bar").build();
            assertThat(params.getAttributionTag()).isEqualTo("bar");
        }
        {
            final ContextParams params = new ContextParams.Builder(orig)
                    .setAttributionTag(null).build();
            assertThat(params.getAttributionTag()).isNull();
        }
    }

    /**
     * Ensure that default and device encrypted storage areas are stored
     * separately on disk. All devices must support these storage areas, even if
     * they don't have file-based encryption, so that apps can go through a
     * backup/restore cycle between FBE and non-FBE devices.
     */
    @Test
    public void testCreateDeviceProtectedStorageContext() throws Exception {
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();

        assertThat(mContext.isDeviceProtectedStorage()).isFalse();
        assertThat(deviceContext.isDeviceProtectedStorage()).isTrue();

        final File defaultFile = new File(mContext.getFilesDir(), TEST_SHARED_PREFERENCE_NAME);
        final File deviceFile = new File(deviceContext.getFilesDir(), TEST_SHARED_PREFERENCE_NAME);

        assertThat(defaultFile).isNotEqualTo(deviceFile);

        deviceFile.createNewFile();

        // Make sure storage areas are mutually exclusive
        assertThat(defaultFile.exists()).isFalse();
        assertThat(deviceFile.exists()).isTrue();
    }

    @Test
    public void testMoveSharedPreferencesFrom() {
        final String answerKey = "answer";
        final String questionKey = "question";
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();

        mContext.getSharedPreferences(TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(answerKey, 42)
                .commit();

        // Verify that we can migrate
        assertThat(deviceContext.moveSharedPreferencesFrom(mContext, TEST_SHARED_PREFERENCE_NAME))
                .isTrue();
        assertThat(
                        mContext.getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(answerKey, 0))
                .isEqualTo(0);
        assertThat(
                        deviceContext
                                .getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(answerKey, 0))
                .isEqualTo(42);

        // Trying to migrate again when already done is a no-op
        assertThat(deviceContext.moveSharedPreferencesFrom(mContext, TEST_SHARED_PREFERENCE_NAME))
                .isTrue();
        assertThat(
                        mContext.getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(answerKey, 0))
                .isEqualTo(0);
        assertThat(
                        deviceContext
                                .getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(answerKey, 0))
                .isEqualTo(42);

        // Add a new value and verify that we can migrate back
        deviceContext
                .getSharedPreferences(TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(questionKey, 24)
                .commit();

        assertThat(mContext.moveSharedPreferencesFrom(deviceContext, TEST_SHARED_PREFERENCE_NAME))
                .isTrue();
        assertThat(
                        mContext.getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(answerKey, 0))
                .isEqualTo(42);
        assertThat(
                        mContext.getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(questionKey, 0))
                .isEqualTo(24);
        assertThat(
                        deviceContext
                                .getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(answerKey, 0))
                .isEqualTo(0);
        assertThat(
                        deviceContext
                                .getSharedPreferences(
                                        TEST_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                                .getInt(questionKey, 0))
                .isEqualTo(0);
    }

    @Test
    public void testMoveDatabaseFrom() {
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();

        SQLiteDatabase db =
                mContext.openOrCreateDatabase(
                        TEST_DB_NAME,
                        Context.MODE_PRIVATE | Context.MODE_ENABLE_WRITE_AHEAD_LOGGING,
                        null);
        db.execSQL("CREATE TABLE list(item TEXT);");
        db.execSQL("INSERT INTO list VALUES ('cat')");
        db.execSQL("INSERT INTO list VALUES ('dog')");
        db.close();

        // Verify that we can migrate
        assertThat(deviceContext.moveDatabaseFrom(mContext, TEST_DB_NAME)).isTrue();
        db =
                deviceContext.openOrCreateDatabase(
                        TEST_DB_NAME,
                        Context.MODE_PRIVATE | Context.MODE_ENABLE_WRITE_AHEAD_LOGGING,
                        null);
        Cursor c = db.query("list", null, null, null, null, null, null);
        assertThat(c.getCount()).isEqualTo(2);
        assertThat(c.moveToFirst()).isTrue();
        assertThat(c.getString(0)).isEqualTo("cat");
        assertThat(c.moveToNext()).isTrue();
        assertThat(c.getString(0)).isEqualTo("dog");
        c.close();
        db.execSQL("INSERT INTO list VALUES ('mouse')");
        db.close();

        // Trying to migrate again when already done is a no-op
        assertThat(deviceContext.moveDatabaseFrom(mContext, TEST_DB_NAME)).isTrue();

        // Verify that we can migrate back
        assertThat(mContext.moveDatabaseFrom(deviceContext, TEST_DB_NAME)).isTrue();
        db =
                mContext.openOrCreateDatabase(
                        TEST_DB_NAME,
                        Context.MODE_PRIVATE | Context.MODE_ENABLE_WRITE_AHEAD_LOGGING,
                        null);
        c = db.query("list", null, null, null, null, null, null);
        assertThat(c.getCount()).isEqualTo(3);
        assertThat(c.moveToFirst()).isTrue();
        assertThat(c.getString(0)).isEqualTo("cat");
        assertThat(c.moveToNext()).isTrue();
        assertThat(c.getString(0)).isEqualTo("dog");
        assertThat(c.moveToNext()).isTrue();
        assertThat(c.getString(0)).isEqualTo("mouse");
        c.close();
        db.close();
    }

    @Test
    public void testAccessTheme() {
        mContext.setTheme(R.style.Test_Theme);
        final Theme testTheme = mContext.getTheme();
        assertThat(testTheme).isNotNull();

        int[] attrs = {
                android.R.attr.windowNoTitle,
                android.R.attr.panelColorForeground,
                android.R.attr.panelColorBackground
        };
        try (TypedArray attrArray = testTheme.obtainStyledAttributes(attrs)) {
            assertThat(attrArray.getBoolean(0, false)).isTrue();
            assertThat(attrArray.getColor(1, 0)).isEqualTo(0xff000000);
            assertThat(attrArray.getColor(2, 0)).isEqualTo(0xffffffff);
        }

        // setTheme only works for the first time
        mContext.setTheme(android.R.style.Theme_Black);
        assertThat(mContext.getTheme()).isSameInstanceAs(testTheme);
    }

    @Test
    public void testObtainStyledAttributes() {
        // Test obtainStyledAttributes(int[])
        TypedArray testTypedArray = mContext
                .obtainStyledAttributes(android.R.styleable.View);
        assertThat(testTypedArray).isNotNull();
        assertThat(testTypedArray.length()).isGreaterThan(2);
        assertThat(testTypedArray.length()).isGreaterThan(0);
        testTypedArray.recycle();

        // Test obtainStyledAttributes(int, int[])
        testTypedArray = mContext.obtainStyledAttributes(android.R.style.TextAppearance_Small,
                android.R.styleable.TextAppearance);
        assertThat(testTypedArray).isNotNull();
        assertThat(testTypedArray.length()).isGreaterThan(2);
        testTypedArray.recycle();

        // Test wrong null array pointer
        assertThrows(
                "obtainStyledAttributes will throw a NullPointerException here.",
                NullPointerException.class,
                () -> mContext.obtainStyledAttributes(-1, null));

        // Test obtainStyledAttributes(AttributeSet, int[]) with unavailable resource id.
        int[] testInt = {0, 0};
        testTypedArray = mContext.obtainStyledAttributes(-1, testInt);
        // fail("Wrong resource id should not be accepted.");
        assertThat(testTypedArray).isNotNull();
        assertThat(testTypedArray.length()).isEqualTo(2);
        testTypedArray.recycle();

        // Test obtainStyledAttributes(AttributeSet, int[])
        int[] attrs = android.R.styleable.DatePicker;
        testTypedArray = mContext.obtainStyledAttributes(getAttributeSet(R.layout.context_layout),
                attrs);
        assertThat(testTypedArray).isNotNull();
        assertThat(testTypedArray.length()).isEqualTo(attrs.length);
        testTypedArray.recycle();

        // Test obtainStyledAttributes(AttributeSet, int[], int, int)
        testTypedArray = mContext.obtainStyledAttributes(getAttributeSet(R.layout.context_layout),
                attrs, 0, 0);
        assertThat(testTypedArray).isNotNull();
        assertThat(testTypedArray.length()).isEqualTo(attrs.length);
        testTypedArray.recycle();
    }

    @Test
    public void testGetSystemService() {
        // Test invalid service name
        assertThat(mContext.getSystemService("invalid")).isNull();

        // Test valid service name
        assertThat(mContext.getSystemService(Context.WINDOW_SERVICE)).isNotNull();
    }

    @Test
    public void testGetSystemServiceByClass() {
        // Test invalid service class
        assertThat(mContext.getSystemService(Object.class)).isNull();

        // Test valid service name
        assertThat(mContext.getSystemService(WindowManager.class)).isNotNull();
        assertThat(mContext.getSystemService(WindowManager.class))
                .isEqualTo(mContext.getSystemService(Context.WINDOW_SERVICE));
    }

    @Test
    public void testGetColorStateList() {
        assertThrows(
                "Failed at testGetColorStateList",
                NotFoundException.class,
                () -> mContext.getColorStateList(0));

        final ColorStateList colorStateList = mContext.getColorStateList(R.color.color2);
        final int[] focusedState = {android.R.attr.state_focused};
        final int focusColor = colorStateList.getColorForState(focusedState, R.color.failColor);
        assertThat(focusColor).isEqualTo(0xffff0000);
    }

    @Test
    public void testGetColor() {
        assertThrows("Failed at testGetColor", NotFoundException.class, () -> mContext.getColor(0));

        final int color = mContext.getColor(R.color.color2);
        assertThat(color).isEqualTo(0xffffff00);
    }

    /**
     * Developers have come to expect at least ext4-style filename behavior, so
     * verify that the underlying filesystem supports them.
     */
    @Test
    public void testFilenames() throws Exception {
        final File base = mContext.getFilesDir();
        assertValidFile(new File(base, "foo"));
        assertValidFile(new File(base, ".bar"));
        assertValidFile(new File(base, "foo.bar"));
        assertValidFile(new File(base, "\u2603"));
        assertValidFile(new File(base, "\uD83D\uDCA9"));

        final int pid = android.os.Process.myPid();
        final StringBuilder sb = new StringBuilder(255);
        while (sb.length() <= 255) {
            sb.append(pid);
            sb.append(mContext.getPackageName());
        }
        sb.setLength(255);

        final String longName = sb.toString();
        final File longDir = new File(base, longName);
        assertValidFile(longDir);
        longDir.mkdir();
        final File longFile = new File(longDir, longName);
        assertValidFile(longFile);
    }

    @Test
    public void testMainLooper() {
        final Thread mainThread = Looper.getMainLooper().getThread();
        final Handler handler = new Handler(mContext.getMainLooper());
        handler.post(() -> assertThat(Thread.currentThread()).isEqualTo(mainThread));
    }

    @Test
    public void testMainExecutor() {
        final Thread mainThread = Looper.getMainLooper().getThread();
        mContext.getMainExecutor()
                .execute(() -> assertThat(Thread.currentThread()).isEqualTo(mainThread));
    }

    private void assertValidFile(File file) throws Exception {
        Log.d(TAG, "Checking " + file);
        if (file.exists()) {
            assertWithMessage("File already exists and couldn't be deleted before test: " + file)
                    .that(file.delete())
                    .isTrue();
        }
        assertWithMessage("Failed to create " + file).that(file.createNewFile()).isTrue();
        assertWithMessage("Doesn't exist after create " + file).that(file.exists()).isTrue();
        assertWithMessage("Failed to delete after create " + file).that(file.delete()).isTrue();
        new FileOutputStream(file).close();
        assertWithMessage("Doesn't exist after stream " + file).that(file.exists()).isTrue();
        assertWithMessage("Failed to delete after stream " + file).that(file.delete()).isTrue();
    }

    private static void beginDocument(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // Expected
        }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals("RelativeLayout")) {
            throw new XmlPullParserException(
                    "Unexpected start tag: found "
                            + parser.getName()
                            + ", expected "
                            + "RelativeLayout");
        }
    }

    private AttributeSet getAttributeSet(int resourceId) {
        final XmlResourceParser parser = mContext.getResources().getXml(
                resourceId);

        try {
            beginDocument(parser);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Exception in getAttributeSet ", e);
        }

        final AttributeSet attr = Xml.asAttributeSet(parser);
        assertThat(attr).isNotNull();
        return attr;
    }

    private void registerBroadcastReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        registerBroadcastReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void registerBroadcastReceiver(BroadcastReceiver receiver, IntentFilter filter,
            int flags) {
        mContext.registerReceiver(receiver, filter, flags);

        mRegisteredReceiverList.add(receiver);
    }

    @Test
    public void testSendOrderedBroadcast1() {
        final HighPriorityBroadcastReceiver highPriorityReceiver =
                new HighPriorityBroadcastReceiver();
        final LowPriorityBroadcastReceiver lowPriorityReceiver =
                new LowPriorityBroadcastReceiver();

        final IntentFilter filterHighPriority = new IntentFilter(ResultReceiver.MOCK_ACTION);
        filterHighPriority.setPriority(1);
        final IntentFilter filterLowPriority = new IntentFilter(ResultReceiver.MOCK_ACTION);
        registerBroadcastReceiver(highPriorityReceiver, filterHighPriority);
        registerBroadcastReceiver(lowPriorityReceiver, filterLowPriority);

        final Intent broadcastIntent = new Intent(ResultReceiver.MOCK_ACTION)
                .setPackage(mContext.getPackageName());
        broadcastIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendOrderedBroadcast(broadcastIntent, null);
        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return highPriorityReceiver.hasReceivedBroadCast()
                        && !lowPriorityReceiver.hasReceivedBroadCast();
            }
        }.run();

        synchronized (highPriorityReceiver) {
            highPriorityReceiver.notify();
        }

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return highPriorityReceiver.hasReceivedBroadCast()
                        && lowPriorityReceiver.hasReceivedBroadCast();
            }
        }.run();
    }

    @Test
    public void testSendOrderedBroadcast2() {
        final TestBroadcastReceiver broadcastReceiver = new TestBroadcastReceiver();
        broadcastReceiver.mIsOrderedBroadcasts = true;

        Bundle bundle = new Bundle();
        bundle.putString(KEY_KEPT, VALUE_KEPT);
        bundle.putString(KEY_REMOVED, VALUE_REMOVED);
        Intent intent = new Intent(ResultReceiver.MOCK_ACTION)
                .setPackage(mContext.getPackageName());
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendOrderedBroadcast(
                intent, null, broadcastReceiver, null, 1, INITIAL_RESULT, bundle);

        synchronized (mLockObj) {
            try {
                mLockObj.wait(BROADCAST_TIMEOUT);
            } catch (InterruptedException e) {
                assertWithMessage("unexpected InterruptedException.").fail();
            }
        }

        assertWithMessage("Receiver didn't make any response.")
                .that(broadcastReceiver.hadReceivedBroadCast())
                .isTrue();
        assertWithMessage("Incorrect code: " + broadcastReceiver.getResultCode())
                .that(broadcastReceiver.getResultCode())
                .isEqualTo(3);
        assertThat(broadcastReceiver.getResultData()).isEqualTo(ACTUAL_RESULT);
        Bundle resultExtras = broadcastReceiver.getResultExtras(false);
        assertThat(resultExtras.getString(KEY_ADDED)).isEqualTo(VALUE_ADDED);
        assertThat(resultExtras.getString(KEY_KEPT)).isEqualTo(VALUE_KEPT);
        assertThat(resultExtras.getString(KEY_REMOVED)).isNull();
    }

    @Test
    public void testSendOrderedBroadcastWithAppOp() {
        // we use a HighPriorityBroadcastReceiver because the final receiver should get the
        // broadcast only at the end.
        final ResultReceiver receiver = new HighPriorityBroadcastReceiver();
        final ResultReceiver finalReceiver = new ResultReceiver();

        setReadCellBroadcastsAppOpMode(AppOpsManager.MODE_ALLOWED);

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));

        mContext.sendOrderedBroadcast(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                null, // permission
                AppOpsManager.OPSTR_READ_CELL_BROADCASTS,
                finalReceiver,
                null, // scheduler
                0, // initial code
                null, //initial data
                null); // initial extras

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast()
                        && !finalReceiver.hasReceivedBroadCast();
            }
        }.run();

        synchronized (receiver) {
            receiver.notify();
        }

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                // ensure that first receiver has received broadcast before final receiver
                return receiver.hasReceivedBroadCast()
                        && finalReceiver.hasReceivedBroadCast();
            }
        }.run();
    }

    @Test
    public void testSendOrderedBroadcastWithAppOp_NotGranted() {
        final ResultReceiver receiver = new ResultReceiver();
        setReadCellBroadcastsAppOpMode(AppOpsManager.MODE_ERRORED);

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));

        mContext.sendOrderedBroadcast(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                null, // permission
                AppOpsManager.OPSTR_READ_CELL_BROADCASTS,
                null, // final receiver
                null, // scheduler
                0, // initial code
                null, //initial data
                null); // initial extras

        boolean broadcastNeverSent = false;
        try {
            new PollingCheck(BROADCAST_TIMEOUT) {
                @Override
                protected boolean check() {
                    return receiver.hasReceivedBroadCast();
                }

                public void runWithInterruption() throws InterruptedException {
                    if (check()) {
                        return;
                    }

                    long timeout = BROADCAST_TIMEOUT;
                    while (timeout > 0) {
                        SystemClock.sleep(50 /* time slice */);

                        if (check()) {
                            return;
                        }

                        timeout -= 50; // time slice
                    }
                    throw new InterruptedException();
                }
            }.runWithInterruption();
        } catch (InterruptedException e) {
            broadcastNeverSent = true;
        }

        assertThat(broadcastNeverSent).isTrue();
    }

    @Test
    public void testRegisterReceiver1() throws InterruptedException {
        final FilteredReceiver broadcastReceiver = new FilteredReceiver();
        final IntentFilter filter = new IntentFilter(MOCK_ACTION1);

        // Test registerReceiver
        mContext.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED_UNAUDITED);

        // Test unwanted intent(action = MOCK_ACTION2)
        broadcastReceiver.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION2);
        assertThat(broadcastReceiver.hadReceivedBroadCast1()).isFalse();
        assertThat(broadcastReceiver.hadReceivedBroadCast2()).isFalse();

        // Send wanted intent(action = MOCK_ACTION1)
        broadcastReceiver.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION1);
        assertThat(broadcastReceiver.hadReceivedBroadCast1()).isTrue();
        assertThat(broadcastReceiver.hadReceivedBroadCast2()).isFalse();

        mContext.unregisterReceiver(broadcastReceiver);

        // Test unregisterReceiver
        FilteredReceiver broadcastReceiver2 = new FilteredReceiver();
        mContext.registerReceiver(broadcastReceiver2, filter, Context.RECEIVER_EXPORTED_UNAUDITED);
        mContext.unregisterReceiver(broadcastReceiver2);

        // Test unwanted intent(action = MOCK_ACTION2)
        broadcastReceiver2.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION2);
        assertThat(broadcastReceiver2.hadReceivedBroadCast1()).isFalse();
        assertThat(broadcastReceiver2.hadReceivedBroadCast2()).isFalse();

        // Send wanted intent(action = MOCK_ACTION1), but the receiver is unregistered.
        broadcastReceiver2.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION1);
        assertThat(broadcastReceiver2.hadReceivedBroadCast1()).isFalse();
        assertThat(broadcastReceiver2.hadReceivedBroadCast2()).isFalse();
    }

    @Test
    public void testRegisterReceiver2() throws InterruptedException {
        FilteredReceiver broadcastReceiver = new FilteredReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MOCK_ACTION1);

        // Test registerReceiver
        mContext.registerReceiver(broadcastReceiver, filter, null, null,
                Context.RECEIVER_EXPORTED_UNAUDITED);

        // Test unwanted intent(action = MOCK_ACTION2)
        broadcastReceiver.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION2);
        assertThat(broadcastReceiver.hadReceivedBroadCast1()).isFalse();
        assertThat(broadcastReceiver.hadReceivedBroadCast2()).isFalse();

        // Send wanted intent(action = MOCK_ACTION1)
        broadcastReceiver.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION1);
        assertThat(broadcastReceiver.hadReceivedBroadCast1()).isTrue();
        assertThat(broadcastReceiver.hadReceivedBroadCast2()).isFalse();

        mContext.unregisterReceiver(broadcastReceiver);
    }

    @Test
    public void testRegisterReceiverForAllUsers() throws InterruptedException {
        FilteredReceiver broadcastReceiver = new FilteredReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MOCK_ACTION1);

        // Test registerReceiverForAllUsers without permission: verify SecurityException.
        assertThrows(
                "testRegisterReceiverForAllUsers: "
                        + "SecurityException expected on registerReceiverForAllUsers",
                SecurityException.class,
                () ->
                        mContext.registerReceiverForAllUsers(
                                broadcastReceiver,
                                filter,
                                null,
                                null,
                                Context.RECEIVER_EXPORTED_UNAUDITED));

        // Test registerReceiverForAllUsers with permission.
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mContext,
                    (ctx) -> ctx.registerReceiverForAllUsers(broadcastReceiver, filter, null, null,
                            Context.RECEIVER_EXPORTED_UNAUDITED)
            );
        } catch (SecurityException se) {
            assertWithMessage("testRegisterReceiverForAllUsers: SecurityException not expected")
                    .fail();
        }

        // Test unwanted intent(action = MOCK_ACTION2)
        broadcastReceiver.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION2);
        assertThat(broadcastReceiver.hadReceivedBroadCast1()).isFalse();
        assertThat(broadcastReceiver.hadReceivedBroadCast2()).isFalse();

        // Send wanted intent(action = MOCK_ACTION1)
        broadcastReceiver.reset();
        waitForFilteredIntent(mContext, MOCK_ACTION1);
        assertThat(broadcastReceiver.hadReceivedBroadCast1()).isTrue();
        assertThat(broadcastReceiver.getSendingUser()).isEqualTo(Process.myUserHandle());
        assertThat(broadcastReceiver.hadReceivedBroadCast2()).isFalse();

        mContext.unregisterReceiver(broadcastReceiver);
    }

    @Test
    public void testAccessWallpaper() {
        if (!isWallpaperSupported()) return;

        SystemUtil.runWithShellPermissionIdentity(
                () -> mOriginalWallpaper = (BitmapDrawable) mContext.getWallpaper(),
                READ_WALLPAPER_INTERNAL);

        // set Wallpaper by context#setWallpaper(Bitmap)
        Bitmap bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.RGB_565);

        // grant permission READ_WALLPAPER_INTERNAL for the whole test
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    // Test getWallpaper
                    Drawable testDrawable = mContext.getWallpaper();
                    // Test peekWallpaper
                    Drawable testDrawable2 = mContext.peekWallpaper();

                    mContext.setWallpaper(bitmap);
                    mWallpaperChanged = true;
                    SystemClock.sleep(500);

                    assertThat(mContext.peekWallpaper()).isNotSameInstanceAs(testDrawable);
                    assertThat(mContext.getWallpaper()).isNotNull();
                    assertThat(mContext.peekWallpaper()).isNotSameInstanceAs(testDrawable2);
                    assertThat(mContext.peekWallpaper()).isNotNull();

                    // set Wallpaper by context#setWallpaper(InputStream)
                    mContext.clearWallpaper();

                    testDrawable = mContext.getWallpaper();
                    InputStream stream =
                            mContext.getResources().openRawResource(R.drawable.scenery);

                    mContext.setWallpaper(stream);
                    SystemClock.sleep(1000);

                    assertThat(mContext.peekWallpaper()).isNotSameInstanceAs(testDrawable);
                },
                READ_WALLPAPER_INTERNAL);
    }

    @Test
    public void testAccessDatabase() {
        String DATABASE_NAME = "databasetest";
        String DATABASE_NAME1 = DATABASE_NAME + "1";
        String DATABASE_NAME2 = DATABASE_NAME + "2";
        SQLiteDatabase mDatabase;
        File mDatabaseFile;

        SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
            public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                    String editTable, SQLiteQuery query) {
                return new android.database.sqlite.SQLiteCursor(db, masterQuery, editTable, query) {
                    @Override
                    public boolean requery() {
                        setSelectionArguments(new String[]{"2"});
                        return super.requery();
                    }
                };
            }
        };

        // FIXME: Move cleanup into tearDown()
        for (String db : mContext.databaseList()) {
            File f = mContext.getDatabasePath(db);
            if (f.exists()) {
                mContext.deleteDatabase(db);
            }
        }

        // Test openOrCreateDatabase with null and actual factory
        mDatabase = mContext.openOrCreateDatabase(DATABASE_NAME1,
                Context.MODE_ENABLE_WRITE_AHEAD_LOGGING, factory);
        assertThat(mDatabase).isNotNull();
        mDatabase.close();
        mDatabase = mContext.openOrCreateDatabase(DATABASE_NAME2,
                Context.MODE_ENABLE_WRITE_AHEAD_LOGGING, factory);
        assertThat(mDatabase).isNotNull();
        mDatabase.close();

        // Test getDatabasePath
        File actualDBPath = mContext.getDatabasePath(DATABASE_NAME1);

        // Test databaseList()
        List<String> list = Arrays.asList(mContext.databaseList());
        assertWithMessage("1) database list: " + list).that(list.contains(DATABASE_NAME1)).isTrue();
        assertWithMessage("2) database list: " + list).that(list.contains(DATABASE_NAME2)).isTrue();

        // Test deleteDatabase()
        for (int i = 1; i < 3; i++) {
            mDatabaseFile = mContext.getDatabasePath(DATABASE_NAME + i);
            assertThat(mDatabaseFile.exists()).isTrue();
            mContext.deleteDatabase(DATABASE_NAME + i);
            mDatabaseFile = new File(actualDBPath, DATABASE_NAME + i);
            assertThat(mDatabaseFile.exists()).isFalse();
        }
    }

    @Test
    public void testEnforceUriPermission1() {
        assertThrows(
                "enforceUriPermission is not working without possessing an IPC.",
                SecurityException.class,
                () ->
                        mContext.enforceUriPermission(
                                URI,
                                Binder.getCallingPid(),
                                Binder.getCallingUid(),
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                "enforceUriPermission is not working without possessing an IPC."));
    }

    @Test
    public void testEnforceUriPermission2() {
        assertThrows(
                "enforceUriPermission is not working without possessing an IPC.",
                SecurityException.class,
                () ->
                        mContext.enforceUriPermission(
                                URI,
                                NOT_GRANTED_PERMISSION,
                                NOT_GRANTED_PERMISSION,
                                Binder.getCallingPid(),
                                Binder.getCallingUid(),
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                "enforceUriPermission is not working without possessing an IPC."));
    }

    @Test
    public void testGetPackageResourcePath() {
        assertThat(mContext.getPackageResourcePath()).isNotNull();
    }

    @Test
    public void testStartActivityWithActivityNotFound() {
        Intent intent = new Intent(mContext, ContextCtsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Because ContextWrapper is a wrapper class, so no need to test
        // the details of the function's performance. Getting a result
        // from the wrapped class is enough for testing.
        assertThrows(
                "Test startActivity should throw a ActivityNotFoundException here.",
                ActivityNotFoundException.class,
                () -> mContext.startActivity(intent));
    }

    @Test
    public void testStartActivities() {
        final Intent[] intents = {
                new Intent().setComponent(new ComponentName(mContext,
                        AvailableIntentsActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                new Intent().setComponent(new ComponentName(mContext,
                        ImageCaptureActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        };

        final Instrumentation.ActivityMonitor firstMonitor = getInstrumentation()
                .addMonitor(AvailableIntentsActivity.class.getName(), null /* result */,
                        false /* block */);
        final Instrumentation.ActivityMonitor secondMonitor = getInstrumentation()
                .addMonitor(ImageCaptureActivity.class.getName(), null /* result */,
                        false /* block */);

        mContext.startActivities(intents);

        Activity firstActivity = getInstrumentation().waitForMonitorWithTimeout(firstMonitor, 5000);
        assertThat(firstActivity).isNotNull();

        Activity secondActivity = getInstrumentation().waitForMonitorWithTimeout(secondMonitor,
                5000);
        assertThat(secondActivity).isNotNull();
    }

    @Test
    public void testStartActivityAsUser() {
        try (ActivitySession activitySession = new ActivitySession()) {
            Intent intent = new Intent(mContext, AvailableIntentsActivity.class);

            activitySession.assertActivityLaunched(intent.getComponent().getClassName(),
                    () -> SystemUtil.runWithShellPermissionIdentity(() ->
                            mContext.startActivityAsUser(intent, mContext.getUser())));
        }
    }

    @Test
    public void testStartActivity() {
        try (ActivitySession activitySession = new ActivitySession()) {
            Intent intent = new Intent(mContext, AvailableIntentsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activitySession.assertActivityLaunched(intent.getComponent().getClassName(),
                    () -> mContext.startActivity(intent));
        }
    }

    /** Helper class to launch / close test activity. */
    private static final class ActivitySession implements AutoCloseable {
        private Activity mTestActivity;
        private static final int ACTIVITY_LAUNCH_TIMEOUT = 5000;

        void assertActivityLaunched(String activityClassName, Runnable activityStarter) {
            final Instrumentation.ActivityMonitor monitor = getInstrumentation()
                    .addMonitor(activityClassName, null /* result */,
                            false /* block */);
            activityStarter.run();
            // Wait for activity launch with timeout.
            mTestActivity = getInstrumentation().waitForMonitorWithTimeout(monitor,
                    ACTIVITY_LAUNCH_TIMEOUT);
            assertThat(mTestActivity).isNotNull();
        }

        @Override
        public void close() {
            if (mTestActivity != null) {
                mTestActivity.finishAndRemoveTask();
            }
        }
    }

    @Test
    public void testCreatePackageContext() throws PackageManager.NameNotFoundException {
        Context actualContext =
                mContext.createPackageContext(ANDROID_SHELL, Context.CONTEXT_IGNORE_SECURITY);

        assertThat(actualContext).isNotNull();
    }

    @Test
    public void testCreatePackageContextAsUser() throws Exception {
        for (UserHandle user : new UserHandle[]{
                android.os.Process.myUserHandle(),
                UserHandle.ALL, UserHandle.CURRENT, UserHandle.SYSTEM
        }) {
            assertThat(mContext.createPackageContextAsUser(ANDROID_SHELL, 0, user).getUser())
                    .isEqualTo(user);
        }
    }

    @Test
    public void testCreateContextAsUser() {
        for (UserHandle user : new UserHandle[]{
                android.os.Process.myUserHandle(),
                UserHandle.ALL, UserHandle.CURRENT, UserHandle.SYSTEM
        }) {
            assertThat(mContext.createContextAsUser(user, 0).getUser()).isEqualTo(user);
        }
    }

    @Test
    public void testGetMainLooper() {
        assertThat(mContext.getMainLooper()).isNotNull();
    }

    @Test
    public void testGetApplicationContext() {
        assertThat(mContext.getApplicationContext())
                .isSameInstanceAs(mContext.getApplicationContext());
    }

    @Test
    public void testGetSharedPreferences() {
        SharedPreferences sp;
        SharedPreferences localSP;

        sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        String packageName = mContext.getPackageName();
        localSP = mContext.getSharedPreferences(packageName + "_preferences",
                Context.MODE_PRIVATE);
        assertThat(localSP).isSameInstanceAs(sp);
    }

    @Test
    public void testRevokeUriPermission() {
        mContext.revokeUriPermission(URI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Test
    public void testBindAllowFreezeProhibited() {
        MockContextService.reset();
        assertThrows(
                "No SecurityException while using BIND_ALLOW_FREEZE with bindService",
                SecurityException.class,
                () ->
                        mContext.bindService(
                                new Intent(mContext, MockContextService.class),
                                new TestConnection(),
                                Context.BindServiceFlags.of(Context.BIND_ALLOW_FREEZE)));
    }

    @Test
    public void testBindSimulateAllowFreezeProhibited() {
        MockContextService.reset();
        assertThrows(
                "No SecurityException while using BIND_SIMULATE_ALLOW_FREEZE with bindService",
                SecurityException.class,
                () ->
                        mContext.bindService(
                                new Intent(mContext, MockContextService.class),
                                new TestConnection(),
                                Context.BindServiceFlags.of(Context.BIND_SIMULATE_ALLOW_FREEZE)));
    }

    @Test
    public void testAccessService() throws InterruptedException {
        MockContextService.reset();
        bindExpectResult(mContext, new Intent(mContext, MockContextService.class));

        // Check startService
        assertThat(MockContextService.hadCalledOnStart()).isTrue();
        // Check bindService
        assertThat(MockContextService.hadCalledOnBind()).isTrue();

        assertThat(MockContextService.hadCalledOnDestory()).isTrue();
        // Check unbinService
        assertThat(MockContextService.hadCalledOnUnbind()).isTrue();
    }

    @Test
    public void testGetPackageCodePath() {
        assertThat(mContext.getPackageCodePath()).isNotNull();
    }

    @Test
    public void testGetPackageName() {
        assertThat(mContext.getPackageName()).isEqualTo("android.content.cts");
    }

    @Test
    public void testGetCacheDir() {
        assertThat(mContext.getCacheDir()).isNotNull();
    }

    @Test
    public void testGetContentResolver() {
        assertThat(mContext.getContentResolver()).isSameInstanceAs(mContext.getContentResolver());
    }

    @Test
    public void testGetFileStreamPath() {
        String TEST_FILENAME = "TestGetFileStreamPath";

        // Test the path including the input filename
        String fileStreamPath = mContext.getFileStreamPath(TEST_FILENAME).toString();
        assertThat(fileStreamPath.contains(TEST_FILENAME)).isTrue();
    }

    @Test
    public void testGetClassLoader() {
        assertThat(mContext.getClassLoader()).isSameInstanceAs(mContext.getClassLoader());
    }

    @Test
    public void testGetWallpaperDesiredMinimumHeightAndWidth() {
        if (!isWallpaperSupported()) return;

        int height = mContext.getWallpaperDesiredMinimumHeight();
        int width = mContext.getWallpaperDesiredMinimumWidth();

        // returned value is <= 0, the caller should use the height of the
        // default display instead.
        // That is to say, the return values of desired minimumHeight and
        // minimumWidth are at the same side of 0-dividing line.
        assertWithMessage(
                        "Expected (height > 0 && width > 0) or (height <= 0 && width <= 0) but was"
                                + " height="
                                + height
                                + ", width="
                                + width)
                .that((height > 0 && width > 0) || (height <= 0 && width <= 0))
                .isTrue();
    }

    @Test
    public void testAccessStickyBroadcast() throws InterruptedException {
        ResultReceiver resultReceiver = new ResultReceiver();

        Intent intent = new Intent(MOCK_STICKY_ACTION);
        TestBroadcastReceiver stickyReceiver = new TestBroadcastReceiver();

        mContext.sendStickyBroadcast(intent);

        waitForReceiveBroadCast(resultReceiver);

        assertThat(
                        mContext.registerReceiver(
                                        stickyReceiver,
                                        new IntentFilter(MOCK_STICKY_ACTION),
                                        Context.RECEIVER_NOT_EXPORTED)
                                .getAction())
                .isEqualTo(intent.getAction());

        synchronized (mLockObj) {
            mLockObj.wait(BROADCAST_TIMEOUT);
        }

        assertWithMessage("Receiver didn't make any response.")
                .that(stickyReceiver.hadReceivedBroadCast())
                .isTrue();

        mContext.unregisterReceiver(stickyReceiver);
        mContext.removeStickyBroadcast(intent);

        assertThat(
                        mContext.registerReceiver(
                                stickyReceiver,
                                new IntentFilter(MOCK_STICKY_ACTION),
                                Context.RECEIVER_EXPORTED_UNAUDITED))
                .isNull();
        mContext.unregisterReceiver(stickyReceiver);
    }

    @Test
    public void testCheckCallingOrSelfUriPermissions() {
        List<Uri> uris = new ArrayList<>();
        uris.add(URI1);
        uris.add(URI2);

        int[] retValue = mContext.checkCallingOrSelfUriPermissions(uris,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue.length).isEqualTo(2);
        // This package does not have access to the given URIs
        assertThat(retValue[0]).isEqualTo(PERMISSION_DENIED);
        assertThat(retValue[1]).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testCheckCallingOrSelfUriPermission() {
        int retValue =
                mContext.checkCallingOrSelfUriPermission(
                        URI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testGrantUriPermission() {
        mContext.grantUriPermission("com.android.mms", URI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Test
    public void testCheckPermissionGranted() {
        int returnValue = mContext.checkPermission(
                GRANTED_PERMISSION, Process.myPid(), Process.myUid());
        assertThat(returnValue).isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    public void testCheckPermissionNotGranted() {
        int returnValue = mContext.checkPermission(
                NOT_GRANTED_PERMISSION, Process.myPid(), Process.myUid());
        assertThat(returnValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testCheckPermissionRootUser() {
        // Test with root user, everything will be granted.
        int returnValue = mContext.checkPermission(NOT_GRANTED_PERMISSION, 1, ROOT_UID);
        assertThat(returnValue).isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    public void testCheckPermissionInvalidRequest() {
        // Test with null permission.
        assertThrows(
                "checkPermission should not accept null permission",
                IllegalArgumentException.class,
                () -> mContext.checkPermission(null, Process.myPid(), Process.myUid()));

        // Test with invalid uid and included granted permission.
        int returnValue = mContext.checkPermission(GRANTED_PERMISSION, 1, -11);
        assertThat(returnValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testCheckSelfPermissionGranted() {
        int returnValue = mContext.checkSelfPermission(GRANTED_PERMISSION);
        assertThat(returnValue).isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    public void testCheckSelfPermissionNotGranted() {
        int returnValue = mContext.checkSelfPermission(NOT_GRANTED_PERMISSION);
        assertThat(returnValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testEnforcePermissionGranted() {
        mContext.enforcePermission(
                GRANTED_PERMISSION, Process.myPid(), Process.myUid(),
                "permission isn't granted");
    }

    @Test
    public void testEnforcePermissionNotGranted() {
        assertThrows(
                "Permission shouldn't be granted.",
                SecurityException.class,
                () ->
                        mContext.enforcePermission(
                                NOT_GRANTED_PERMISSION,
                                Process.myPid(),
                                Process.myUid(),
                                "permission isn't granted"));
    }

    @Test
    public void testCheckCallingOrSelfPermission_noIpc() {
        // There's no ongoing Binder call, so this package's permissions are checked.
        int retValue = mContext.checkCallingOrSelfPermission(GRANTED_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_GRANTED);

        retValue = mContext.checkCallingOrSelfPermission(NOT_GRANTED_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testCheckCallingOrSelfPermission_ipc() throws Exception {
        bindBinderPermissionTestService();
        try {
            int retValue = mBinderPermissionTestService.doCheckCallingOrSelfPermission(
                    GRANTED_PERMISSION);
            assertThat(retValue).isEqualTo(PERMISSION_GRANTED);

            retValue = mBinderPermissionTestService.doCheckCallingOrSelfPermission(
                    NOT_GRANTED_PERMISSION);
            assertThat(retValue).isEqualTo(PERMISSION_DENIED);
        } finally {
            mContext.unbindService(mBinderPermissionTestConnection);
        }
    }

    @Test
    public void testEnforceCallingOrSelfPermission_noIpc() {
        // There's no ongoing Binder call, so this package's permissions are checked.
        mContext.enforceCallingOrSelfPermission(
                GRANTED_PERMISSION, "permission isn't granted");
        assertThrows(
                "Permission shouldn't be granted.",
                SecurityException.class,
                () ->
                        mContext.enforceCallingOrSelfPermission(
                                NOT_GRANTED_PERMISSION, "permission isn't granted"));
    }

    @Test
    public void testEnforceCallingOrSelfPermission_ipc() throws Exception {
        bindBinderPermissionTestService();
        try {
            mBinderPermissionTestService.doEnforceCallingOrSelfPermission(GRANTED_PERMISSION);
            assertThrows(
                    "Permission shouldn't be granted.",
                    SecurityException.class,
                    () ->
                            mBinderPermissionTestService.doEnforceCallingOrSelfPermission(
                                    NOT_GRANTED_PERMISSION));
        } finally {
            mContext.unbindService(mBinderPermissionTestConnection);
        }
    }

    @Test
    public void testCheckCallingPermission_noIpc() {
        // Denied because no IPC is active.
        int retValue = mContext.checkCallingPermission(GRANTED_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testEnforceCallingPermission_noIpc() {
        // Currently no IPC is handled by this process, this exception is expected
        assertThrows(
                "enforceCallingPermission is not working without possessing an IPC.",
                SecurityException.class,
                () ->
                        mContext.enforceCallingPermission(
                                GRANTED_PERMISSION,
                                "enforceCallingPermission is not working without possessing an"
                                        + " IPC."));
    }

    @Test
    public void testEnforceCallingPermission_ipc() throws Exception {
        bindBinderPermissionTestService();
        try {
            mBinderPermissionTestService.doEnforceCallingPermission(GRANTED_PERMISSION);
            assertThrows(
                    "Permission shouldn't be granted.",
                    SecurityException.class,
                    () ->
                            mBinderPermissionTestService.doEnforceCallingPermission(
                                    NOT_GRANTED_PERMISSION));
        } finally {
            mContext.unbindService(mBinderPermissionTestConnection);
        }
    }

    @Test
    public void testCheckCallingPermission_ipc() throws Exception {
        bindBinderPermissionTestService();
        try {
            int returnValue = mBinderPermissionTestService.doCheckCallingPermission(
                    GRANTED_PERMISSION);
            assertThat(returnValue).isEqualTo(PERMISSION_GRANTED);

            returnValue = mBinderPermissionTestService.doCheckCallingPermission(
                    NOT_GRANTED_PERMISSION);
            assertThat(returnValue).isEqualTo(PERMISSION_DENIED);
        } finally {
            mContext.unbindService(mBinderPermissionTestConnection);
        }
    }

    private void bindBinderPermissionTestService() {
        Intent intent = new Intent(mContext, IBinderPermissionTestService.class);
        intent.setComponent(new ComponentName(
                "com.android.cts", "com.android.cts.BinderPermissionTestService"));

        mBinderPermissionTestConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                mBinderPermissionTestService =
                        IBinderPermissionTestService.Stub.asInterface(iBinder);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };

        assertWithMessage("Service not bound")
                .that(
                        mContext.bindService(
                                intent, mBinderPermissionTestConnection, Context.BIND_AUTO_CREATE))
                .isTrue();

        new PollingCheck(SERVICE_TIMEOUT) {
            protected boolean check() {
                return mBinderPermissionTestService != null; // Service was bound.
            }
        }.run();
    }

    @Test
    public void testCheckUriPermissions() {
        List<Uri> uris = new ArrayList<>();
        uris.add(URI1);
        uris.add(URI2);

        // Root has access to all URIs
        int[] retValue = mContext.checkUriPermissions(uris, Binder.getCallingPid(), 0,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue.length).isEqualTo(2);
        assertThat(retValue[0]).isEqualTo(PERMISSION_GRANTED);
        assertThat(retValue[1]).isEqualTo(PERMISSION_GRANTED);

        retValue = mContext.checkUriPermissions(uris, Binder.getCallingPid(),
                Binder.getCallingUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue.length).isEqualTo(2);
        // This package does not have access to the given URIs
        assertThat(retValue[0]).isEqualTo(PERMISSION_DENIED);
        assertThat(retValue[1]).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testCheckUriPermission1() {
        int retValue =
                mContext.checkUriPermission(
                        URI, Binder.getCallingPid(), 0, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_GRANTED);

        retValue =
                mContext.checkUriPermission(
                        URI,
                        Binder.getCallingPid(),
                        Binder.getCallingUid(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testCheckUriPermission2() {
        int retValue =
                mContext.checkUriPermission(
                        URI,
                        NOT_GRANTED_PERMISSION,
                        NOT_GRANTED_PERMISSION,
                        Binder.getCallingPid(),
                        0,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_GRANTED);

        retValue =
                mContext.checkUriPermission(
                        URI,
                        NOT_GRANTED_PERMISSION,
                        NOT_GRANTED_PERMISSION,
                        Binder.getCallingPid(),
                        Binder.getCallingUid(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_DENIED);
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    @Test
    public void testCheckContentUriPermissionFull_exceptionsAndNonExistentProviders() {
        final int myPid = Process.myPid();
        final int myUid = Process.myUid();
        final Uri nonExistentContentUri = Uri.parse("content://provider.does.not.exist");
        final Uri fileUri = Uri.parse("file://some.file");
        assertThrows(
                "Shouldn't accept non-access mode flags",
                IllegalArgumentException.class,
                () ->
                        mContext.checkContentUriPermissionFull(
                                nonExistentContentUri, myPid, myUid, /* modeFlags */ 0));

        assertThrows(
                "Shouldn't accept non-access mode flags",
                IllegalArgumentException.class,
                () ->
                        mContext.checkContentUriPermissionFull(
                                nonExistentContentUri,
                                myPid,
                                myUid,
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));

        assertThrows(
                "Shouldn't accept non-content URIs",
                IllegalArgumentException.class,
                () ->
                        mContext.checkContentUriPermissionFull(
                                fileUri, myPid, myUid, Intent.FLAG_GRANT_READ_URI_PERMISSION));

        int res = mContext.checkContentUriPermissionFull(fileUri, myPid, Process.INVALID_UID,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String msg = "Should return PERMISSION_DENIED for an invalid UID";
        assertWithMessage(msg).that(res).isEqualTo(PERMISSION_DENIED);

        // Non-existent content URI
        res = mContext.checkContentUriPermissionFull(nonExistentContentUri, myPid,
                myUid, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        msg = "Should return PERMISSION_DENIED for a non-existent content URI";
        assertWithMessage(msg).that(res).isEqualTo(PERMISSION_DENIED);
    }

    /**
     * This test does the following:
     * 1. Binds to TestService in {@link android.content.cts.contenturitestapp}.
     * 2. Sends a message to TestService requesting a content URI that this package has (or doesn't
     * have) access to via grants or general permissions.
     * 3. Checks the result from checkContentUriPermissionFull().
     */
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    @Test
    public void testCheckContentUriPermissionFull_withGrantsAndGeneralAccess() {
        try {
            setUpContentUriTestServiceConnection();

            internalTestCheckContentUriPermissionFull(PKG_ACCESS_TYPE_NONE,
                    /* modeFlagsTestHasAccessTo */ 0);

            int[] packageAccessTypeValues = new int[]{
                    PKG_ACCESS_TYPE_GRANT,
                    PKG_ACCESS_TYPE_GENERAL
            };
            int[] modeFlagsTestHasAccessToValues = new int[]{
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            };

            for (int packageAccessType : packageAccessTypeValues) {
                for (int modeFlagsTestHasAccessTo : modeFlagsTestHasAccessToValues) {
                    internalTestCheckContentUriPermissionFull(packageAccessType,
                            modeFlagsTestHasAccessTo);
                }
            }
        } catch (Exception e) {
            assertWithMessage(e.getMessage()).fail();
        } finally {
            mContext.unbindService(mContentUriServiceConnection);
        }
    }

    private void setUpContentUriTestServiceConnection() {
        mContentUriServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mContentUriTestService = IContentUriTestService.Stub.asInterface(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mContentUriTestService = null;
            }
        };

        Intent intent = new Intent();
        intent.setComponent(COMPONENT_CONTENT_URI_TEST_SERVICE);
        assertThat(
                        mContext.bindService(
                                intent, mContentUriServiceConnection, Service.BIND_AUTO_CREATE))
                .isTrue();

        new PollingCheck(SERVICE_TIMEOUT) {
            protected boolean check() {
                return mContentUriTestService != null;
            }
        }.run();
    }

    private void internalTestCheckContentUriPermissionFull(int packageAccessType,
            int modeFlagsTestHasAccessTo) throws Exception {
        Uri contentUri = mContentUriTestService.getContentUriForContext(packageAccessType,
                modeFlagsTestHasAccessTo);
        String argsInfo = "packageAccessType: " + packageAccessType + ", modeFlags: "
                + modeFlagsTestHasAccessTo;
        assertWithMessage("Can't retrieve content URI for args (" + argsInfo + ")")
                .that(contentUri)
                .isNotNull();

        boolean hasRead = (modeFlagsTestHasAccessTo & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
        boolean hasWrite = (modeFlagsTestHasAccessTo & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
        final int myPid = Process.myPid();
        final int myUid = Process.myUid();

        // Checks for read permission
        String msg = getInternalContentUriErrorMessage(hasRead, "read", packageAccessType,
                contentUri);
        int expected = hasRead ? PERMISSION_GRANTED : PERMISSION_DENIED;
        int actual = mContext.checkContentUriPermissionFull(contentUri, myPid, myUid,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        assertWithMessage(msg).that(actual).isEqualTo(expected);

        // Checks for write permission
        msg = getInternalContentUriErrorMessage(hasWrite, "write", packageAccessType, contentUri);
        expected = hasWrite ? PERMISSION_GRANTED : PERMISSION_DENIED;
        actual = mContext.checkContentUriPermissionFull(contentUri, myPid, myUid,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertWithMessage(msg).that(actual).isEqualTo(expected);

        // Checks for read and write permissions
        msg = getInternalContentUriErrorMessage(hasRead && hasWrite, "read and write",
                packageAccessType, contentUri);
        expected = (hasRead && hasWrite) ? PERMISSION_GRANTED : PERMISSION_DENIED;
        actual = mContext.checkContentUriPermissionFull(contentUri, myPid, myUid,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertWithMessage(msg).that(actual).isEqualTo(expected);
    }

    private String getInternalContentUriErrorMessage(boolean has, String permissions,
            int packageAccessType, Uri contentUri) {
        StringBuilder sb = new StringBuilder("Should");
        if (!has) sb.append("n't");
        sb.append(" have ");
        sb.append(permissions);
        sb.append(" for: ");
        sb.append(contentUri);
        if (packageAccessType == PKG_ACCESS_TYPE_GRANT) {
            sb.append(" via grant");
        } else if (packageAccessType == PKG_ACCESS_TYPE_GENERAL) {
            sb.append(" via permission");
        }
        return sb.toString();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testCheckCallingUriPermissions() {
        List<Uri> uris = new ArrayList<>();
        uris.add(URI1);
        uris.add(URI2);

        int[] retValue = mContext.checkCallingUriPermissions(uris,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue.length).isEqualTo(2);
        // This package does not have access to the given URIs
        assertThat(retValue[0]).isEqualTo(PERMISSION_DENIED);
        assertThat(retValue[1]).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testCheckCallingUriPermission() {
        int retValue =
                mContext.checkCallingUriPermission(URI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertThat(retValue).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testEnforceCallingUriPermission() {
        // If the function is OK, it should throw a SecurityException here because currently no
        // IPC is handled by this process.
        assertThrows(
                "enforceCallingUriPermission is not working without possessing an IPC.",
                SecurityException.class,
                () ->
                        mContext.enforceCallingUriPermission(
                                URI,
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                "enforceCallingUriPermission is not working without possessing an"
                                        + " IPC."));
    }

    @Test
    public void testGetDir() {
        File dir = mContext.getDir("testpath", Context.MODE_PRIVATE);
        assertThat(dir).isNotNull();
        dir.delete();
    }

    @Test
    public void testGetPackageManager() {
        assertThat(mContext.getPackageManager()).isSameInstanceAs(mContext.getPackageManager());
    }

    @Test
    public void testSendBroadcast1() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));

        mContext.sendBroadcast(new Intent(ResultReceiver.MOCK_ACTION)
                .setPackage(mContext.getPackageName()));

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    @Test
    public void testSendBroadcast2() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));

        final Intent intent = new Intent(ResultReceiver.MOCK_ACTION)
                .setPackage(mContext.getPackageName());
        final BroadcastOptions options = BroadcastOptions.makeBasic()
                .setDebugLogEnabled(true);
        mContext.sendBroadcast(intent, null, options.toBundle());

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    /**
     * Verify the receiver should get the broadcast since it has all of the required permissions.
     */
    @Test
    public void testSendBroadcastRequireAllOfPermissions_receiverHasAllPermissions() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setDebugLogEnabled(true);
        options.setRequireAllOfPermissions(
                new String[]{ // this test APK has both these permissions
                        android.Manifest.permission.ACCESS_WIFI_STATE,
                        android.Manifest.permission.ACCESS_NETWORK_STATE
                });
        mContext.sendBroadcast(new Intent(ResultReceiver.MOCK_ACTION)
                .setPackage(mContext.getPackageName()), null, options.toBundle());

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    @Test
    public void testSendBroadcast_requireAppOpPermission_receiverHasPermissionAndDefaultAppOp() {
        setGetUsageStatsAppOpMode(AppOpsManager.MODE_DEFAULT);
        final ResultReceiver receiver = new ResultReceiver();
        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setDebugLogEnabled(true);
        // The test APK has this AppOp permission.
        options.setRequireAllOfPermissions(
                new String[]{android.Manifest.permission.PACKAGE_USAGE_STATS});

        mContext.sendBroadcast(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                null /* receiverPermission */,
                options.toBundle());

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    @Test
    public void testSendBroadcast_requireAppOpPermission_receiverHasPermissionAndAllowedAppOp() {
        setGetUsageStatsAppOpMode(AppOpsManager.MODE_ALLOWED);
        final ResultReceiver receiver = new ResultReceiver();
        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setDebugLogEnabled(true);
        options.setRequireAllOfPermissions(
                new String[]{android.Manifest.permission.PACKAGE_USAGE_STATS});

        mContext.sendBroadcast(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                null /* receiverPermission */,
                options.toBundle());

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    @Test
    public void testSendBroadcast_requireAppOpPermission_receiverHasPermissionAndErroredAppOp() {
        setGetUsageStatsAppOpMode(AppOpsManager.MODE_ERRORED);
        final ResultReceiver receiver = new ResultReceiver();
        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setDebugLogEnabled(true);
        options.setRequireAllOfPermissions(
                new String[]{android.Manifest.permission.PACKAGE_USAGE_STATS});

        mContext.sendBroadcast(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                null /* receiverPermission */,
                options.toBundle());

        SystemClock.sleep(BROADCAST_TIMEOUT);
        assertThat(receiver.hasReceivedBroadCast()).isFalse();
    }

    /** The receiver should not get the broadcast if it does not have all the permissions. */
    @Test
    public void testSendBroadcastRequireAllOfPermissions_receiverHasSomePermissions() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setDebugLogEnabled(true);
        options.setRequireAllOfPermissions(
                new String[]{ // this test APK only has ACCESS_WIFI_STATE
                        android.Manifest.permission.ACCESS_WIFI_STATE,
                        android.Manifest.permission.NETWORK_STACK,
                });

        mContext.sendBroadcast(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                null, options.toBundle());

        SystemClock.sleep(BROADCAST_TIMEOUT);
        assertThat(receiver.hasReceivedBroadCast()).isFalse();
    }

    /** Verify the receiver will get the broadcast since it has none of the excluded permissions. */
    @Test
    public void testSendBroadcastRequireNoneOfPermissions_receiverHasNoneOfExcludedPermissions() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setDebugLogEnabled(true);
        options.setRequireAllOfPermissions(
                new String[]{ // this test APK has both these permissions
                        android.Manifest.permission.ACCESS_WIFI_STATE,
                        android.Manifest.permission.ACCESS_NETWORK_STATE
                });
        options.setRequireNoneOfPermissions(
                new String[]{ // test package does not have NETWORK_STACK
                        android.Manifest.permission.NETWORK_STACK
                });
        mContext.sendBroadcast(new Intent(ResultReceiver.MOCK_ACTION)
                .setPackage(mContext.getPackageName()), null, options.toBundle());

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    /**
     * Verify the receiver will not get the broadcast since it has one of the excluded permissions.
     */
    @Test
    public void testSendBroadcastRequireNoneOfPermissions_receiverHasExcludedPermissions() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setDebugLogEnabled(true);
        options.setRequireAllOfPermissions(
                new String[]{ // this test APK has ACCESS_WIFI_STATE
                        android.Manifest.permission.ACCESS_WIFI_STATE
                });
        options.setRequireNoneOfPermissions(
                new String[]{ // test package has ACCESS_NETWORK_STATE
                        android.Manifest.permission.ACCESS_NETWORK_STATE
                });
        mContext.sendBroadcast(new Intent(ResultReceiver.MOCK_ACTION)
                        .setPackage(mContext.getPackageName()), null,
                options.toBundle());

        SystemClock.sleep(BROADCAST_TIMEOUT);
        assertThat(receiver.hasReceivedBroadCast()).isFalse();
    }

    /** The receiver should get the broadcast if it has all the permissions. */
    @Test
    public void testSendBroadcastWithMultiplePermissions_receiverHasAllPermissions() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));

        mContext.sendBroadcastWithMultiplePermissions(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                new String[]{ // this test APK has both these permissions
                        android.Manifest.permission.ACCESS_WIFI_STATE,
                        android.Manifest.permission.ACCESS_NETWORK_STATE,
                });

        new PollingCheck(BROADCAST_TIMEOUT) {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    /** The receiver should not get the broadcast if it does not have all the permissions. */
    @Test
    public void testSendBroadcastWithMultiplePermissions_receiverHasSomePermissions() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));

        mContext.sendBroadcastWithMultiplePermissions(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                new String[]{ // this test APK only has ACCESS_WIFI_STATE
                        android.Manifest.permission.ACCESS_WIFI_STATE,
                        android.Manifest.permission.NETWORK_STACK,
                });

        SystemClock.sleep(BROADCAST_TIMEOUT);
        assertThat(receiver.hasReceivedBroadCast()).isFalse();
    }

    /** The receiver should not get the broadcast if it has none of the permissions. */
    @Test
    public void testSendBroadcastWithMultiplePermissions_receiverHasNoPermissions() {
        final ResultReceiver receiver = new ResultReceiver();

        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION));

        mContext.sendBroadcastWithMultiplePermissions(
                new Intent(ResultReceiver.MOCK_ACTION).setPackage(mContext.getPackageName()),
                new String[]{ // this test APK has neither of these permissions
                        android.Manifest.permission.NETWORK_SETTINGS,
                        android.Manifest.permission.NETWORK_STACK,
                });

        SystemClock.sleep(BROADCAST_TIMEOUT);
        assertThat(receiver.hasReceivedBroadCast()).isFalse();
    }

    /**
     * Starting from Android 13, a SecurityException is thrown for apps targeting this release or
     * later that do not specify {@link Context#RECEIVER_EXPORTED} or {@link
     * Context#RECEIVER_NOT_EXPORTED} when registering for non-system broadcasts.
     */
    @Test
    public void testRegisterReceiver_noFlags_exceptionThrown() {
        final ResultReceiver receiver = new ResultReceiver();
        assertThrows(
                "An app targeting Android 13 and registering a dynamic receiver for a "
                        + "non-system broadcast must receive a SecurityException if "
                        + "RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED is not specified",
                SecurityException.class,
                () -> {
                    registerBroadcastReceiver(
                            receiver, new IntentFilter(ResultReceiver.MOCK_ACTION), 0);
                });
    }

    /**
     * An app targeting Android 13 or later can register for system broadcasts without specifying
     * {@link Context#RECEIVER_EXPORTED} or {@link Context@RECEIVER_NOT_EXPORTED}.
     */
    @Test
    public void testRegisterReceiver_noFlagsProtectedBroadcast_noExceptionThrown() {
        final ResultReceiver receiver = new ResultReceiver();

        // Intent.ACTION_SCREEN_OFF is a system broadcast and thus should not require a flag
        // indicating whether the receiver is exported.
        registerBroadcastReceiver(receiver, new IntentFilter(Intent.ACTION_SCREEN_OFF), 0);
    }

    /**
     * An app targeting Android 13 or later can request a sticky broadcast via {@code
     * Context#registerReceiver} without specifying {@link Context#RECEIVER_EXPORTED} or {@link
     * Context#RECEIVER_NOT_EXPORTED}.
     */
    @Test
    public void testRegisterReceiver_noFlagsStickyBroadcast_noExceptionThrown() {
        // If a null receiver is specified to Context#registerReceiver, it indicates the caller
        // is requesting a sticky broadcast without actually registering a receiver; a flag
        // must not be required in this case.
        mContext.registerReceiver(null, new IntentFilter(ResultReceiver.MOCK_ACTION), 0);
    }

    /**
     * Starting from Android 13, an app targeting this release or later must specify one of either
     * {@link Context#RECEIVER_EXPORTED} or {@link Context#RECEIVER_NOT_EXPORTED} when registering a
     * receiver for non-system broadcasts; however if both are specified then an {@link
     * IllegalArgumentException} should be thrown.
     */
    @Test
    public void testRegisterReceiver_bothFlags_exceptionThrown() {
        final ResultReceiver receiver = new ResultReceiver();
        assertThrows(
                "An app invoke invoking Context#registerReceiver with both RECEIVER_EXPORTED and"
                        + " RECEIVER_NOT_EXPORTED set must receive an IllegalArgumentException",
                IllegalArgumentException.class,
                () -> {
                    registerBroadcastReceiver(
                            receiver,
                            new IntentFilter(ResultReceiver.MOCK_ACTION),
                            Context.RECEIVER_EXPORTED | Context.RECEIVER_NOT_EXPORTED);
                });
    }

    /**
     * Verifies a receiver registered with {@link Context#RECEIVER_EXPORTED} can receive a broadcast
     * from an external app.
     *
     * <p>The broadcast is sent as a shell command since this most closely simulates sending a
     * broadcast from an external app; sending the broadcast via {@code
     * ShellIdentityUtils#invokeMethodWithShellPermissionsNoReturn} is still delivered even to apps
     * that use {@link Context#RECEIVER_NOT_EXPORTED}.
     */
    @Test
    public void testRegisterReceiver_exported_broadcastReceived() {
        final ResultReceiver receiver = new ResultReceiver();
        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION),
                Context.RECEIVER_EXPORTED);

        SystemUtil.runShellCommand(mExternalAppBroadcastCommand);

        new PollingCheck(BROADCAST_TIMEOUT, "The broadcast to the exported receiver"
                + " was not received within the timeout window") {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    /**
     * Verifies a receiver registered with {@link Context#RECEIVER_EXPORTED_UNAUDITED} can receive a
     * broadcast from an external app.
     *
     * <p>{@code Context#RECEIVER_EXPORTED_UNAUDITED} is only intended to be applied to receivers
     * that have not yet been audited to determine their intended exported state; this test ensures
     * this flag maintains the existing behavior of exporting the receiver until it can be
     * evaluated.
     */
    @Test
    public void testRegisterReceiver_exportedUnaudited_broadcastReceived() {
        final ResultReceiver receiver = new ResultReceiver();
        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);

        SystemUtil.runShellCommand(mExternalAppBroadcastCommand);

        new PollingCheck(BROADCAST_TIMEOUT, "The broadcast to the exported receiver"
                + " was not received within the timeout window") {
            @Override
            protected boolean check() {
                return receiver.hasReceivedBroadCast();
            }
        }.run();
    }

    /**
     * Verifies a receiver registered with {@link Context#RECEIVER_NOT_EXPORTED} does not receive a
     * broadcast from an external app.
     */
    @Test
    public void testRegisterReceiver_notExported_broadcastNotReceived() {
        final ResultReceiver receiver = new ResultReceiver();
        registerBroadcastReceiver(receiver, new IntentFilter(ResultReceiver.MOCK_ACTION),
                Context.RECEIVER_NOT_EXPORTED);

        SystemUtil.runShellCommand(mExternalAppBroadcastCommand);

        SystemClock.sleep(BROADCAST_TIMEOUT);
        assertWithMessage(
                        "An external app must not be able to send a broadcast to a dynamic receiver"
                                + " registered with RECEIVER_NOT_EXPORTED")
                .that(receiver.hasReceivedBroadCast())
                .isFalse();
    }

    @Test
    public void testRegisterReceiverForSystemBroadcast_notExported_stickyBroadcastReceived()
            throws InterruptedException {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            return;
        }
        final WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
        boolean wifiInitiallyOn = wifiManager.isWifiEnabled();
        // Cycle Wifi to force the WIFI_STATE_CHANGED_ACTION sticky broadcast
        if (wifiInitiallyOn) {
            SystemUtil.runShellCommand("cmd wifi set-wifi-enabled disabled");
            SystemClock.sleep(1000);
        }
        SystemUtil.runShellCommand("cmd wifi set-wifi-enabled enabled");
        SystemClock.sleep(1000);

        try {
            TestBroadcastReceiver stickyReceiver = new TestBroadcastReceiver();
            // A receiver registered for sticky broadcasts with the RECEIVER_NOT_EXPORTED flag
            // should still receive back a sticky broadcast sent from the system UID.
            assertThat(
                            mContext.registerReceiver(
                                            stickyReceiver,
                                            new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
                                            Context.RECEIVER_NOT_EXPORTED)
                                    .getAction())
                    .isEqualTo(WifiManager.WIFI_STATE_CHANGED_ACTION);
            synchronized (mLockObj) {
                mLockObj.wait(BROADCAST_TIMEOUT);
            }
            assertWithMessage("Sticky broadcast not delivered to unexported receiver")
                    .that(stickyReceiver.hadReceivedBroadCast())
                    .isTrue();
        } finally {
            if (wifiInitiallyOn) {
                SystemUtil.runShellCommand("cmd wifi set-wifi-enabled enabled");
            } else {
                SystemUtil.runShellCommand("cmd wifi set-wifi-enabled disabled");
            }
        }
    }

    @Test
    public void testEnforceCallingOrSelfUriPermission() {
        // If the function is OK, it should throw a SecurityException here because currently no
        // IPC is handled by this process.
        assertThrows(
                "enforceCallingOrSelfUriPermission is not working without possessing an IPC.",
                SecurityException.class,
                () ->
                        mContext.enforceCallingOrSelfUriPermission(
                                URI,
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                "enforceCallingOrSelfUriPermission is not working without"
                                        + " possessing an IPC."));
    }

    @Test
    public void testGetAssets() {
        assertThat(mContext.getAssets()).isSameInstanceAs(mContext.getAssets());
    }

    @Test
    public void testGetResources() {
        assertThat(mContext.getResources()).isSameInstanceAs(mContext.getResources());
    }

    @Test
    public void testStartInstrumentation() {
        // Use wrong name
        ComponentName cn = new ComponentName("com.android",
                "com.android.content.FalseLocalSampleInstrumentation");
        assertThat(cn).isNotNull();
        assertThat(mContext).isNotNull();
        // If the target instrumentation is wrong, the function should return false.
        assertThat(mContext.startInstrumentation(cn, null, null)).isFalse();
    }

    private void bindExpectResult(Context context, Intent service)
            throws InterruptedException {
        if (service == null) {
            assertWithMessage("No service created!").fail();
        }
        TestConnection conn = new TestConnection();

        context.bindService(service, conn, Context.BIND_AUTO_CREATE);
        context.startService(service);

        // Wait for a short time, so the service related operations could be
        // working.
        SystemClock.sleep(2500);
        // Test stop Service
        assertThat(context.stopService(service)).isTrue();
        context.unbindService(conn);

        SystemClock.sleep(1000);
    }

    private interface Condition {
        boolean onCondition();
    }

    private synchronized void waitForCondition(Condition con) {
        // check the condition every 1 second until the condition is fulfilled
        // and wait for 3 seconds at most
        for (int i = 0; !con.onCondition() && i <= 3; i++) {
            SystemClock.sleep(1000);
        }
    }

    private void waitForReceiveBroadCast(final ResultReceiver receiver)
            throws InterruptedException {
        Condition con = receiver::hasReceivedBroadCast;
        waitForCondition(con);
    }

    private void waitForFilteredIntent(Context context, final String action)
            throws InterruptedException {
        final BroadcastOptions options = BroadcastOptions.makeBasic()
                .setDebugLogEnabled(true);
        context.sendBroadcast(new Intent(action), null, options.toBundle());

        synchronized (mLockObj) {
            mLockObj.wait(BROADCAST_TIMEOUT);
        }
    }

    private final class TestBroadcastReceiver extends BroadcastReceiver {
        boolean mHadReceivedBroadCast;
        boolean mIsOrderedBroadcasts;

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                if (mIsOrderedBroadcasts) {
                    setResultCode(3);
                    setResultData(ACTUAL_RESULT);
                }

                Bundle map = getResultExtras(false);
                if (map != null) {
                    map.remove(KEY_REMOVED);
                    map.putString(KEY_ADDED, VALUE_ADDED);
                }
                mHadReceivedBroadCast = true;
                this.notifyAll();
            }

            synchronized (mLockObj) {
                mLockObj.notify();
            }
        }

        boolean hadReceivedBroadCast() {
            return mHadReceivedBroadCast;
        }

        void reset() {
            mHadReceivedBroadCast = false;
        }
    }

    private final class FilteredReceiver extends BroadcastReceiver {
        private boolean mHadReceivedBroadCast1 = false;
        private boolean mHadReceivedBroadCast2 = false;

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MOCK_ACTION1.equals(action)) {
                mHadReceivedBroadCast1 = true;
            } else if (MOCK_ACTION2.equals(action)) {
                mHadReceivedBroadCast2 = true;
            }

            synchronized (mLockObj) {
                mLockObj.notify();
            }
        }

        public boolean hadReceivedBroadCast1() {
            return mHadReceivedBroadCast1;
        }

        public boolean hadReceivedBroadCast2() {
            return mHadReceivedBroadCast2;
        }

        public void reset() {
            mHadReceivedBroadCast1 = false;
            mHadReceivedBroadCast2 = false;
        }
    }

    private static final class TestConnection implements ServiceConnection {
        TestConnection() {}

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    }

    @Test
    public void testOpenFileOutput_mustNotCreateWorldReadableFile() {
        assertThrows(
                "Exception expected",
                SecurityException.class,
                () -> mContext.openFileOutput("test.txt", Context.MODE_WORLD_READABLE));
    }

    @Test
    public void testOpenFileOutput_mustNotCreateWorldWriteableFile() {
        assertThrows(
                "Exception expected",
                SecurityException.class,
                () -> mContext.openFileOutput("test.txt", Context.MODE_WORLD_WRITEABLE));
    }

    @Test
    public void testOpenFileOutput_mustNotWriteToParentDirectory() {
        // Created files must be under the application's private directory.
        assertThrows(
                "Exception expected",
                IllegalArgumentException.class,
                () -> mContext.openFileOutput("../test.txt", Context.MODE_PRIVATE));
    }

    @Test
    public void testOpenFileOutput_mustNotUseAbsolutePath() {
        // Created files must be under the application's private directory.
        assertThrows(
                "Exception expected",
                IllegalArgumentException.class,
                () -> mContext.openFileOutput("/tmp/test.txt", Context.MODE_PRIVATE));
    }

    private boolean isWallpaperSupported() {
        return WallpaperManager.getInstance(mContext).isWallpaperSupported();
    }

    private void setGetUsageStatsAppOpMode(@AppOpsManager.Mode int appOpMode) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                (AppOpsManager) getContextUnderTest().getSystemService(Context.APP_OPS_SERVICE),
                (appOpsMan) ->
                        appOpsMan.setUidMode(
                                AppOpsManager.OP_GET_USAGE_STATS, Process.myUid(), appOpMode));
    }

    private void setReadCellBroadcastsAppOpMode(@AppOpsManager.Mode int appOpMode) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                (AppOpsManager) getContextUnderTest().getSystemService(Context.APP_OPS_SERVICE),
                (appOpsMan) ->
                        appOpsMan.setUidMode(
                                AppOpsManager.OPSTR_READ_CELL_BROADCASTS,
                                Process.myUid(),
                                appOpMode));
    }
}
