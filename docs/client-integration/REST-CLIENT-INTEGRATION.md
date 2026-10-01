# Client Integration Guide — Call Product gRPC from a Spring Boot REST API

Use this document when you create a **separate** Spring Boot project that exposes REST/JSON and calls **this** Product gRPC service over the network.

| Item | Value |
|------|--------|
| gRPC host (local) | `localhost` (or Docker service name `api`) |
| gRPC port | `9090` |
| Proto package | `product.v1` |
| Java package (generated) | `com.example.product.v1` |
| Service | `product.v1.ProductService` |
| Recommended Boot | **4.1.x** (same major as this server) |
| Recommended Java | **21** |
| Copy of contract | [`product.proto`](./product.proto) (keep in sync with server) |

Plaintext (no TLS) is used locally. For production, enable TLS on both sides and point the client channel at `static://host:9090` with SSL config.

---

## 1. Architecture

```
Browser / HTTP client
        │  JSON over HTTP
        ▼
┌───────────────────────────┐
│  Your new Spring Boot     │
│  REST API (this guide)    │
│  Controllers → Client svc │
└─────────────┬─────────────┘
              │  protobuf / gRPC
              ▼
┌───────────────────────────┐
│  grpc-product-api         │
│  ProductService :9090     │
│  PostgreSQL               │
└───────────────────────────┘
```

Do **not** share the server’s JPA entities. Share only the `.proto` (or a published proto JAR). Generated stubs are the client’s API surface.

---

## 2. Share the contract

1. Copy [`product.proto`](./product.proto) into the REST project at:
   ```
   src/main/proto/product.proto
   ```
2. When the server changes RPCs or fields, update this file in the REST project (or consume a versioned proto artifact).
3. Keep these options unless you intentionally rename packages:
   ```protobuf
   option java_package = "com.example.product.v1";
   option java_multiple_files = true;
   ```

Optional later: publish a small `product-api-proto` Gradle module both apps depend on.

---

## 3. Gradle setup (REST project)

### Plugins

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.6"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### Dependencies

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
    // optional: implementation("io.grpc:grpc-services")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-grpc-client-test")
}
```

### Enable gRPC stub codegen

Spring Boot only attaches `protoc-gen-grpc-java` after you declare a plugin named `grpc`:

```kotlin
protobuf {
    plugins {
        create("grpc")
    }
}
```

Then run `./gradlew compileJava` — stubs appear under `build/generated/sources/proto/...`, including:

- `com.example.product.v1.ProductServiceGrpc.ProductServiceBlockingStub`
- Message types: `Product`, `CreateProductRequest`, …

---

## 4. Configure the gRPC channel

`application.yml` in the REST project:

```yaml
server:
  port: 8081   # keep free of the gRPC server's 8080/9090

spring:
  application:
    name: product-rest-bff
  grpc:
    client:
      channel:
        product:                                    # logical channel name
          target: static://${PRODUCT_GRPC_HOST:localhost}:${PRODUCT_GRPC_PORT:9090}
```

| Environment | `PRODUCT_GRPC_HOST` | Notes |
|-------------|---------------------|--------|
| Local `bootRun` + local/Compose gRPC | `localhost` | Default |
| REST in Docker, gRPC Compose service `api` | `api` | Same Compose network |
| Remote / k8s | service DNS / LB host | Use TLS in real deploys |

---

## 5. Import the blocking stub

On the REST application class:

```java
package com.example.rest;

