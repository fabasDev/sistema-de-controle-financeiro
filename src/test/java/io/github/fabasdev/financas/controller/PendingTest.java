package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.TransactionService;
import io.github.fabasdev.financas.service.TransactionService.TransactionData;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Pendências do mês na visão geral e o filtro por situação em Lançamentos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PendingTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    SampleData sample;
    @Autowired
    TransactionService transactions;
    @Autowired
    TransactionRepository repository;

    AppUserPrincipal me;
    Long categoryId;

    @BeforeEach
    void setUp() {
        me = accounts.create().principal();
        categoryId = sample.create(me.getId()).category();
    }

    @Test
    void visaoGeralMostraOQueFaltaPagarEReceber() throws Exception {
        expense("Aluguel de outubro", false);
        income("Freela a receber", false);
        expense("Mercado já pago", true);

        mvc.perform(get("/?mes=2026-10").with(user(me)))
                .andExpect(content().string(containsString("Pendências do mês")))
                .andExpect(content().string(containsString("Aluguel de outubro")))
                .andExpect(content().string(containsString("Marcar como pago")))
                .andExpect(content().string(containsString("Freela a receber")))
                .andExpect(content().string(containsString("Marcar como recebido")))
                .andExpect(content().string(not(containsString("Mercado já pago"))));
    }

    @Test
    void marcarComoPagoNaVisaoGeralVoltaParaElaComAviso() throws Exception {
        Transaction rent = expense("Aluguel de outubro", false);

        mvc.perform(post("/lancamentos/{id}/pago", rent.getId()).param("voltar", "inicio").with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/?mes=2026-10"))
                .andExpect(flash().attribute("success", "\"Aluguel de outubro\" marcado como pago."));
        assertThat(repository.findById(rent.getId()).orElseThrow().isPaid()).isTrue();
        mvc.perform(get("/?mes=2026-10").with(user(me)))
                .andExpect(content().string(not(containsString("Aluguel de outubro"))));

        // Qualquer outro valor de "voltar" vai para a lista (não é um redirect aberto).
        mvc.perform(post("/lancamentos/{id}/pago", rent.getId()).param("voltar", "https://exemplo.com")
                        .with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/lancamentos?mes=2026-10"));
    }

    @Test
    void mesSemPendenciaDizQueEstaTudoEmDia() throws Exception {
        // Em março não há conta fixa dos dados de exemplo (ela começa no mês atual).
        transactions.create(me.getId(), new TransactionData(TransactionType.EXPENSE, LocalDate.of(2026, 3, 2),
                "Pago em março", new BigDecimal("10.00"), categoryId, null, true, false, null));
        mvc.perform(get("/?mes=2026-03").with(user(me)))
                .andExpect(content().string(containsString("Tudo em dia")));
    }

    @Test
    void maisDeOitoPendenciasMostraOLinkParaTodas() throws Exception {
        for (int i = 1; i <= 9; i++) {
            expense("Conta " + i, false);
        }
        mvc.perform(get("/?mes=2026-10").with(user(me)))
                .andExpect(content().string(containsString("Conta 8")))
                .andExpect(content().string(not(containsString("Conta 9<"))))
                .andExpect(content().string(containsString("href=\"/lancamentos?mes=2026-10&amp;situacao=pendente\"")));
    }

    @Test
    void filtroDeSituacaoNaListaDeLancamentos() throws Exception {
        expense("Aluguel de outubro", false);
        expense("Mercado já pago", true);

        mvc.perform(get("/lancamentos?mes=2026-10&situacao=pendente").with(user(me)))
                .andExpect(content().string(containsString("Aluguel de outubro")))
                .andExpect(content().string(not(containsString("Mercado já pago"))));
        mvc.perform(get("/lancamentos?mes=2026-10&situacao=pago").with(user(me)))
                .andExpect(content().string(not(containsString("Aluguel de outubro"))))
                .andExpect(content().string(containsString("Mercado já pago")));
        // Valor desconhecido é ignorado: mostra tudo, sem erro.
        mvc.perform(get("/lancamentos?mes=2026-10&situacao=lixo").with(user(me)))
                .andExpect(content().string(containsString("Aluguel de outubro")))
                .andExpect(content().string(containsString("Mercado já pago")));
    }

    @Test
    void marcarComoPagoNaListaFiltradaMantemOFiltro() throws Exception {
        Transaction rent = expense("Aluguel de outubro", false);

        mvc.perform(post("/lancamentos/{id}/pago", rent.getId()).param("situacao", "pendente")
                        .with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/lancamentos?mes=2026-10&situacao=pendente"));
        mvc.perform(post("/lancamentos/{id}/pago", rent.getId()).param("situacao", "&x=1")
                        .with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/lancamentos?mes=2026-10"));
    }

    private Transaction expense(String description, boolean paid) {
        return transactions.create(me.getId(), new TransactionData(TransactionType.EXPENSE, DAY, description,
                new BigDecimal("100.00"), categoryId, null, paid, false, null));
    }

    private Transaction income(String description, boolean paid) {
        return transactions.create(me.getId(), new TransactionData(TransactionType.INCOME, DAY, description,
                new BigDecimal("300.00"), null, null, paid, false, null));
    }
}
