package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.CategoryComparisonResponse;
import be.vercauteren.accounting.dto.SupplierChangesResponse;
import be.vercauteren.accounting.dto.SupplierInventoryResponse;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.service.AnalysisService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lecture seule: ouvert a tout utilisateur connecte, lecteurs compris. */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/categories")
    public CategoryComparisonResponse compareCategories(
        @RequestParam int year,
        @RequestParam int previousYear,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return analysisService.compareCategories(year, previousYear, until);
    }

    @GetMapping("/suppliers/changes")
    public SupplierChangesResponse compareSuppliers(
        @RequestParam int year,
        @RequestParam int previousYear,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return analysisService.compareSuppliers(year, previousYear, until);
    }

    @GetMapping("/suppliers")
    public SupplierInventoryResponse inventory(
        @RequestParam int year,
        @RequestParam(required = false) ExpenseCategory category) {
        return analysisService.inventory(year, category);
    }
}
