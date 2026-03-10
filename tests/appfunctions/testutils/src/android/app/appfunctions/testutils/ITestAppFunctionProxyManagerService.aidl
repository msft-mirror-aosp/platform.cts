package android.app.appfunctions.testutils;

import android.app.appfunctions.testutils.TestObserverHistory;

/**
 * AIDL interface for the TestAppFunctionProxyManagerService.
 */
interface ITestAppFunctionProxyManagerService {
    void startTestObserver();
    TestObserverHistory getTestObserverHistory();
}