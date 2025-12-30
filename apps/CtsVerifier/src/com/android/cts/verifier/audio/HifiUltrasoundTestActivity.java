/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.DynamicTableModel;
import com.androidplot.ui.XLayoutStyle;
import com.androidplot.ui.YLayoutStyle;
import com.androidplot.ui.widget.TextLabelWidget;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYLegendWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;

import java.util.Arrays;

public class HifiUltrasoundTestActivity extends PassFailButtons.Activity {

  public enum Status {
    START, RECORDING, DONE, PLAYER
  }

  private static final String TAG = "HifiUltrasoundTestActivity";

  private Status status = Status.START;
  private boolean onPlotScreen = false;
  private TextView info;
  private Button playerButton;
  private Button recorderButton;
  private AudioTrack audioTrack;
  private LayoutInflater layoutInflater;
  private View popupView;
  private PopupWindow popupWindow;
  private boolean micSupport = true;
  private boolean spkrSupport = true;

  @Override
  public void onBackPressed () {
    if (onPlotScreen) {
      popupWindow.dismiss();
      onPlotScreen = false;
      recorderButton.setEnabled(true);
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (audioTrack != null) {
      audioTrack.stop();
      audioTrack.release();
      audioTrack = null;
    }
  }

  boolean getBoolPropValue(final String value) {
    if (value == null) {
      return false;
    }

    return !value.equalsIgnoreCase(getResources().getString(
        R.string.hifi_ultrasound_test_default_false_string));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.hifi_ultrasound);
    setInfoResources(R.string.hifi_ultrasound_test, R.string.hifi_ultrasound_test_info, -1);
    setPassFailButtonClickListeners();
    getPassButton().setEnabled(false);

    info = (TextView) findViewById(R.id.info_text);
    info.setMovementMethod(new ScrollingMovementMethod());
    info.setText(R.string.hifi_ultrasound_test_instruction1);

    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    micSupport = getBoolPropValue(audioManager.getProperty(
        AudioManager.PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND));
    spkrSupport = getBoolPropValue(audioManager.getProperty(
        AudioManager.PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND));
    Log.d(TAG, "PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND = " + micSupport);
    Log.d(TAG, "PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND = " + spkrSupport);

    if (!micSupport) {
      getPassButton().setEnabled(true);
      getPassButton().performClick();
      info.append(getResources().getString(R.string.hifi_ultrasound_test_mic_no_support));
    }
    if (!spkrSupport) {
      info.append(getResources().getString(R.string.hifi_ultrasound_test_spkr_no_support));
    }

    layoutInflater = (LayoutInflater) getBaseContext().getSystemService(
        LAYOUT_INFLATER_SERVICE);
    popupView = layoutInflater.inflate(R.layout.hifi_ultrasound_popup, null);
    popupWindow = new PopupWindow(
        popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

    final AudioRecordHelper audioRecorder = AudioRecordHelper.getInstance();
    final int recordRate = audioRecorder.getSampleRate();

    recorderButton = (Button) findViewById(R.id.recorder_button);
    recorderButton.setEnabled(micSupport);
    recorderButton.setOnClickListener(new View.OnClickListener() {
      private WavAnalyzerTask wavAnalyzerTask = null;
      private void stopRecording() {
        audioRecorder.stop();
        wavAnalyzerTask = new WavAnalyzerTask(audioRecorder.getByte());
        wavAnalyzerTask.execute();
        status = Status.DONE;
      }
      @Override
      public void onClick(View v) {
        switch (status) {
          case START:
            info.append("Recording at " + recordRate + "Hz using ");
            final int source = audioRecorder.getAudioSource();
            switch (source) {
              case 1:
                info.append("MIC");
                break;
              case 6:
                info.append("VOICE_RECOGNITION");
                break;
              default:
                info.append("UNEXPECTED " + source);
                break;
            }
            info.append("\n");
            status = Status.RECORDING;
            playerButton.setEnabled(false);
            recorderButton.setEnabled(false);
            audioRecorder.start();

            final View finalV = v;
            new Thread() {
              @Override
              public void run() {
                Double recordingDuration_millis = new Double(1000 * (2.5
                    + Common.PREFIX_LENGTH_S
                    + Common.PAUSE_BEFORE_PREFIX_DURATION_S
                    + Common.PAUSE_AFTER_PREFIX_DURATION_S
                    + Common.PIP_NUM * (Common.PIP_DURATION_S + Common.PAUSE_DURATION_S)
                    * Common.REPETITIONS));
                Log.d(TAG, "Recording for " + recordingDuration_millis + "ms");
                try {
                  Thread.sleep(recordingDuration_millis.intValue());
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    stopRecording();
                  }
                });
              }
            }.start();

            break;

          case DONE:
            plotResponse(wavAnalyzerTask);
            break;

          default: break;
        }
      }
    });

