package com.demo.retailedge.inventory;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the inventory operations page. */
@Controller
public class InventoryPageController {

	/** Displays the inventory API console. */
	@GetMapping("/inventory-ui")
	public String inventoryPage() {
		return "inventory";
	}
}