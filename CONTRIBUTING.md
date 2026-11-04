# Contributing

Start with an issue describing the device combination and observable failure. Hardware compatibility claims should include real-device evidence, not only a successful build or BLE notification callback.

Before submitting a PR:

1. Run `python3 test-local.py`; add a behavioral regression for protocol or freshness changes.
2. Keep input GATT operations serialized and output compatibility independently testable.
3. Represent missing/stale data as unknown; never silently substitute simulated values.
4. Do not add calibration/firmware writes as a speculative compatibility workaround.
5. Remove private logs, captures, serial numbers, addresses and signing material from changes.

The known-working0.10 output behavior is the baseline. Please explain any change to advertising name/manufacturer bytes, CCCD acceptance, or crank event reconstruction and what hardware verified it.
