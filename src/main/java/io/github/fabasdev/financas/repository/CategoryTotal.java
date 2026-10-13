package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.TransactionType;
import java.math.BigDecimal;

/** Soma dos lançamentos de um mês agrupada por categoria, tipo e situação (pago ou não). */
public record CategoryTotal(Long categoryId, TransactionType type, boolean paid, BigDecimal total, long count) {
}
