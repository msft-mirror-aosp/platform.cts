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

package android.webkit.cts;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.webkit.WebView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** The tests for printing depend on platform APIs, which ideally they shouldn't. */
// TODO(b/435421458): expose the relevant APIs in the test SDK or rewrite the
// tests to not require them.
@MediumTest
@RunWith(AndroidJUnit4.class)
public class PrintingTest {
    private static final String PRINTER_TEST_FILE = "print.pdf";
    private static final String PDF_PREAMBLE = "%PDF-1";

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    private Context mContext;
    private WebView mWebView;
    private WebViewOnUiThread mOnUiThread;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        activity -> {
                            mContext = activity;
                            WebView webview = ((WebViewCtsActivity) activity).getWebView();
                            if (webview != null) {
                                mWebView = webview;
                                mOnUiThread = new WebViewOnUiThread(webview);
                            }
                        });
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    // Verify Print feature can create a PDF file with a correct preamble.
    @Test
    public void testPrinting() throws Throwable {
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><head></head>" + "<body>foo</body></html>", "text/html", null);
        final PrintDocumentAdapter adapter = mOnUiThread.createPrintDocumentAdapter();
        printDocumentStart(adapter);
        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("foo", "bar", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build();
        final File file = mContext.getFileStreamPath(PRINTER_TEST_FILE);
        final ParcelFileDescriptor descriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("w"));
        final SettableFuture<Void> result = SettableFuture.create();
        printDocumentLayout(
                adapter,
                null,
                attributes,
                new LayoutResultCallback() {
                    // Called on UI thread
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        PageRange[] pageRanges = new PageRange[] {PageRange.ALL_PAGES};
                        savePrintedPage(adapter, descriptor, pageRanges, result);
                    }
                });
        try {
            WebkitUtils.waitForFuture(result);
            assertThat(file.length(), greaterThan(0L));
            FileInputStream in = new FileInputStream(file);
            byte[] b = new byte[PDF_PREAMBLE.length()];
            in.read(b);
            String preamble = new String(b);
            assertEquals(PDF_PREAMBLE, preamble);
        } finally {
            // close the descriptor, if not closed already.
            descriptor.close();
            file.delete();
        }
    }

    // Verify Print feature can create a PDF file with correct number of pages.
    @Test
    public void testPrintingPagesCount() throws Throwable {
        String content = "<html><head></head><body>";
        for (int i = 0; i < 500; ++i) {
            content += "<br />abcdefghijk<br />";
        }
        content += "</body></html>";
        mOnUiThread.loadDataAndWaitForCompletion(content, "text/html", null);
        final PrintDocumentAdapter adapter = mOnUiThread.createPrintDocumentAdapter();
        printDocumentStart(adapter);
        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("foo", "bar", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build();
        final File file = mContext.getFileStreamPath(PRINTER_TEST_FILE);
        final ParcelFileDescriptor descriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("w"));
        final SettableFuture<Void> result = SettableFuture.create();
        printDocumentLayout(
                adapter,
                null,
                attributes,
                new LayoutResultCallback() {
                    // Called on UI thread
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        PageRange[] pageRanges =
                                new PageRange[] {new PageRange(1, 1), new PageRange(4, 7)};
                        savePrintedPage(adapter, descriptor, pageRanges, result);
                    }
                });
        try {
            WebkitUtils.waitForFuture(result);
            assertThat(file.length(), greaterThan(0L));
            PdfRenderer renderer =
                    new PdfRenderer(
                            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY));
            assertEquals(5, renderer.getPageCount());
        } finally {
            descriptor.close();
            file.delete();
        }
    }

    private void savePrintedPage(
            final PrintDocumentAdapter adapter,
            final ParcelFileDescriptor descriptor,
            final PageRange[] pageRanges,
            final SettableFuture<Void> result) {
        adapter.onWrite(
                pageRanges,
                descriptor,
                new CancellationSignal(),
                new WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        try {
                            descriptor.close();
                            result.set(null);
                        } catch (IOException ex) {
                            result.setException(ex);
                        }
                    }
                });
    }

    private void printDocumentStart(final PrintDocumentAdapter adapter) {
        WebkitUtils.onMainThreadSync(
                () -> {
                    adapter.onStart();
                });
    }

    private void printDocumentLayout(
            final PrintDocumentAdapter adapter,
            final PrintAttributes oldAttributes,
            final PrintAttributes newAttributes,
            final LayoutResultCallback layoutResultCallback) {
        WebkitUtils.onMainThreadSync(
                () -> {
                    adapter.onLayout(
                            oldAttributes,
                            newAttributes,
                            new CancellationSignal(),
                            layoutResultCallback,
                            null);
                });
    }
}
