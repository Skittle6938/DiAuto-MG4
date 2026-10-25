#!/usr/bin/env bash
# Install a review or signed release APK without relying on debuggable/run-as access.
set -euo pipefail
CAR="${1:?Usage: install.sh CAR_IP:5555 PATH_TO_APK}"
APK="${2:?Provide an APK path}"
ADB="${ADB:-adb}"
PKG=com.drivehub.diauto.mg4
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }
"$ADB" connect "$CAR"
adb_car() { "$ADB" -s "$CAR" "$@"; }
adb_car get-state
adb_car install -r "$APK"
adb_car shell am force-stop "$PKG"
if adb_car shell pidof "$PKG" >/dev/null; then
    echo "DiAuto is still running; stop it before continuing." >&2
    exit 1
fi
for permission in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION RECORD_AUDIO READ_PHONE_STATE \
    BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE NEARBY_WIFI_DEVICES POST_NOTIFICATIONS; do
    adb_car shell pm grant "$PKG" "android.permission.$permission" 2>/dev/null || true
done
adb_car shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb_car shell settings put secure location_mode 3
adb_car shell dumpsys deviceidle whitelist +"$PKG"
adb_car shell appops set "$PKG" RUN_IN_BACKGROUND allow
adb_car shell appops set "$PKG" RUN_ANY_IN_BACKGROUND allow
adb_car shell am start -n "$PKG/com.andrerinas.openheadunit.main.MainActivity"
echo 'Finish setup on the display, then choose Connect phone.'
echo 'DiLink masks the Wi-Fi Direct address. If wireless pairing fails, run:'
echo "  adb -s $CAR shell cat /sys/class/net/p2p0/address"
echo 'Enter that address in Settings > Display and performance > Static BSSID.'
echo 'Existing pairing, calibration, DPI and surface preferences were preserved.'
