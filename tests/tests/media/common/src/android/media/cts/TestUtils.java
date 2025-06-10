/*
 * Copyright 2018 The Android Open Source Project
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

package android.media.cts;

import static android.content.pm.PackageManager.MATCH_APEX;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.AssumptionViolatedException;

import java.util.Locale;
import java.util.Objects;

/**
 * Utilities for tests.
 */
public final class TestUtils {
    private static String TAG = "TestUtils";
    private static final int WAIT_TIME_MS = 1000;
    private static final int WAIT_SERVICE_TIME_MS = 5000;

    /**
     * Compares contents of two bundles.
     *
     * @param a a bundle
     * @param b another bundle
     * @return {@code true} if two bundles are the same. {@code false} otherwise. This may be
     *     incorrect if any bundle contains a bundle.
     */
    public static boolean equals(Bundle a, Bundle b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!a.keySet().containsAll(b.keySet())
                || !b.keySet().containsAll(a.keySet())) {
            return false;
        }
        for (String key : a.keySet()) {
            if (!Objects.equals(a.get(key), b.get(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks {@code module} is at least {@code minVersion}
     *
     * The tests are skipped by throwing a {@link AssumptionViolatedException}.  CTS test runners
     * will report this as a {@code ASSUMPTION_FAILED}.
     *
     * @param module     the apex module name
     * @param minVersion the minimum version
     * @throws AssumptionViolatedException if module version < minVersion
     */
    public static void assumeMainlineModuleAtLeast(String module, long minVersion) {
        try {
            long actualVersion = getModuleVersion(module);
            assumeTrue("Assume  module  " + module + " version " + actualVersion + " < minVersion"
                    + minVersion, actualVersion >= minVersion);
        } catch (PackageManager.NameNotFoundException e) {
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Checks if {@code module} is < {@code minVersion}
     *
     * <p>
     * {@link AssumptionViolatedException} is not handled properly by {@code JUnit3} so just return
     * the test
     * early instead.
     *
     * @param module     the apex module name
     * @param minVersion the minimum version
     * @deprecated convert test to JUnit4 and use
     * {@link #assumeMainlineModuleAtLeast(String, long)} instead.
     */
    @Deprecated
    public static boolean skipTestIfMainlineLessThan(String module, long minVersion) {
        try {
            long actualVersion = getModuleVersion(module);
            if (actualVersion < minVersion) {
                Log.i(TAG, "Skipping test because Module  " + module + " minVersion " + minVersion
                        + " > "
                        + minVersion
                );
                return true;
            } else {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Assert.fail(e.getMessage());
            return false;
        }
    }

    private static long getModuleVersion(String module)
            throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(module,
                MATCH_APEX);
        return info.getLongVersionCode();
    }

    /**
     * Reports whether the APEX mainline module {@code module} has been updated from the version in
     * the system image. The result is used to decide whether to relax some test criteria, like
     * software codec performance being improved to run faster than performance data at initial
     * release. Another case is to introduce new codecs via mainline so the base system image won't
     * have performance data.
     *
     * @param module the apex module name
     * @return {@code true} {@code module} refers to an apex module which has been updated.
     */
    public static boolean isUpdatedMainlineModule(String module) {
        try {
            Context context = ApplicationProvider.getApplicationContext();
            PackageInfo info = context.getPackageManager().getPackageInfo(module,
                    MATCH_APEX);
            if (info == null) {
                Log.d(TAG, "module " + module + " is unknown");
                return false;
            }
            ApplicationInfo appInfo = info.applicationInfo;
            if (appInfo == null) {
                Log.d(TAG, "module " + module + " has no ApplicationInfo");
                return false;
            }
            // FLAG_SYSTEM changes during apex update on <= T; but stays set on >=U
            // FLAG_UPDATED_SYSTEM_APP always provides desired signalling
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                Log.d(TAG, "module " + module + " has not been updated");
                return false;
            }
            Log.d(TAG, "module " + module + " HAS been updated");
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            // doesn't exist, so it can't be upgraded
        }
        // we don't have information telling us otherwise.
        Log.d(TAG, "module " + module + "unable to determine if updated");
        return false;
    }

    /*
     * decide whether we are in CTS, MCTS, or MTS mode.
     * return the appropriate constant value
     */
    private static final int TESTMODE_NONE = 0;
    private static final int TESTMODE_CTS = 1;
    private static final int TESTMODE_MCTS = 2;
    private static final int TESTMODE_MTS = 3;

    private static int sCalculatedTestMode = TESTMODE_NONE;

    /**
     * Report the current testing mode, as an enumeration. Testing mode is determined by argument
     * 'media-testing-mode' which specifies 'cts', 'mcts', or 'mts' If missing, we use the older
     * boolean "mts-media" to generate either 'cts' or 'mts'
     *
     * <p>This is most often specified in a CtsMedia* app's AndroidTest.xml, using a line like:
     * <test class="com.android.tradefed.testtype.AndroidJUnitTest" > ... <option
     * name="instrumentation-arg" key="media-testing-mode" value="CTS" /> </test>
     *
     * @return {@code} one of the values TESTMODE_CTS, TESTMODE_MCTS, or TESTMODE_MTS.
     */
    public static synchronized int currentTestMode() {
        if (sCalculatedTestMode != TESTMODE_NONE) {
            return sCalculatedTestMode;
        }

        Bundle bundle = InstrumentationRegistry.getArguments();
        String value = bundle.getString("media-testing-mode");
        if (value == null) {
            value = bundle.getString("mts-media");
            if (value == null || !value.equals("true")) {
                value = "CTS";
            } else {
                value = "MTS";
            }
        }
        int mode;
        value = value.toUpperCase(Locale.ROOT);
        if (value.equals("CTS")) {
            mode = TESTMODE_CTS;
        } else if (value.equals("MCTS")) {
            mode = TESTMODE_MCTS;
        } else if (value.equals("MTS")) {
            mode = TESTMODE_MTS;
        } else {
            mode = TESTMODE_CTS;
        }

        // if invoked in MCTS mode, we'll look to see if *both* modules have been
        // updated from the system image; if so, we treat it as MTS.
        // this means updating media but not swcodec will still get us
        // behavior of validating performance data.
        if (mode == TESTMODE_MCTS) {
            Log.d(TAG, "Check on MCTS Mapping...");
            if (isUpdatedMainlineModule("com.google.android.media.swcodec")
                    && isUpdatedMainlineModule("com.google.android.media")) {
                Log.d(TAG, "Both media+swcodec modules updated; map MCTS to MTS mode");
                mode = TESTMODE_MTS;
            } else {
                Log.d(TAG, "MCTS stays in MCTS mode");
                mode = TESTMODE_MCTS;
            }
        }
        sCalculatedTestMode = mode;
        return mode;
    }

    /**
     * Report the current testing mode, as a string.
     * Testing mode is determined by argument 'media-testing-mode'
     * which specifies 'cts', 'mcts', or 'mts'
     * If missing, we use the older boolean "mts-media" to generate either 'cts' or 'mts'
     *
     * @return {@code} "CTS", "MCTS", or "MTS" corresponding to the mode.
     */
    public static String currentTestModeName() {
        int mode = currentTestMode();
        String result;

        switch (mode) {
            case TESTMODE_NONE:
            default:
                result = "unknown";
                break;
            case TESTMODE_CTS:
                result = "CTS";
                break;
            case TESTMODE_MCTS:
                result = "MCTS";
                break;
            case TESTMODE_MTS:
                result = "MTS";
                break;
        }
        return result;
    }

    /**
     * Report whether this test run should evaluate module functionality.
     * Some tests (or parts of tests) are restricted to a particular mode.
     *
     * @return {@code} true is the current test mode is MCTS or MTS.
     */
    public static boolean isTestingModules() {
        int mode = currentTestMode();
        switch (mode) {
            case TESTMODE_MCTS:
            case TESTMODE_MTS:
                return true;
            default:
                break;
        }
        return false;
    }

    /**
     * Report whether this test run should evaluate module functionality. Some tests (or parts of
     * tests) are restricted to a particular mode.
     *
     * @return {@code} true is the current test mode is MCTS or MTS.
     */
    public static boolean isTestingFramework() {
        return !isTestingModules();
    }

    /**
     * Report whether we are in CTS mode (vs MTS or MCTS) mode. Some tests (or parts of tests) are
     * restricted to a particular mode.
     *
     * @return {@code} true is the current test mode is CTS.
     */
    public static boolean isCtsMode() {
        int mode = currentTestMode();
        return mode == TESTMODE_CTS;
    }

    /**
     * Report whether we are in MTS mode (vs CTS or MCTS) mode.
     * Some tests (or parts of tests) are restricted to a particular mode.
     *
     * @return {@code} true is the current test mode is MTS.
     */
    public static boolean isMtsMode() {
        int mode = currentTestMode();
        return mode == TESTMODE_MTS;
    }

    /*
     * Report whether we want to test a particular code in the current test mode.
     * CTS is pretty much "test them all".
     * MTS should only be testing codecs that are part of the swcodec module; all of these
     * begin with "c2.android."
     *
     * Used in spots throughout the test suite where we want to limit our testing to relevant
     * codecs. This avoids false alarms that are sometimes triggered by non-compliant,
     * non-mainline codecs.
     *
     * @param name    the name of a codec
     * @return {@code} true is the codec should be tested in the current operating mode.
     */
    public static boolean isTestableCodecInCurrentMode(String name) {
        if (name == null) {
            return true;
        }
        int mode = currentTestMode();
        boolean result = false;
        switch (mode) {
            case TESTMODE_CTS:
                result = !isMainlineCodec(name);
                break;
            case TESTMODE_MCTS:
            case TESTMODE_MTS:
                result = isMainlineCodec(name);
                break;
        }
        Log.d(TAG, "codec " + name + (result ? " is " : " is not ")
                   + "tested in mode " + currentTestModeName());
        return result;
    }

    /*
     * Report whether this codec is a google-supplied codec that lives within the
     * mainline modules.
     *
     * @param name    the name of a codec
     * @return {@code} true if the codec is one that lives within the mainline boundaries
     */
    public static boolean isMainlineCodec(String name) {
        if (name.startsWith("c2.android.")) {
            return true;
        }
        return false;
    }

    private TestUtils() {
    }

    public static class Monitor {
        private int mNumSignal;

        public synchronized void reset() {
            mNumSignal = 0;
        }

        public synchronized void signal() {
            mNumSignal++;
            notifyAll();
        }

        public synchronized boolean waitForSignal() throws InterruptedException {
            return waitForCountedSignals(1) > 0;
        }

        public synchronized int waitForCountedSignals(int targetCount) throws InterruptedException {
            while (mNumSignal < targetCount) {
                wait();
            }
            return mNumSignal;
        }

        public synchronized boolean waitForSignal(long timeoutMs) throws InterruptedException {
            return waitForCountedSignals(1, timeoutMs) > 0;
        }

        public synchronized int waitForCountedSignals(int targetCount, long timeoutMs)
                throws InterruptedException {
            if (timeoutMs == 0) {
                return waitForCountedSignals(targetCount);
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (mNumSignal < targetCount) {
                long delay = deadline - System.currentTimeMillis();
                if (delay <= 0) {
                    break;
                }
                wait(delay);
            }
            return mNumSignal;
        }

        public synchronized boolean isSignalled() {
            return mNumSignal >= 1;
        }

        public synchronized int getNumSignal() {
            return mNumSignal;
        }
    }
}
