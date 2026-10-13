package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.AssetClass;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.dto.PlanItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Divide o valor a investir do mês entre as classes de ativos do perfil.
 * Porte de DEFAULT_ALLOCATION, allocationFor e splitPlan (advisor.ts).
 */
@Service
public class PortfolioService {

    /** Carteira padrão de cada perfil, em %. Cada uma soma 100. */
    public static final Map<RiskProfile, Map<AssetClass, BigDecimal>> DEFAULT_ALLOCATION = Map.of(
            RiskProfile.CONSERVATIVE, allocation(45, 25, 5, 20, 5, 0, 0),
            RiskProfile.MODERATE, allocation(25, 20, 5, 10, 15, 15, 10),
            RiskProfile.AGGRESSIVE, allocation(10, 15, 5, 5, 15, 30, 20));

    /** Percentuais do perfil, com os ajustes do usuário por cima. */
    public Map<AssetClass, BigDecimal> allocationFor(RiskProfile profile, Map<AssetClass, BigDecimal> custom) {
        Map<AssetClass, BigDecimal> result = new EnumMap<>(DEFAULT_ALLOCATION.get(profile));
        result.putAll(custom);
        return result;
    }

    /**
     * Divide {@code total} entre as classes. Cada parte é arredondada para baixo, e os centavos
     * que sobram vão para a primeira classe, para a soma fechar exatamente no total.
     * O resultado vem da maior fatia para a menor.
     */
    public List<PlanItem> split(BigDecimal total, Map<AssetClass, BigDecimal> allocation) {
        BigDecimal sum = Money.sum(allocation.values());
        if (sum.signum() == 0) {
            sum = BigDecimal.ONE;
        }
        List<PlanItem> items = new ArrayList<>();
        for (AssetClass assetClass : AssetClass.values()) {
            BigDecimal share = allocation.getOrDefault(assetClass, BigDecimal.ZERO);
            if (share.signum() <= 0) {
                continue;
            }
            BigDecimal percent = share.multiply(Money.HUNDRED).divide(sum, 4, RoundingMode.HALF_UP);
            BigDecimal amount = total.multiply(share).divide(sum, Money.SCALE, RoundingMode.FLOOR);
            items.add(new PlanItem(assetClass, percent, amount));
        }
        BigDecimal diff = total.subtract(items.stream().map(PlanItem::amount).reduce(Money.ZERO, BigDecimal::add));
        if (!items.isEmpty() && diff.signum() != 0) {
            PlanItem first = items.getFirst();
            items.set(0, new PlanItem(first.assetClass(), first.percent(), first.amount().add(diff)));
        }
        items.sort(Comparator.comparing(PlanItem::percent).reversed());
        return items;
    }

    private static Map<AssetClass, BigDecimal> allocation(int... percents) {
        Map<AssetClass, BigDecimal> map = new EnumMap<>(AssetClass.class);
        AssetClass[] classes = AssetClass.values();
        for (int i = 0; i < classes.length; i++) {
            map.put(classes[i], BigDecimal.valueOf(percents[i]));
        }
        return Map.copyOf(map);
    }
}
