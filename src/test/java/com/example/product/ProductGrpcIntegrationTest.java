package com.example.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.product.v1.AdjustStockRequest;
import com.example.product.v1.CreateProductRequest;
import com.example.product.v1.DeleteProductRequest;
import com.example.product.v1.GetProductRequest;
import com.example.product.v1.ListProductsRequest;
import com.example.product.v1.ProductServiceGrpc;
import com.example.product.v1.UpdateProductRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = ProductServiceGrpc.ProductServiceBlockingStub.class)
class ProductGrpcIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void grpcPort(DynamicPropertyRegistry registry) {
		registry.add("spring.grpc.server.port", () -> 0);
	}

	@Autowired
	private ProductServiceGrpc.ProductServiceBlockingStub productStub;

	@Test
	void fullCrudLifecycle() {
		var created = productStub.createProduct(CreateProductRequest.newBuilder()
				.setName("Keyboard")
				.setDescription("Mechanical")
				.setPrice("79.99")
				.setStock(10)
				.build());

		assertThat(created.getId()).isNotBlank();
		assertThat(created.getName()).isEqualTo("Keyboard");
		assertThat(created.getPrice()).isEqualTo("79.99");

		var fetched = productStub.getProduct(GetProductRequest.newBuilder()
				.setId(created.getId())
				.build());
		assertThat(fetched.getDescription()).isEqualTo("Mechanical");

		var listed = productStub.listProducts(ListProductsRequest.newBuilder()
				.setPage(0)
				.setSize(10)
				.setNameContains("key")
				.build());
		assertThat(listed.getTotalCount()).isGreaterThanOrEqualTo(1);
		assertThat(listed.getProductsList()).anyMatch(p -> p.getId().equals(created.getId()));

		var updated = productStub.updateProduct(UpdateProductRequest.newBuilder()
				.setId(created.getId())
				.setName("Keyboard Pro")
				.setDescription("Hot-swap")
				.setPrice("99.99")
				.setStock(8)
				.build());
		assertThat(updated.getName()).isEqualTo("Keyboard Pro");
		assertThat(updated.getStock()).isEqualTo(8);

		var adjusted = productStub.adjustStock(AdjustStockRequest.newBuilder()
				.setId(created.getId())
				.setDelta(-2)
				.build());
		assertThat(adjusted.getStock()).isEqualTo(6);

		var deleted = productStub.deleteProduct(DeleteProductRequest.newBuilder()
				.setId(created.getId())
				.build());
		assertThat(deleted.getDeleted()).isTrue();

		assertThatThrownBy(() -> productStub.getProduct(GetProductRequest.newBuilder()
				.setId(created.getId())
				.build()))
				.isInstanceOf(StatusRuntimeException.class)
				.satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
						.isEqualTo(Status.Code.NOT_FOUND));
	}

	@Test
	void adjustStockRejectsInsufficientInventory() {
		var created = productStub.createProduct(CreateProductRequest.newBuilder()
				.setName("Mouse")
				.setDescription("Wireless")
				.setPrice("29.99")
				.setStock(1)
				.build());

		assertThatThrownBy(() -> productStub.adjustStock(AdjustStockRequest.newBuilder()
				.setId(created.getId())
				.setDelta(-5)
				.build()))
				.isInstanceOf(StatusRuntimeException.class)
				.satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
						.isEqualTo(Status.Code.INVALID_ARGUMENT));
	}

	@Test
	void createRejectsInvalidPrice() {
		assertThatThrownBy(() -> productStub.createProduct(CreateProductRequest.newBuilder()
				.setName("Bad")
				.setPrice("not-a-number")
				.setStock(1)
				.build()))
				.isInstanceOf(StatusRuntimeException.class)
				.satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
						.isEqualTo(Status.Code.INVALID_ARGUMENT));
	}
}
