package io.github.fabasdev.financas.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

/**
 * Cache do JavaScript. O CSS e as fontes levam o hash do conteúdo no nome (application.properties),
 * mas isso não funciona com módulos: o {@code import ... from './lib/http.js'} dentro do app.js
 * continuaria pedindo o arquivo sem hash. A documentação do Spring recomenda, nesse caso, a versão
 * no caminho: {@code /js/v1a2b3c4d5e/app.js}. Os imports relativos herdam a mesma pasta de versão.
 *
 * <p>A versão é um resumo de todos os arquivos .js, então muda quando qualquer um deles muda. O
 * caminho continua começando com /js/, coberto pela regra pública do SecurityConfig.
 */
@Configuration
public class StaticResourcesConfig implements WebMvcConfigurer {

    private static final String JS_LOCATION = "classpath:/static/js/";

    private final WebProperties.Resources resources;
    private final String jsVersion;

    public StaticResourcesConfig(WebProperties web) {
        this.resources = web.getResources();
        this.jsVersion = versionOf(JS_LOCATION);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mesmo cache dos outros arquivos estáticos (1 ano; no perfil dev, sempre confere).
        registry.addResourceHandler("/js/**")
                .addResourceLocations(JS_LOCATION)
                .setCacheControl(resources.getCache().getCachecontrol().toHttpCacheControl())
                .setUseLastModified(resources.getCache().isUseLastModified())
                .resourceChain(resources.getChain().isCache())
                .addResolver(new VersionResourceResolver().addFixedVersionStrategy(jsVersion, "/**"));
    }

    /** "v" + os 10 primeiros dígitos do SHA-256 dos nomes e do conteúdo dos arquivos .js da pasta. */
    static String versionOf(String location) {
        try {
            String root = new PathMatchingResourcePatternResolver().getResource(location).getURL().toString();
            // Ordenado pelo caminho, para a versão não depender da ordem em que os arquivos são listados.
            Map<String, Resource> files = new TreeMap<>();
            for (Resource file : new PathMatchingResourcePatternResolver().getResources(location + "**/*.js")) {
                String url = file.getURL().toString();
                files.put(url.startsWith(root) ? url.substring(root.length()) : url, file);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, Resource> entry : files.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.getValue().getContentAsByteArray());
            }
            return "v" + HexFormat.of().formatHex(digest.digest()).substring(0, 10);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler os arquivos JavaScript", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
