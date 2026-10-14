package io.github.fabasdev.financas.dto;

import io.github.fabasdev.financas.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha da listagem de lançamentos. Vem pronta do banco (projeção com JOIN na categoria),
 * então listar 20 ou 100 lançamentos custa as mesmas 2 queries (dados + contagem).
 */
public record TransactionRow(Long id, TransactionType type, LocalDate date, String description, BigDecimal amount,
        boolean paid, String categoryName, String categoryIcon, boolean fromRecurring) {
}
