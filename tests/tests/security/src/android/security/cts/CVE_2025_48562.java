/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.security.cts;

import static android.print.PrintManager.PRINT_SPOOLER_PACKAGE_NAME;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Looper;
import android.platform.test.annotations.AsbSecurityTest;
import android.print.IPrintDocumentAdapter;
import android.print.PrintManager.PrintDocumentAdapterDelegate;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48562 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 423815728)
    @Test
    public void testPocCVE_2025_48562() {
        try {
            // Dynamically load classloader for 'frameworks.jar' to load
            // required classes.
            final Context applicationContext = getApplicationContext();
            final Context context =
                    applicationContext.createPackageContext(
                            PRINT_SPOOLER_PACKAGE_NAME,
                            Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
            final ClassLoader classLoader = context.getClassLoader();

            // Create instance of 'Activity' class and set 'mFinished=false', 'mBase', and
            // 'mApplication'.
            Looper.prepare();
            final Activity activity = new Activity();
            getDeclaredField(Activity.class, "mFinished").set(activity, false);
            getDeclaredField(classLoader.loadClass("android.content.ContextWrapper"), "mBase")
                    .set(activity, applicationContext);
            getDeclaredField(Activity.class, "mApplication").set(activity, new Application());

            // Create an instance of 'MutexFileProvider' class.
            final Class mutexFileProviderClass =
                    classLoader.loadClass(
                            String.format(
                                    "%s.model.MutexFileProvider", PRINT_SPOOLER_PACKAGE_NAME));
            final Constructor mutexFileProviderConstructor =
                    mutexFileProviderClass.getDeclaredConstructor(File.class);
            final Object mutexFileProvider =
                    mutexFileProviderConstructor.newInstance(
                            new File(applicationContext.getFilesDir(), "cve_2025_48562.pdf"));

            // Load 'RemoteAdapterDeathObserver' and 'UpdateResultCallbacks' class to create
            // instance of 'RemotePrintDocument'.
            final Class remotePrintDocumentClass =
                    classLoader.loadClass(
                            String.format(
                                    "%s.model.RemotePrintDocument", PRINT_SPOOLER_PACKAGE_NAME));
            final Class remoteAdapterDeathObserverClass =
                    classLoader.loadClass(
                            String.format(
                                    "%s$RemoteAdapterDeathObserver",
                                    remotePrintDocumentClass.getCanonicalName()));
            final Class updateResultCallbacksClass =
                    classLoader.loadClass(
                            String.format(
                                    "%s$UpdateResultCallbacks",
                                    remotePrintDocumentClass.getCanonicalName()));
            final Constructor remotePrintDocumentConstructor =
                    remotePrintDocumentClass.getDeclaredConstructor(
                            Context.class,
                            IPrintDocumentAdapter.class,
                            mutexFileProviderClass,
                            remoteAdapterDeathObserverClass,
                            updateResultCallbacksClass);
            final Object remotePrintDocument =
                    remotePrintDocumentConstructor.newInstance(
                            context,
                            new PrintDocumentAdapterDelegate(activity, null),
                            mutexFileProvider,
                            null,
                            null);

            // Invoke vulnerable method 'writeContent'.
            final StringBuilder mode = new StringBuilder();
            final Method writeContentMethod =
                    remotePrintDocumentClass.getDeclaredMethod(
                            "writeContent", ContentResolver.class, Uri.class);
            writeContentMethod.invoke(
                    remotePrintDocument,
                    getCustomContentResolver(mode),
                    Uri.parse("content://cve_2025_48562"));

            // Without fix, ContentResolver::openOutputStream() is invoked with mode="w".
            // With fix, ContentResolver::openOutputStream() is invoked with mode="wt".
            assertWithMessage(
                            "Device is vulnerable to b/423815728 !! PDF data can be leaked after"
                                    + " overwrite.")
                    .that(mode.toString())
                    .isNotEqualTo("w");
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private ContentResolver getCustomContentResolver(StringBuilder streamMode) {
        // Override ContentProvider::openAssetFile() to fetch the mode being used to get
        // OutputStream.
        return ContentResolver.wrap(
                new ContentProvider() {
                    @Override
                    public AssetFileDescriptor openAssetFile(
                            Uri uri, String mode, CancellationSignal signal)
                            throws FileNotFoundException {
                        streamMode.append(mode);
                        return super.openAssetFile(uri, mode, signal);
                    }

                    @Override
                    public boolean onCreate() {
                        return false;
                    }

                    @Override
                    public Cursor query(
                            Uri uri,
                            String[] projection,
                            String selection,
                            String[] selectionArgs,
                            String sortOrder) {
                        return null;
                    }

                    @Override
                    public String getType(Uri uri) {
                        return "";
                    }

                    @Override
                    public Uri insert(Uri uri, ContentValues values) {
                        return null;
                    }

                    @Override
                    public int delete(Uri uri, String selection, String[] selectionArgs) {
                        return 0;
                    }

                    @Override
                    public int update(
                            Uri uri,
                            ContentValues values,
                            String selection,
                            String[] selectionArgs) {
                        return 0;
                    }
                });
    }

    private Field getDeclaredField(Class cls, String fieldName) {
        for (Field field : cls.getDeclaredFields()) {
            if (field.getName().endsWith(fieldName)) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException(
                String.format("No field:%s was found in clss:%s", fieldName, cls.getName()));
    }
}
