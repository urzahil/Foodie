# Foodie 2.0

Foodie is an Android application for discovering and managing Michelin Guide restaurants. It maintains a local restaurant catalogue, supports location-aware search and filtering, provides list and Google Maps views, and stores favourites, visited state, and notes locally.

> **Status:** active development. Current app version: **1.0**. Application ID: `com.aistudio.foodie.michelin`.

## Features

- Local catalogue of roughly 19,000+ Michelin restaurants.
- 30 km location-aware restaurant search with exact Haversine distance sorting.
- Search by restaurant name, cuisine, city/location, and address.
- Autocomplete from Michelin destinations plus Android geocoding.
- Paris is the default location on first launch.
- **Near Me** using device location.
- Michelin filters: 3 Stars, 2 Stars, 1 Star, Bib Gourmand, and Selected Restaurants.
- Cuisine filtering derived from the current result set.
- Favourites and visited-restaurant lists.
- Google Maps view with Michelin-category markers and restaurant previews.
- Restaurant detail pages with Michelin metadata, opening hours, images, contact information, and user state.
- Local JSON backup/restore of favourites and visited restaurants.
- Search editing does not disturb the currently displayed list until the search is submitted.
- List scroll position is preserved when returning from restaurant details.

## Data sources

### Restaurant catalogue

The catalogue is downloaded from the public `michelin-my-maps` dataset:

`https://raw.githubusercontent.com/ngshiheng/michelin-my-maps/refs/heads/main/data/michelin_my_maps.csv`

Foodie parses the CSV locally and stores it in Room. Catalogue refreshes normally occur at most once every 30 days. Each restaurant gets a stable identity based on its Michelin URL where available, with a deterministic name/location/coordinate fallback for URL-less records.

### Restaurant details

Michelin restaurant pages are fetched on demand for additional information such as opening hours and image metadata. Detail fetching is intentionally separate from catalogue synchronization.

### Location search

Autocomplete combines cities already present in the local catalogue with Android geocoding results for arbitrary places and addresses.

## Architecture

```text
Compose UI
   │
   ▼
FoodieViewModel
   │
   ▼
RestaurantRepository
   ├── MichelinCsvDownloader
   ├── MichelinPageScraper
   └── LocationHelper
   │
   ▼
Room / RestaurantDao
   │
   ▼
SQLite
```

### UI

The UI uses Jetpack Compose and Material 3. Important components include:

- `MainScreen` — application shell, search, filters, tabs, and details navigation.
- `RestaurantListTab` — restaurant list and scroll-state preservation.
- `RestaurantMapTab` — Google Maps and marker previews.
- `RestaurantDetailsScreen` — live restaurant details and user actions.
- `RestaurantCard` — restaurant list presentation and visible-item image loading.

### ViewModel and reactive filtering

`FoodieViewModel` owns transient UI state and coordinates repository operations. The filtering pipeline uses a `FilterParams` value containing the committed search query, active location, award filters, cuisine selection, and special-list mode. Those parameters use `distinctUntilChanged()` so editing the search field does not rerun the executed search.

Expensive filtering and distance calculations run on `Dispatchers.Default`. Available cuisines are derived from the filter result rather than written back into the state that drives the filter.

Restaurant details are identified by `selectedRestaurantId` and observed from Room. The UI therefore does not hold a stale restaurant copy while asynchronous detail scraping or favourite/visited updates are occurring.

### Room

The full `RestaurantEntity` contains catalogue and detail fields such as name, address, location, cuisine, price, coordinates, Michelin award, Green Star, description, opening hours, image metadata, favourites, and visited notes.

List and map screens use the lightweight `RestaurantListItem` projection instead of loading long detail fields such as descriptions. Relevant database indexes include:

- unique `sourceKey`;
- `award`;
- `location`;
- `isFavorite`;
- `isVisited`;
- `(latitude, longitude)`.

## Performance design

Foodie's catalogue is large enough that repeatedly reading every full entity is unnecessarily expensive. The current implementation reduces work at the database boundary and avoids filter-state feedback loops.

### SQL bounding box

When a location is active, Foodie first calculates an approximate 30 km latitude/longitude bounding box and asks SQLite for only rows inside that box. Exact Haversine distance is calculated only for the resulting candidates.

### Slim projections

`RestaurantListItem` intentionally excludes large detail-only fields, reducing Room/Flow traffic when favourites, catalogue updates, or other writes occur.

