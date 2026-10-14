package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.form.FormValues;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.ReserveBase;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.BusinessRuleException;
import io.github.fabasdev.financas.service.GoalService;
import io.github.fabasdev.financas.service.GoalService.GoalData;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.InvestmentSuggestionService;
import io.github.fabasdev.financas.service.undo.UndoAction;
import io.github.fabasdev.financas.service.undo.UndoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Metas, reserva de emergência e prioridade entre reserva e investimentos. */
@Controller
@RequestMapping("/metas")
public class GoalController {

    public record GoalForm(
            @NotBlank(message = "Dê um nome à meta.") @Size(max = 60, message = "Use no máximo 60 caracteres.") String name,
            @NotBlank(message = "Escolha um ícone.") @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9]+", message = "Ícone inválido.") String icon,
            @NotNull(message = "Escolha o tipo.") GoalKind kind,
            @Size(max = 20, message = "Valor inválido.") String target,
            YearMonth deadline,
            @Size(max = 20, message = "Valor inválido.") String initialBalance) {
    }

    public record ReserveForm(
            @NotNull(message = "Informe os meses.") @Min(value = 1, message = "Use de 1 a 60 meses.")
            @Max(value = 60, message = "Use de 1 a 60 meses.") Integer months,
            @NotNull(message = "Escolha a base.") ReserveBase base,
            @Size(max = 20, message = "Valor inválido.") String manualCost,
            @NotBlank(message = "Informe a prioridade.") @Size(max = 10) String priority) {
    }

    private final GoalService goals;
    private final InvestmentSuggestionService suggestions;
    private final UndoService undo;
    private final Clock clock;

    public GoalController(GoalService goals, InvestmentSuggestionService suggestions, UndoService undo, Clock clock) {
        this.goals = goals;
        this.suggestions = suggestions;
        this.undo = undo;
        this.clock = clock;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        YearMonth month = YearMonth.now(clock);
        UserSettings s = goals.settings(me.getId());
        model.addAttribute("goals", goals.list(me.getId(), month));
        model.addAttribute("rec", suggestions.recommendation(me.getId(), month));
        if (!model.containsAttribute("reserveForm")) {
            model.addAttribute("reserveForm", new ReserveForm(s.getReserveMonths(), s.getReserveBase(),
                    s.getReserveManualCost().toPlainString().replace('.', ','),
                    s.getReservePriority().stripTrailingZeros().toPlainString().replace('.', ',')));
        }
        model.addAttribute("bases", ReserveBase.values());
        return "goals/list";
    }

    @PostMapping("/reserva")
    public String updateReserve(@AuthenticationPrincipal AppUserPrincipal me,
            @Valid @ModelAttribute("reserveForm") ReserveForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        BigDecimal manual = errors.hasFieldErrors("manualCost") ? null
                : FormValues.money(form.manualCost(), "manualCost", errors, false, false);
        BigDecimal priority = errors.hasFieldErrors("priority") ? null
                : FormValues.percent(form.priority(), "priority", errors);
        if (errors.hasErrors()) {
            return list(me, model);
        }
        goals.updateReserve(me.getId(), new GoalService.ReserveSettings(form.months(), form.base(), manual, priority));
        redirect.addFlashAttribute("success", "Reserva atualizada.");
        return "redirect:/metas";
    }

    @GetMapping("/nova")
    public String newForm(Model model) {
        return showForm(new GoalForm("", "Target", GoalKind.GOAL, "", null, ""), null, model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("form") GoalForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        GoalData data = toData(form, errors);
        if (data != null) {
            try {
                goals.create(me.getId(), data);
                redirect.addFlashAttribute("success", "Meta criada.");
                return "redirect:/metas";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return showForm(form, null, model);
    }

    @GetMapping("/{id}/editar")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        Goal g = goals.get(me.getId(), id);
        GoalForm form = new GoalForm(g.getName(), g.getIcon(), g.getKind(),
                g.getTarget().toPlainString().replace('.', ','), g.getDeadline(),
                g.getInitialBalance().toPlainString().replace('.', ','));
        model.addAttribute("isReserve", g.isReserve());
        return showForm(form, id, model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            @Valid @ModelAttribute("form") GoalForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        Goal g = goals.get(me.getId(), id);
        GoalData data = toData(form, errors);
        if (data != null) {
            try {
                goals.update(me.getId(), id, data);
                redirect.addFlashAttribute("success", "Meta atualizada.");
                return "redirect:/metas";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        model.addAttribute("isReserve", g.isReserve());
        return showForm(form, id, model);
    }

    @GetMapping("/{id}/excluir")
    public String confirmDelete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("goal", goals.get(me.getId(), id));
        return "goals/delete";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            UndoAction action = goals.delete(me.getId(), id);
            redirect.addFlashAttribute("success", "Meta apagada. Os lançamentos continuam, só sem a meta.");
            redirect.addFlashAttribute(UndoController.UNDO_TOKEN, undo.offer(me.getId(), action, "/metas"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/metas";
    }

    private String showForm(GoalForm form, Long id, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("editingId", id);
        model.addAttribute("kinds", new GoalKind[] {GoalKind.GOAL, GoalKind.INVESTMENT});
        return "goals/form";
    }

    private static GoalData toData(GoalForm form, BindingResult errors) {
        BigDecimal target = errors.hasFieldErrors("target") ? null
                : FormValues.money(form.target(), "target", errors, false, false);
        BigDecimal initial = errors.hasFieldErrors("initialBalance") ? null
                : FormValues.money(form.initialBalance(), "initialBalance", errors, false, false);
        if (form.deadline() != null && (form.deadline().getYear() < 2000 || form.deadline().getYear() > 2100)) {
            errors.rejectValue("deadline", "range", "Use um mês entre 2000 e 2100.");
        }
        if (errors.hasErrors()) {
            return null;
        }
        return new GoalData(form.name().strip(), form.icon(), form.kind(), target, form.deadline(), initial);
    }
}
