package io.github.fabasdev.financas.domain;

/** Classes de ativos da carteira sugerida, na ordem de legacy-web/src/lib/advisor.ts. */
public enum AssetClass {
    POST_FIXED("Renda fixa pós-fixada"),
    INFLATION_LINKED("Renda fixa atrelada à inflação"),
    PRE_FIXED("Renda fixa prefixada"),
    TAX_EXEMPT("Renda fixa isenta"),
    REAL_ESTATE_FUNDS("Fundos imobiliários (FIIs)"),
    STOCKS("Ações e ETFs brasileiros"),
    INTERNATIONAL("Investimentos no exterior");

    private final String label;

    AssetClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
