# gRPC Product API

Spring Boot 4.1 gRPC service with Product CRUD + stock adjustment on PostgreSQL.

## Stack

- Java 21 + Gradle
- Spring Boot 4.1 (`spring-boot-starter-grpc-server`)
- Spring Data JPA + Flyway + PostgreSQL
- Docker Compose (Postgres and/or full stack)
- Actuator health on HTTP `:8080`
- gRPC on `:9090`

## Quick start (full stack in Docker)

```bash
cp .env.example .env   # optional
docker compose up --build -d
```

- gRPC: `localhost:9090`
- Health: `http://localhost:8080/actuator/health`

## Local API + Compose Postgres

```bash
docker compose up -d postgres
./gradlew bootRun
```

## grpcurl examples

```bash
grpcurl -plaintext localhost:9090 list
```

**Create**

```bash
grpcurl -plaintext -d '{
  "name": "Keyboard",
  "description": "Mechanical",
  "price": "79.99",
  "stock": 10
}' localhost:9090 product.v1.ProductService/CreateProduct
```

**Get** (replace `PRODUCT_ID`)

```bash
grpcurl -plaintext -d '{"id":"PRODUCT_ID"}' \
  localhost:9090 product.v1.ProductService/GetProduct
```

**List** (optional name filter)

```bash
grpcurl -plaintext -d '{"page":0,"size":20,"name_contains":"key"}' \
  localhost:9090 product.v1.ProductService/ListProducts
```

**Update**

```bash
grpcurl -plaintext -d '{
  "id":"PRODUCT_ID",
  "name":"Keyboard Pro",
  "description":"Hot-swap",
  "price":"99.99",
  "stock":8
}' localhost:9090 product.v1.ProductService/UpdateProduct
```

**Delete**

```bash
grpcurl -plaintext -d '{"id":"PRODUCT_ID"}' \
  localhost:9090 product.v1.ProductService/DeleteProduct
```

**Adjust stock** (relative delta; rejects if result would be negative)

```bash
grpcurl -plaintext -d '{"id":"PRODUCT_ID","delta":-2}' \
  localhost:9090 product.v1.ProductService/AdjustStock
```

## External PostgreSQL

Copy `.env.example` or export:

| Variable        | Default     |
|-----------------|-------------|
| `DB_HOST`       | `localhost` |
| `DB_PORT`       | `5432`      |
| `DB_NAME`       | `products`  |
| `DB_USER`       | `product`   |
| `DB_PASSWORD`   | `product`   |
| `GRPC_PORT`     | `9090`      |
| `SERVER_PORT`   | `8080`      |

```bash
export DB_HOST=db.example.com DB_PORT=5432 DB_NAME=products DB_USER=app DB_PASSWORD=secret
./gradlew bootRun
```

Schema is managed by Flyway (`src/main/resources/db/migration`). Hibernate `ddl-auto` is `validate`.

## Calling this API from another Spring Boot REST app

See **[docs/client-integration/REST-CLIENT-INTEGRATION.md](docs/client-integration/REST-CLIENT-INTEGRATION.md)** — setup, channel config, stub injection, REST↔gRPC mapping, error handling, and a copy of [`product.proto`](docs/client-integration/product.proto).

## Tests

```bash
./gradlew test
```

- Unit tests: `ProductServiceTest`
- Integration: `ProductGrpcIntegrationTest` (Testcontainers PostgreSQL + in-process gRPC)

## Project layout

```
src/main/proto/product.proto
src/main/resources/db/migration/V1__create_products.sql
src/main/java/com/example/product/
  entity/Product.java
  repository/ProductRepository.java
  service/ProductService.java
  grpc/ProductGrpcService.java
  mapper/ProductMapper.java
Dockerfile
docker-compose.yml
```

## Errors

| Condition              | gRPC status         |
|------------------------|---------------------|
| Bad input / bad stock  | `INVALID_ARGUMENT`  |
| Missing product        | `NOT_FOUND`         |
| Unexpected failure     | `INTERNAL`          |
