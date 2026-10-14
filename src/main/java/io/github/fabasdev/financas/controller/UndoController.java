package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.BusinessRuleException;
import io.github.fabasdev.financas.service.undo.UndoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Botão "Desfazer" que aparece depois de apagar algo. */
@Controller
public class UndoController {

    /** Atributo flash com o token do botão "Desfazer" (lido em fragments/layout :: flash). */
    public static final String UNDO_TOKEN = "undoToken";

    private final UndoService undo;

    public UndoController(UndoService undo) {
        this.undo = undo;
    }

    @PostMapping("/desfazer")
    public String undo(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "token", defaultValue = "") String token, RedirectAttributes redirect) {
        try {
            String returnTo = undo.undo(me.getId(), token);
            redirect.addFlashAttribute("success", "Pronto, desfeito.");
            // returnTo é montado pelo servidor na hora de apagar (nunca vem da requisição).
            return "redirect:" + returnTo;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }
}
