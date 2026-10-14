package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.form.FormValues;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.CategoryService;
import io.github.fabasdev.financas.service.GoalService;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.RecurringService;
import io.github.fabasdev.financas.service.RecurringService.RecurringData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
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

/** Contas fixas e parcelamentos. */
@Controller
@RequestMapping("/contas-fixas")
public class RecurringController {

    /** @param installments parcelas (vazio = sem fim). O último mês é calculado a partir do primeiro. */
    public record RecurringForm(
            @NotNull(message = "Escolha receita ou despesa.") TransactionType type,
            @NotBlank(message = "Dê um nome à conta.") @Size(max = 200, message = "Use no máximo 200 caracteres.")
            String description,
            @NotBlank(message = "Informe um valor.") @Size(max = 20, message = "Valor inválido.") String amount,
            Long categoryId,
            Long goalId,
            @NotNull(message = "Informe o dia.") @Min(value = 1, message = "Use um dia de 1 a 31.")
            @Max(value = 31, message = "Use um dia de 1 a 31.") Integer dayOfMonth,
            @NotNull(message = "Informe o primeiro mês.") YearMonth startMonth,
            @Min(value = 2, message = "Use 2 parcelas ou mais.") @Max(value = 360, message = "Use até 360 parcelas.")
            Integer installments,
            Boolean active) {
    }

    private final RecurringService recurring;
    private final CategoryService categories;
    private final GoalService goals;
    private final Clock clock;

    public RecurringController(RecurringService recurring, CategoryService categories, GoalService goals,
            Clock clock) {
        this.recurring = recurring;
        this.categories = categories;
        this.goals = goals;
        this.clock = clock;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        model.addAttribute("entries", recurring.list(me.getId()));
        model.addAttribute("currentMonth", YearMonth.now(clock));
        return "recurring/list";
    }

    @GetMapping("/nova")
    public String newForm(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        RecurringForm form = new RecurringForm(TransactionType.EXPENSE, "", "", null, null, 10, YearMonth.now(clock),
                null, true);
        return showForm(me, form, null, model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("form") RecurringForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        RecurringData data = toData(form, errors);
        if (data != null) {
            try {
                recurring.create(me.getId(), data);
                redirect.addFlashAttribute("success", "Conta fixa salva.");
                return "redirect:/contas-fixas";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return showForm(me, form, null, model);
    }

    @GetMapping("/{id}/editar")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        RecurringEntry r = recurring.get(me.getId(), id);
        Integer installments = r.getEndMonth() == null ? null
                : (int) java.time.temporal.ChronoUnit.MONTHS.between(r.getStartMonth(), r.getEndMonth()) + 1;
        RecurringForm form = new RecurringForm(r.getType(), r.getDescription(),
                r.getAmount().toPlainString().replace('.', ','),
                r.getCategory() == null ? null : r.getCategory().getId(), r.getGoal() == null ? null : r.getGoal().getId(),
                r.getDayOfMonth(), r.getStartMonth(), installments, r.isActive());
        return showForm(me, form, id, model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            @Valid @ModelAttribute("form") RecurringForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        recurring.get(me.getId(), id);
        RecurringData data = toData(form, errors);
        if (data != null) {
            try {
                recurring.update(me.getId(), id, data);
                redirect.addFlashAttribute("success", "Conta fixa atualizada.");
                return "redirect:/contas-fixas";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return showForm(me, form, id, model);
    }

    @GetMapping("/{id}/excluir")
    public String confirmDelete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("entry", recurring.get(me.getId(), id));
        return "recurring/delete";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            RedirectAttributes redirect) {
        recurring.remove(me.getId(), id);
        redirect.addFlashAttribute("success", "Conta fixa apagada. Os lançamentos já feitos continuam no histórico.");
        return "redirect:/contas-fixas";
    }

    private String showForm(AppUserPrincipal me, RecurringForm form, Long id, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("editingId", id);
        model.addAttribute("categories", categories.list(me.getId()).stream()
                .filter(c -> !c.isArchived() || c.getId().equals(form.categoryId()))
                .sorted(Comparator.comparing(Category::getName))
                .toList());
        model.addAttribute("goals", goals.listEntities(me.getId()));
        return "recurring/form";
    }

    private static RecurringData toData(RecurringForm form, BindingResult errors) {
        BigDecimal amount = FormValues.money(form.amount(), "amount", errors, true, true);
        if (form.startMonth() != null && (form.startMonth().getYear() < 2000 || form.startMonth().getYear() > 2100)) {
            errors.rejectValue("startMonth", "range", "Use um mês entre 2000 e 2100.");
        }
        if (form.type() == TransactionType.EXPENSE && form.categoryId() == null) {
            errors.rejectValue("categoryId", "required", "Escolha uma categoria.");
        }
        if (errors.hasErrors()) {
            return null;
        }
        YearMonth end = form.installments() == null ? null : form.startMonth().plusMonths(form.installments() - 1L);
        boolean expense = form.type() == TransactionType.EXPENSE;
        return new RecurringData(form.type(), form.description().strip(), amount,
                expense ? form.categoryId() : null, expense ? form.goalId() : null, form.dayOfMonth(),
                form.startMonth(), end, form.active() == null || form.active());
    }
}
