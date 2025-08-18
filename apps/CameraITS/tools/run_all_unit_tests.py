# Copyright 2022 The Android Open Source Project
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
"""Unit tests for run_all_tests script."""

import itertools
import os
import unittest
import yaml

import run_all_tests


class RunAllUnitTests(unittest.TestCase):
  """Unit tests to verify run_all_tests tool."""

  def setUp(self):
    super().setUp()
    blank_config_path = os.path.join(
        os.environ['CAMERA_ITS_TOP'],
        'tools', 'unit_test_configs', 'blank_config.yml'
    )
    sensor_fusion_and_gen2_config_path = os.path.join(
        os.environ['CAMERA_ITS_TOP'],
        'tools', 'unit_test_configs', 'sensor_fusion_and_gen2_config.yml'
    )
    tablet_and_sensor_fusion_config_path = os.path.join(
        os.environ['CAMERA_ITS_TOP'],
        'tools', 'unit_test_configs', 'tablet_and_sensor_fusion_config.yml'
    )
    tablet_scenes_config_path = os.path.join(
        os.environ['CAMERA_ITS_TOP'],
        'tools', 'unit_test_configs', 'tablet_scenes_config.yml'
    )
    manual_config_path = os.path.join(
        os.environ['CAMERA_ITS_TOP'],
        'tools', 'unit_test_configs', 'manual_config.yml'
    )
    with open(blank_config_path) as f:
      self.blank_config_file_contents = yaml.safe_load(f)
    with open(sensor_fusion_and_gen2_config_path) as f:
      self.sensor_fusion_and_gen2_config_file_contents = yaml.safe_load(f)
    with open(tablet_and_sensor_fusion_config_path) as f:
      self.tablet_and_sensor_fusion_config_file_contents = yaml.safe_load(f)
    with open(tablet_scenes_config_path) as f:
      self.tablet_scenes_config_file_contents = yaml.safe_load(f)
    with open(manual_config_path) as f:
      self.manual_config_file_contents = yaml.safe_load(f)

  def _scene_folders_exist(self, scene_folders):
    """Asserts all scene_folders exist in tests directory."""
    for scene_folder in scene_folders:
      scene_path = os.path.join(os.environ['CAMERA_ITS_TOP'],
                                'tests', scene_folder)
      self.assertTrue(os.path.exists(scene_path),
                      msg=f'{scene_path} does not exist!')

  def test_sub_camera_tests(self):
    """Ensures SUB_CAMERA_TESTS matches test files in tests directory."""
    for scene_folder in run_all_tests.SUB_CAMERA_TESTS:
      for test in run_all_tests.SUB_CAMERA_TESTS[scene_folder]:
        test_path = os.path.join(os.environ['CAMERA_ITS_TOP'],
                                 'tests', scene_folder, f'{test}.py')
        self.assertTrue(os.path.exists(test_path),
                        msg=f'{test_path} does not exist!')

  def test_all_scenes(self):
    """Ensures _ALL_SCENES list matches scene folders in test directory."""
    self._scene_folders_exist(run_all_tests._ALL_SCENES)

  def test_tablet_scenes(self):
    """Ensures _TABLET_SCENES list matches scene folders in test directory."""
    self._scene_folders_exist(run_all_tests._TABLET_SCENES)

  def test_scene_req(self):
    """Ensures _SCENE_REQ scenes match scene folders in test directory."""
    self._scene_folders_exist(run_all_tests._SCENE_REQ.keys())

  def test_grouped_scenes(self):
    """Ensures _GROUPED_SCENES scenes match scene folders in test directory."""
    # flatten list of scene folder lists stored as values of a dictionary
    scene_folders = list(itertools.chain.from_iterable(
        run_all_tests._GROUPED_SCENES.values()))
    self._scene_folders_exist(scene_folders)

  def test_blank_config_file_contents_invalid_with_all_tablet_scenes(self):
    """Ensures invalid blank config raises error with all tablet scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.blank_config_file_contents, [])

  def test_blank_config_file_contents_invalid_with_some_tablet_scenes(self):
    """Ensures invalid blank config raises error with all tablet scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.blank_config_file_contents, ['scene4', 'scene6'])

  def test_blank_config_file_contents_invalid_with_sensor_fusion_scenes(self):
    """Ensures invalid blank config raises error with sensor fusion scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.blank_config_file_contents, ['sensor_fusion'])

  def test_blank_config_file_contents_invalid_with_scene_ip_scenes(self):
    """Ensures invalid blank config raises error with scene ip scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.blank_config_file_contents, ['scene_ip'])

  def test_tablet_config_file_contents_valid_with_all_tablet_scenes(self):
    """Ensures tablet config is valid with all tablet scenes."""
    config_file_contents = (
        run_all_tests.get_config_file_contents_for_scenes(
            self.tablet_scenes_config_file_contents, ['<scene-name>']))
    self.assertIn('TEST_BED_TABLET_SCENES', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_tablet_config_file_contents_valid_with_some_tablet_scenes(self):
    """Ensures tablet config is valid with some arbitrary tablet scenes."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.tablet_scenes_config_file_contents, ['scene4', 'scene6']
    )
    self.assertIn('TEST_BED_TABLET_SCENES', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_tablet_config_file_contents_valid_with_extension_tablet_scenes(self):
    """Ensures tablet config is valid with extension tablet scenes."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.tablet_scenes_config_file_contents,
        ['scene_extensions']
    )
    self.assertIn('TEST_BED_TABLET_SCENES', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_tablet_config_file_contents_valid_with_tele_tablet_scenes(self):
    """Ensures tablet config is valid with TELE tablet scenes."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.tablet_scenes_config_file_contents,
        ['scene_tele']
    )
    self.assertIn('TEST_BED_TABLET_SCENES', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_tablet_config_file_contents_valid_with_implicit_tablet_scenes(self):
    """Ensures tablet config is valid with implicit tablet scenes."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.tablet_scenes_config_file_contents, []
    )
    self.assertIn('TEST_BED_TABLET_SCENES', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_tablet_config_file_contents_invalid_with_sensor_fusion_scenes(self):
    """Ensures tablet config is invalid with sensor fusion scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.tablet_scenes_config_file_contents, ['sensor_fusion'])

  def test_tablet_config_file_contents_invalid_with_gen2_scenes(self):
    """Ensures tablet config is invalid with gen2 scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.tablet_scenes_config_file_contents, ['gen2_scenes'])

  def test_tablet_sf_config_file_contents_valid_with_tablet_scenes(self):
    """Ensures tablet and SF config is valid with tablet scenes."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.tablet_and_sensor_fusion_config_file_contents,
        ['<scene-name>']
    )
    self.assertIn('TEST_BED_TABLET_SCENES', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_tablet_sf_config_file_contents_valid_with_sf_scenes(self):
    """Ensures tablet and SF config is valid with SF scenes."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.tablet_and_sensor_fusion_config_file_contents,
        ['sensor_fusion']
    )
    self.assertIn('TEST_BED_SENSOR_FUSION', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_tablet_sf_config_file_contents_invalid_with_gen2_scenes(self):
    """Ensures tablet and SF config is valid with gen2 scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.tablet_and_sensor_fusion_config_file_contents,
          ['scene_ip']
      )

  def test_tablet_sf_config_invalid_with_tablet_and_sf_scenes(self):
    """Ensures tablet and SF config is invalid with tablet and SF scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.tablet_and_sensor_fusion_config_file_contents,
          ['scene6', 'sensor_fusion']
      )

  def test_sf_gen2_config_invalid_with_tablet_scenes(self):
    """Ensures SF and gen2 config is invalid with tablet scenes."""
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.sensor_fusion_and_gen2_config_file_contents,
          ['<scene-name>']
      )

  def test_sf_gen2_config_invalid_with_ambiguous_scene(self):
    """Ensures SF and gen2 config is invalid with an ambiguous scene.

    An ambiguous scene (sensor_fusion) is a scene that can be tested with
    either the gen1 or gen2 rig.
    """
    with self.assertRaises(AssertionError):
      run_all_tests.get_config_file_contents_for_scenes(
          self.sensor_fusion_and_gen2_config_file_contents,
          ['sensor_fusion']
      )

  def test_sf_gen2_config_valid_with_unambiguous_gen1_scenes(self):
    """Ensures SF and gen2 config is valid with gen1-specific scene."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.sensor_fusion_and_gen2_config_file_contents,
        ['checkerboard']
    )
    self.assertIn('TEST_BED_SENSOR_FUSION', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_sf_gen2_config_valid_with_unambiguous_gen2_scenes(self):
    """Ensures SF and gen2 config is valid with gen2-specific scene."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.sensor_fusion_and_gen2_config_file_contents,
        ['gen2_scenes']
    )
    self.assertIn('TEST_BED_GEN2', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_sf_gen2_config_valid_with_unambiguous_scene_ip_scene(self):
    """Ensures SF and gen2 config is valid with gen2-specific `scene_ip`."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.sensor_fusion_and_gen2_config_file_contents,
        ['scene_ip']
    )
    self.assertIn('TEST_BED_GEN2', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_manual_config_file_contents_valid_with_all_scenes(self):
    """Ensures manual config is valid with all scenes."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.manual_config_file_contents,
        ['<scene-name>']
    )
    self.assertIn('TEST_BED_MANUAL', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)

  def test_manual_config_file_contents_valid_with_scene5(self):
    """Ensures manual config is valid with scene5, a manual scene."""
    config_file_contents = run_all_tests.get_config_file_contents_for_scenes(
        self.manual_config_file_contents,
        ['scene5']
    )
    self.assertIn('TEST_BED_MANUAL', str(config_file_contents))
    self.assertEqual(len(config_file_contents['TestBeds']), 1)


if __name__ == '__main__':
  unittest.main()