import com.example.product.v1.ProductServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication
@ImportGrpcClients(
        target = "product",   // must match spring.grpc.client.channel.product
        types = ProductServiceGrpc.ProductServiceBlockingStub.class
)
public class ProductRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductRestApplication.class, args);
    }
}
```

Inject the stub anywhere as a normal Spring bean.

---

## 6. Suggested REST ↔ gRPC mapping

| REST endpoint | gRPC RPC | Notes |
|---------------|----------|--------|
| `POST /api/products` | `CreateProduct` | Body → create request |
| `GET /api/products/{id}` | `GetProduct` | Path id |
| `GET /api/products?page&size&q` | `ListProducts` | `q` → `name_contains` |
| `PUT /api/products/{id}` | `UpdateProduct` | Full replace of fields |
| `DELETE /api/products/{id}` | `DeleteProduct` | Map `deleted` → 204/404 |
| `POST /api/products/{id}/stock` | `AdjustStock` | Body `{ "delta": -1 }` |

### Example JSON DTOs (REST side)

**Create / Update body**

```json
{
  "name": "Wireless Mouse",
  "description": "Ergonomic 2.4GHz",
  "price": "39.50",
  "stock": 80
}
```

**Product response**

```json
{
  "id": "8dd95081-3434-4681-9bfd-53bee5de9738",
  "name": "Wireless Mouse",
  "description": "Ergonomic 2.4GHz",
  "price": "39.50",
  "stock": 80,
  "createdAt": "2026-09-16T16:00:35.382542Z",
  "updatedAt": "2026-09-16T16:00:35.382542Z"
}
```

**Adjust stock body**

```json
{ "delta": -5 }
```

Keep `price` as a **string** in JSON too (matches proto and avoids float rounding).

---

## 7. Sample client service + controller

### Mapper (proto ↔ REST DTO)

```java
package com.example.rest.product;

import com.example.product.v1.Product;
import com.google.protobuf.Timestamp;
import java.time.Instant;

public final class ProductDtoMapper {

    private ProductDtoMapper() {}

    public static ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStock(),
                toInstant(p.getCreatedAt()),
                toInstant(p.getUpdatedAt())
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }
}
```

### Client service

```java
package com.example.rest.product;

import com.example.product.v1.AdjustStockRequest;
import com.example.product.v1.CreateProductRequest;
import com.example.product.v1.DeleteProductRequest;
import com.example.product.v1.GetProductRequest;
import com.example.product.v1.ListProductsRequest;
import com.example.product.v1.ProductServiceGrpc.ProductServiceBlockingStub;
import com.example.product.v1.UpdateProductRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

@Service
public class ProductGrpcClient {

    private final ProductServiceBlockingStub productStub;

    public ProductGrpcClient(ProductServiceBlockingStub productStub) {
        this.productStub = productStub;
    }

    public ProductResponse create(CreateProductRequestDto dto) {
        var created = productStub.createProduct(CreateProductRequest.newBuilder()
                .setName(dto.name())
                .setDescription(dto.description() == null ? "" : dto.description())
                .setPrice(dto.price())
                .setStock(dto.stock())
                .build());
        return ProductDtoMapper.toResponse(created);
    }

    public ProductResponse get(String id) {
        var product = productStub.getProduct(GetProductRequest.newBuilder().setId(id).build());
        return ProductDtoMapper.toResponse(product);
    }

    public ProductListResponse list(int page, int size, String nameContains) {
        var response = productStub.listProducts(ListProductsRequest.newBuilder()
                .setPage(page)
                .setSize(size)
                .setNameContains(nameContains == null ? "" : nameContains)
                .build());
        var items = response.getProductsList().stream()
                .map(ProductDtoMapper::toResponse)
                .toList();
        return new ProductListResponse(items, response.getTotalCount());
    }

    public ProductResponse update(String id, CreateProductRequestDto dto) {
        var updated = productStub.updateProduct(UpdateProductRequest.newBuilder()
                .setId(id)
                .setName(dto.name())
                .setDescription(dto.description() == null ? "" : dto.description())
                .setPrice(dto.price())
                .setStock(dto.stock())
                .build());
        return ProductDtoMapper.toResponse(updated);
    }

    public boolean delete(String id) {
        return productStub.deleteProduct(DeleteProductRequest.newBuilder().setId(id).build())
                .getDeleted();
    }

    public ProductResponse adjustStock(String id, int delta) {
        var updated = productStub.adjustStock(AdjustStockRequest.newBuilder()
                .setId(id)
                .setDelta(delta)
                .build());
        return ProductDtoMapper.toResponse(updated);
    }
}
```

### REST controller sketch

```java
package com.example.rest.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductGrpcClient products;

    public ProductController(ProductGrpcClient products) {
        this.products = products;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequestDto body) {
        return products.create(body);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable String id) {
        return products.get(id);
    }

    @GetMapping
    public ProductListResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        return products.list(page, size, q);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody CreateProductRequestDto body) {
        return products.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        products.delete(id);
    }

    @PostMapping("/{id}/stock")
    public ProductResponse adjustStock(@PathVariable String id, @Valid @RequestBody AdjustStockDto body) {
        return products.adjustStock(id, body.delta());
    }
}

