package io.github.fabasdev.financas.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Um mês do histórico: entradas, consumo, aportes e o que sobrou. */
public record HistoryRow(YearMonth month, BigDecimal income, BigDecimal spent, BigDecimal saved, BigDecimal balance,
        BigDecimal savingsRate) {
}
