package com.demo.retailedge.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class InventoryPostgresIntegrationTests {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private InventoryRepository inventoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Test
	void postgresRejectsNegativeStockAtDatabaseBoundary() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
				"insert into inventory (store_id, product_id, stock_quantity, version) values (?, ?, ?, ?)",
				"store-db", "SKU-NEGATIVE", -1, 0L));
	}

	@Test
	void postgresRejectsStaleEntityVersion() {
		Inventory created = inventoryService.create("store-db", "SKU-LOCK", 10);
		Inventory staleCopy = inventoryRepository.findByStoreIdAndProductId("store-db", "SKU-LOCK").orElseThrow();
		inventoryService.updateStock("store-db", "SKU-LOCK", 8, created.getVersion());
		staleCopy.setStockQuantity(7);

		assertThrows(OptimisticLockingFailureException.class, () -> inventoryRepository.saveAndFlush(staleCopy));
		assertEquals(8, inventoryService.getByProduct("store-db", "SKU-LOCK").getStockQuantity());
	}
}