# FreightFox Toll Plaza Discovery Service

A production-quality Spring Boot 3 REST API that resolves Indian postal codes (pincodes), calculates driving routes using real routing engines (Mappls & OSRM), and identifies all highway toll plazas located along the route with their cumulative driving distance from the origin.

---

## 🚀 Key Features

- **Pincode Geocoding**: Resolves 6-digit Indian postal codes into geographical coordinates with fallback caching.
- **Dual Routing Provider Architecture (Strategy Pattern)**:
  - **Mappls (MapmyIndia)**: Official Route ADV API with Static REST API Key (`MAPPLS_API_KEY`), supporting both GeoJSON and encoded polyline decoding.
  - **OSRM (Open Source Routing Machine)**: Real-time driving routes with 8,000+ GeoJSON polyline waypoints over OpenStreetMap without credential bottlenecks.
  - **Strategy Selector**: Automatically selects Mappls when `MAPPLS_API_KEY` is present, with fallback to OSRM.
- **Geospatial Corridor Filtering**: High-speed two-tier filtering:
  1. **PostgreSQL Spatial Bounding Box**: Uses B-Tree index on `(latitude, longitude)` for $O(\log N)$ candidate pre-filtering.
  2. **Vector Projection Algorithm**: Computes exact perpendicular distance from toll plazas to highway polyline segments in meters using local planar Cartesian projection.
- **Accurate Route Distance**: Calculates cumulative driving distance *along the highway* from source to toll plaza (not straight-line Euclidean distance).
- **Automated CSV Ingestion**: Parses, validates, deduplicates, and seeds 2,385+ Indian toll plazas into PostgreSQL on startup with JDBC batching in under 1.5 seconds.
- **In-Memory Caching**: Implements Caffeine caching with configurable TTL and directional cache keys (`source:destination`).
- **OpenAPI / Swagger UI**: Interactive API documentation at `/swagger-ui.html`.
- **Dockerized**: Containerized deployment with configurable host database port (`POSTGRES_PORT:-5433`).

---

## 🛠 Technology Stack

- **Java**: 21 LTS
- **Framework**: Spring Boot 3.4.1
- **Database**: PostgreSQL 16 (H2 for unit testing & dev profile)
- **Persistence**: Spring Data JPA / Hibernate ORM (`open-in-view: false`)
- **Cache**: Caffeine (`com.github.ben-manes.caffeine`)
- **API Documentation**: Springdoc OpenAPI (Swagger UI 2.7.0)
- **CSV Parser**: Apache Commons CSV 1.11.0
- **Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test (34 automated tests across 8 test suites)
- **Containerization**: Docker & Docker Compose

---

## 🚦 Quick Start with Docker Compose

To prevent collisions with any PostgreSQL instances already running on host port 5432, Docker Compose maps to host port **5433** by default.

```bash
docker-compose up --build -d
```

Check logs:
```bash
docker-compose logs -f app
```

The application will start on `http://localhost:8080`.

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

To stop:
```bash
docker-compose down -v
```

---

## 💻 Local Development Setup (Without Docker)

### Option 1: Run with In-Memory H2 Dev Profile (Zero Database Setup Needed)
```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### Option 2: Run with Local PostgreSQL
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tollplaza_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
mvn spring-boot:run
```

### Running Tests
```bash
mvn test
```
All 34 test cases across 8 test suites execute in under 20 seconds with zero external database dependencies.

---

## 🗺 Routing Provider: Mappls vs. OSRM

The service supports both Mappls and OSRM through `RoutingClientFactory` using the Strategy Pattern:

