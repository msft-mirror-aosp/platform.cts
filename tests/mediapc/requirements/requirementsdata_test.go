// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package requirementsdata_test

import (
	"testing"

	"google3/third_party/android/mediapc_requirements/requirements"
	pb "cts/test/mediapc/requirements/requirements_go_proto"

	_ "embed"
)

// MPC Requirements data from requirements.txtpb
//
//go:embed requirements.binbp
var reqBinary []byte

func TestUniqueRequirementIDs(t *testing.T) {
	reqList := mustUnmarshalRequirementList(t)

	// Requirement ids must be unique
	ids := make(map[string]bool)
	for _, req := range reqList.GetRequirements() {
		id := req.GetId()
		if ids[id] {
			t.Errorf("requirement [%s] is a duplicate", id)
		}
		ids[id] = true
	}
}

func TestUniqueRequirementNames(t *testing.T) {
	reqList := mustUnmarshalRequirementList(t)

	// Requirement names must be unique
	nameToID := make(map[string]string) // name to id
	for _, req := range reqList.GetRequirements() {
		if req.HasName() {
			name := req.GetName()
			if nameToID[name] != "" {
				t.Errorf("the name %q of requirement [%s] is a duplicate of requirement [%s]'s name", req.GetId(), name, nameToID[name])
			}
			nameToID[name] = req.GetId()
		}
	}

}

func mustUnmarshalRequirementList(t *testing.T) *pb.RequirementList {
	t.Helper()
	reqList, err := requirements.UnmarshalRequirementList(reqBinary)
	if err != nil {
		t.Fatalf("failed to unmarshal reqBinary: %v", err)
	}
	return reqList
}
