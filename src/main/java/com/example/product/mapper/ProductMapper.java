package com.example.product.mapper;

import com.example.product.entity.Product;
import com.google.protobuf.Timestamp;
import java.time.Instant;

public final class ProductMapper {

	private ProductMapper() {
	}

	public static com.example.product.v1.Product toProto(Product entity) {
		return com.example.product.v1.Product.newBuilder()
				.setId(entity.getId().toString())
				.setName(entity.getName())
				.setDescription(entity.getDescription() == null ? "" : entity.getDescription())
				.setPrice(entity.getPrice().toPlainString())
				.setStock(entity.getStock())
				.setCreatedAt(toTimestamp(entity.getCreatedAt()))
				.setUpdatedAt(toTimestamp(entity.getUpdatedAt()))
				.build();
	}

	private static Timestamp toTimestamp(Instant instant) {
		return Timestamp.newBuilder()
				.setSeconds(instant.getEpochSecond())
				.setNanos(instant.getNano())
				.build();
	}
}