| Feature | Mappls (MapmyIndia) | OSRM (Open Source Routing Machine) |
| :--- | :--- | :--- |
| **Authentication** | Static REST API Key (`MAPPLS_API_KEY`) | None (Public open-source API or self-hosted) |
| **API Endpoint** | `https://route.mappls.com/route/direction/route_adv/driving/...` | `https://router.project-osrm.org/route/v1/driving/...` |
| **Geometry Support**| Full GeoJSON & Encoded Polyline (5-digit & 6-digit precision) | Full GeoJSON Polyline |
| **Availability** | Requires valid Mappls API key & IP whitelisting | 100% free, unlimited, zero-credential dependency |
| **Highway Preference** | Official freight corridors (NH 48 Golden Quadrilateral) | Fastest vehicle road graph (routes north via NH 50 / NH 65) |
| **Selection Behavior** | Primary default (`app.routing.provider: mappls`) when `MAPPLS_API_KEY` is present | Automatic fallback when key is absent, or explicitly via `ROUTING_PROVIDER=osrm` |

---

## 📊 Empirical Route & Toll Plaza Comparison: Bengaluru (560064) to Pune (411045)

### 1. Mappls Corridor: NH 48 (Golden Quadrilateral via Hubballi / Belagavi)
*Active default when `MAPPLS_API_KEY` is configured in environment / `.env`:*

Mappls Route ADV routes south to connect to the national freight trunk corridor:
Yelahanka $\to$ Outer Ring Road $\to$ Nelamangala $\to$ Tumakuru $\to$ Chitradurga $\to$ Davanagere $\to$ Hubballi $\to$ Belagavi $\to$ Kolhapur $\to$ Satara $\to$ Pune.
- **Driving Distance**: 842.27 km
- **Toll Plazas Detected (22)** *(at default 500m corridor threshold)*:
  1. Bangalore-Nelamangala Toll Plaza (31.19 km)
  2. Nelamangala Toll Plaza (31.83 km)
  3. Kulumapalya Toll Plaza (35.19 km)
  4. Chokkenahalli Toll Plaza (66.67 km)
  5. Karjeevanhally Toll Plaza (109.05 km)
  6. Guilalu Toll Plaza (177.94 km)
  7. Hebbalu Toll Plaza (244.59 km)
  8. Chalageri Toll Plaza (293.12 km)
  9. Bankapur Toll Plaza (358.43 km)
  10. Gabbur Toll Plaza (409.68 km)
  11. Tarihal Toll Plaza (420.26 km)
  12. Kalghatgi Toll Plaza (426.88 km)
  13. Goa Toll Plaza (436.14 km)
  14. Narendra Toll Plaza (439.26 km)
  15. Hirebagewadi Toll Plaza (489.32 km)
  16. Hattargi Toll Plaza (541.68 km)
  17. Kognoli Toll Plaza (595.23 km)
  18. Kini Toll Plaza (635.47 km)
  19. Tasawade Toll Plaza (695.25 km)
  20. Anewadi Toll Plaza (749.77 km)
  21. Khed-Shivapur Toll Plaza (816.83 km)
  22. Khedshivapur Toll Plaza (816.84 km)

### 2. OSRM Corridor: NH 50 / NH 65 Corridor (Fallback / Zero-Credential Provider)
*Active when `ROUTING_PROVIDER=osrm` or when `MAPPLS_API_KEY` is not provided:*

Starting from **Yelahanka (`560064`)** in North Bengaluru, OSRM routes north via Doddaballapur $\to$ Chitradurga $\to$ Hosapete $\to$ Vijayapura (Bijapur) $\to$ Solapur $\to$ Pune.
- **Driving Distance**: 855.03 km
- **Toll Plazas Detected**:
  - **11 Toll Plazas** at the default Docker 500m corridor threshold:
    1. Kadathanamale Toll Plaza (15.05 km)
    2. Hulikunte Toll Plaza (46.09 km)
    3. Chokkenahalli Toll Plaza (71.00 km)
    4. Karjeevanhally Toll Plaza (113.36 km)
    5. Guilalu Toll Plaza (182.23 km)
    6. Kananakatte Toll Plaza (245.46 km)
    7. Thimmalapura Toll Plaza (308.87 km)
    8. Shahapur Toll Plaza (347.17 km)
    9. Vanagera Toll Plaza (401.88 km)
    10. Nagarhalla Toll Plaza (462.48 km)
    11. Bijapur Toll Plaza (524.46 km)
  - *(Note: At a wider 1000m corridor threshold, Hitnal Toll Plaza is also captured at 342.27 km, yielding 12 toll plazas).*

