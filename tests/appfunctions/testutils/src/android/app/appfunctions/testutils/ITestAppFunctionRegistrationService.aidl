package android.app.appfunctions.testutils;

/**
 * AIDL interface for the TestAppFunctionCallbackService.
 */
interface ITestAppFunctionRegistrationService {
    /**
     * Registers an app function from the service's process.
     */
    void registerAppFunction();

    /**
     * Unregisters previously registered app function.
     */
    void unregisterAppFunction();
}