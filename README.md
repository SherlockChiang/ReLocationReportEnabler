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
WGS-84 &rarr; GCJ-02 transform when the coordinate falls inside mainland
China's GCJ-02 coverage. Hong Kong, Macao, and Taiwan are excluded because
Maps does not need this compensation there. The dot then realigns with the
GCJ-02 tiles without introducing an offset in those regions.

### Regional boundary data

The module uses simplified OpenStreetMap administrative-boundary polygons for
Hong Kong, Macao, and Taiwan rather than the broad GCJ-02 bounding box. The
generated boundary source is checked in, so builds never make a network request.
It is an explicit, maintainable exclusion policy, not a claim that Google Maps'
internal tile coverage exactly follows administrative boundaries.

The boundary source, ODbL attribution, and fixed OpenStreetMap relation IDs are
in [`NOTICE`](NOTICE). To refresh the generated data, use Node.js 18 or later:

```bash
node scripts/generate-region-boundaries.mjs
git diff -- xposed/src/main/java/io/github/timeline_unlocker/xposed/RegionBoundary.java
```

Review any boundary diff before committing it. The generator requests geometry
simplified to `0.00005` degrees (roughly 5 m) and writes it to
`RegionBoundary.java`.

This compensation is **only** installed in `com.google.android.apps.maps`;
GMS / GSF still see the original WGS-84 values, which is what Location
History upload expects.
