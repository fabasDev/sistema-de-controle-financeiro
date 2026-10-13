package io.github.fabasdev.financas.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * Relógio único do app. "Hoje" e "mês atual" vêm sempre daqui, no fuso do Brasil,
     * e os testes podem trocar por um relógio fixo.
     */
    @Bean
    public Clock clock(@Value("${app.time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