record CreateProductRequestDto(
        @NotBlank String name,
        String description,
        @NotBlank String price,
        @Min(0) int stock
) {}

record AdjustStockDto(@NotNull Integer delta) {}

record ProductResponse(
        String id,
        String name,
        String description,
        String price,
        int stock,
        java.time.Instant createdAt,
        java.time.Instant updatedAt
) {}

record ProductListResponse(java.util.List<ProductResponse> products, long totalCount) {}
```

---

## 8. Map gRPC errors → HTTP

The Product server uses:

| Condition | gRPC status |
|-----------|-------------|
| Validation / insufficient stock | `INVALID_ARGUMENT` |
| Missing product | `NOT_FOUND` |
| Unexpected | `INTERNAL` |

Suggested REST mapping:

```java
package com.example.rest.product;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GrpcExceptionAdvice {

    @ExceptionHandler(StatusRuntimeException.class)
    public ProblemDetail handleGrpc(StatusRuntimeException ex) {
        HttpStatus http = switch (ex.getStatus().getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = ProblemDetail.forStatus(http);
        problem.setDetail(ex.getStatus().getDescription());
        problem.setProperty("grpcCode", ex.getStatus().getCode().name());
        return problem;
    }
}
```

Optional: set deadlines on the stub for each call:

```java
productStub.withDeadlineAfter(3, TimeUnit.SECONDS).getProduct(...);
```

---

## 9. Run both apps locally

Terminal A — gRPC API (this repo):

```bash
docker compose up -d postgres   # if not already running
./gradlew bootRun               # :9090 gRPC, :8080 actuator
```

Terminal B — new REST project:

```bash
export PRODUCT_GRPC_HOST=localhost PRODUCT_GRPC_PORT=9090
./gradlew bootRun               # e.g. :8081
```

Smoke test REST → gRPC:

```bash
curl -s http://localhost:8081/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"From REST","description":"via gRPC","price":"10.00","stock":2}'
```

Direct gRPC sanity check (optional):

```bash
grpcurl -plaintext 127.0.0.1:9090 list
grpcurl -plaintext -d '{"page":0,"size":5}' 127.0.0.1:9090 product.v1.ProductService/ListProducts
```

---

## 10. RPC quick reference

### CreateProduct

- Request: `name`, `description`, `price` (decimal string), `stock`
- Response: `Product`

### GetProduct

- Request: `id` (UUID string)
- Response: `Product` or `NOT_FOUND`

### ListProducts

- Request: `page` (default 0), `size` (default 20, max 100 on server), `name_contains` (optional)
- Response: `products[]`, `total_count`

### UpdateProduct

- Request: `id` + full field set (same as create)
- Response: `Product`

### DeleteProduct

- Request: `id`
- Response: `{ "deleted": true }` or `NOT_FOUND`

### AdjustStock

- Request: `id`, `delta` (negative sells / positive restocks)
- Response: `Product` or `INVALID_ARGUMENT` if stock would go below 0

---

## 11. Checklist for the new project

- [ ] Spring Boot **4.1.x** + Java **21**
- [ ] Copy `product.proto` into `src/main/proto/`
- [ ] `spring-boot-starter-grpc-client` + `protobuf { plugins { create("grpc") } }`
- [ ] `@ImportGrpcClients(target = "product", types = …BlockingStub.class)`
- [ ] `spring.grpc.client.channel.product.target=static://host:9090`
- [ ] REST DTOs use **string** prices
- [ ] `@RestControllerAdvice` maps `StatusRuntimeException` → HTTP
- [ ] Deadlines / timeouts on outbound calls
- [ ] Do not reuse server JPA entities or Postgres from the REST app for Product writes (single source of truth = gRPC API)

---

## 12. Official docs

- Spring Boot gRPC (client): https://docs.spring.io/spring-boot/reference/io/grpc.html

When you scaffold the REST project, start from sections **3–7** of this guide; keep [`product.proto`](./product.proto) as the source of truth for field names and RPCs.
