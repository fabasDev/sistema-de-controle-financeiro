package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Um lançamento com só os ids das ligações, para o backup (sem carregar a categoria, a meta e a conta). */
public record TransactionExportRow(Long id, TransactionType type, LocalDate date, String description,
        BigDecimal amount, boolean paid, Long categoryId, Long goalId, Long recurringId, YearMonth recurringMonth) {
}
