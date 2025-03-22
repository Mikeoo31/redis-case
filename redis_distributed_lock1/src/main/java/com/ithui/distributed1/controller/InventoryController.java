package com.ithui.distributed1.controller;

import com.ithui.distributed1.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {

    @Autowired
    @Qualifier("inventoryService")
    private InventoryService inventoryService;

    @GetMapping("/inventory")
    public String getInventory() {
        return inventoryService.getInventory();
    }

    @GetMapping("/inventoryByRedisson")
    public String getInventoryByRedisson() {
        return inventoryService.getInventoryByRedisson();
    }
}
