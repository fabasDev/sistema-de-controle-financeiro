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
 *
 * <p>O SVG encolhe junto com a tela, e a letra encolhe junto. Por isso há dois desenhos: um de
 * 640 de largura para a tela larga e um de 320 para o celular, que aparece quase no tamanho real
 * (o CSS mostra um ou outro). No estreito, a letra é maior e há menos nomes de mês no eixo.
 */
public final class HistoryChart {

    public static final int HEIGHT = 240;
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

    /**
     * Uma largura de desenho. {@code left}: espaço para os valores do eixo; {@code labelSpacing}:
     * distância mínima entre os nomes de mês, para não se encostarem (depende do tamanho da letra
     * no CSS: 11px no largo, 13px no estreito).
     */
    public enum Size {
        WIDE("largo", 640, 56, 44, 18),
        NARROW("estreito", 320, 68, 52, 20);

        private final String key;
        private final int width;
        private final int left;
        private final int labelSpacing;
        private final int labelOffset;

        Size(String key, int width, int left, int labelSpacing, int labelOffset) {
            this.key = key;
            this.width = width;
            this.left = left;
            this.labelSpacing = labelSpacing;
            this.labelOffset = labelOffset;
        }

        public int width() {
            return width;
        }

        int labelSpacing() {
            return labelSpacing;
        }
    }

    /** {@code key} vai na classe CSS (chart-svg-largo) e no id da descrição, que precisa ser único na página. */
    public record Model(String key, int width, int height, int baseline, int plotLeft, int plotRight,
            int xLabelY, List<Bar> bars, List<Tick> ticks, List<XLabel> xLabels, String description) {
    }

    private HistoryChart() {
    }

    /** Os dois desenhos, largo e estreito, para a mesma página. */
    public static List<Model> buildAll(List<HistoryRow> rows, ViewFormats fmt) {
        return List.of(build(rows, fmt, Size.WIDE), build(rows, fmt, Size.NARROW));
    }

    public static Model build(List<HistoryRow> rows, ViewFormats fmt, Size size) {
        BigDecimal max = Money.ZERO;
        for (HistoryRow r : rows) {
            max = Money.max(max, Money.max(r.income(), outflow(r)));
        }
        BigDecimal step = niceStep(max);
        BigDecimal top = max.signum() == 0 ? step.multiply(BigDecimal.valueOf(4))
                : max.divide(step, 0, RoundingMode.CEILING).multiply(step);
        int baseline = TOP + PLOT_HEIGHT;
        int left = size.left;
        // À direita, espaço para meio nome de mês: o do mês mais recente fica centrado sem sair do desenho.
        int plotRight = size.width - Math.max(RIGHT, size.labelSpacing / 2);
        int groupWidth = (plotRight - left) / rows.size();
        int barWidth = Math.max(2, Math.min(MAX_BAR, (groupWidth - 8 - GAP) / 2));

        List<Tick> ticks = new ArrayList<>();
        for (BigDecimal v = BigDecimal.ZERO; v.compareTo(top) <= 0; v = v.add(step)) {
            ticks.add(new Tick(y(v, top, baseline), fmt.compact(v)));
        }

        List<Bar> bars = new ArrayList<>();
        List<XLabel> labels = new ArrayList<>();
        // Quando não cabe o nome de todos os meses, mostra um a cada tantos, contando a partir do
        // mês mais recente (o da direita), que sempre aparece.
        int labelEvery = Math.max(1, (size.labelSpacing + groupWidth - 1) / groupWidth);
        for (int i = 0; i < rows.size(); i++) {
            HistoryRow r = rows.get(i);
            int center = left + groupWidth * i + groupWidth / 2;
            String month = fmt.month(r.month());
            addBar(bars, center - GAP / 2 - barWidth, barWidth, r.income(), top, baseline, "bar-income",
                    month + ": entrou " + fmt.money(r.income()));
            addBar(bars, center + GAP / 2 + GAP % 2, barWidth, outflow(r), top, baseline, "bar-outflow",
                    month + ": saiu " + fmt.money(outflow(r)));
            if ((rows.size() - 1 - i) % labelEvery == 0) {
                // O nome é centrado na coluna, mas o último não pode passar da borda direita.
                labels.add(new XLabel(Math.min(center, size.width - size.labelSpacing / 2), fmt.shortMonth(r.month())));
            }
        }
        // Se o último nome, puxado para dentro, ficou perto demais do anterior, o anterior sai.
        if (labels.size() > 1 && labels.getLast().x() - labels.get(labels.size() - 2).x() < size.labelSpacing) {
            labels.remove(labels.size() - 2);
        }
        String description = "Colunas por mês: entrou e saiu (gastos mais o que foi guardado). Os valores estão na tabela abaixo.";
        return new Model(size.key, size.width, HEIGHT, baseline, left, plotRight, baseline + size.labelOffset, bars,
                ticks, labels, description);
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
