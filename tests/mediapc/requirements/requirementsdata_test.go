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
	"slices"
	"testing"

	"google.golang.org/protobuf/proto"

	"google3/third_party/android/mediapc_requirements/requirements"
	pb "cts/test/mediapc/requirements/requirements_go_proto"
	"github.com/google/go-cmp/cmp"
	"github.com/google/go-cmp/cmp/cmpopts"

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

func TestAllTestConfigsSpecifiedAndUsed(t *testing.T) {
	reqList := mustUnmarshalRequirementList(t)

	for _, req := range reqList.GetRequirements() {
		if !req.HasName() {
			continue // Do not check requirements that are not implemented yet
		}

		t.Run(req.GetName(), func(t *testing.T) {

			specifiedTestConfigs := []string{}
			for id, testConfig := range req.GetTestConfigs() {
				if id != testConfig.GetId() {
					t.Errorf("Test config id [%s] does not match its key [%s]", testConfig.GetId(), id)
				}
				specifiedTestConfigs = append(specifiedTestConfigs, testConfig.GetId())
			}

			usedTestConfigs := []string{}
			for _, spec := range req.GetSpecs() {
				if !slices.Contains(usedTestConfigs, spec.GetTestConfigId()) {
					usedTestConfigs = append(usedTestConfigs, spec.GetTestConfigId())
				}
			}

			if diff := cmp.Diff(specifiedTestConfigs, usedTestConfigs, cmpopts.SortSlices(
				func(a, b string) bool { return a < b })); diff != "" {
				t.Errorf("Specified test configs do not match used test configs (-want +got):\n%s", diff)
			}
		})
	}
}

func TestConfigMeasurementsValid(t *testing.T) {
	reqList := mustUnmarshalRequirementList(t)

	for _, req := range reqList.GetRequirements() {
		if !req.HasName() {
			continue // Do not check requirements that are not implemented yet
		}

		t.Run(req.GetName(), func(t *testing.T) {
			for measurementName, measurement := range req.GetMeasurements() {
				if measurement.GetComparison() != pb.Comparison_COMPARISON_CONFIG {
					continue // Do not check measurements that are not config measurements
				}

				t.Run(measurementName, func(t *testing.T) {
					measurementValues := make(map[string]*pb.RequiredValue)
					for _, spec := range req.GetSpecs() {
						val, ok := measurementValues[spec.GetTestConfigId()]
						if !ok {
							measurementValues[spec.GetTestConfigId()] = spec.GetRequiredValues()[measurementName]
						} else if !proto.Equal(val, spec.GetRequiredValues()[measurementName]) {
							t.Errorf("Test config [%s] has multiple different values for measurement [%s]: [%v] and [%v]", spec.GetTestConfigId(), measurementName, spec.GetRequiredValues()[measurementName], val)
						}
					}
				})
			}

		})
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