### Stable filtering

Only actual filter inputs invalidate the filter pipeline. Autocomplete and the editable search draft are independent of the committed search query.

### Image loading

Foodie does not prefetch a fixed number of images whenever the restaurant list changes. Image/detail requests are initiated for visible cards or when a restaurant is opened, are bounded to three concurrent operations, and failed requests have a retry cooldown. Coil owns image caching; Compose does not perform synchronous filesystem checks.

## Catalogue synchronization and data integrity

`MichelinCsvDownloader` protects synchronization with a coroutine `Mutex` and applies catalogue changes in a Room transaction.

The sync process:

1. Checks the existing catalogue and last-sync timestamp.
2. Downloads and parses the CSV off the main thread.
3. Generates stable restaurant identities.
4. Applies catalogue-owned changes transactionally.
5. Preserves user-owned favourites, visited state, timestamps, and notes.
6. Removes catalogue rows no longer present upstream.
7. Records the successful sync timestamp only after the transaction completes.

Cancellation is propagated rather than being swallowed as a generic sync error.

### Ownership model

Catalogue-owned fields include restaurant name, address, cuisine, award, coordinates, description, Michelin URL, and website URL.

User-owned fields include favourite state, favourite timestamp, visited state, visited timestamp, and visited notes.

Catalogue refreshes must never overwrite user-owned state.

## Backup and restore

Foodie supports JSON export/import of favourites and visited restaurants. The data includes restaurant identity information plus favourite/visited state, timestamps, and notes.

Imports are validated before changing the database and the restore itself is transactional. Matching uses:

1. `sourceKey`, when available;
2. a unique restaurant URL;
3. a unique restaurant name + location for legacy data.

Ambiguous matches are rejected rather than silently choosing an arbitrary restaurant. The importer supports the current format and legacy version 1 backups.

## Database migrations

Room uses explicit migrations; destructive migration fallback is not used. The current schema includes the coordinate index required for nearby queries.

When changing the schema:

1. Increment the Room database version.
2. Add an explicit migration.
3. Preserve existing user data.
4. Add or update tests where practical.
5. Run the complete test suite before merging.

## Technology stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | ViewModel + Repository |
| Database | Room |
| Async/reactive | Kotlin Coroutines + Flow |
| Images | Coil |
| Networking | OkHttp |
| Maps | Google Maps SDK + Maps Compose |
| Location | Google Play Services Location |
| Testing | JUnit, Robolectric, AndroidX test libraries |
| Gradle used by CI | 9.3.1 |
| Android Gradle Plugin | 9.1.1 |
| Kotlin | 2.2.10 |
| Minimum Android | API 24 |
| Target Android | API 36 |
| Compile SDK | API 36.1 |

## Project structure

```text
Foodie/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   ├── network/
│       │   │   │   └── repository/
│       │   │   ├── location/
│       │   │   ├── ui/
│       │   │   │   ├── components/
│       │   │   │   ├── screens/
│       │   │   │   ├── theme/
│       │   │   │   └── FoodieViewModel.kt
│       │   │   ├── FoodieApp.kt
│       │   │   └── MainActivity.kt
│       │   └── res/
│       └── test/
├── gradle/
│   └── libs.versions.toml
├── .github/
│   └── workflows/
│       └── release-apk.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Requirements

For local development you need:

- Android Studio compatible with the project's Android Gradle Plugin and Kotlin versions.
- JDK 17.
- Android SDK API 36/36.1.
- A Google Maps API key.
- A release keystore only when producing signed releases.

CI uses Gradle 9.3.1.

## Configuration

### Google Maps API key

The Android manifest expects the `MAPS_API_KEY` environment variable to be injected during the build:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

Example:

```bash
export MAPS_API_KEY="YOUR_GOOGLE_MAPS_API_KEY"
gradle assembleDebug
```

Do not commit a real API key. The Maps SDK for Android must be enabled in the associated Google Cloud project, and the key should be appropriately restricted.

### Release signing

Release builds use these environment variables:

| Variable | Purpose |
|---|---|
| `KEYSTORE_PATH` | Release keystore path |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Signing-key password |

The signing alias is `upload`.

```bash
export KEYSTORE_PATH="/path/to/foodie-release.jks"
export STORE_PASSWORD="..."
export KEY_PASSWORD="..."
gradle assembleRelease
```

Never commit the keystore or its passwords.

## Building locally

### Debug

```bash
export MAPS_API_KEY="YOUR_GOOGLE_MAPS_API_KEY"
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