### 3. Routing & Algorithmic Validation
- **On-Route Geometric Filtering**: Every toll plaza returned by the API is mathematically validated to lie within the configured perpendicular corridor threshold (default 500m in Docker, 1000m in standalone) of the calculated highway polyline and is sequenced strictly in ascending road distance from origin to destination.
- **Inter-Provider Route Differences**: Because Mappls and OSRM choose distinct highway corridors between Bengaluru and Pune, they encounter different sets of toll plazas. Both providers share the initial Bengaluru-Tumakuru-Chitradurga stretch (where plazas such as Chokkenahalli, Karjeevanhally, and Guilalu are located) before branching onto their respective routes (NH 48 via Hubballi/Belagavi for Mappls vs. NH 50 via Hosapete/Bijapur/Solapur for OSRM).

---

## 📖 API Contracts

### Find Toll Plazas Between Two Pincodes

- **Endpoint**: `POST /api/v1/toll-plazas`
- **Content-Type**: `application/json`

#### Request Payload
```json
{
  "sourcePincode": "560064",
  "destinationPincode": "411045"
}
```

#### Success Response (`200 OK`)

**When using Mappls (Configured Default Provider):**
```json
{
  "route": {
    "sourcePincode": "560064",
    "destinationPincode": "411045",
    "distanceInKm": 842.27
  },
  "tollPlazas": [
    {
      "name": "Banglaore-Nelamangala Toll Plaza",
      "latitude": 13.048924,
      "longitude": 77.476483,
      "distanceFromSource": 31.19
    },
    {
      "name": "Nelamangala Toll Plaza",
      "latitude": 13.064562,
      "longitude": 77.464731,
      "distanceFromSource": 31.83
    },
    {
      "name": "Kulumapalya Toll Plaza",
      "latitude": 13.090483,
      "longitude": 77.429188,
      "distanceFromSource": 35.19
    },
    {
      "name": "Chokkenahalli Toll Plaza",
      "latitude": 13.302016,
      "longitude": 77.162007,
      "distanceFromSource": 66.67
    }
  ]
}
```

**When using OSRM (Fallback / Offline Provider):**
```json
{
  "route": {
    "sourcePincode": "560064",
    "destinationPincode": "411045",
    "distanceInKm": 855.03
  },
  "tollPlazas": [
    {
      "name": "Kadathanamale Toll Plaza",
      "latitude": 13.2116847,
      "longitude": 77.5528493,
      "distanceFromSource": 15.05
    },
    {
      "name": "Hulikunte Toll Plaza",
      "latitude": 13.2710263,
      "longitude": 77.3475844,
      "distanceFromSource": 46.09
    },
    {
      "name": "Chokkenahalli Toll Plaza",
      "latitude": 13.3020161,
      "longitude": 77.1620066,
      "distanceFromSource": 71.00
    }
  ]
}
```

#### Error Response (`400 Bad Request` - Identical Pincodes)
```json
{
  "timestamp": "2026-11-14T19:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Source and destination pincodes cannot be identical",
  "path": "/api/v1/toll-plazas",
  "validationErrors": null
}
```

#### Error Response (`400 Bad Request` - Invalid Pincode Format)
```json
{
  "timestamp": "2026-11-14T19:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/toll-plazas",
  "validationErrors": [
    "sourcePincode: Source pincode must be a valid 6-digit Indian postal code"
  ]
}
```

#### Error Response (`404 Not Found` - Unresolvable Pincode)
```json
{
  "timestamp": "2026-11-14T19:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Pincode not found: 999999",
  "path": "/api/v1/toll-plazas",
  "validationErrors": null
}
```

