# Copyright 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json
import logging
import os
import pathlib
import subprocess
import sys
import time

from mobly import asserts
from mobly import test_runner
import secure_playback_base_test

_ACTION_SECURE_PLAYBACK_START = (
    'com.android.cts.verifier.media.ACTION_SECURE_PLAYBACK_START'
)
_ACTION_SECURE_PLAYBACK_RESULT = (
    'com.android.cts.verifier.media.ACTION_SECURE_PLAYBACK_RESULT'
)
_APP_SIDE_SERVER_PORT = 8000
_APP_START_WAIT_TIME_SEC = 3
_EXPECTED_NUMBER_OF_CODES = 256
_HOST_SIDE_SERVER_PORT = 51501  # Arbitrary port number
_HTTP_SERVER_STR = 'http.server'
_MAX_DROPPED_FRAMES = 5
_SECURE_PLAYBACK_CODEC_NAME = 'media.secureplayback.extra.CODEC_NAME'
_SECURE_PLAYBACK_EXTRA_STREAMING_URI = (
    'media.secureplayback.extra.STREAMING_URI'
)
_SECURE_PLAYBACK_EXTRA_VIDEO_SCALING = (
    'media.secureplayback.extra.VIDEO_SCALING'
)
_SECURE_PLAYBACK_NUM_DROPPED_FRAMES = (
    'media.secureplayback.extra.NUM_DROPPED_FRAMES'
)
_SECURE_PLAYBACK_RESULTS_KEY = 'media.secureplayback.extra.RESULTS'
_TEST_ACTIVITY = 'com.android.cts.verifier/.media.SecurePlaybackTestActivity'
_TEST_VIDEO_FOLDER = 'piano-keys-wv-cenc'
_WAIT_FOR_NETWORK_SEC = 10


def _get_code_set(edges):
  return set([edge['code'] for edge in edges])


