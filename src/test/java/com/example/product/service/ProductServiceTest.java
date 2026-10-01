package com.example.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.product.entity.Product;
import com.example.product.exception.InvalidProductException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.repository.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository productRepository;

	private ProductService productService;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productRepository);
	}

	@Test
	void createPersistsValidatedProduct() {
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product product = invocation.getArgument(0);
			product.setId(UUID.randomUUID());
			return product;
		});

		Product saved = productService.create("Widget", "A widget", "12.50", 3);

		ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("Widget");
		assertThat(captor.getValue().getPrice()).isEqualByComparingTo("12.50");
		assertThat(saved.getId()).isNotNull();
	}

	@Test
	void createRejectsNegativePrice() {
		assertThatThrownBy(() -> productService.create("Widget", "", "-1", 1))
				.isInstanceOf(InvalidProductException.class)
				.hasMessageContaining("price");
	}

	@Test
	void getByIdThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(productRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> productService.getById(id.toString()))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void listUsesNameFilterWhenProvided() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(productRepository.findByNameContains("wid", pageable))
				.thenReturn(Page.empty());

		Page<Product> result = productService.listWithOptionalNameFilter("wid", pageable);

		assertThat(result).isEmpty();
		verify(productRepository).findByNameContains("wid", pageable);
	}

	@Test
	void deleteThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(productRepository.existsById(id)).thenReturn(false);

		assertThatThrownBy(() -> productService.delete(id.toString()))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void createRejectsBlankName() {
		assertThatThrownBy(() -> productService.create("  ", "", "1.00", 1))
				.isInstanceOf(InvalidProductException.class)
				.hasMessageContaining("name");
	}

	@Test
	void adjustStockIncreasesInventory() {
		UUID id = UUID.randomUUID();
		Product existing = new Product();
		existing.setId(id);
		existing.setStock(10);
		when(productRepository.findById(id)).thenReturn(Optional.of(existing));
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Product updated = productService.adjustStock(id.toString(), 5);

		assertThat(updated.getStock()).isEqualTo(15);
	}

	@Test
	void adjustStockRejectsUnderflow() {
		UUID id = UUID.randomUUID();
		Product existing = new Product();
		existing.setId(id);
		existing.setStock(2);
		when(productRepository.findById(id)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> productService.adjustStock(id.toString(), -3))
				.isInstanceOf(InvalidProductException.class)
				.hasMessageContaining("Insufficient stock");
	}
}
