package com.demo.retailedge.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryPageTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void servesThymeleafInventoryPageAndStaticScript() throws Exception {
		mockMvc.perform(get("/inventory-ui"))
				.andExpect(status().isOk())
				.andExpect(view().name("inventory"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Create inventory")));

		mockMvc.perform(get("/js/inventory.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("fetch(path")));
	}
}