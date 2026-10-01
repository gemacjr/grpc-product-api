package com.example.product.repository;

import com.example.product.entity.Product;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	@Query("""
			SELECT p FROM Product p
			WHERE (:nameContains IS NULL OR :nameContains = ''
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :nameContains, '%')))
			""")
	Page<Product> findByNameContains(@Param("nameContains") String nameContains, Pageable pageable);
}
