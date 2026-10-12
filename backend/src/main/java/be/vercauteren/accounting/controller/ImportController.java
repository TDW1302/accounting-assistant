package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.ExcelImportResponse;
import be.vercauteren.accounting.dto.FileAdoptionResponse;
import be.vercauteren.accounting.service.ExcelImportService;
import be.vercauteren.accounting.service.FileAdoptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ExcelImportService excelImportService;
    private final FileAdoptionService fileAdoptionService;

    @PostMapping("/excel")
    public ResponseEntity<ExcelImportResponse> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(excelImportService.importExcel(file));
    }

    /**
     * Rattache les documents deja presents sur le disque aux factures importees.
     * Ne modifie aucun fichier. Appeler avec dryRun=true pour obtenir le bilan
     * sans rien enregistrer.
     */
    @PostMapping("/adopt-files")
    public ResponseEntity<FileAdoptionResponse> adoptFiles(
            @RequestParam(defaultValue = "false") boolean dryRun) throws IOException {
        return ResponseEntity.ok(fileAdoptionService.adopt(dryRun));
    }
}
