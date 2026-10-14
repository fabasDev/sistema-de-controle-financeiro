package io.github.fabasdev.financas.domain;

import java.util.Arrays;
import java.util.Optional;

/** Tema escolhido em Ajustes. Fica na conta, então vale em qualquer aparelho em que a pessoa entrar. */
public enum Theme {
    SYSTEM("Automático (segue o aparelho)", null),
    LIGHT("Claro", "light"),
    DARK("Escuro", "dark");

    private final String label;
    private final String attribute;

    Theme(String label, String attribute) {
        this.label = label;
        this.attribute = attribute;
    }

    public String label() {
        return label;
    }

    /** Valor do data-theme no &lt;html&gt;; null deixa a página seguir o aparelho. */
    public String attribute() {
        return attribute;
    }

    /** Só aceita os três nomes; qualquer outra coisa vinda do formulário fica vazia. */
    public static Optional<Theme> fromParam(String value) {
        return Arrays.stream(values()).filter(t -> t.name().equals(value)).findFirst();
    }
}
