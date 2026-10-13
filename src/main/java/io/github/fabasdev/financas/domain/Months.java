package io.github.fabasdev.financas.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Contas com meses. Porte de legacy-web/src/lib/month.ts. */
public final class Months {

    private Months() {
    }

    /** {@code count} meses terminando em {@code end}, do mais antigo para o mais recente. */
    public static List<YearMonth> range(YearMonth end, int count) {
        List<YearMonth> months = new ArrayList<>(count);
        for (int i = count - 1; i >= 0; i--) {
            months.add(end.minusMonths(i));
        }
        return months;
    }

    /** Quantos meses de {@code from} até {@code to} (positivo se {@code to} for depois). */
    public static long between(YearMonth from, YearMonth to) {
        return ChronoUnit.MONTHS.between(from, to);
    }

    /** Data dentro do mês, com o dia limitado ao último dia do mês (31 vira 28 em fevereiro). */
    public static LocalDate dateInMonth(YearMonth month, int day) {
        return month.atDay(Math.min(Math.max(1, day), month.lengthOfMonth()));
    }

    /** Primeiro dia do mês seguinte: limite exclusivo para consultas por intervalo. */
    public static LocalDate endExclusive(YearMonth month) {
        return month.plusMonths(1).atDay(1);
    }
}
