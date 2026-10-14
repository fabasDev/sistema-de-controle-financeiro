package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.form.FormValues;
import io.github.fabasdev.financas.controller.view.MonthParam;
import io.github.fabasdev.financas.controller.view.ViewFormats;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.BusinessRuleException;
import io.github.fabasdev.financas.service.CategoryService;
import io.github.fabasdev.financas.service.GoalService;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.RecurringService;
import io.github.fabasdev.financas.service.TransactionService;
import io.github.fabasdev.financas.service.TransactionService.Filter;
import io.github.fabasdev.financas.service.TransactionService.TransactionData;
import io.github.fabasdev.financas.service.undo.UndoAction;
import io.github.fabasdev.financas.service.undo.UndoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Lançamentos do mês: listar, registrar, editar, marcar como pago e apagar. */
@Controller
@RequestMapping("/lancamentos")
public class TransactionController {

    /**
     * Formulário de lançamento. O valor chega como texto ("1.234,56") e é convertido no controller.
     *
     * @param back mês para onde voltar depois de salvar (mantém a navegação)
     */
    public record TransactionForm(
            @NotNull(message = "Escolha receita ou despesa.") TransactionType type,
            @NotNull(message = "Informe a data.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Size(max = 200, message = "Use no máximo 200 caracteres.") String description,
            @NotBlank(message = "Informe um valor.") @Size(max = 20, message = "Valor inválido.") String amount,
            Long categoryId,
            Long goalId,
            Boolean paid,
            Boolean repeat,
            @Min(value = 2, message = "Use 2 parcelas ou mais.") @Max(value = 360, message = "Use até 360 parcelas.")
            Integer installments) {
    }

    private final TransactionService transactions;
    private final CategoryService categories;
    private final GoalService goals;
    private final RecurringService recurring;
    private final UndoService undo;
    private final ViewFormats formats;
    private final Clock clock;

    public TransactionController(TransactionService transactions, CategoryService categories, GoalService goals,
            RecurringService recurring, UndoService undo, ViewFormats formats, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.goals = goals;
        this.recurring = recurring;
        this.undo = undo;
        this.formats = formats;
        this.clock = clock;
    }

    /** Página e filtros ficam na URL, então voltar depois de editar mantém o que estava na tela. */
    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "mes", required = false) String monthParam,
            @RequestParam(name = "tipo", required = false) TransactionType type,
            @RequestParam(name = "categoria", required = false) Long categoryId,
            @RequestParam(name = "busca", required = false) String search,
            Pageable pageable, Model model) {
        YearMonth month = MonthParam.parse(monthParam, clock);
        recurring.ensureMonth(me.getId(), month);
        String trimmedSearch = search == null ? null : search.strip();
        if (trimmedSearch != null && trimmedSearch.length() > 100) {
            trimmedSearch = trimmedSearch.substring(0, 100);
        }
        model.addAttribute("month", month);
        model.addAttribute("previousMonth", month.minusMonths(1));
        model.addAttribute("nextMonth", month.plusMonths(1));
        model.addAttribute("type", type);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("search", trimmedSearch);
        model.addAttribute("categories", categories.list(me.getId()));
        model.addAttribute("quick", categories.quickAddOptions(me.getId(), LocalDate.now(clock)));
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("page", transactions.page(me.getId(), month, new Filter(type, categoryId, trimmedSearch),
                pageable));
        return "transactions/list";
    }

    /**
     * Lançamento rápido do topo das telas: valor e categoria (data de hoje já vem preenchida).
     * {@code voltar} só aceita as duas telas que têm o formulário, para não virar um redirect aberto.
     */
    @PostMapping("/rapido")
    public String quickAdd(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("form") TransactionForm form,
            BindingResult errors, @RequestParam(name = "voltar", defaultValue = "") String back, Model model,
            RedirectAttributes redirect) {
        TransactionData data = toData(form, errors);
        if (data != null) {
            try {
                Transaction t = transactions.create(me.getId(), data);
                redirect.addFlashAttribute("success", "Gasto de " + formats.money(t.getAmount()) + " salvo.");
                return "inicio".equals(back) ? "redirect:/?mes=" + t.getMonth() : "redirect:/lancamentos?mes=" + t.getMonth();
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        // Com erro, abre o formulário completo com a mensagem ao lado do campo.
        return showForm(me, form, null, model);
    }

    @GetMapping("/novo")
    public String newForm(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "tipo", required = false) TransactionType type, Model model) {
        TransactionForm form = new TransactionForm(type == null ? TransactionType.EXPENSE : type, LocalDate.now(clock),
                "", "", null, null, true, false, null);
        return showForm(me, form, null, model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("form") TransactionForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TransactionData data = toData(form, errors);
        if (data != null) {
            try {
                Transaction t = transactions.create(me.getId(), data);
                redirect.addFlashAttribute("success", "Lançamento salvo.");
                return "redirect:/lancamentos?mes=" + t.getMonth();
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return showForm(me, form, null, model);
    }

    @GetMapping("/{id}/editar")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        Transaction t = transactions.get(me.getId(), id);
        TransactionForm form = new TransactionForm(t.getType(), t.getDate(), t.getDescription(),
                t.getAmount().toPlainString().replace('.', ','),
                t.getCategory() == null ? null : t.getCategory().getId(),
                t.getGoal() == null ? null : t.getGoal().getId(), t.isPaid(), false, null);
        model.addAttribute("fromRecurring", t.getRecurring() != null);
        return showForm(me, form, id, model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            @Valid @ModelAttribute("form") TransactionForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        transactions.get(me.getId(), id);
        TransactionData data = toData(form, errors);
        if (data != null) {
            try {
                transactions.update(me.getId(), id, data);
                redirect.addFlashAttribute("success", "Lançamento atualizado.");
                return "redirect:/lancamentos?mes=" + YearMonth.from(data.date());
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return showForm(me, form, id, model);
    }

    @PostMapping("/{id}/pago")
    public String togglePaid(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        Transaction t = transactions.get(me.getId(), id);
        transactions.togglePaid(me.getId(), id);
        return "redirect:/lancamentos?mes=" + t.getMonth();
    }

    /** Sem JavaScript, apagar passa por esta página de confirmação. O "desfazer" chega na Fase 3. */
    @GetMapping("/{id}/excluir")
    public String confirmDelete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("transaction", transactions.get(me.getId(), id));
        return "transactions/delete";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            RedirectAttributes redirect) {
        YearMonth month = transactions.get(me.getId(), id).getMonth();
        String returnTo = "/lancamentos?mes=" + month;
        try {
            UndoAction action = transactions.delete(me.getId(), id);
            redirect.addFlashAttribute("success", "Lançamento apagado.");
            redirect.addFlashAttribute(UndoController.UNDO_TOKEN, undo.offer(me.getId(), action, returnTo));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + returnTo;
    }

    private String showForm(AppUserPrincipal me, TransactionForm form, Long id, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("editingId", id);
        model.addAttribute("categories", categories.list(me.getId()).stream()
                .filter(c -> !c.isArchived() || c.getId().equals(form.categoryId()))
                .sorted(java.util.Comparator.comparing((Category c) -> c.getBucket().getSortOrder())
                        .thenComparing(Category::getName))
                .toList());
        model.addAttribute("goals", goals.listEntities(me.getId()));
        model.addAttribute("types", TransactionType.values());
        return "transactions/form";
    }

    private static TransactionData toData(TransactionForm form, BindingResult errors) {
        BigDecimal amount = FormValues.money(form.amount(), "amount", errors, true, true);
        if (form.date() != null && (form.date().getYear() < 2000 || form.date().getYear() > 2100)) {
            errors.rejectValue("date", "range", "Use uma data entre 2000 e 2100.");
        }
        if (errors.hasErrors()) {
            return null;
        }
        return new TransactionData(form.type(), form.date(), form.description(), amount, form.categoryId(),
                form.goalId(), Boolean.TRUE.equals(form.paid()), Boolean.TRUE.equals(form.repeat()),
                form.installments());
    }
}
