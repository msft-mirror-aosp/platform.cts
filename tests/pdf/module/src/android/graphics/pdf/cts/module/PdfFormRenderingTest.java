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

package android.graphics.pdf.cts.module;

import static android.graphics.pdf.cts.module.Utils.assertScreenshotsAreEqual;
import static android.graphics.pdf.cts.module.Utils.createPreVRenderer;
import static android.graphics.pdf.cts.module.Utils.createRenderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.DrawableRes;
import androidx.annotation.RawRes;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Tests covering compat changes in {@link PdfRenderer} */
@RunWith(Parameterized.class)
public class PdfFormRenderingTest {
    private static final String LOCAL_DIRECTORY =
            Environment.getExternalStorageDirectory() + "/PdfFormRenderingTest";
    private static final int CLICK_FORM = R.raw.click_form;
    private static final int COMBOBOX_FORM = R.raw.combobox_form;
    private static final int LISTBOX_FORM = R.raw.listbox_form;
    private static final int TEXT_FORM = R.raw.text_form;

    private static final int CLICK_FORM_GOLDEN = R.drawable.click_form_golden;
    private static final int COMBOBOX_FORM_GOLDEN = R.drawable.combobox_form_golden;
    private static final int LISTBOX_FORM_GOLDEN = R.drawable.listbox_form_golden;
    private static final int TEXT_FORM_GOLDEN = R.drawable.text_form_golden;

    private static final int CLICK_FORM_GOLDEN_NOFORM = R.drawable.click_noform_golden;
    private static final int COMBOBOX_FORM_GOLDEN_NOFORM = R.drawable.combobox_noform_golden;
    private static final int LISTBOX_FORM_GOLDEN_NOFORM = R.drawable.listbox_noform_golden;
    private static final int TEXT_FORM_GOLDEN_NO_FORM = R.drawable.text_noform_golden;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final int mPdfRes;
    private final int mGoldenRes;
    private final int mGoldenResNoForm;
    private final String mPdfName;

    public PdfFormRenderingTest(
            @RawRes int pdfRes,
            @DrawableRes int goldenRes,
            @DrawableRes int goldenResNoForm,
            String pdfName) {
        mPdfRes = pdfRes;
        mGoldenRes = goldenRes;
        mGoldenResNoForm = goldenResNoForm;
        mPdfName = pdfName;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        List<Object[]> parameters = new ArrayList<>();
        parameters.add(
                new Object[] {
                    CLICK_FORM, CLICK_FORM_GOLDEN, CLICK_FORM_GOLDEN_NOFORM, "click_form"
                });
        parameters.add(
                new Object[] {
                    COMBOBOX_FORM,
                    COMBOBOX_FORM_GOLDEN,
                    COMBOBOX_FORM_GOLDEN_NOFORM,
                    "combobox_form"
                });
        parameters.add(
                new Object[] {
                    LISTBOX_FORM, LISTBOX_FORM_GOLDEN, LISTBOX_FORM_GOLDEN_NOFORM, "listbox_form"
                });
        parameters.add(
                new Object[] {TEXT_FORM, TEXT_FORM_GOLDEN, TEXT_FORM_GOLDEN_NO_FORM, "text_form"});
        return parameters;
    }

    @Test
    @SdkSuppress(
            minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    public void renderFormContentWhenEnabled() throws Exception {
        RenderParams includeFormContent =
                new RenderParams.Builder(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        .setRenderFormContentMode(RenderParams.RENDER_FORM_CONTENT_ENABLED)
                        .build();

        renderAndCompare(mPdfName + "-form", mGoldenRes, includeFormContent);
    }

    @Test
    public void renderFormContentWhenEnabledPreV() throws Exception {
        RenderParams includeFormContent =
                new RenderParams.Builder(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        .setRenderFormContentMode(RenderParams.RENDER_FORM_CONTENT_ENABLED)
                        .build();

        renderAndComparePreV(mPdfName + "-pv-form", mGoldenRes, includeFormContent);
    }

    @Test
    @SdkSuppress(
            minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    public void doNotRenderFormContentWhenDisabled() throws Exception {
        RenderParams excludeFormContent =
                new RenderParams.Builder(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        .setRenderFormContentMode(RenderParams.RENDER_FORM_CONTENT_DISABLED)
                        .build();

        renderAndCompare(mPdfName + "-noform", mGoldenResNoForm, excludeFormContent);
    }

    @Test
    public void doNotRenderFormContentWhenDisabledPreV() throws Exception {
        RenderParams excludeFormContent =
                new RenderParams.Builder(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        .setRenderFormContentMode(RenderParams.RENDER_FORM_CONTENT_DISABLED)
                        .build();

        renderAndComparePreV(mPdfName + "-pv-noform", mGoldenResNoForm, excludeFormContent);
    }

    private void renderAndCompare(String testName, int goldenRes, RenderParams renderParams)
            throws IOException {
        try (PdfRenderer renderer = createRenderer(mPdfRes, mContext)) {
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                Bitmap golden =
                        BitmapFactory.decodeResource(mContext.getResources(), goldenRes, options);
                Bitmap output =
                        Bitmap.createBitmap(
                                page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(output, null, null, renderParams);
                assertScreenshotsAreEqual(golden, output, testName, LOCAL_DIRECTORY);
            }
        }
    }

    private void renderAndComparePreV(String testName, int goldenRes, RenderParams renderParams)
            throws IOException {
        try (PdfRendererPreV renderer =
                createPreVRenderer(mPdfRes, mContext, /* loadPdfParams= */ null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(0)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                Bitmap golden =
                        BitmapFactory.decodeResource(mContext.getResources(), goldenRes, options);
                Bitmap output =
                        Bitmap.createBitmap(
                                page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(output, null, null, renderParams);
                assertScreenshotsAreEqual(golden, output, testName, LOCAL_DIRECTORY);
            }
        }
    }
}
