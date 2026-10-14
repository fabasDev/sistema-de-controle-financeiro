package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.service.AccountService;
import io.github.fabasdev.financas.service.InvalidInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas públicas: entrar, criar conta e recuperar a senha com o código. */
@Controller
public class AuthController {

    static final String RECOVERY_CODE = "recoveryCode";

    /** Limites de tamanho de entrada. As regras de verdade ficam no AccountService. */
    public record RegisterForm(
            @NotBlank(message = "Escolha um nome de usuário.") @Size(max = 60, message = "Use no máximo 60 caracteres.")
            String username,
            @NotBlank(message = "Crie uma senha.") @Size(max = 200, message = "Senha longa demais.")
            String password,
            @NotBlank(message = "Repita a senha.") @Size(max = 200, message = "Senha longa demais.")
            String confirm) {

        static RegisterForm empty() {
            return new RegisterForm("", "", "");
        }
    }

    public record RecoverForm(
            @NotBlank(message = "Informe seu usuário.") @Size(max = 60, message = "Use no máximo 60 caracteres.")
            String username,
            @NotBlank(message = "Informe o código de recuperação.") @Size(max = 64, message = "Código inválido.")
            String code,
            @NotBlank(message = "Crie uma senha nova.") @Size(max = 200, message = "Senha longa demais.")
            String password,
            @NotBlank(message = "Repita a senha nova.") @Size(max = 200, message = "Senha longa demais.")
            String confirm) {

        static RecoverForm empty() {
            return new RecoverForm("", "", "", "");
        }
    }

    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        return isLoggedIn(authentication) ? "redirect:/" : "auth/login";
    }

    @GetMapping("/cadastro")
    public String registerForm(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("form", RegisterForm.empty());
        return "auth/register";
    }

    @PostMapping("/cadastro")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult errors,
            HttpServletRequest request, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                String code = accounts.register(form.username(), form.password(), form.confirm(),
                        request.getRemoteAddr());
                redirect.addFlashAttribute(RECOVERY_CODE, code);
                return "redirect:/cadastro/codigo";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return "auth/register";
    }

    /** Mostra o código uma vez só: ele chega por flash attribute e some no próximo acesso. */
    @GetMapping("/cadastro/codigo")
    public String registeredCode(Model model) {
        if (!model.containsAttribute(RECOVERY_CODE)) {
            return "redirect:/login";
        }
        model.addAttribute("afterRecovery", false);
        return "auth/recovery-code";
    }

    @GetMapping("/recuperar")
    public String recoverForm(Model model) {
        model.addAttribute("form", RecoverForm.empty());
        return "auth/recover";
    }

    @PostMapping("/recuperar")
    public String recover(@Valid @ModelAttribute("form") RecoverForm form, BindingResult errors,
            HttpServletRequest request, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                String code = accounts.recover(form.username(), form.code(), form.password(), form.confirm(),
                        request.getRemoteAddr());
                redirect.addFlashAttribute(RECOVERY_CODE, code);
                return "redirect:/recuperar/codigo";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return "auth/recover";
    }

    @GetMapping("/recuperar/codigo")
    public String recoveredCode(Model model) {
        if (!model.containsAttribute(RECOVERY_CODE)) {
            return "redirect:/login";
        }
        model.addAttribute("afterRecovery", true);
        return "auth/recovery-code";
    }

    @GetMapping("/privacidade")
    public String privacy() {
        return "privacy";
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
