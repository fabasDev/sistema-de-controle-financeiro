package io.github.fabasdev.financas.domain;

import java.util.Arrays;
import java.util.Optional;

/** Tema escolhido em Ajustes. Fica na conta, então vale em qualquer aparelho em que a pessoa entrar. */
public enum Theme {
    DARK("Escuro, azul-noite (padrão)", "dark"),
    LIGHT("Claro", "light"),
    SYSTEM("Automático (segue o aparelho)", "system");

    /** Tema de quem ainda não escolheu. As telas sem login também usam o escuro. */
    public static final Theme DEFAULT = DARK;

    private final String label;
    private final String attribute;

    Theme(String label, String attribute) {
        this.label = label;
        this.attribute = attribute;
    }

    public String label() {
        return label;
    }

    /** Valor do data-theme no &lt;html&gt;, que o tokens.css lê. */
    public String attribute() {
        return attribute;
    }

    /** Só aceita os três nomes; qualquer outra coisa vinda do formulário fica vazia. */
    public static Optional<Theme> fromParam(String value) {
        return Arrays.stream(values()).filter(t -> t.name().equals(value)).findFirst();
    }
}
