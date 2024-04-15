#!/usr/bin/env python3
# Lint as: python3
"""
Test core CDM APIs involving multiple devices on mobly.
"""

import cdm_base_test

from mobly import asserts
from mobly import test_runner
from time import sleep

class CompanionDeviceManagerTestClass(cdm_base_test.BaseTestClass):

    def test_associate_createsAssociation_classicBluetooth(self):
        """Test that CDM can create association with another BT device"""

        secondary_address = self.secondary.cdm.btGetAddress()

        # Create association
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(secondary_address)

        # Assert association was created
        associations = self.primary.cdm.getMyAssociations()
        asserts.assert_true(secondary_id in associations, 'Association not found.')


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()