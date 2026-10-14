package io.github.fabasdev.financas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cache e compressão dos arquivos estáticos (Fase 5). Usa o servidor de verdade, numa porta
 * aleatória, porque a compressão é feita pelo Tomcat e o MockMvc não passa por ele.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StaticResourcesTest {

    private static final Pattern APP_CSS = Pattern.compile("/css/app-[0-9a-f]{32}\\.css");
    private static final Pattern TOKENS_CSS = Pattern.compile("/css/tokens-[0-9a-f]{32}\\.css");
    private static final Pattern APP_JS = Pattern.compile("/js/v[0-9a-f]{10}/app\\.js");
    private static final Pattern FONT = Pattern.compile("besley-latin-wght-[0-9a-f]{32}\\.woff2");
    private static final String ONE_YEAR = "max-age=31536000, public";

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void paginaApontaParaOsArquivosComVersao() throws Exception {
        String html = get("/login").body();
        assertThat(html).containsPattern(APP_CSS).containsPattern(TOKENS_CSS).containsPattern(APP_JS)
                .doesNotContain("\"/css/app.css\"", "\"/js/app.js\"");
    }

    /** Sem login, o CSS com versão fica 1 ano no cache. A página continua com no-store. */
    @Test
    void cssComVersaoFicaUmAnoNoCache() throws Exception {
        HttpResponse<String> page = get("/login");
        assertThat(page.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        HttpResponse<String> css = get(find(APP_CSS, page.body()));
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(css.headers().firstValue("Cache-Control")).hasValue(ONE_YEAR);
    }

    /** O app.js com versão e os módulos que ele importa por caminho relativo ('./lib/http.js'). */
    @Test
    void jsComVersaoEImportsRelativosFuncionamSemLogin() throws Exception {
        String url = find(APP_JS, get("/login").body());
        String folder = url.substring(0, url.lastIndexOf('/') + 1);
        for (String path : List.of(url, folder + "lib/http.js", folder + "lib/forms.js")) {
            HttpResponse<String> js = get(path);
            assertThat(js.statusCode()).as(path).isEqualTo(200);
            assertThat(js.headers().firstValue("Cache-Control")).as(path).hasValue(ONE_YEAR);
        }
    }

    /** O endereço das fontes dentro do CSS também ganha versão, e o endereço reescrito funciona. */
    @Test
    void fontesDentroDoCssTemVersao() throws Exception {
        String css = get(find(TOKENS_CSS, get("/login").body())).body();
        HttpResponse<String> font = get("/fonts/" + find(FONT, css));
        assertThat(font.statusCode()).isEqualTo(200);
        assertThat(font.headers().firstValue("Cache-Control")).hasValue(ONE_YEAR);
    }

    /** CSS vai comprimido; HTML não (traz o token CSRF ao lado de texto digitado: ataque BREACH). */
    @Test
    void comprimeOCssMasNaoOHtml() throws Exception {
        HttpResponse<String> page = get("/login", "gzip");
        // O Tomcat só comprime acima de 2 KB; abaixo disso o teste passaria mesmo com HTML na lista.
        assertThat(page.body().length()).as("tamanho do HTML").isGreaterThan(2048);
        assertThat(page.headers().firstValue("Content-Encoding")).isEmpty();
        HttpResponse<String> css = get(find(APP_CSS, page.body()), "gzip");
        assertThat(css.headers().firstValue("Content-Encoding")).hasValue("gzip");
    }

    @Test
    void versaoDoJsMudaQuandoUmArquivoMuda(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("lib"));
        Files.writeString(dir.resolve("app.js"), "import { a } from './lib/a.js'");
        Files.writeString(dir.resolve("lib/a.js"), "export const a = 1");
        String location = dir.toUri().toString();

        String first = StaticResourcesConfig.versionOf(location);
        assertThat(first).matches("v[0-9a-f]{10}").isEqualTo(StaticResourcesConfig.versionOf(location));

        Files.writeString(dir.resolve("lib/a.js"), "export const a = 2");
        assertThat(StaticResourcesConfig.versionOf(location)).isNotEqualTo(first);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return get(path, "identity");
    }

    /** O HttpClient do Java não descomprime sozinho: só peça gzip quando o corpo não for lido. */
    private HttpResponse<String> get(String path, String encoding) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept-Encoding", encoding)
                .build();
        return client.send(request, BodyHandlers.ofString());
    }

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        assertThat(m.find()).as("procura %s", pattern).isTrue();
        return m.group();
    }
}
