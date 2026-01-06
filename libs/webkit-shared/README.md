# Shared Webkit Environment

## Overview

TODO(b/453666068): this library is now obsolete. Do not convert new tests to use
this library. We will be refactoring the code to remove this library soon.

This helper lib makes a test suite extendable to run in both an activity based environment and
within the SDK Runtime.

[design](go/shared-sdk-sandbox-webview-tests) (*only visible to Googlers)

## Tutorial

*** aside
**Tip:**  If your test suite already extends SharedWebViewTest, you can skip to section
3, "Converting a test to shared"
***

### 1. Making a test suite sharable with the SDK Runtime

If you want to share webkit tests inside the SDK runtime, you will need to make your
test suite inherit from `android.webkit.cts.SharedWebViewTest`. This is used to indicate
that a test suite has a configurable environment.

Eg:
```java
- public class WebViewTest
+ public class WebViewTest extends SharedWebViewTest
```

This abstract class requires you to implement the method `createTestEnvironment` that
defines the test environment for your test suite. Think of the test environment as a
concrete description of where this test suite will execute. `createTestEnvironment` should
have all the references your test has to an activity. This will be overridden later on by the
SDK tests using the API method `setTestEnvironment`.

Eg:
```java
@Override
protected WebViewTestEnvironment createTestEnvironment() {
    return new WebViewTestEnvironment.Builder()
            .setContext(mActivity)
            .setWebView(mWebView)
            // This allows SDK methods to invoke APIs from outside the SDK.
            // The Activity based tests don't need to worry about this so you can
            // just provide the host app invoker directly to this environment.
            .setHostAppInvoker(WebViewTestEnvironment.createHostAppInvoker())
            .build();
}
```

Your test suite is now sharable with an SDK runtime test suite!

### 2. Sharing your tests with the SDK Runtime

**Note:** this section is obsolete. The SDK runtime tests have been deleted.

### 3. Converting a test to shared

You need to do two things when you are making a test shared:
1. Update the test suite to use the `WebViewTestEnvironment`
2. Update the SDK JUnit Test Suite to invoke the test

We will use `WebViewTest` as an example:
`//cts/tests/tests/webkit/src/android/webkit/cts/WebViewTest.java`

Search for `getTestEnvironment()`. This method returns a `WebViewTestEnvironment`.
Whenever your test needs to refer to anything that is not available in the SDK runtime,
or needs to be shared between the SDK runtime and the activity based tests,
use this class.

Open `WebViewTestEnvironment` to familiarize yourself with what is available:
`//cts/libs/webkit-shared/src/android/webkit/cts/WebViewTestEnvironment.java`

First convert any direct references to any variable that should come from the shared test
environment.

Eg:
```java
@Test
public void testExample() throws Throwable {
    - InstrumentationRegistry.getInstrumentation().waitForIdleSync();

    + getTestEnvironment().waitForIdleSync();
    ...
}
```

*** note
**Tip:**  You can likely just update your setup method to pull from the test environment for
minimal refactoring. Eg:

```java
@Test
public void setup() {
    mWebView = getTestEnvironment().getWebView();
    ...
}
```
***

## Invoking behavior in the Activity

There are some things you won't be able to initiate from within the SDK runtime
that are needed to write tests. For example, you cannot start a local server
in the SDK runtime, but this would be useful for testing against.

You can use the
ActivityInvoker (`//cts/libs/webkit-shared/src/android/webkit/cts/IActivityInvoker.aidl`)
to add this functionality.
The activity invoker allows SDK runtime tests to initiate events in the activity driving
the tests.

Once you have added a new ActivityInvoker API, provide a wrapper API to
WebViewTestEnvironment to abstract these APIs away from test authors.
