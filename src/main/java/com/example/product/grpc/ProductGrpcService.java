package com.example.product.grpc;

import com.example.product.exception.InvalidProductException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.mapper.ProductMapper;
import com.example.product.service.ProductService;
import com.example.product.v1.AdjustStockRequest;
import com.example.product.v1.CreateProductRequest;
import com.example.product.v1.DeleteProductRequest;
import com.example.product.v1.DeleteProductResponse;
import com.example.product.v1.GetProductRequest;
import com.example.product.v1.ListProductsRequest;
import com.example.product.v1.ListProductsResponse;
import com.example.product.v1.Product;
import com.example.product.v1.ProductServiceGrpc;
import com.example.product.v1.UpdateProductRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.data.domain.Page;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

	private final ProductService productService;

	public ProductGrpcService(ProductService productService) {
		this.productService = productService;
	}

	@Override
	public void createProduct(CreateProductRequest request, StreamObserver<Product> responseObserver) {
		try {
			var saved = productService.create(
					request.getName(),
					request.getDescription(),
					request.getPrice(),
					request.getStock());
			responseObserver.onNext(ProductMapper.toProto(saved));
			responseObserver.onCompleted();
		}
		catch (RuntimeException ex) {
			responseObserver.onError(toStatus(ex).asRuntimeException());
		}
	}

	@Override
	public void getProduct(GetProductRequest request, StreamObserver<Product> responseObserver) {
		try {
			var product = productService.getById(request.getId());
			responseObserver.onNext(ProductMapper.toProto(product));
			responseObserver.onCompleted();
		}
		catch (RuntimeException ex) {
			responseObserver.onError(toStatus(ex).asRuntimeException());
		}
	}

	@Override
	public void listProducts(ListProductsRequest request, StreamObserver<ListProductsResponse> responseObserver) {
		try {
			Page<com.example.product.entity.Product> page = productService.list(
					request.getPage(),
					request.getSize(),
					request.getNameContains());

			ListProductsResponse.Builder builder = ListProductsResponse.newBuilder()
					.setTotalCount(page.getTotalElements());
			page.getContent().forEach(product -> builder.addProducts(ProductMapper.toProto(product)));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		}
		catch (RuntimeException ex) {
			responseObserver.onError(toStatus(ex).asRuntimeException());
		}
	}

	@Override
	public void updateProduct(UpdateProductRequest request, StreamObserver<Product> responseObserver) {
		try {
			var updated = productService.update(
					request.getId(),
					request.getName(),
					request.getDescription(),
					request.getPrice(),
					request.getStock());
			responseObserver.onNext(ProductMapper.toProto(updated));
			responseObserver.onCompleted();
		}
		catch (RuntimeException ex) {
			responseObserver.onError(toStatus(ex).asRuntimeException());
		}
	}

	@Override
	public void deleteProduct(DeleteProductRequest request, StreamObserver<DeleteProductResponse> responseObserver) {
		try {
			boolean deleted = productService.delete(request.getId());
			responseObserver.onNext(DeleteProductResponse.newBuilder().setDeleted(deleted).build());
			responseObserver.onCompleted();
		}
		catch (RuntimeException ex) {
			responseObserver.onError(toStatus(ex).asRuntimeException());
		}
	}

	@Override
	public void adjustStock(AdjustStockRequest request, StreamObserver<Product> responseObserver) {
		try {
			var updated = productService.adjustStock(request.getId(), request.getDelta());
			responseObserver.onNext(ProductMapper.toProto(updated));
			responseObserver.onCompleted();
		}
		catch (RuntimeException ex) {
			responseObserver.onError(toStatus(ex).asRuntimeException());
		}
	}

	private static Status toStatus(RuntimeException ex) {
		if (ex instanceof InvalidProductException) {
			return Status.INVALID_ARGUMENT.withDescription(ex.getMessage());
		}
		if (ex instanceof ProductNotFoundException) {
			return Status.NOT_FOUND.withDescription(ex.getMessage());
		}
		return Status.INTERNAL.withDescription("Unexpected server error");
	}
}
