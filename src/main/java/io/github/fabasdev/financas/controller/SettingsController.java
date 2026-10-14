package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.AccountService;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.SampleDataService;
import io.github.fabasdev.financas.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Perfil, senha, código de recuperação, recomeçar do zero e excluir a conta. */
@Controller
@RequestMapping("/ajustes")
public class SettingsController {

    private final SettingsService settings;
    private final AccountService accounts;
    private final SampleDataService sampleData;

    public SettingsController(SettingsService settings, AccountService accounts, SampleDataService sampleData) {
        this.settings = settings;
        this.accounts = accounts;
        this.sampleData = sampleData;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        model.addAttribute("settings", settings.get(me.getId()));
        model.addAttribute("deleteWord", AccountService.DELETE_WORD);
        return "settings/page";
    }

    @PostMapping("/perfil")
    public String updateName(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "displayName", defaultValue = "") String displayName, RedirectAttributes redirect) {
        if (displayName.strip().length() > 60) {
            redirect.addFlashAttribute("error", "Use no máximo 60 caracteres no nome.");
        } else {
            settings.updateDisplayName(me.getId(), displayName);
            redirect.addFlashAttribute("success", "Nome salvo.");
        }
        return "redirect:/ajustes";
    }

    @PostMapping("/senha")
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "currentPassword", defaultValue = "") String current,
            @RequestParam(name = "password", defaultValue = "") String password,
            @RequestParam(name = "confirm", defaultValue = "") String confirm,
            HttpServletRequest request, RedirectAttributes redirect) {
        try {
            accounts.changePassword(me.getId(), current, password, confirm, request.getRemoteAddr(),
                    request.getSession().getId());
            redirect.addFlashAttribute("success", "Senha trocada. As outras sessões abertas foram encerradas.");
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ajustes";
    }

    @PostMapping("/codigo")
    public String newRecoveryCode(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "currentPassword", defaultValue = "") String current, HttpServletRequest request,
            RedirectAttributes redirect) {
        try {
            String code = accounts.newRecoveryCode(me.getId(), current, request.getRemoteAddr());
            redirect.addFlashAttribute("newRecoveryCode", code);
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ajustes";
    }

    /** Apaga os dados financeiros e volta aos dados iniciais. Pede a palavra de confirmação. */
    @PostMapping("/recomecar")
    public String reset(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "confirmWord", defaultValue = "") String word, RedirectAttributes redirect) {
        if (!"APAGAR".equals(word.strip())) {
            redirect.addFlashAttribute("error", "Digite APAGAR para confirmar.");
            return "redirect:/ajustes";
        }
        settings.resetData(me.getId());
        redirect.addFlashAttribute("success", "Pronto. Seus dados voltaram ao começo.");
        return "redirect:/";
    }

    /** Troca os dados da conta por 6 meses fictícios. Pede confirmação (substitui tudo). */
    @PostMapping("/exemplo")
    public String sample(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "confirmo", required = false) String confirm, RedirectAttributes redirect) {
        if (confirm == null) {
            redirect.addFlashAttribute("error", "Marque a confirmação: os dados atuais serão substituídos.");
            return "redirect:/ajustes";
        }
        sampleData.load(me.getId());
        redirect.addFlashAttribute("success", "Dados de exemplo carregados. Para voltar ao começo, use \"Recomeçar do zero\" em Ajustes.");
        return "redirect:/";
    }

    @PostMapping("/excluir-conta")
    public String deleteAccount(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "currentPassword", defaultValue = "") String current,
            @RequestParam(name = "confirmWord", defaultValue = "") String word, HttpServletRequest request,
            HttpServletResponse response, Authentication authentication, RedirectAttributes redirect) {
        try {
            accounts.deleteAccount(me.getId(), current, word, request.getRemoteAddr());
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/ajustes";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?excluida";
    }
}
