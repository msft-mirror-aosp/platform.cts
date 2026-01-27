package android.app.appfunctions.testutils;

/**
 * AIDL interface for the TestAppFunctionRegistrationService.
 */
interface ITestAppFunctionRegistrationService {
    /**
     * Registers an app function from the service's process. FunctionId is taken from the
     * AppFunction class.
     * @param functionType The type of the app function as in {@link FunctionType}
     * @return true if the registration was successful
     */
    boolean registerAppFunction(String functionType);

    /**
     * Registers one or several app functions from the service's process. FunctionIds are taken
     * from the AppFunction class.
     * @param functionTypes The types of the app function as in {@link FunctionType}
     * @return true if the registration was successful
     */
    boolean registerAppFunctions(in List<String> functionTypes);

    /**
     * Unregisters previously registered app function. FunctionId is taken from the
     * AppFunction class.
     * @param functionType The type of the app function as in {@link FunctionType}
     * @return true if the unregistration was successful
     */
    boolean unregisterAppFunction(String functionType);
}