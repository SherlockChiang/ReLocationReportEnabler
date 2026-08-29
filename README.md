# Timeline Unlocker (LSPosed Module)

An LSPosed (Xposed) module that spoofs telephony country/operator inside Google
Play Services and Google Maps so that GMS Location History / Timeline can be
enabled on devices whose SIM is registered in a region where Google has
restricted the feature.

## Disclaimer

Spoofing telephony identity inside Google Play Services likely violates the
Google Terms of Service and may put your Google account at risk. This module
is provided for personal, educational use with **no warranty**. Use at your
own risk.

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
- `SubscriptionInfo.getCountryIso()` &rarr; `US` (upper case, per the
  platform contract for this method)
- `SubscriptionInfo.getMcc{,String}()` &rarr; `310`
- `SubscriptionInfo.getMnc{,String}()` &rarr; `30` / `030`
- `SystemProperties.get(...)` for `gsm.(sim.)?operator.(numeric|iso-country)`

Other property reads pass through unchanged.

All processes of the three packages are injected (including secondary GMS
processes such as `com.google.android.gms.unstable`); the hooks themselves
only alter the telephony reads listed above.

## Build

```bash
./gradlew :xposed:assembleDebug
# output: xposed/build/outputs/apk/debug/xposed-debug.apk
```

Release builds never use the Android debug key. Configure all four environment
variables to produce a signed release APK:

```text
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Without those variables, `assembleRelease` produces an unsigned APK suitable
for build verification only. Keep the release keystore and its passwords out
of the repository.

Pushing a `v*` tag runs the GitHub release job. Configure these repository
secrets first: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, and `RELEASE_APK_CERT_SHA256`.
The release job stops before building if any required secret is absent.

On Windows, create a new keystore and configure all five secrets interactively:

```powershell
.\scripts\setup-release-signing.ps1
```

The script requires `keytool`, an authenticated `gh` CLI, and a remote named
`origin` pointing to GitHub. It creates a PKCS12 keystore in the user profile
by default and never writes its passwords to the repository.

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

For Timeline history points, the hook `PlaceCandidate$Point(int, int)` —
the Parcelable constructor used when Maps deserializes Visit / Activity data
received from GMS over Binder — applies the same transform on read, so
historical entries align with the tiles as well.

## Known limitations and risks

**The Maps-process Location hook is process-global.** Every consumer of
`Location.getLatitude()` / `getLongitude()` inside Maps sees GCJ-02 values,
including non-display uses:

- Location shared with other people is sent as GCJ-02 coordinates labelled
  as WGS-84, so recipients see the usual few-hundred-meter offset.
- `Location.distanceBetween()` (a static API) reads raw internal values and
  does not go through the hook, so mixed raw/transformed coordinates can
  produce slightly inconsistent distance/bearing results inside Maps.

**Anything Maps derives from hooked locations and persists or uploads
leaves the device as GCJ-02-mislabelled WGS-84.** The intended data flow is:
GMS records Timeline history from unhooked WGS-84 locations, and Maps only
*renders* that data. A dex-level inspection of the Maps build this module was
developed against supports that model — the only production path found for
`PlaceCandidate$Point` is the Parcelable `CREATOR.createFromParcel` (GMS
&rarr; Maps IPC) plus a static defaults table in the rendering pipeline, and
serialization (`writeToParcel`) writes the int fields directly without
re-entering the hooked constructor. However, Maps versions differ: any flow
that uploads Maps-derived coordinates (manually edited places, visit
confirmations, on-device Timeline sync, location sharing) could persist
GCJ-02 values. To verify on your device: stand at a known location, manually
create/save a Timeline entry, then check whether the saved point aligns with
reality after a sync/export round-trip.

**The GCJ-02 transform uses the public approximation** with a coarse China
bounding box. Accuracy degrades near box edges (Hong Kong, Macau, Taiwan
areas are inside the box but outside the well-calibrated region), and if a
Chinese ROM's location stack already delivers GCJ-02 coordinates, the
compensation would double-offset those locations.

## Troubleshooting

1. Confirm the module is enabled in LSPosed and the scope includes
   `com.google.android.gms`, `com.google.android.gsf`, and
   `com.google.android.apps.maps`.
2. Force-stop all three packages (or reboot) after enabling — hooks install
   at process start.
3. Check hook installation in LSPosed logs or via:

   ```bash
   adb logcat | grep -i TimelineUnlocker
   ```

   Each successful hook group logs once per process, e.g.
   `hooked N overload(s) of TelephonyManager.getSimCountryIso -> us`.
   Failures are logged too; a `not found` message usually means the target
   app version changed — open an issue with the log output.
