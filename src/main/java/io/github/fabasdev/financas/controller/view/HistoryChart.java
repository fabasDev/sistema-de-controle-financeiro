package io.github.fabasdev.financas.controller.view;

import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.dto.HistoryRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometria do gráfico "entrou × saiu por mês" (colunas agrupadas, SVG gerado no servidor).
 * Segue a skill dataviz: uma escala só, colunas de no máximo 24px, ponta arredondada de 4px e
 * base reta, 2px de folga entre as colunas, grade em linha fina. As posições saem de BigDecimal
 * direto para pixels inteiros, sem passar por double.
 */
public final class HistoryChart {

    public static final int WIDTH = 640;
    public static final int HEIGHT = 240;
    static final int LEFT = 56;
    static final int RIGHT = 8;
    static final int TOP = 12;
    static final int PLOT_HEIGHT = 180;
    static final int MAX_BAR = 24;
    static final int GAP = 2;

    /** Uma coluna: o desenho (path), a classe da cor e o texto do tooltip. */
    public record Bar(String path, String cssClass, String title) {
    }

    public record Tick(int y, String label) {
    }

    public record XLabel(int x, String label) {
    }

    public record Model(int width, int height, int baseline, int plotLeft, int plotRight, List<Bar> bars,
            List<Tick> ticks, List<XLabel> xLabels, String description) {
    }

    private HistoryChart() {
    }

    public static Model build(List<HistoryRow> rows, ViewFormats fmt) {
        BigDecimal max = Money.ZERO;
        for (HistoryRow r : rows) {
            max = Money.max(max, Money.max(r.income(), outflow(r)));
        }
        BigDecimal step = niceStep(max);
        BigDecimal top = max.signum() == 0 ? step.multiply(BigDecimal.valueOf(4))
                : max.divide(step, 0, RoundingMode.CEILING).multiply(step);
        int baseline = TOP + PLOT_HEIGHT;
        int plotRight = WIDTH - RIGHT;
        int groupWidth = (plotRight - LEFT) / rows.size();
        int barWidth = Math.max(2, Math.min(MAX_BAR, (groupWidth - 8 - GAP) / 2));

        List<Tick> ticks = new ArrayList<>();
        for (BigDecimal v = BigDecimal.ZERO; v.compareTo(top) <= 0; v = v.add(step)) {
            ticks.add(new Tick(y(v, top, baseline), fmt.compact(v)));
        }

        List<Bar> bars = new ArrayList<>();
        List<XLabel> labels = new ArrayList<>();
        // Com 24 meses não cabe o nome de todos: mostra um a cada três.
        int labelEvery = rows.size() > 12 ? 3 : 1;
        for (int i = 0; i < rows.size(); i++) {
            HistoryRow r = rows.get(i);
            int center = LEFT + groupWidth * i + groupWidth / 2;
            String month = fmt.month(r.month());
            addBar(bars, center - GAP / 2 - barWidth, barWidth, r.income(), top, baseline, "bar-income",
                    month + ": entrou " + fmt.money(r.income()));
            addBar(bars, center + GAP / 2 + GAP % 2, barWidth, outflow(r), top, baseline, "bar-outflow",
                    month + ": saiu " + fmt.money(outflow(r)));
            if (i % labelEvery == 0 || i == rows.size() - 1) {
                labels.add(new XLabel(center, fmt.shortMonth(r.month())));
            }
        }
        String description = "Colunas por mês: entrou e saiu (gastos mais o que foi guardado). Os valores estão na tabela abaixo.";
        return new Model(WIDTH, HEIGHT, baseline, LEFT, plotRight, bars, ticks, labels, description);
    }

    /** Saiu = consumo + o que foi guardado. */
    static BigDecimal outflow(HistoryRow r) {
        return r.spent().add(r.saved());
    }

    private static void addBar(List<Bar> bars, int x, int w, BigDecimal value, BigDecimal top, int baseline,
            String cssClass, String title) {
        if (value.signum() <= 0) {
            return;
        }
        int y = y(value, top, baseline);
        int h = baseline - y;
        if (h < 1) {
            y = baseline - 1;
            h = 1;
        }
        int r = Math.min(4, Math.min(w / 2, h));
        // Ponta arredondada em cima, base reta (a coluna "nasce" da linha de base).
        String path = "M" + x + "," + baseline
                + " V" + (y + r)
                + " Q" + x + "," + y + " " + (x + r) + "," + y
                + " H" + (x + w - r)
                + " Q" + (x + w) + "," + y + " " + (x + w) + "," + (y + r)
                + " V" + baseline + " Z";
        bars.add(new Bar(path, cssClass, title));
    }

    private static int y(BigDecimal value, BigDecimal top, int baseline) {
        int h = value.multiply(BigDecimal.valueOf(PLOT_HEIGHT)).divide(top, 0, RoundingMode.HALF_UP).intValue();
        return baseline - h;
    }

    /** Passo "redondo" (1, 2, 2,5 ou 5 vezes uma potência de 10) para umas 4 linhas de grade. */
    static BigDecimal niceStep(BigDecimal max) {
        if (max.signum() <= 0) {
            return new BigDecimal("250");
        }
        BigDecimal raw = max.divide(BigDecimal.valueOf(4), 10, RoundingMode.HALF_UP).stripTrailingZeros();
        int exponent = raw.precision() - raw.scale() - 1;
        BigDecimal magnitude = BigDecimal.ONE.scaleByPowerOfTen(exponent);
        BigDecimal normalized = raw.divide(magnitude, 10, RoundingMode.HALF_UP);
        for (String m : new String[] {"1", "2", "2.5", "5", "10"}) {
            BigDecimal multiplier = new BigDecimal(m);
            if (normalized.compareTo(multiplier) <= 0) {
                return multiplier.multiply(magnitude).stripTrailingZeros();
            }
        }
        return BigDecimal.TEN.multiply(magnitude);
    }
}
