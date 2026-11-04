# Privacy

Power Bridge has no INTERNET permission, account, remote service, ads or analytics SDK. It processes BLE measurement data locally.

The selected sensor address/name and the phone's original Bluetooth name (for restoration after temporary compatibility advertising) are saved in local preferences. Battery/device-info values and recent power readings are held in memory and cleared at disconnect/stop. Limited raw measurements and connection events are written to app diagnostics and Android logcat.

Copying logs or using adb/bugreport is a user-initiated export and may reveal device details. Redact sensor names/addresses, serial numbers and unrelated phone data before sharing. Full phone bugreports, HCI captures and signing keys must never be attached to public issues or committed here.

Android12+ requires nearby-device permissions for scan/connect/advertise. Older Android requires location permission and system location enabled for scanning; the app does not request GPS positions. A foreground notification and partial wake lock are active while bridging. Stop the bridge when finished.

Source code and distributed APK may differ when third parties rebuild or modify them. This statement covers this repository's implementation, not third-party builds.
