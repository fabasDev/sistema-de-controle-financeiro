package io.github.fabasdev.financas.controller.form;

import io.github.fabasdev.financas.domain.Money;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.validation.BindingResult;

/**
 * Converte o texto dos campos de valor ("1.234,56") em BigDecimal. Se o valor for inválido,
 * registra o erro ao lado do campo e devolve null.
 */
public final class FormValues {

    private FormValues() {
    }

    /** Valor em R$. {@code positive}: precisa ser maior que zero (senão, zero é aceito). */
    public static BigDecimal money(String raw, String field, BindingResult errors, boolean required,
            boolean positive) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                errors.rejectValue(field, "required", "Informe um valor.");
            }
            return required ? null : (positive ? null : Money.ZERO);
        }
        Optional<BigDecimal> parsed = Money.parse(raw);
        if (parsed.isEmpty()) {
            errors.rejectValue(field, "invalid", "Valor inválido. Use o formato 1.234,56.");
            return null;
        }
        BigDecimal value = parsed.get();
        if (positive ? value.signum() <= 0 : value.signum() < 0) {
            errors.rejectValue(field, "range", positive ? "Informe um valor maior que zero." : "O valor não pode ser negativo.");
            return null;
        }
        if (value.compareTo(Money.MAX) > 0) {
            errors.rejectValue(field, "range", "Valor alto demais.");
            return null;
        }
        return value;
    }

    /** Percentual de 0 a 100, com até 2 casas. */
    public static BigDecimal percent(String raw, String field, BindingResult errors) {
        Optional<BigDecimal> parsed = raw == null || raw.isBlank() ? Optional.empty() : Money.parse(raw);
        if (parsed.isEmpty() || parsed.get().signum() < 0 || parsed.get().compareTo(Money.HUNDRED) > 0) {
            errors.rejectValue(field, "range", "Use um número de 0 a 100.");
            return null;
        }
        return parsed.get();
    }
}
