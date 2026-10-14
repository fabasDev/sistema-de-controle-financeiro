package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.form.FormValues;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.OnboardingService;
import io.github.fabasdev.financas.service.RuleService;
import io.github.fabasdev.financas.service.SettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Assistente inicial: configura a conta nova em uma página só. */
@Controller
@RequestMapping("/comecar")
public class OnboardingController {

    static final Set<Integer> RESERVE_OPTIONS = Set.of(3, 6, 9, 12);

    public record OnboardingForm(
            @Size(max = 60, message = "Use no máximo 60 caracteres.") String displayName,
            @NotBlank(message = "Escolha uma regra.") @Size(max = 20) String presetId,
            @Size(max = 20, message = "Valor inválido.") String income,
            @NotNull(message = "Informe o dia.") @Min(value = 1, message = "Use um dia de 1 a 31.")
            @Max(value = 31, message = "Use um dia de 1 a 31.") Integer incomeDay,
            @Size(max = 20, message = "Valor inválido.") String reserveBalance,
            @NotNull(message = "Escolha quantos meses.") Integer reserveMonths) {
    }

    private final OnboardingService onboarding;
    private final RuleService rules;
    private final SettingsService settings;

    public OnboardingController(OnboardingService onboarding, RuleService rules, SettingsService settings) {
        this.onboarding = onboarding;
        this.rules = rules;
        this.settings = settings;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        var s = settings.get(me.getId());
        OnboardingForm form = new OnboardingForm(s.getDisplayName(), s.getPresetId().equals("custom") ? "50-30-20"
                : s.getPresetId(), "", 5, "", 6);
        return show(form, emptyBills(), model);
    }

    @PostMapping
    public String complete(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("form") OnboardingForm form,
            BindingResult errors, @RequestParam(name = "conta", required = false) List<String> bills, Model model,
            RedirectAttributes redirect) {
        List<String> billValues = bills == null ? emptyBills() : bills;
        BigDecimal income = errors.hasFieldErrors("income") ? null
                : FormValues.money(form.income(), "income", errors, false, false);
        BigDecimal reserve = errors.hasFieldErrors("reserveBalance") ? null
                : FormValues.money(form.reserveBalance(), "reserveBalance", errors, false, false);
        if (form.reserveMonths() != null && !RESERVE_OPTIONS.contains(form.reserveMonths())) {
            errors.rejectValue("reserveMonths", "range", "Escolha 3, 6, 9 ou 12 meses.");
        }
        if (form.presetId() != null && rules.listPresets().stream().noneMatch(p -> p.getId().equals(form.presetId()))) {
            errors.rejectValue("presetId", "invalid", "Escolha uma regra da lista.");
        }
        List<BigDecimal> billAmounts = new ArrayList<>();
        for (int i = 0; i < OnboardingService.COMMON_BILLS.size(); i++) {
            String raw = i < billValues.size() ? billValues.get(i) : "";
            if (raw == null || raw.isBlank()) {
                billAmounts.add(null);
                continue;
            }
            BigDecimal amount = Money.parse(raw).orElse(null);
            if (raw.length() > 20 || amount == null || amount.signum() < 0
                    || amount.compareTo(Money.MAX) > 0) {
                errors.reject("bill", "O valor de \"" + OnboardingService.COMMON_BILLS.get(i).description()
                        + "\" é inválido. Use o formato 1.234,56.");
                billAmounts.add(null);
            } else {
                billAmounts.add(amount);
            }
        }
        if (errors.hasErrors()) {
            return show(form, billValues, model);
        }
        onboarding.complete(me.getId(), new OnboardingService.OnboardingData(form.displayName(), form.presetId(),
                income, form.incomeDay(), billAmounts, reserve, form.reserveMonths()));
        redirect.addFlashAttribute("success", "Pronto! Sua conta está configurada.");
        return "redirect:/";
    }

    @PostMapping("/pular")
    public String skip(@AuthenticationPrincipal AppUserPrincipal me) {
        onboarding.skip(me.getId());
        return "redirect:/";
    }

    private String show(OnboardingForm form, List<String> bills, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("presets", rules.listPresets());
        model.addAttribute("bills", OnboardingService.COMMON_BILLS);
        model.addAttribute("billValues", bills);
        model.addAttribute("reserveOptions", List.of(3, 6, 9, 12));
        return "onboarding";
    }

    private static List<String> emptyBills() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < OnboardingService.COMMON_BILLS.size(); i++) {
            values.add("");
        }
        return values;
    }
}
