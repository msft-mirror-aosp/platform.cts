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

# This file should be sourced from bash. Sets environment variables for
# running tests, and also checks that a number of dependencies are present
# and that the unit tests for the modules passed (indicating that the setup
# is correct).

export SECURE_PLAYBACK_TEST_APP_TOP=$PWD
echo "SECURE_PLAYBACK_TEST_APP_TOP=$PWD"

[[ "${BASH_SOURCE[0]}" != "${0}" ]] || \
    { echo ">> Script must be sourced with 'source $0'" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || \
    echo ">> Require adb executable to be in path" >&2

command -v python >/dev/null 2>&1 || \
    echo ">> Require python executable to be in path" >&2

command -v patctl >/dev/null 2>&1 || \
    echo ">> Require patctl executable to be in path" >&2

python -V 2>&1 | grep -q "Python 3.*" || \
    echo ">> Require python version 3" >&2
