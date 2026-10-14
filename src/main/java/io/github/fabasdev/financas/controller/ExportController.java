package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.backup.BackupExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/** "Exportar meus dados": baixa um backup criptografado que volta pela tela de importação. */
@Controller
@RequestMapping("/ajustes/exportar")
public class ExportController {

    private final BackupExportService exporter;
    private final Clock clock;

    public ExportController(BackupExportService exporter, Clock clock) {
        this.exporter = exporter;
        this.clock = clock;
    }

    @GetMapping
    public String form() {
        return "settings/export";
    }

    /**
     * Com a senha certa, a resposta é o próprio arquivo, e o navegador baixa sem sair da tela.
     * Com erro, a tela volta com a mensagem. Devolver null diz ao Spring que a resposta já foi escrita.
     */
    @PostMapping
    public ModelAndView export(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "senha", defaultValue = "") String password,
            @RequestParam(name = "confirmacao", defaultValue = "") String confirm, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        byte[] file;
        try {
            file = exporter.export(me.getId(), password, confirm, request.getRemoteAddr());
        } catch (InvalidInputException e) {
            return new ModelAndView("settings/export", Map.of("error", e.getMessage()));
        }
        String name = "financas-backup-" + LocalDate.now(clock) + ".json";
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString());
        response.setContentLength(file.length);
        response.getOutputStream().write(file);
        return null;
    }
}
