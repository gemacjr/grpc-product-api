package com.example.product.service;

import com.example.product.entity.Product;
import com.example.product.exception.InvalidProductException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Transactional
	public Product create(String name, String description, String price, int stock) {
		validateName(name);
		BigDecimal parsedPrice = parseAndValidatePrice(price);
		validateStock(stock);

		Product product = new Product();
		product.setName(name.trim());
		product.setDescription(description == null ? "" : description.trim());
		product.setPrice(parsedPrice);
		product.setStock(stock);
		return productRepository.save(product);
	}

	@Transactional(readOnly = true)
	public Product getById(String id) {
		return productRepository.findById(parseId(id))
				.orElseThrow(() -> new ProductNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public Page<Product> list(int page, int size, String nameContains) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? 20 : Math.min(size, 100);
		PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());
		return listWithOptionalNameFilter(nameContains, pageable);
	}

	Page<Product> listWithOptionalNameFilter(String nameContains, PageRequest pageable) {
		if (nameContains == null || nameContains.isBlank()) {
			return productRepository.findAll(pageable);
		}
		return productRepository.findByNameContains(nameContains.trim(), pageable);
	}

	@Transactional
	public Product update(String id, String name, String description, String price, int stock) {
		Product product = getById(id);
		validateName(name);
		BigDecimal parsedPrice = parseAndValidatePrice(price);
		validateStock(stock);

		product.setName(name.trim());
		product.setDescription(description == null ? "" : description.trim());
		product.setPrice(parsedPrice);
		product.setStock(stock);
		return productRepository.save(product);
	}

	@Transactional
	public boolean delete(String id) {
		UUID uuid = parseId(id);
		if (!productRepository.existsById(uuid)) {
			throw new ProductNotFoundException(id);
		}
		productRepository.deleteById(uuid);
		return true;
	}

	/**
	 * Adjusts stock by a relative delta (e.g. +5 after restock, -1 after sale).
	 * Rejects when the result would be negative so oversells surface as errors.
	 */
	@Transactional
	public Product adjustStock(String id, int delta) {
		Product product = getById(id);
		int nextStock = product.getStock() + delta;
		if (nextStock < 0) {
			throw new InvalidProductException(
					"Insufficient stock: current=%d, delta=%d".formatted(product.getStock(), delta));
		}
		product.setStock(nextStock);
		return productRepository.save(product);
	}

	private static UUID parseId(String id) {
		if (id == null || id.isBlank()) {
			throw new InvalidProductException("Product id is required");
		}
		try {
			return UUID.fromString(id.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidProductException("Product id must be a valid UUID");
		}
	}

	private static void validateName(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidProductException("Product name is required");
		}
		if (name.trim().length() > 255) {
			throw new InvalidProductException("Product name must be at most 255 characters");
		}
	}

	private static BigDecimal parseAndValidatePrice(String price) {
		if (price == null || price.isBlank()) {
			throw new InvalidProductException("Product price is required");
		}
		BigDecimal value;
		try {
			value = new BigDecimal(price.trim());
		}
		catch (NumberFormatException ex) {
			throw new InvalidProductException("Product price must be a valid decimal number");
		}
		if (value.compareTo(BigDecimal.ZERO) < 0) {
			throw new InvalidProductException("Product price must be >= 0");
		}
		if (value.scale() > 2) {
			throw new InvalidProductException("Product price must have at most 2 decimal places");
		}
		return value;
	}

	private static void validateStock(int stock) {
		if (stock < 0) {
			throw new InvalidProductException("Product stock must be >= 0");
		}
	}
}