#### Error Response (`502 Bad Gateway` - Upstream Routing Provider Error)
```json
{
  "timestamp": "2026-11-14T19:30:00",
  "status": 502,
  "error": "Bad Gateway",
  "message": "Routing provider error: Routing provider unavailable or connection timed out",
  "path": "/api/v1/toll-plazas",
  "validationErrors": null
}
```

---

## ⚙️ Environment Variables Reference

| Variable | Description | Default |
|:---|:---|:---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/tollplaza_db` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgres` |
| `POSTGRES_PORT` | Host port mapped by Docker Compose | `5433` |
| `ROUTING_PROVIDER` | Active routing engine: `mappls`, `osrm`, or `auto` | `mappls` (falls back to `osrm` if key unset) |
| `MAPPLS_API_KEY` | Mappls Static REST API Key | *(read from `.env` or environment)* |
| `MAPPLS_BASE_URL` | Mappls Route ADV base endpoint | `https://route.mappls.com/route/direction` |
| `MAPPLS_WHITELISTED_IP` | Whitelisted public IPv4 for Mappls console | `157.48.9.232` |
| `OSRM_BASE_URL` | OSRM routing endpoint | `https://router.project-osrm.org` |
| `GEOCODING_PROVIDER` | Geocoding service for Indian postal codes | `nominatim` |
| `NOMINATIM_BASE_URL` | OpenStreetMap Nominatim endpoint | `https://nominatim.openstreetmap.org` |
| `CORRIDOR_THRESHOLD_METERS` | Highway buffer width in meters | `500.0` (Docker) / `1000.0` (Standalone) |
| `SPRING_JPA_OPEN_IN_VIEW` | Enforce strict repository transaction boundaries | `false` |
| `JAVA_OPTS` | JVM flags (memory limits, IPv4 stack preference) | `-XX:+UseG1GC -Djava.net.preferIPv4Stack=true` |

---

## 🔄 Switching Between Routing Providers (Mappls vs. OSRM)

### In Docker Compose:
To run with Mappls (NH 48 Golden Quadrilateral corridor):
```bash
# In .env: MAPPLS_API_KEY=your_mappls_api_key_here
ROUTING_PROVIDER=mappls docker-compose up -d
```
To run with OSRM (NH 50 corridor, zero credentials needed):
```bash
ROUTING_PROVIDER=osrm docker-compose up -d
```

### In Local Development:
```bash
# Run with Mappls:
export MAPPLS_API_KEY="your_mappls_api_key_here"
export ROUTING_PROVIDER=mappls
mvn spring-boot:run

# Run with OSRM:
export ROUTING_PROVIDER=osrm
mvn spring-boot:run
```

---

## 🧪 Testing Commands

### 1. Run All Automated Unit & Integration Tests (34 Tests)
```bash
mvn test
```

### 2. Live API Testing (curl)

**Happy Path (200 OK):**
```bash
curl -X POST http://localhost:8080/api/v1/toll-plazas \
  -H "Content-Type: application/json" \
  -d '{"sourcePincode":"560064","destinationPincode":"411045"}'
```

**Identical Pincodes (400 Bad Request):**
```bash
curl -X POST http://localhost:8080/api/v1/toll-plazas \
  -H "Content-Type: application/json" \
  -d '{"sourcePincode":"560064","destinationPincode":"560064"}'
```

**Unresolvable Pincode (404 Not Found):**
```bash
curl -X POST http://localhost:8080/api/v1/toll-plazas \
  -H "Content-Type: application/json" \
  -d '{"sourcePincode":"999999","destinationPincode":"411045"}'
```

**Inspect Caffeine Cache Hits via Actuator:**
```bash
curl http://localhost:8080/actuator/metrics/cache.gets?tag=result:hit
```

---

