package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.TransactionType;
import java.math.BigDecimal;
import java.time.YearMonth;

/** Soma dos lançamentos agrupada por mês, tipo e natureza da categoria (null = sem categoria). */
public record MonthKindTotal(int year, int month, TransactionType type, BucketKind kind, BigDecimal total) {

    public YearMonth yearMonth() {
        return YearMonth.of(year, month);
    }
}
