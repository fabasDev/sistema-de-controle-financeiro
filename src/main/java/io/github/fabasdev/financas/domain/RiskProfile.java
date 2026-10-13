package io.github.fabasdev.financas.domain;

/** Perfil de investidor, calculado pelo questionário (que roda no navegador). */
public enum RiskProfile {
    CONSERVATIVE("Conservador"),
    MODERATE("Moderado"),
    AGGRESSIVE("Arrojado");

    private final String label;

    RiskProfile(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
