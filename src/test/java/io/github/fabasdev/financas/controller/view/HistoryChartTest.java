package io.github.fabasdev.financas.controller.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fabasdev.financas.dto.HistoryRow;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HistoryChartTest {

    final ViewFormats fmt = new ViewFormats();

    @ParameterizedTest(name = "máximo {0} → passo {1}")
    @CsvSource({"4000, 1000", "5200, 2000", "900, 250", "12345.67, 5000", "0, 250", "100000, 25000"})
    void escolhePassosRedondos(String max, String step) {
        assertThat(HistoryChart.niceStep(new BigDecimal(max))).isEqualByComparingTo(step);
    }

    @Test
    void colunasCabemNoGraficoESaoFinas() {
        List<HistoryRow> rows = List.of(
                row(2026, 9, "5000", "3200", "800"),
                row(2026, 10, "5200", "4100", "0"));
        HistoryChart.Model chart = HistoryChart.build(rows, fmt);

        assertThat(chart.bars()).hasSize(4);
        assertThat(chart.bars()).allSatisfy(b -> assertThat(b.path()).startsWith("M").endsWith("Z"));
        assertThat(chart.ticks().getFirst().label()).isEqualTo("R$ 0");
        assertThat(chart.ticks().getLast().y()).isGreaterThanOrEqualTo(0);
        assertThat(chart.bars().getFirst().title()).isEqualTo("Setembro de 2026: entrou R$ 5.000,00");
        assertThat(chart.xLabels()).extracting(HistoryChart.XLabel::label).containsExactly("set/26", "out/26");
    }

    @Test
    void mesSemValorNaoDesenhaColuna() {
        HistoryChart.Model chart = HistoryChart.build(List.of(row(2026, 10, "0", "0", "0")), fmt);
        assertThat(chart.bars()).isEmpty();
    }

    private static HistoryRow row(int y, int m, String income, String spent, String saved) {
        return new HistoryRow(YearMonth.of(y, m), new BigDecimal(income), new BigDecimal(spent), new BigDecimal(saved),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
