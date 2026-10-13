package io.github.fabasdev.financas.dto;

import io.github.fabasdev.financas.domain.AssetClass;
import java.math.BigDecimal;

/** Uma linha da carteira sugerida: classe, fatia (%) e valor do mês. */
public record PlanItem(AssetClass assetClass, BigDecimal percent, BigDecimal amount) {
}
