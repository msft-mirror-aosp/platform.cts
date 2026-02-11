package android.app.appfunctions.testutils;

/**
 * AIDL interface for the TestAppFunctionRegistrationService.
 */
interface ITestAppFunctionRegistrationService {
    /**
     * Registers an app function from the service's process. FunctionId is taken from the
     * AppFunction class.
     * @param functionId The id of the app function.
     * @return true if the registration was successful
     */
    boolean registerAppFunction(String functionId);

    /**
     * Registers one or several app functions from the service's process. FunctionIds are taken
     * from the AppFunction class.
     * @param functionIds The ids of the app functions.
     * @return true if the registration was successful
     */
    boolean registerAppFunctions(in List<String> functionIds);

    /**
     * Unregisters previously registered app function. FunctionId is taken from the
     * AppFunction class.
     * @param functionId The id of the app function.
     * @return true if the unregistration was successful
     */
    boolean unregisterAppFunction(String functionId);
}