package io.github.fabasdev.financas.controller.view;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Lê o mês da URL (?mes=2026-10). Valor ausente, inválido ou fora de 2000–2100 vira o mês atual. */
public final class MonthParam {

    private MonthParam() {
    }

    public static YearMonth parse(String value, Clock clock) {
        if (value != null && value.length() == 7) {
            try {
                YearMonth month = YearMonth.parse(value);
                if (month.getYear() >= 2000 && month.getYear() <= 2100) {
                    return month;
                }
            } catch (DateTimeParseException ignored) {
                // cai no mês atual
            }
        }
        return YearMonth.now(clock);
    }
}