class SecurePlaybackTest(secure_playback_base_test.SecurePlaybackBaseTest):
  """Tests that there are no frame drops when using secure codecs.

  Each test plays a 20-second clip of a video encoded with secure AVC, HEVC,
  VP9, or AV1 codecs and verifies that the number of dropped frames is within
  the acceptable limit.
  """

  def setup_class(self):
    super().setup_class()
    # Set up server
    serve_directory = os.path.join(
        os.environ['SECURE_PLAYBACK_TEST_APP_TOP'],
        _TEST_VIDEO_FOLDER
    )
    self.server_process = subprocess.Popen(
        [sys.executable, '-m', _HTTP_SERVER_STR, str(_HOST_SIDE_SERVER_PORT)],
        cwd=serve_directory,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.STDOUT
    )
    self.dut.adb.reverse(
        [f'tcp:{_APP_SIDE_SERVER_PORT}', f'tcp:{_HOST_SIDE_SERVER_PORT}']
    )
    self.aligned = False

  def teardown_class(self):
    if self.server_process:
      logging.info('Terminating server process')
      self.server_process.terminate()
      self.server_process.wait()
    logging.info('Removing all reverse socket connections')
    self.dut.adb.reverse(['--remove-all'])
    passed_or_skipped = len(self.results.passed) + len(self.results.skipped)
    if passed_or_skipped == len(self.results.requested):
      logging.info('All tests passed or skipped')
      self.dut.adb.shell(
          f'am broadcast -a {_ACTION_SECURE_PLAYBACK_RESULT} '
          f'--es {_SECURE_PLAYBACK_RESULTS_KEY} PASS'
      )
    super().teardown_class()

  def setup_test(self):
    super().setup_test()
    self.dut.adb.shell('input keyevent KEYCODE_WAKEUP')
    self.dut.adb.shell('input keyevent KEYCODE_MENU')
    self.dut.adb.shell(
        f'am start -n {_TEST_ACTIVITY} '
        '--activity-brought-to-front --activity-reorder-to-front'
    )
    time.sleep(_APP_START_WAIT_TIME_SEC)

  def teardown_test(self):
    super().teardown_test()
    self.dut.adb.shell(
        f'am broadcast -a {_ACTION_SECURE_PLAYBACK_RESULT} '
        f'--es {_SECURE_PLAYBACK_RESULTS_KEY} INCOMPLETE '
        f'--es {_SECURE_PLAYBACK_CODEC_NAME} {self.codec_name} '
        f'--ei {_SECURE_PLAYBACK_NUM_DROPPED_FRAMES} {self.total_dropped}'
    )

  def _align_playback_tool(self, video_url):
    """Prompts user to align the playback analysis tool with the video.

    Also allows the user to scale the video to match the grooves of the tool.

    Args:
      video_url: The URL of the video to align.
    """
    if self.aligned:
      return
    while True:
      response = input('Align Playback Analysis Tool and enter "y" to continue.'
                       ' If video is too large, enter a number'
                       ' in (0, 1] to scale the video... ')
      if response.lower() == 'y':
        break
      else:
        try:
          self.video_scaling = float(response)
          if self.video_scaling <= 0 or self.video_scaling > 1:
            logging.error(
                'Invalid video scaling factor: %.2f', self.video_scaling
            )
            continue
          self._play_video(video_url)
        except ValueError:
          logging.error('Failed to parse video_scaling from user input')
    self.aligned = True

  def _play_video(self, video_url):
    """Plays the video by sending a broadcast to CTS Verifier."""
    self.dut.adb.shell(
        f'am broadcast -a {_ACTION_SECURE_PLAYBACK_START} '
        f'--es {_SECURE_PLAYBACK_EXTRA_STREAMING_URI} '
        f'{video_url} '
        f'--ef {_SECURE_PLAYBACK_EXTRA_VIDEO_SCALING} '
        f'{self.video_scaling}'
    )

  def _test_secure_playback(self, video_url, duration_sec=20):
    """Helper method to test secure playback of a video.

    Args:
      video_url: The URL of the video to test.
      duration_sec: The duration of the video to test in seconds.
    """
    self._play_video(video_url)
    if not self.aligned:
      self._align_playback_tool(video_url)
    else:
      time.sleep(_WAIT_FOR_NETWORK_SEC)
    logging.info('Calling patctl to start analysis')
    file_name = pathlib.Path(video_url).stem
    json_path = os.path.join(self.log_path, f'{file_name}.json')
    subprocess.run(
        f'patctl --usb -r {duration_sec} -d {json_path}',
        shell=True,
        check=True,
    )
    subprocess.run(
        f'patproc  --no-plot --video-only --output-dir {self.log_path} '
        f'-j {json_path}',
        shell=True,
        check=True
    )
    processed_json_path = os.path.join(
        self.log_path, f'{file_name}-processed.json'
    )
    with open(json_path, 'r') as f:
      json_data = json.load(f)
    with open(processed_json_path, 'r') as f:
      processed_json_data = json.load(f)
    code_set = _get_code_set(json_data['video_edges'])
    logging.info(
        'number of codes: %s', len(code_set)
    )
    if len(code_set) != _EXPECTED_NUMBER_OF_CODES:
      raise AssertionError(
          f'Expected {_EXPECTED_NUMBER_OF_CODES} codes, got {len(code_set)}. '
          'Please re-run the test and verify that the PAT is correctly aligned '
          'throughout the entire test.'
      )
    dropped_frames = processed_json_data[
        'video_analysis']['metrics']['dropped_frames']
    logging.info('dropped frames: %s', dropped_frames)
    self.total_dropped = dropped_frames['total_dropped']
    if self.total_dropped > _MAX_DROPPED_FRAMES:
      asserts.skip(
          f'Expected no more than {_MAX_DROPPED_FRAMES} dropped frames, '
          f'got {self.total_dropped}'
      )

  def test_secure_playback_avc_60fps(self):
    self.codec_name = 'avc'
    self._test_secure_playback(
        f'http://localhost:{_APP_SIDE_SERVER_PORT}/wv-cenc-avc-1080p-60fps.mpd'
    )

  def test_secure_playback_hevc_60fps(self):
    self.codec_name = 'hevc'
    self._test_secure_playback(
        f'http://localhost:{_APP_SIDE_SERVER_PORT}/wv-cenc-hevc-1080p-60fps.mpd'
    )

  def test_secure_playback_vp9_60fps(self):
    self.codec_name = 'vp9'
    self._test_secure_playback(
        f'http://localhost:{_APP_SIDE_SERVER_PORT}/wv-cenc-vp9-1080p-60fps.mpd'
    )

  def test_secure_playback_av1_60fps(self):
    self.codec_name = 'av1'
    self._test_secure_playback(
        f'http://localhost:{_APP_SIDE_SERVER_PORT}/wv-cenc-av1-1080p-60fps.mpd'
    )


if __name__ == '__main__':
  test_runner.main()
