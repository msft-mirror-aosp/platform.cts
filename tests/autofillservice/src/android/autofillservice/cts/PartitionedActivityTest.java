/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.autofillservice.cts;

import static android.autofillservice.cts.GridActivity.ID_L1C1;
import static android.autofillservice.cts.GridActivity.ID_L1C2;
import static android.autofillservice.cts.GridActivity.ID_L2C1;
import static android.autofillservice.cts.GridActivity.ID_L2C2;
import static android.autofillservice.cts.GridActivity.ID_L3C1;
import static android.autofillservice.cts.GridActivity.ID_L3C2;
import static android.autofillservice.cts.GridActivity.ID_L4C1;
import static android.autofillservice.cts.GridActivity.ID_L4C2;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertValue;
import static android.service.autofill.FillRequest.FLAG_MANUAL_REQUEST;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_USERNAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.GridActivity.FillExpectation;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.os.Bundle;
import android.support.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for an activity containing multiple partitions.
 */
public class PartitionedActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<GridActivity> mActivityRule =
        new ActivityTestRule<GridActivity>(GridActivity.class);

    private GridActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testAutofillTwoPartitionsSkipFirst() throws Exception {
        // Set service.
        enableService();

        // Prepare 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .build();
        sReplier.addResponse(response1);

        // Trigger auto-fill on 1st partition.
        mActivity.focusCell(1, 1);
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(0);
        final ViewNode p1l1c1 = assertTextIsSanitized(fillRequest1.structure, ID_L1C1);
        final ViewNode p1l1c2 = assertTextIsSanitized(fillRequest1.structure, ID_L1C2);
        assertWithMessage("Focus on p1l1c1").that(p1l1c1.isFocused()).isTrue();
        assertWithMessage("Focus on p1l1c2").that(p1l1c2.isFocused()).isFalse();

        // Make sure UI is shown, but don't tap it.
        sUiBot.assertDatasets("l1c1");
        mActivity.focusCell(1, 2);
        sUiBot.assertDatasets("l1c2");

        // Now tap a field in a different partition
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .build();
        sReplier.addResponse(response2);

        // Trigger auto-fill on 2nd partition.
        mActivity.focusCell(2, 1);
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.flags).isEqualTo(0);
        final ViewNode p2l1c1 = assertTextIsSanitized(fillRequest2.structure, ID_L1C1);
        final ViewNode p2l1c2 = assertTextIsSanitized(fillRequest2.structure, ID_L1C2);
        final ViewNode p2l2c1 = assertTextIsSanitized(fillRequest2.structure, ID_L2C1);
        final ViewNode p2l2c2 = assertTextIsSanitized(fillRequest2.structure, ID_L2C2);
        assertWithMessage("Focus on p2l1c1").that(p2l1c1.isFocused()).isFalse();
        assertWithMessage("Focus on p2l1c2").that(p2l1c2.isFocused()).isFalse();
        assertWithMessage("Focus on p2l2c1").that(p2l2c1.isFocused()).isTrue();
        assertWithMessage("Focus on p2l2c2").that(p2l2c2.isFocused()).isFalse();
        // Make sure UI is shown, but don't tap it.
        sUiBot.assertDatasets("l2c1");
        mActivity.focusCell(2, 2);
        sUiBot.assertDatasets("l2c2");

        // Now fill them
        final FillExpectation expectation1 = mActivity.expectAutofill()
              .onCell(1, 1, "l1c1")
              .onCell(1, 2, "l1c2");
        mActivity.focusCell(1, 1);
        sUiBot.selectDataset("l1c1");
        expectation1.assertAutoFilled();

        // Change previous values to make sure they are not filled again
        mActivity.setText(1, 1, "L1C1");
        mActivity.setText(1, 2, "L1C2");

        final FillExpectation expectation2 = mActivity.expectAutofill()
                .onCell(2, 1, "l2c1")
                .onCell(2, 2, "l2c2");
        mActivity.focusCell(2, 2);
        sUiBot.selectDataset("l2c2");
        expectation2.assertAutoFilled();

        // Make sure previous partition didn't change
        assertThat(mActivity.getText(1, 1)).isEqualTo("L1C1");
        assertThat(mActivity.getText(1, 2)).isEqualTo("L1C2");
    }

    @Test
    public void testAutofillTwoPartitionsInSequence() throws Exception {
        // Set service.
        enableService();

        // 1st partition
        // Prepare.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 1"))
                        .setField(ID_L1C1, "l1c1")
                        .setField(ID_L1C2, "l1c2")
                        .build())
                .build();
        sReplier.addResponse(response1);
        final FillExpectation expectation1 = mActivity.expectAutofill()
                .onCell(1, 1, "l1c1")
                .onCell(1, 2, "l1c2");

        // Trigger auto-fill.
        mActivity.focusCell(1, 1);
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(0);

        assertTextIsSanitized(fillRequest1.structure, ID_L1C1);
        assertTextIsSanitized(fillRequest1.structure, ID_L1C2);

        // Auto-fill it.
        sUiBot.selectDataset("Partition 1");

        // Check the results.
        expectation1.assertAutoFilled();

        // 2nd partition
        // Prepare.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 2"))
                        .setField(ID_L2C1, "l2c1")
                        .setField(ID_L2C2, "l2c2")
                        .build())
                .build();
        sReplier.addResponse(response2);
        final FillExpectation expectation2 = mActivity.expectAutofill()
                .onCell(2, 1, "l2c1")
                .onCell(2, 2, "l2c2");

        // Trigger auto-fill.
        mActivity.focusCell(2, 1);
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.flags).isEqualTo(0);

        assertValue(fillRequest2.structure, ID_L1C1, "l1c1");
        assertValue(fillRequest2.structure, ID_L1C2, "l1c2");
        assertTextIsSanitized(fillRequest2.structure, ID_L2C1);
        assertTextIsSanitized(fillRequest2.structure, ID_L2C2);

        // Auto-fill it.
        sUiBot.selectDataset("Partition 2");

        // Check the results.
        expectation2.assertAutoFilled();
    }

    @Test
    public void testAutofill4PartitionsAutomatically() throws Exception {
        autofill4PartitionsTest(false);
    }

    @Test
    public void testAutofill4PartitionsManually() throws Exception {
        autofill4PartitionsTest(true);
    }

    private void autofill4PartitionsTest(boolean manually) throws Exception {
        final int expectedFlag = manually ? FLAG_MANUAL_REQUEST : 0;

        // Set service.
        enableService();

        // 1st partition
        // Prepare.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 1"))
                        .setField(ID_L1C1, "l1c1")
                        .setField(ID_L1C2, "l1c2")
                        .build())
                .build();
        sReplier.addResponse(response1);
        final FillExpectation expectation1 = mActivity.expectAutofill()
                .onCell(1, 1, "l1c1")
                .onCell(1, 2, "l1c2");

        // Trigger auto-fill.
        mActivity.triggerAutofill(manually, 1, 1);
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(expectedFlag);

        assertTextIsSanitized(fillRequest1.structure, ID_L1C1);
        assertTextIsSanitized(fillRequest1.structure, ID_L1C2);

        // Auto-fill it.
        if (!manually) {
            sUiBot.selectDataset("Partition 1");
        }

        // Check the results.
        expectation1.assertAutoFilled();

        // 2nd partition
        // Prepare.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 2"))
                        .setField(ID_L2C1, "l2c1")
                        .setField(ID_L2C2, "l2c2")
                        .build())
                .build();
        sReplier.addResponse(response2);
        final FillExpectation expectation2 = mActivity.expectAutofill()
                .onCell(2, 1, "l2c1")
                .onCell(2, 2, "l2c2");

        // Trigger auto-fill.
        mActivity.triggerAutofill(manually, 2, 1);
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.flags).isEqualTo(expectedFlag);

        assertValue(fillRequest2.structure, ID_L1C1, "l1c1");
        assertValue(fillRequest2.structure, ID_L1C2, "l1c2");
        assertTextIsSanitized(fillRequest2.structure, ID_L2C1);
        assertTextIsSanitized(fillRequest2.structure, ID_L2C2);

        // Auto-fill it.
        if (!manually) {
            sUiBot.selectDataset("Partition 2");
        }

        // Check the results.
        expectation2.assertAutoFilled();

        // 3rd partition
        // Prepare.
        final CannedFillResponse response3 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 3"))
                        .setField(ID_L3C1, "l3c1")
                        .setField(ID_L3C2, "l3c2")
                        .build())
                .build();
        sReplier.addResponse(response3);
        final FillExpectation expectation3 = mActivity.expectAutofill()
                .onCell(3, 1, "l3c1")
                .onCell(3, 2, "l3c2");

        // Trigger auto-fill.
        mActivity.triggerAutofill(manually, 3, 1);
        final FillRequest fillRequest3 = sReplier.getNextFillRequest();
        assertThat(fillRequest3.flags).isEqualTo(expectedFlag);

        assertValue(fillRequest3.structure, ID_L1C1, "l1c1");
        assertValue(fillRequest3.structure, ID_L1C2, "l1c2");
        assertValue(fillRequest3.structure, ID_L2C1, "l2c1");
        assertValue(fillRequest3.structure, ID_L2C2, "l2c2");
        assertTextIsSanitized(fillRequest3.structure, ID_L3C1);
        assertTextIsSanitized(fillRequest3.structure, ID_L3C2);

        // Auto-fill it.
        if (!manually) {
            sUiBot.selectDataset("Partition 3");
        }

        // Check the results.
        expectation3.assertAutoFilled();

        // 4th partition
        // Prepare.
        final CannedFillResponse response4 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 4"))
                        .setField(ID_L4C1, "l4c1")
                        .setField(ID_L4C2, "l4c2")
                        .build())
                .build();
        sReplier.addResponse(response4);
        final FillExpectation expectation4 = mActivity.expectAutofill()
                .onCell(4, 1, "l4c1")
                .onCell(4, 2, "l4c2");

        // Trigger auto-fill.
        mActivity.triggerAutofill(manually, 4, 1);
        final FillRequest fillRequest4 = sReplier.getNextFillRequest();
        assertThat(fillRequest4.flags).isEqualTo(expectedFlag);

        assertValue(fillRequest4.structure, ID_L1C1, "l1c1");
        assertValue(fillRequest4.structure, ID_L1C2, "l1c2");
        assertValue(fillRequest4.structure, ID_L2C1, "l2c1");
        assertValue(fillRequest4.structure, ID_L2C2, "l2c2");
        assertValue(fillRequest4.structure, ID_L3C1, "l3c1");
        assertValue(fillRequest4.structure, ID_L3C2, "l3c2");
        assertTextIsSanitized(fillRequest4.structure, ID_L4C1);
        assertTextIsSanitized(fillRequest4.structure, ID_L4C2);

        // Auto-fill it.
        if (!manually) {
            sUiBot.selectDataset("Partition 4");
        }

        // Check the results.
        expectation4.assertAutoFilled();
    }

    @Test
    public void testAutofill4PartitionsMixManualAndAuto() throws Exception {
        // Set service.
        enableService();

        // 1st partition - auto
        // Prepare.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 1"))
                        .setField(ID_L1C1, "l1c1")
                        .setField(ID_L1C2, "l1c2")
                        .build())
                .build();
        sReplier.addResponse(response1);
        final FillExpectation expectation1 = mActivity.expectAutofill()
                .onCell(1, 1, "l1c1")
                .onCell(1, 2, "l1c2");

        // Trigger auto-fill.
        mActivity.focusCell(1, 1);
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(0);

        assertTextIsSanitized(fillRequest1.structure, ID_L1C1);
        assertTextIsSanitized(fillRequest1.structure, ID_L1C2);

        // Auto-fill it.
        sUiBot.selectDataset("Partition 1");

        // Check the results.
        expectation1.assertAutoFilled();

        // 2nd partition - manual
        // Prepare.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 2"))
                        .setField(ID_L2C1, "l2c1")
                        .setField(ID_L2C2, "l2c2")
                        .build())
                .build();
        sReplier.addResponse(response2);
        final FillExpectation expectation2 = mActivity.expectAutofill()
                .onCell(2, 1, "l2c1")
                .onCell(2, 2, "l2c2");

        // Trigger auto-fill.
        mActivity.forceAutofill(2, 1);
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        assertValue(fillRequest2.structure, ID_L1C1, "l1c1");
        assertValue(fillRequest2.structure, ID_L1C2, "l1c2");
        assertTextIsSanitized(fillRequest2.structure, ID_L2C1);
        assertTextIsSanitized(fillRequest2.structure, ID_L2C2);

        // Check the results.
        expectation2.assertAutoFilled();

        // 3rd partition - auto
        // Prepare.
        final CannedFillResponse response3 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 3"))
                        .setField(ID_L3C1, "l3c1")
                        .setField(ID_L3C2, "l3c2")
                        .build())
                .build();
        sReplier.addResponse(response3);
        final FillExpectation expectation3 = mActivity.expectAutofill()
                .onCell(3, 1, "l3c1")
                .onCell(3, 2, "l3c2");

        // Trigger auto-fill.
        mActivity.focusCell(3, 1);
        final FillRequest fillRequest3 = sReplier.getNextFillRequest();
        assertThat(fillRequest3.flags).isEqualTo(0);

        assertValue(fillRequest3.structure, ID_L1C1, "l1c1");
        assertValue(fillRequest3.structure, ID_L1C2, "l1c2");
        assertValue(fillRequest3.structure, ID_L2C1, "l2c1");
        assertValue(fillRequest3.structure, ID_L2C2, "l2c2");
        assertTextIsSanitized(fillRequest3.structure, ID_L3C1);
        assertTextIsSanitized(fillRequest3.structure, ID_L3C2);

        // Auto-fill it.
        sUiBot.selectDataset("Partition 3");

        // Check the results.
        expectation3.assertAutoFilled();

        // 4th partition - manual
        // Prepare.
        final CannedFillResponse response4 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("Partition 4"))
                        .setField(ID_L4C1, "l4c1")
                        .setField(ID_L4C2, "l4c2")
                        .build())
                .build();
        sReplier.addResponse(response4);
        final FillExpectation expectation4 = mActivity.expectAutofill()
                .onCell(4, 1, "l4c1")
                .onCell(4, 2, "l4c2");

        // Trigger auto-fill.
        mActivity.forceAutofill(4, 1);
        final FillRequest fillRequest4 = sReplier.getNextFillRequest();
        assertThat(fillRequest4.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        assertValue(fillRequest4.structure, ID_L1C1, "l1c1");
        assertValue(fillRequest4.structure, ID_L1C2, "l1c2");
        assertValue(fillRequest4.structure, ID_L2C1, "l2c1");
        assertValue(fillRequest4.structure, ID_L2C2, "l2c2");
        assertValue(fillRequest4.structure, ID_L3C1, "l3c1");
        assertValue(fillRequest4.structure, ID_L3C2, "l3c2");
        assertTextIsSanitized(fillRequest4.structure, ID_L4C1);
        assertTextIsSanitized(fillRequest4.structure, ID_L4C2);

        // Check the results.
        expectation4.assertAutoFilled();
    }

    @Test
    public void testAutofillBundleDataIsPassedAlong() throws Exception {
        // Set service.
        enableService();

        final Bundle extras = new Bundle();
        extras.putString("numbers", "4");

        // Prepare 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .setExtras(extras)
                .build();
        sReplier.addResponse(response1);

        // Trigger auto-fill on 1st partition.
        mActivity.focusCell(1, 1);
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(0);
        assertThat(fillRequest1.data).isNull();
        sUiBot.assertDatasets("l1c1");

        // Prepare 2nd partition; it replaces 'number' and adds 'numbers2'
        extras.clear();
        extras.putString("numbers", "48");
        extras.putString("numbers2", "1516");

        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .setExtras(extras)
                .build();
        sReplier.addResponse(response2);

        // Trigger auto-fill on 2nd partition
        mActivity.focusCell(2, 1);
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.flags).isEqualTo(0);
        assertWithMessage("null bundle on request 2").that(fillRequest2.data).isNotNull();
        assertWithMessage("wrong number of extras on request 2 bundle")
                .that(fillRequest2.data.size()).isEqualTo(1);
        assertThat(fillRequest2.data.getString("numbers")).isEqualTo("4");

        // Prepare 3nd partition; it has no extras
        final CannedFillResponse response3 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L3C1, "l3c1", createPresentation("l3c1"))
                        .setField(ID_L3C2, "l3c2", createPresentation("l3c2"))
                        .build())
                .setExtras(null)
                .build();
        sReplier.addResponse(response3);

        // Trigger auto-fill on 3rd partition
        mActivity.focusCell(3, 1);
        final FillRequest fillRequest3 = sReplier.getNextFillRequest();
        assertThat(fillRequest3.flags).isEqualTo(0);
        assertWithMessage("null bundle on request 3").that(fillRequest2.data).isNotNull();
        assertWithMessage("wrong number of extras on request 3 bundle")
                .that(fillRequest3.data.size()).isEqualTo(2);
        assertThat(fillRequest3.data.getString("numbers")).isEqualTo("48");
        assertThat(fillRequest3.data.getString("numbers2")).isEqualTo("1516");


        // Prepare 4th partition; it contains just 'numbers4'
        extras.clear();
        extras.putString("numbers4", "2342");

        final CannedFillResponse response4 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L4C1, "l4c1", createPresentation("l4c1"))
                        .setField(ID_L4C2, "l4c2", createPresentation("l4c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_L1C1)
                .setExtras(extras)
                .build();
        sReplier.addResponse(response4);

        // Trigger auto-fill on 4th partition
        mActivity.focusCell(4, 1);
        final FillRequest fillRequest4 = sReplier.getNextFillRequest();
        assertThat(fillRequest4.flags).isEqualTo(0);
        assertWithMessage("non-null bundle on request 4").that(fillRequest4.data).isNull();

        // Trigger save
        mActivity.setText(1, 1, "L1C1");
        mActivity.save();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        assertWithMessage("wrong number of extras on save request bundle")
                .that(saveRequest.data.size()).isEqualTo(1);
        assertThat(saveRequest.data.getString("numbers4")).isEqualTo("2342");
    }

    @Test
    public void testSaveOneSaveInfoOnFirstPartitionWithIdsOnSecond() throws Exception {
        // Set service.
        enableService();

        // Trigger 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .build();
        sReplier.addResponse(response1);
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        // Trigger 2nd partition.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_L2C1)
                .build();
        sReplier.addResponse(response2);
        mActivity.focusCell(2, 1);
        sReplier.getNextFillRequest();

        // Trigger save
        mActivity.setText(2, 1, "L2C1");
        mActivity.save();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertValue(saveRequest.structure, ID_L2C1, "L2C1");
    }

    @Test
    public void testSaveOneSaveInfoOnSecondPartitionWithIdsOnFirst() throws Exception {
        // Set service.
        enableService();

        // Trigger 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .build();
        sReplier.addResponse(response1);
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        // Trigger 2nd partition.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_L1C1)
                .build();
        sReplier.addResponse(response2);
        mActivity.focusCell(2, 1);
        sReplier.getNextFillRequest();

        // Trigger save
        mActivity.setText(1, 1, "L1C1");
        mActivity.save();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertValue(saveRequest.structure, ID_L1C1, "L1C1");
    }

    @Test
    public void testSaveTwoSaveInfosDifferentTypes() throws Exception {
        // Set service.
        enableService();

        // Trigger 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_L1C1)
                .build();
        sReplier.addResponse(response1);
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        // Trigger 2nd partition.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_CREDIT_CARD,
                        ID_L2C1)
                .build();
        sReplier.addResponse(response2);
        mActivity.focusCell(2, 1);
        sReplier.getNextFillRequest();

        // Trigger save
        mActivity.setText(1, 1, "L1C1");
        mActivity.setText(2, 1, "L2C1");
        mActivity.save();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD, SAVE_DATA_TYPE_CREDIT_CARD);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertValue(saveRequest.structure, ID_L1C1, "L1C1");
        assertValue(saveRequest.structure, ID_L2C1, "L2C1");
    }

    @Test
    public void testSaveThreeSaveInfosDifferentTypes() throws Exception {
        // Set service.
        enableService();

        // Trigger 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_L1C1)
                .build();
        sReplier.addResponse(response1);
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        // Trigger 2nd partition.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_CREDIT_CARD,
                        ID_L2C1)
                .build();
        sReplier.addResponse(response2);
        mActivity.focusCell(2, 1);
        sReplier.getNextFillRequest();

        // Trigger 3rd partition.
        final CannedFillResponse response3 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L3C1, "l3c1", createPresentation("l3c1"))
                        .setField(ID_L3C2, "l3c2", createPresentation("l3c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_CREDIT_CARD
                        | SAVE_DATA_TYPE_USERNAME, ID_L3C1)
                .build();
        sReplier.addResponse(response3);
        mActivity.focusCell(3, 1);
        sReplier.getNextFillRequest();

        // Trigger save
        mActivity.setText(1, 1, "L1C1");
        mActivity.setText(2, 1, "L2C1");
        mActivity.setText(3, 1, "L3C1");
        mActivity.save();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD, SAVE_DATA_TYPE_CREDIT_CARD,
                SAVE_DATA_TYPE_USERNAME);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertValue(saveRequest.structure, ID_L1C1, "L1C1");
        assertValue(saveRequest.structure, ID_L2C1, "L2C1");
        assertValue(saveRequest.structure, ID_L3C1, "L3C1");
    }

    @Test
    public void testSaveThreeSaveInfosDifferentTypesIncludingGeneric() throws Exception {
        // Set service.
        enableService();

        // Trigger 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_L1C1)
                .build();
        sReplier.addResponse(response1);
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        // Trigger 2nd partition.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_GENERIC, ID_L2C1)
                .build();
        sReplier.addResponse(response2);
        mActivity.focusCell(2, 1);
        sReplier.getNextFillRequest();

        // Trigger 3rd partition.
        final CannedFillResponse response3 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L3C1, "l3c1", createPresentation("l3c1"))
                        .setField(ID_L3C2, "l3c2", createPresentation("l3c2"))
                        .build())
                .setRequiredSavableIds(
                        SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_GENERIC | SAVE_DATA_TYPE_USERNAME,
                        ID_L3C1)
                .build();
        sReplier.addResponse(response3);
        mActivity.focusCell(3, 1);
        sReplier.getNextFillRequest();


        // Trigger save
        mActivity.setText(1, 1, "L1C1");
        mActivity.setText(2, 1, "L2C1");
        mActivity.setText(3, 1, "L3C1");
        mActivity.save();

        // Make sure GENERIC type is not shown on snackbar
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD, SAVE_DATA_TYPE_USERNAME);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertValue(saveRequest.structure, ID_L1C1, "L1C1");
        assertValue(saveRequest.structure, ID_L2C1, "L2C1");
        assertValue(saveRequest.structure, ID_L3C1, "L3C1");
    }

    @Test
    public void testSaveMoreThanThreeSaveInfosDifferentTypes() throws Exception {
        // Set service.
        enableService();

        // Trigger 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_L1C1)
                .build();
        sReplier.addResponse(response1);
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        // Trigger 2nd partition.
        final CannedFillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L2C1, "l2c1", createPresentation("l2c1"))
                        .setField(ID_L2C2, "l2c2", createPresentation("l2c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_CREDIT_CARD,
                        ID_L2C1)
                .build();
        sReplier.addResponse(response2);
        mActivity.focusCell(2, 1);
        sReplier.getNextFillRequest();

        // Trigger 3rd partition.
        final CannedFillResponse response3 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L3C1, "l3c1", createPresentation("l3c1"))
                        .setField(ID_L3C2, "l3c2", createPresentation("l3c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_CREDIT_CARD
                        | SAVE_DATA_TYPE_USERNAME, ID_L3C1)
                .build();
        sReplier.addResponse(response3);
        mActivity.focusCell(3, 1);
        sReplier.getNextFillRequest();

        // Trigger 4th partition.
        final CannedFillResponse response4 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L4C1, "l4c1", createPresentation("l4c1"))
                        .setField(ID_L4C2, "l4c2", createPresentation("l4c2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD | SAVE_DATA_TYPE_CREDIT_CARD
                        | SAVE_DATA_TYPE_USERNAME | SAVE_DATA_TYPE_ADDRESS, ID_L4C1)
                .build();
        sReplier.addResponse(response4);
        mActivity.focusCell(4, 1);
        sReplier.getNextFillRequest();


        // Trigger save
        mActivity.setText(1, 1, "L1C1");
        mActivity.setText(2, 1, "L2C1");
        mActivity.setText(3, 1, "L3C1");
        mActivity.setText(4, 1, "L4C1");
        mActivity.save();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertValue(saveRequest.structure, ID_L1C1, "L1C1");
        assertValue(saveRequest.structure, ID_L2C1, "L2C1");
        assertValue(saveRequest.structure, ID_L3C1, "L3C1");
        assertValue(saveRequest.structure, ID_L4C1, "L4C1");
    }

    @Test
    public void testIgnoredFieldsDontTriggerAutofill() throws Exception {
        // Set service.
        enableService();

        // Prepare 1st partition.
        final CannedFillResponse response1 = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_L1C1, "l1c1", createPresentation("l1c1"))
                        .setField(ID_L1C2, "l1c2", createPresentation("l1c2"))
                        .build())
                .setIgnoreFields(ID_L2C1, ID_L2C2)
                .build();
        sReplier.addResponse(response1);

        // Trigger auto-fill on 1st partition.
        mActivity.focusCell(1, 1);
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(0);
        final ViewNode p1l1c1 = assertTextIsSanitized(fillRequest1.structure, ID_L1C1);
        final ViewNode p1l1c2 = assertTextIsSanitized(fillRequest1.structure, ID_L1C2);
        assertWithMessage("Focus on p1l1c1").that(p1l1c1.isFocused()).isTrue();
        assertWithMessage("Focus on p1l1c2").that(p1l1c2.isFocused()).isFalse();

        // Make sure UI is shown on 1st partition
        sUiBot.assertDatasets("l1c1");
        mActivity.focusCell(1, 2);
        sUiBot.assertDatasets("l1c2");

        // Make sure UI is not shown on ignored partition
        mActivity.focusCell(2, 1);
        sUiBot.assertNoDatasets();
        mActivity.focusCell(2, 2);
        sUiBot.assertNoDatasets();
    }
}
