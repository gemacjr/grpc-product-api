# gRPC Spring Boot Product API — Design Spec

**Date:** 2026-09-16  
**Status:** Approved in conversation; awaiting final review  
**Stack:** Java 21, Spring Boot 3.x, Gradle, spring-grpc, Spring Data JPA, PostgreSQL

## Goal

Replace the empty Scala/sbt stub in this workspace with a comprehensive Spring Boot gRPC API that provides full CRUD for `Product`, backed by PostgreSQL. Local development uses Docker Compose; production/external databases are supported via environment variables. Verification is via `grpcurl` examples in the README (no Java client module).

## Architecture

Single Gradle Java module:

```
grpc_client/
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
├── src/main/proto/product.proto
├── src/main/java/.../
│   ├── GrpcProductApplication.java
│   ├── entity/Product.java
│   ├── repository/ProductRepository.java
│   ├── service/ProductService.java
│   └── grpc/ProductGrpcService.java
├── src/main/resources/application.yml
└── src/test/java/.../
```

**Request flow:** gRPC client → `ProductGrpcService` → `ProductService` → `ProductRepository` (JPA) → PostgreSQL.

**Ports:**
- gRPC: `9090`
- HTTP Actuator (health): `8080`

**Schema:** Hibernate `ddl-auto=update` for local development. Flyway is out of scope for the initial delivery.

## Data model

Table: `products`

| Column        | Type           | Constraints                          |
|---------------|----------------|--------------------------------------|
| `id`          | UUID           | Primary key, generated               |
| `name`        | varchar(255)   | Not null                             |
| `description` | text           | Nullable                             |
| `price`       | numeric(12,2)  | Not null, ≥ 0                        |
| `stock`       | integer        | Not null, ≥ 0                        |
| `created_at`  | timestamptz    | Set on create                        |
| `updated_at`  | timestamptz    | Updated on modification              |

**Proto money representation:** `price` is a `string` in protobuf (decimal text) and maps to `BigDecimal` in Java to avoid floating-point errors.

## gRPC contract

Service: `ProductService`

| RPC             | Request                                      | Response                         |
|-----------------|----------------------------------------------|----------------------------------|
| `CreateProduct` | name, description, price, stock              | Product                          |
| `GetProduct`    | id                                           | Product                          |
| `ListProducts`  | page, size (defaults: page=0, size=20)        | repeated Product + total_count   |
| `UpdateProduct` | id, name, description, price, stock          | Product                          |
| `DeleteProduct` | id                                           | `DeleteProductResponse { bool deleted }` |

`Product` message fields: `id`, `name`, `description`, `price`, `stock`, `created_at`, `updated_at`. Timestamps use `google.protobuf.Timestamp` (UTC).

## Error handling

| Condition                         | gRPC status           |
|-----------------------------------|-----------------------|
| Blank name, invalid price/stock   | `INVALID_ARGUMENT`    |
| Unknown product id                | `NOT_FOUND`           |
| Unexpected server failure         | `INTERNAL`            |

Server logs full exceptions; clients receive status + short description only (no stack traces).

## Configuration & deployment

**Docker Compose:** PostgreSQL 16 on host port `5432`, named volume for data, default credentials documented in README.

**`application.yml` defaults** wired to Compose service name/`localhost`, overridable via:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`

**Run sequence:**
1. `docker compose up -d`
2. `./gradlew bootRun`
3. Exercise RPCs with `grpcurl` (examples in README)

## Testing

- Unit tests for `ProductService` validation and not-found behavior
- One Spring Boot integration test using Testcontainers PostgreSQL for create/get/list/update/delete

## Out of scope

- Authentication / authorization
- Separate gRPC client module
- Flyway/Liquibase migrations
- Reactive stack (R2DBC)
- Soft deletes / audit trail beyond `created_at` / `updated_at`

## Success criteria

- All five CRUD RPCs work against Postgres via `grpcurl`
- Compose and external-DB env config both documented and functional
- Invalid input and missing entities map to correct gRPC statuses
- Tests cover service logic and end-to-end CRUD against a real Postgres (Testcontainers)