## ☁️ Cloud Deployment on Render (Docker + Managed PostgreSQL)

This application is ready for cloud deployment on **Render** using its multi-stage `Dockerfile` and a managed Render PostgreSQL database.

### Step 1: Provision PostgreSQL on Render (In Render Dashboard)
1. Log in to your [Render Dashboard](https://dashboard.render.com/).
2. Click **New +** and select **PostgreSQL**.
3. Configure the database instance:
   - **Name**: `tollplaza-postgres`
   - **Database**: `tollplaza_db`
   - **User**: `postgres` (or keep Render's auto-generated user)
   - **Region**: Choose a region near your target users (e.g., Singapore or Frankfurt)
   - **Plan**: Free tier
4. Click **Create Database**.
5. Once created, navigate to the database details page and copy the **Internal Database URL** (for example: `postgres://user:password@dpg-xxx-a:5432/tollplaza_db`).
6. Note the JDBC conversion of this URL:
   `jdbc:postgresql://<render-internal-host>:5432/tollplaza_db`

### Step 2: Deploy Web Service via Docker on Render (In Render Dashboard)
1. In the Render Dashboard, click **New +** and select **Web Service**.
2. Connect your Git repository.
3. Configure the service settings:
   - **Name**: `tollplaza-service`
   - **Region**: Select the **same region** chosen for your PostgreSQL instance.
   - **Branch**: `main` (or your target branch)
   - **Runtime**: **Docker** (Render automatically detects the repository's `Dockerfile`).
   - **Instance Type**: Free tier.

### Step 3: Configure Production Environment Variables (In Render Dashboard)
Under the **Environment** tab of your new Web Service, add the following variables:

| Key | Value | Description |
|:---|:---|:---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<render-internal-host>:5432/tollplaza_db` | Render internal database connection URL |
| `SPRING_DATASOURCE_USERNAME` | `<database-user>` | Render database username |
| `SPRING_DATASOURCE_PASSWORD` | `<database-password>` | Render database password |
| `ROUTING_PROVIDER` | `osrm` *(or `mappls`)* | `osrm` is recommended for cloud free tiers with shared/dynamic egress IPs; use `mappls` if your key allows cloud IP ranges |
| `MAPPLS_API_KEY` | `your_mappls_api_key_here` | *(Optional)* Only needed if using Mappls |
| `MAPPLS_BASE_URL` | `https://route.mappls.com/route/direction` | Official Mappls Route ADV endpoint |
| `CORRIDOR_THRESHOLD_METERS` | `1000.0` | Spatial highway buffer width |
| `SPRING_JPA_OPEN_IN_VIEW` | `false` | Prevents connection pool starvation |
| `PORT` | `8080` | Port exposed by Spring Boot container |

Under **Advanced Settings**:
- **Health Check Path**: `/actuator/health`

Click **Create Web Service**. Render will automatically build the Docker image, run database migrations on startup, and seed the toll dataset from `toll_plaza_india.csv`.

### Step 4: Verification & Live Health Checks (Placeholder URLs)
Once the deployment finishes on Render, verify the service using your assigned Render URL:

*Replace `https://<your-service-name>.onrender.com` with your actual Render deployment URL:*

1. **Actuator Health Check**:
   ```bash
   curl https://<your-service-name>.onrender.com/actuator/health
   ```
   *Expected Response:* `{"status":"UP","components":{"db":{"status":"UP"}}}`

2. **Swagger UI Interactive Documentation**:
   ```
   https://<your-service-name>.onrender.com/swagger-ui.html
   ```

3. **OpenAPI Specification**:
   ```
   https://<your-service-name>.onrender.com/v3/api-docs
   ```

4. **Live Route & Toll Discovery Test**:
   ```bash
   curl -X POST https://<your-service-name>.onrender.com/api/v1/toll-plazas \
     -H "Content-Type: application/json" \
     -d '{"sourcePincode":"560064","destinationPincode":"411045"}'
   ```

