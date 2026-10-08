# MovieHub reference backend (`:server`)

A small, standalone JVM service that implements the app's **`CatalogApi` contract (PRD §7)**
— the read-only, CDN-cacheable catalogue API the client already knows how to call. It lets
you run the app against a *real server* instead of the bundled `catalog.json` asset, without
inventing any new models or endpoints.

It is **not** an Android module. It runs on the same JDK + Gradle toolchain the app already
uses (the Android Studio JBR), so there is nothing extra to install.

> **Content note.** This serves the app's own sample catalogue: invented titles and free,
> public, multi-bitrate **test streams** (Apple BipBop, Mux, Shaka, Google DASH). It does
> **not** proxy, scrape, or rehost any commercial streaming service. Point it only at media
> you own or are licensed to serve.

## Endpoints

| Method & path            | Returns                      | Client call             |
|--------------------------|------------------------------|-------------------------|
| `GET /v1/home`           | the whole catalogue document | `CatalogApi.home()`     |
| `GET /v1/titles/{id}`    | one title                    | `CatalogApi.title(id)`  |
| `GET /v1/search?q=`      | matching titles              | `CatalogApi.search(q)`  |
| `GET /v1/genres`         | the genre list               | `CatalogApi.genres()`   |
| `GET /health`            | `{"status":"ok","titles":N}` | —                       |

Responses are exactly the shapes `CatalogDto` / `TitleDto` / `GenreDto` parse — in fact
`GET /v1/home` returns the same bytes the app bundles as `catalog.json`.

Search is a **character-for-character port of the client's `FuzzyMatcher`** (`norm` + `skel`
+ bounded skeleton subsequence), so the server and the offline client agree on results.

## Run it

```bash
# from the project root
./gradlew :server:run
```

You should see:

```
MovieHub reference catalogue API
  titles loaded : 14
  listening on  : http://localhost:8080/v1
  point the app : API_BASE_URL = "http://10.0.2.2:8080/v1/"  (Android emulator -> host machine)
```

Smoke-test it:

```bash
curl http://localhost:8080/health
curl http://localhost:8080/v1/home
curl http://localhost:8080/v1/titles/1
curl "http://localhost:8080/v1/search?q=moonlight"   # fuzzy: matches "Moonlit Night"
curl http://localhost:8080/v1/genres
```

### Config (env vars)

| Var            | Default                        | Purpose                                   |
|----------------|--------------------------------|-------------------------------------------|
| `PORT`         | `8080`                         | listen port                               |
| `API_PREFIX`   | `/v1`                          | path prefix for the catalogue routes      |
| `CATALOG_PATH` | *(classpath `catalog.json`)*   | serve a different catalogue file          |

The default catalogue is **the app's own asset**: the build copies
`app/src/main/assets/catalog.json` into this module's resources (`syncCatalog` task), so the
server and the app can never drift. Edit the app asset, re-run, and both update together.

## Point the app at it

`BuildConfig.API_BASE_URL` is the only switch. Set it in `app/build.gradle.kts`:

```kotlin
// defaultConfig { ... }
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/v1/\"")
```

- Must end in `/` (Retrofit joins the relative paths `home`, `titles/{id}`, … onto it).
- **Android emulator → host machine** is `10.0.2.2`, not `localhost`. A physical device
  uses your machine's LAN IP (e.g. `http://192.168.1.20:8080/v1/`).
- Cleartext HTTP to `10.0.2.2` already works in debug builds; for a LAN IP on a device
  you may need a `network-security-config` entry allowing cleartext to that host.

With a non-empty `API_BASE_URL`, `CatalogRepositoryImpl` seeds its Room cache from
`GET /home` on first run (falling back to the bundled asset if the server is unreachable, so
offline-first still holds). Leave it empty — the default — and the app behaves exactly as it
did before: asset + Room, no network.
