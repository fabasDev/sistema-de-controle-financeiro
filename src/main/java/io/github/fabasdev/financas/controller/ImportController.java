package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.backup.BackupImportService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Importar um backup criptografado: o exportado pelo app antigo (React) ou pelo "Exportar meus dados". */
@Controller
@RequestMapping("/ajustes/importar")
public class ImportController {

    private final BackupImportService importer;

    public ImportController(BackupImportService importer) {
        this.importer = importer;
    }

    @GetMapping
    public String form() {
        return "settings/import";
    }

    @PostMapping
    public String importBackup(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "arquivo", required = false) MultipartFile file,
            @RequestParam(name = "senha", defaultValue = "") String password,
            @RequestParam(name = "confirmo", required = false) String confirm, HttpServletRequest request,
            Model model) throws IOException {
        if (confirm == null) {
            model.addAttribute("error", "Marque a confirmação: os dados atuais desta conta serão substituídos.");
            return "settings/import";
        }
        try {
            byte[] bytes = file == null ? new byte[0] : file.getBytes();
            model.addAttribute("result", importer.importLegacy(me.getId(), bytes, password, request.getRemoteAddr()));
        } catch (InvalidInputException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "settings/import";
    }
}
