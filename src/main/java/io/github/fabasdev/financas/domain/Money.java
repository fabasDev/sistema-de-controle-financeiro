package io.github.fabasdev.financas.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regras de dinheiro do app: sempre BigDecimal com 2 casas e arredondamento HALF_UP
 * (o "arredondamento escolar": 0,005 vira 0,01).
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    /** Maior valor que cabe em NUMERIC(14,2). */
    public static final BigDecimal MAX = new BigDecimal("999999999999.99");
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    public static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Precisão usada nas contas intermediárias (34 dígitos), antes de arredondar para centavos. */
    public static final MathContext PRECISION = MathContext.DECIMAL128;

    private static final Pattern NOT_NUMBER = Pattern.compile("[^\\d,.-]");
    private static final Pattern THOUSANDS_GROUP = Pattern.compile("\\.\\d{3}$");

    private Money() {
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(ZERO, BigDecimal::add);
    }

    /** {@code value × percent / 100}, arredondado para centavos. */
    public static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        return round(value.multiply(percent).divide(HUNDRED, PRECISION));
    }

    /** Razão {@code a / b} (ex.: 0,16 para 16%). Devolve 0 quando {@code b} é 0. */
    public static BigDecimal ratio(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, 6, ROUNDING);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * Converte texto digitado no padrão brasileiro em valor.
     * Aceita "1.234,56", "1234,56", "1234.56", "R$ 1.234" e "1.234" (milhar).
     * Porte de parseBRL (legacy-web/src/lib/format.ts).
     */
    public static Optional<BigDecimal> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = NOT_NUMBER.matcher(input).replaceAll("");
        if (s.isEmpty() || s.equals("-")) {
            return Optional.empty();
        }
        if (s.contains(",")) {
            s = s.replace(".", "").replaceFirst(",", ".");
        } else if (s.chars().filter(c -> c == '.').count() > 1 || THOUSANDS_GROUP.matcher(s).find()) {
            s = s.replace(".", "");
        }
        try {
            return Optional.of(round(new BigDecimal(s)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
