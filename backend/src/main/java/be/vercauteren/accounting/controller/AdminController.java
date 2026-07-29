package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.AdminStatsResponse;
import be.vercauteren.accounting.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.getStats();
    }

    @DeleteMapping("/invoices")
    public ResponseEntity<Void> deleteInvoicesByYear(@RequestParam Integer year) {
        adminService.deleteInvoicesByYear(year);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/suppliers")
    public ResponseEntity<Void> deleteAllSuppliers() {
        adminService.deleteAllSuppliers();
        return ResponseEntity.noContent().build();
    }
}
