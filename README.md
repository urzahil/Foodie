# Foodie

An Android app for finding Michelin Guide restaurants and keeping track of the ones you want to try or have already visited.

## Features

- **Near me:** finds restaurants within 30 km of your GPS location.
- **Search:** look up any city or place.
- **List and map views:** browse results either way.
- **Filters:** narrow by Michelin stars or Bib Gourmand, and pick a cuisine from the dropdown.
- **Restaurant details:** award, cuisine, price, description, facilities and services, address and opening hours, with one-tap call and directions.
- **Favourites and Visited:** save places to either list and add notes to the ones you have visited.
- **Backup and restore:** export Favourites and Visited to a JSON file and import them back.
- **Offline use:** once the catalogue has been downloaded, the app works without a connection.

## How it works

- **Catalogue:** the app downloads the restaurant data from the community-maintained [michelin-my-maps](https://github.com/ngshiheng/michelin-my-maps) CSV and stores it in a local Room database. It re-syncs when the table is empty or the last sync is more than 7 days old. A sync is rejected if the new file has fewer than 80% of the existing rows, so a truncated download cannot wipe the catalogue.
- **Images and opening hours:** these are fetched from each restaurant's Michelin Guide page and cached locally. Hours are refreshed after 30 days.
- **User data:** favourites, visited flags and notes live in the same database and are kept across catalogue syncs.

## Data and attribution

Restaurant data comes from the [michelin-my-maps](https://github.com/ngshiheng/michelin-my-maps) dataset, and images and opening hours come from the Michelin Guide website. Foodie is an independent project and is not affiliated with or endorsed by Michelin.

## License

Released under the [GNU General Public License v3.0](LICENSE).
