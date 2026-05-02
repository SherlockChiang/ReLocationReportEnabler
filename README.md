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
- `SubscriptionInfo.getCountryIso()` &rarr; `us`
- `SubscriptionInfo.getMcc{,String}()` &rarr; `310`
- `SubscriptionInfo.getMnc{,String}()` &rarr; `30` / `030`
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
4. Force-stop GMS, Maps, and GSF (or reboot). Open Maps &rarr; Timeline.

## GCJ-02 offset compensation

Spoofing country to `us` in `com.google.android.apps.maps` makes Maps stop
applying its built-in WGS-84 &rarr; GCJ-02 conversion, so the live location
dot drifts off the China map tiles by a few hundred meters.

To compensate, the Maps process additionally hooks
`Location.getLatitude()` / `Location.getLongitude()` and applies the public
WGS-84 &rarr; GCJ-02 transform when the coordinate falls inside the China
bounding box. The dot then realigns with the GCJ-02 tiles.

This compensation is **only** installed in `com.google.android.apps.maps`;
GMS / GSF still see the original WGS-84 values, which is what Location
History upload expects.