### Release

```bash
export MAPS_API_KEY="YOUR_GOOGLE_MAPS_API_KEY"
export KEYSTORE_PATH="/path/to/foodie-release.jks"
export STORE_PASSWORD="..."
export KEY_PASSWORD="..."
gradle assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

## Testing

Run unit tests with:

```bash
gradle test
```

Current tests cover areas including:

- application/resource configuration;
- geographic distance calculations and formatting;
- backup JSON structure;
- cuisine extraction and availability;
- stable restaurant identity generation.

Robolectric tests run against Android API 34.

Changes involving Room, synchronization, filtering, or user-owned data should include appropriate tests and be verified with the full test suite.

## Continuous integration and releases

GitHub Actions uses Ubuntu, JDK 17, and Gradle 9.3.1.

` .github/workflows/release-apk.yml` is the release workflow (without the leading space when used as a path). It supports manual `workflow_dispatch` runs and version tags matching `v*`.

A tagged release:

1. Restores the base64-encoded release keystore from a GitHub secret.
2. Supplies the Maps API key from a GitHub secret.
3. Builds a signed release APK.
4. Uploads the APK as an Actions artifact.
5. Creates a GitHub Release with generated notes.

Required GitHub Actions secrets:

| Secret | Purpose |
|---|---|
| `MAPS_API_KEY` | Google Maps API key |
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Signing-key password |

Example release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Development guidelines

### Protect user state

Do not let catalogue synchronization blindly replace favourites, visited state, notes, or their timestamps.

### Keep list queries lightweight

Do not add descriptions, opening hours, or other detail-only fields to `RestaurantListItem` unless the list/map UI actually requires them.

When adding a filter:

1. Add it to `FilterParams`.
2. Keep filtering pure.
3. Do not mutate `UiState` from inside the filtering Flow.
4. Push filtering into SQL where it materially reduces the candidate set.

### Keep details live

Restaurant details should be identified by ID and collected from Room instead of holding a stale entity copy.

### Avoid synchronous I/O in Compose

Network, database, and filesystem work belongs in coroutines/background dispatchers. Coil should own image loading and caching.

## Privacy and permissions

The app requests:

- `INTERNET` — catalogue and restaurant-detail downloads.
- `ACCESS_NETWORK_STATE` — network state information.
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` — Near Me.

Favourites, visited state, and notes are stored locally. Core functionality does not require a Foodie account.

## Troubleshooting

### Blank Google Map

Check that `MAPS_API_KEY` is present during the build, `com.google.android.geo.API_KEY` appears in the merged manifest, Maps SDK for Android is enabled, the key permits the SDK, restrictions match the application, and the device has network access.

### No restaurants

The initial catalogue may still be downloading. Check the sync state and network connection.

### Near Me fails

Check Android location permission and device location services. A city or other location can be selected manually if GPS cannot be obtained.

### Missing image

Images are fetched on demand. Failed requests are throttled with a retry cooldown to avoid repeatedly contacting an unavailable source.

## Known limitations

- The Michelin CSV is an external public dataset and can change independently of Foodie.
- Michelin restaurant pages are external HTML and may change structure, affecting opening-hours/image parsing.
- Maps require a valid Google Maps API key and network access.
- The 30 km bounding-box optimization has a conservative fallback for locations near the international date line.
- Personal favourites/visited data is local; there is currently no Foodie account or cloud synchronization.
- Release signing is intentionally performed outside source control using CI secrets.

## Contributing

1. Create a feature/fix branch from `main`.
2. Make the smallest coherent change.
3. Add or update tests.
4. Run the test suite.
5. Build the debug APK.
6. Verify a signed release build when applicable.
7. Open a pull request describing the problem, implementation, migration/data-integrity implications, tests, and manual device testing.

Changes affecting the database, synchronization, backup/restore, or user-owned data should be reviewed before merging.

## License

No license is currently declared in the repository. Until a license is added, the source should not be assumed to grant permission to redistribute, modify, or commercially use it.

## Acknowledgements

- Restaurant catalogue data comes from the public `michelin-my-maps` dataset referenced above.
- Restaurant detail information is obtained from Michelin Guide pages.
- Maps are provided by Google Maps SDK for Android.
- Image loading/caching is provided by Coil.
- The application is built on Kotlin, Android, Jetpack Compose, Room, and Kotlin Coroutines.