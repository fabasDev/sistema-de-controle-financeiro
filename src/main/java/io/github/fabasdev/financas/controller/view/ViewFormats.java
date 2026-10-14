package io.github.fabasdev.financas.controller.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Formatação para as telas, sempre em pt-BR (R$ 1.234,56 e dd/mm/aaaa), qualquer que seja
 * o idioma do navegador. Nos templates: {@code ${@fmt.money(valor)}}.
 */
@Component("fmt")
public class ViewFormats {

    public static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);

    public String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(PT_BR);
        return format.format(value);
    }

    /** Valor para campo de formulário: "1.234,56" (sem R$). */
    public String amount(BigDecimal value) {
        if (value == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    /** Número em pt-BR com até 2 casas: 12,5 / 50. */
    public String number(BigDecimal value) {
        if (value == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    /** Recebe uma razão (0,25) e devolve "25%" (até uma casa decimal). */
    public String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getPercentInstance(PT_BR);
        format.setMaximumFractionDigits(1);
        return format.format(ratio.setScale(4, RoundingMode.HALF_UP));
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    /** "Outubro de 2026" */
    public String month(YearMonth month) {
        if (month == null) {
            return "";
        }
        String name = month.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " de " + month.getYear();
    }

    /** "2 anos e 3 meses". Porte de formatDuration (format.ts). */
    public String duration(int totalMonths) {
        int years = totalMonths / 12;
        int months = totalMonths % 12;
        StringBuilder out = new StringBuilder();
        if (years > 0) {
            out.append(years).append(years == 1 ? " ano" : " anos");
        }
        if (months > 0 || years == 0) {
            if (!out.isEmpty()) {
                out.append(" e ");
            }
            out.append(months).append(months == 1 ? " mês" : " meses");
        }
        return out.toString();
    }
}