    playerButton = (Button) findViewById(R.id.player_button);
    playerButton.setEnabled(spkrSupport);
    playerButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        recorderButton.setEnabled(false);
        status = Status.PLAYER;
        play();
      }
    });
  }

  private void plotResponse(WavAnalyzerTask wavAnalyzerTask) {
    Button dismissButton = (Button)popupView.findViewById(R.id.dismiss);
    dismissButton.setOnClickListener(new Button.OnClickListener(){
      @Override
      public void onClick(View v) {
        popupWindow.dismiss();
        onPlotScreen = false;
        recorderButton.setEnabled(true);
      }});
    popupWindow.showAtLocation(info, Gravity.CENTER, 0, 0);
    onPlotScreen = true;

    recorderButton.setEnabled(false);

    XYPlot plot = (XYPlot) popupView.findViewById(R.id.responseChart);
    plot.clear();
    // Write the domain step every 6000 Hz
    plot.setDomainStep(XYStepMode.INCREMENT_BY_VAL, 6000);
    XYLegendWidget legendWidget = plot.getLegendWidget();
    // Stack the legend vertically
    legendWidget.setTableModel(new DynamicTableModel(1 /* numColumns */, 0 /* numRows */));
    TextLabelWidget domainLabelWidget = plot.getDomainLabelWidget();
    domainLabelWidget.position(
        PixelUtils.dpToPix(0),
        XLayoutStyle.ABSOLUTE_FROM_CENTER,
        PixelUtils.dpToPix(150), // Absolute position from bottom
        YLayoutStyle.ABSOLUTE_FROM_BOTTOM,
        AnchorPosition.BOTTOM_MIDDLE);

    Double[] frequencies = new Double[Common.PIP_NUM];
    for (int i = 0; i < Common.PIP_NUM; i++) {
      frequencies[i] = new Double(Common.FREQUENCIES_ORIGINAL[i]);
    }

    Paint transparentFillPaint = new Paint();
    transparentFillPaint.setColor(Color.TRANSPARENT);
    transparentFillPaint.setStyle(Paint.Style.FILL);

    if (wavAnalyzerTask != null && wavAnalyzerTask.getPower() != null &&
        wavAnalyzerTask.getNoiseDB() != null && wavAnalyzerTask.getDB() != null) {

      double[][] power = wavAnalyzerTask.getPower();
      for(int i = 0; i < Common.REPETITIONS; i++) {
        Double[] powerWrap = new Double[Common.PIP_NUM];
        for (int j = 0; j < Common.PIP_NUM; j++) {
          powerWrap[j] = new Double(10 * Math.log10(power[j][i]));
        }
        XYSeries series = new SimpleXYSeries(
            Arrays.asList(frequencies),
            Arrays.asList(powerWrap),
            "repetition: " + (i + 1));
        // Use colors around the color wheel
        float hue = (360.0f / Common.REPETITIONS) * i;
        // Use a low saturation and a high value so the median sticks out
        int trialColorInt = Color.HSVToColor(new float[]{hue, 0.2f, 0.9f});
        LineAndPointFormatter seriesFormat = new LineAndPointFormatter(
            trialColorInt /* lineColor */,
            trialColorInt /* pointColor */,
            Color.TRANSPARENT /* fillColor */,
            null /* don't label points */);
        plot.addSeries(series, seriesFormat);
      }

      double[] noiseDB = wavAnalyzerTask.getNoiseDB();
      Double[] noiseDBWrap = new Double[Common.PIP_NUM];
      for (int i = 0; i < Common.PIP_NUM; i++) {
        noiseDBWrap[i] = new Double(noiseDB[i]);
      }

      XYSeries noiseSeries = new SimpleXYSeries(
          Arrays.asList(frequencies),
          Arrays.asList(noiseDBWrap),
          "background noise");
      LineAndPointFormatter noiseSeriesFormat = new LineAndPointFormatter();
      noiseSeriesFormat.setPointLabelFormatter(null);
      noiseSeriesFormat.configure(getApplicationContext(),
          R.xml.ultrasound_line_formatter_noise);
      noiseSeriesFormat.setFillPaint(transparentFillPaint);
      plot.addSeries(noiseSeries, noiseSeriesFormat);

      double[] dB = wavAnalyzerTask.getDB();
      Double[] dBWrap = new Double[Common.PIP_NUM];
      for (int i = 0; i < Common.PIP_NUM; i++) {
        dBWrap[i] = new Double(dB[i]);
      }

      XYSeries series = new SimpleXYSeries(
          Arrays.asList(frequencies),
          Arrays.asList(dBWrap),
          "median");
      LineAndPointFormatter seriesFormat = new LineAndPointFormatter();
      seriesFormat.setPointLabelFormatter(null);
      seriesFormat.configure(getApplicationContext(),
          R.xml.ultrasound_line_formatter_median);
      seriesFormat.setFillPaint(transparentFillPaint);
      plot.addSeries(series, seriesFormat);

      Double[] passX = new Double[] {Common.MIN_FREQUENCY_HZ, Common.MAX_FREQUENCY_HZ};
      Double[] passY = new Double[] {wavAnalyzerTask.getThreshold(), wavAnalyzerTask.getThreshold()};
      XYSeries passSeries = new SimpleXYSeries(
          Arrays.asList(passX), Arrays.asList(passY), "passing");
      LineAndPointFormatter passSeriesFormat = new LineAndPointFormatter();
      passSeriesFormat.setPointLabelFormatter(null);
      passSeriesFormat.configure(getApplicationContext(),
          R.xml.ultrasound_line_formatter_pass);
      passSeriesFormat.setFillPaint(transparentFillPaint);
      plot.addSeries(passSeries, passSeriesFormat);
    }
  }

  /**
   * Plays the generated pips.
   */
  private void play() {
    play(SoundGenerator.getInstance().getByte(), Common.PLAYING_SAMPLE_RATE_HZ);
  }

  /**
   * Plays the sound data.
   */
  private void play(byte[] data, int sampleRate) {
    if (audioTrack != null) {
      audioTrack.stop();
      audioTrack.release();
    }
    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
        sampleRate, AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT, Math.max(data.length, AudioTrack.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)),
        AudioTrack.MODE_STATIC);
    audioTrack.write(data, 0, data.length);
    audioTrack.play();
  }

  /**
   * AsyncTask class for the analyzing.
   */
  private class WavAnalyzerTask extends AsyncTask<Void, String, String>
      implements WavAnalyzer.Listener {

    private static final String TAG = "WavAnalyzerTask";
    WavAnalyzer wavAnalyzer;

    public WavAnalyzerTask(byte[] recording) {
      wavAnalyzer = new WavAnalyzer(recording, Common.RECORDING_SAMPLE_RATE_HZ,
          WavAnalyzerTask.this);
    }

    double[] getDB() {
      return wavAnalyzer.getDB();
    }

    double[][] getPower() {
      return wavAnalyzer.getPower();
    }

    double[] getNoiseDB() {
      return wavAnalyzer.getNoiseDB();
    }

    double getThreshold() {
      return wavAnalyzer.getThreshold();
    }

    @Override
    protected String doInBackground(Void... params) {
      boolean result = wavAnalyzer.doWork();
      if (result) {
        return getString(R.string.hifi_ultrasound_test_pass);
      }
      return getString(R.string.hifi_ultrasound_test_fail);
    }

    @Override
    protected void onPostExecute(String result) {
      info.append(result);
      recorderButton.setEnabled(true);
      if (wavAnalyzer.getResult()) {
        getPassButton().setEnabled(true);
      }
      recorderButton.setText(R.string.hifi_ultrasound_test_plot);
    }

    @Override
    protected void onProgressUpdate(String... values) {
      for (String message : values) {
        info.append(message);
        Log.d(TAG, message);
      }
    }

    @Override
    public void sendMessage(String message) {
      publishProgress(message);
    }
  }
}

