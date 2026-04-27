# Timeline Unlocker (LSPosed Module)

An LSPosed (Xposed) module that spoofs telephony country/operator inside Google
Play Services and Google Maps so that GMS Location History / Timeline can be
enabled on devices whose SIM is registered in a region where Google has
restricted the feature.

## Scope

The module only loads in:

- `com.google.android.gms`
- `com.google.android.gsf`
- `com.google.android.apps.maps`

In those processes it returns fake values for:

- `TelephonyManager.getSimCountryIso{,ForPhone}` &rarr; `us`
- `TelephonyManager.getNetworkCountryIso{,ForPhone}` &rarr; `us`
- `TelephonyManager.getSimOperator{,Numeric,ForPhone}` &rarr; `310030`
- `TelephonyManager.getNetworkOperator{,Numeric,ForPhone}` &rarr; `310030`
- `SystemProperties.get(...)` for `gsm.(sim.)?operator.(numeric|iso-country)`

Other property reads pass through unchanged.

## Build

```bash
./gradlew :xposed:assembleDebug
# output: xposed/build/outputs/apk/debug/xposed-debug.apk
```

## Install

1. Install the APK with `adb install` (or any installer).
2. In LSPosed manager, enable **Timeline Unlocker (Xposed)**.
3. Confirm the scope includes the three Google packages above.
4. Force-stop GMS and Maps (or reboot). Open Maps &rarr; Timeline.

## Caveat

Once active in `com.google.android.apps.maps`, Maps stops applying the
WGS-84 &rarr; GCJ-02 transformation, so your live-location dot will be
offset from the map tiles when you are physically in mainland China.
This is the cost of spoofing the device country: there is no clean way
to keep Timeline gating happy while letting the map renderer think you
are still in China inside the same process.
