# DiAuto MG4+

Wireless / USB **Android Auto** for the MG4 Android head unit (SWI69), with live
battery SoC forwarded into Google Maps via Android Auto `VehicleEnergyModel`
(sensor types 23/25).

> Fork of [shihabal3amri/DiAuto](https://github.com/shihabal3amri/DiAuto) (AGPL-3.0).
> MG4 telemetry: vendored [EVHardware](https://github.com/fatihdonmezdev/MG4ABRP) in `evhardware/`.
> For Apple CarPlay on the same car, use **DiPlay-MG4** separately — do not run both at once.

> **DiAuto MG4+ — a community fork that lives its own life.** It started from
> [merthankaraman/DiAuto-MG4](https://github.com/merthankaraman/DiAuto-MG4) 1.9.6 (itself a port of
> DiAuto) and is maintained separately for the MG4, with its own version line from 2.0.0. It keeps
> the package `com.drivehub.diauto.mg4`, so it installs over DiAuto-MG4 and keeps its settings.
> What differs: reconnection on the next trip (`CommManager.destroy()` no longer cancels the
> app-wide scope), music over Android Auto by default with the radio given back, Maps battery at
> arrival started on every connection, updates from this repository (with or without betas), and
> the upstream PRs #4, #5, #6, #7, #9, #10, #12, #13, #14 and #16. See `CHANGELOG.md`.


## Features (MG4)

- Native wireless Android Auto over the car’s built-in hotspot (Android 9; Wi‑Fi Direct unavailable).
- Platform-signed `android.uid.system` install for CarProperty / BMS access.
- Optional **Battery for Google Maps** (default on): SoC + range → AA sensors 23/25.
- **Demo battery values**: manual SoC / range / capacity (skips EVHardware; desk testing).
- Application id: `com.drivehub.diauto.mg4` (phone desk build: `.phone` suffix).

## Installing APKs on the MG4

If you haven't sideloaded apps before:

1. Open **Bluetooth Settings** and select the car name.
2. Hold down on the **comma** until the settings appear.
3. Select **Android Keyboard Settings**.
4. Select **Languages**.
5. Use the search box at the top to search for **file**.
6. That should give you access to the **Files** app.
7. From there, select a connected USB device with APKs on it and install as usual.

## Build

**MG4 car** (platform-signed, `android.uid.system`):

```powershell
$env:MG4_PLATFORM_KEYS_DIR = "C:\path\to\platform-keys"  # platform.pk8 + platform.x509.pem
powershell -File scripts\build_mg4.ps1
adb install -r ..\DiAuto-MG4-v0.3.11-mg4.1.apk
```

**Normal phone / tablet** (debug keystore, Demo battery):

```powershell
powershell -File scripts\build_phone.ps1
adb install -r ..\DiAuto-MG4-phone-debug.apk
```

In Android Studio: open `DiAuto-MG4`, pick **phoneDebug** or **carDebug**.

See [docs/MG4_TESTING.md](docs/MG4_TESTING.md) for the vehicle checklist.

### Google Maps SoC note

Battery % / arrival charge is shown primarily on **Android Auto Maps** (the projected
head-unit UI) once a route is active. The phone’s own Maps screen often has no permanent
SoC badge; that is expected Google behavior when VEM is delivered over AA.

## Upstream

See upstream DiAuto README / docs for general AA host features. MG4-specific changes
live in this fork.
