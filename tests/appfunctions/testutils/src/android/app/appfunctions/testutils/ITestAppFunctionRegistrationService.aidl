package android.app.appfunctions.testutils;

/**
 * AIDL interface for the TestAppFunctionRegistrationService.
 */
interface ITestAppFunctionRegistrationService {
    /**
     * Registers an app function from the service's process. FunctionId is taken from the
     * AppFunction class.
     * @param functionType The type of the app function as in {@link FunctionType}
     */
    void registerAppFunction(String functionType);

    /**
     * Unregisters previously registered app function. FunctionId is taken from the
     * AppFunction class.
     * @param functionType The type of the app function as in {@link FunctionType}
     */
    void unregisterAppFunction(String functionType);
}