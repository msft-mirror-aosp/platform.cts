# Multiuser WebKit CTS Tests

This directory contains webkit tests that require running
on different user profiles.

We use the Bedstead library to facilitate these multiuser tests.
This module cannot be combined with the general WebKit CTS test module
since Bedstead does not work correctly with test orchestrator. Moreover,
using Bedstead increases initial test module setup time even if multiuser
tests are not run.