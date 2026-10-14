package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.form.FormValues;
import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.BusinessRuleException;
import io.github.fabasdev.financas.service.CategoryService;
import io.github.fabasdev.financas.service.CategoryService.CategoryData;
import io.github.fabasdev.financas.service.GoalService;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.NotFoundException;
import io.github.fabasdev.financas.service.RuleService;
import io.github.fabasdev.financas.service.undo.UndoAction;
import io.github.fabasdev.financas.service.undo.UndoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
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

/** Regra de distribuição (potes) e categorias de gasto. */
@Controller
@RequestMapping("/planejamento")
public class PlanningController {

    public record BucketForm(
            @NotBlank(message = "Dê um nome ao pote.") @Size(max = 60, message = "Use no máximo 60 caracteres.") String name,
            @NotBlank(message = "Informe o percentual.") @Size(max = 10, message = "Use um número de 0 a 100.") String percent,
            @NotNull(message = "Escolha o tipo.") BucketKind kind,
            @Size(max = 500, message = "Use no máximo 500 caracteres.") String description) {
    }

    /** O ícone é o nome de um ícone do Lucide (só letras e números). */
    public record CategoryForm(
            @NotBlank(message = "Dê um nome à categoria.") @Size(max = 60, message = "Use no máximo 60 caracteres.") String name,
            @NotBlank(message = "Escolha um ícone.") @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9]+", message = "Ícone inválido.") String icon,
            @NotNull(message = "Escolha um pote.") Long bucketId,
            @Size(max = 20, message = "Valor inválido.") String budget,
            Long goalId) {
    }

    private final RuleService rules;
    private final CategoryService categories;
    private final GoalService goals;
    private final BucketRepository buckets;
    private final UndoService undo;

    public PlanningController(RuleService rules, CategoryService categories, GoalService goals,
            BucketRepository buckets, UndoService undo) {
        this.rules = rules;
        this.categories = categories;
        this.goals = goals;
        this.buckets = buckets;
        this.undo = undo;
    }

    @GetMapping
    public String overview(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        List<Bucket> userBuckets = buckets.findByUserIdOrderBySortOrderAscIdAsc(me.getId());
        model.addAttribute("buckets", userBuckets);
        BigDecimal total = Money.sum(userBuckets.stream().map(Bucket::getPercent).toList());
        model.addAttribute("total", total);
        model.addAttribute("totalIs100", total.compareTo(Money.HUNDRED) == 0);
        model.addAttribute("presets", rules.listPresets());
        model.addAttribute("presetId", goals.settings(me.getId()).getPresetId());
        model.addAttribute("categories", categories.list(me.getId()));
        model.addAttribute("kinds", BucketKind.values());
        model.addAttribute("maxBuckets", RuleService.MAX_BUCKETS);
        return "planning/overview";
    }

    @PostMapping("/regra")
    public String applyPreset(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam("presetId") String presetId,
            RedirectAttributes redirect) {
        try {
            UndoAction action = rules.applyPreset(me.getId(), presetId);
            redirect.addFlashAttribute("success", "Regra aplicada. As categorias foram para os novos potes.");
            redirect.addFlashAttribute(UndoController.UNDO_TOKEN, undo.offer(me.getId(), action, "/planejamento"));
        } catch (NotFoundException e) {
            redirect.addFlashAttribute("error", "Escolha uma regra da lista.");
        }
        return "redirect:/planejamento";
    }

    @PostMapping("/potes")
    public String addBucket(@AuthenticationPrincipal AppUserPrincipal me, RedirectAttributes redirect) {
        try {
            rules.addBucket(me.getId());
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/planejamento";
    }

    @PostMapping("/potes/normalizar")
    public String normalize(@AuthenticationPrincipal AppUserPrincipal me, RedirectAttributes redirect) {
        rules.normalizeBuckets(me.getId());
        redirect.addFlashAttribute("success", "Percentuais ajustados para somar 100%.");
        return "redirect:/planejamento";
    }

    @GetMapping("/potes/{id}/editar")
    public String editBucket(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        Bucket b = buckets.findByIdAndUserId(id, me.getId()).orElseThrow(NotFoundException::new);
        model.addAttribute("form", new BucketForm(b.getName(), b.getPercent().stripTrailingZeros().toPlainString()
                .replace('.', ','), b.getKind(), b.getDescription()));
        model.addAttribute("bucketId", id);
        model.addAttribute("kinds", BucketKind.values());
        return "planning/bucket-form";
    }

    @PostMapping("/potes/{id}")
    public String updateBucket(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            @Valid @ModelAttribute("form") BucketForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        buckets.findByIdAndUserId(id, me.getId()).orElseThrow(NotFoundException::new);
        BigDecimal percent = errors.hasFieldErrors("percent") ? null : FormValues.percent(form.percent(), "percent", errors);
        if (!errors.hasErrors()) {
            rules.updateBucket(me.getId(), id, form.name().strip(), percent, form.kind(),
                    form.description() == null || form.description().isBlank() ? null : form.description().strip());
            redirect.addFlashAttribute("success", "Pote atualizado.");
            return "redirect:/planejamento";
        }
        model.addAttribute("bucketId", id);
        model.addAttribute("kinds", BucketKind.values());
        return "planning/bucket-form";
    }

    @GetMapping("/potes/{id}/excluir")
    public String confirmRemoveBucket(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("bucket", buckets.findByIdAndUserId(id, me.getId()).orElseThrow(NotFoundException::new));
        return "planning/bucket-delete";
    }

    @PostMapping("/potes/{id}/excluir")
    public String removeBucket(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            UndoAction action = rules.removeBucket(me.getId(), id);
            redirect.addFlashAttribute("success", "Pote removido. As categorias dele foram para o primeiro pote.");
            redirect.addFlashAttribute(UndoController.UNDO_TOKEN, undo.offer(me.getId(), action, "/planejamento"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/planejamento";
    }

    @GetMapping("/categorias/nova")
    public String newCategory(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        return showCategoryForm(me, new CategoryForm("", "Tag", null, "", null), null, model);
    }

    @PostMapping("/categorias")
    public String createCategory(@AuthenticationPrincipal AppUserPrincipal me,
            @Valid @ModelAttribute("form") CategoryForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        CategoryData data = toData(form, errors);
        if (data != null) {
            try {
                categories.create(me.getId(), data);
                redirect.addFlashAttribute("success", "Categoria criada.");
                return "redirect:/planejamento";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return showCategoryForm(me, form, null, model);
    }

    @GetMapping("/categorias/{id}/editar")
    public String editCategory(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        Category c = categories.get(me.getId(), id);
        CategoryForm form = new CategoryForm(c.getName(), c.getIcon(), c.getBucket().getId(),
                c.getBudget() == null ? "" : c.getBudget().toPlainString().replace('.', ','),
                c.getGoal() == null ? null : c.getGoal().getId());
        return showCategoryForm(me, form, id, model);
    }

    @PostMapping("/categorias/{id}")
    public String updateCategory(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            @Valid @ModelAttribute("form") CategoryForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        categories.get(me.getId(), id);
        CategoryData data = toData(form, errors);
        if (data != null) {
            try {
                categories.update(me.getId(), id, data);
                redirect.addFlashAttribute("success", "Categoria atualizada.");
                return "redirect:/planejamento";
            } catch (InvalidInputException e) {
                errors.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return showCategoryForm(me, form, id, model);
    }

    @GetMapping("/categorias/{id}/excluir")
    public String confirmRemoveCategory(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            Model model) {
        model.addAttribute("category", categories.get(me.getId(), id));
        return "planning/category-delete";
    }

    @PostMapping("/categorias/{id}/excluir")
    public String removeCategory(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            RedirectAttributes redirect) {
        CategoryService.RemoveOutcome outcome = categories.remove(me.getId(), id);
        redirect.addFlashAttribute("success", outcome.result() == CategoryService.RemoveResult.REMOVED
                ? "Categoria apagada."
                : "A categoria tem lançamentos, então foi arquivada: some dos formulários, mas o histórico continua.");
        redirect.addFlashAttribute(UndoController.UNDO_TOKEN, undo.offer(me.getId(), outcome.undo(), "/planejamento"));
        return "redirect:/planejamento";
    }

    @PostMapping("/categorias/{id}/reativar")
    public String unarchive(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
            RedirectAttributes redirect) {
        categories.unarchive(me.getId(), id);
        redirect.addFlashAttribute("success", "Categoria reativada.");
        return "redirect:/planejamento";
    }

    private String showCategoryForm(AppUserPrincipal me, CategoryForm form, Long id, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("editingId", id);
        model.addAttribute("buckets", buckets.findByUserIdOrderBySortOrderAscIdAsc(me.getId()));
        model.addAttribute("goals", goals.listEntities(me.getId()));
        return "planning/category-form";
    }

    private static CategoryData toData(CategoryForm form, BindingResult errors) {
        BigDecimal budget = errors.hasFieldErrors("budget") ? null
                : FormValues.money(form.budget(), "budget", errors, false, false);
        if (errors.hasErrors()) {
            return null;
        }
        // Limite 0 ou vazio = sem limite.
        return new CategoryData(form.name().strip(), form.icon(), form.bucketId(),
                budget == null || budget.signum() == 0 ? null : budget, form.goalId());
    }
}
