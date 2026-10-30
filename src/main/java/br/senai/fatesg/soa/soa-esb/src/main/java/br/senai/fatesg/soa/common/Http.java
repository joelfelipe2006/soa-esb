package br.senai.fatesg.soa.common;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Comunicação HTTP entre os participantes da arquitetura, só com o JDK:
 * {@code com.sun.net.httpserver} (servidor) e {@code java.net.http} (cliente).
 */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private Http() {
    }

    // ------------------------------------------------------------------ lado servidor

    /** Uma requisição recebida. */
    public record Request(String method, String path, String query, String body, String contentType) {

        public Map<String, Object> json() {
            return body == null || body.isBlank() ? new LinkedHashMap<>() : Json.parseObject(body);
        }

        public String param(String name) {
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String k = eq < 0 ? pair : pair.substring(0, eq);
                if (k.equals(name)) {
                    return eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                }
            }
            return null;
        }

        public boolean isXml() {
            return contentType != null && contentType.toLowerCase().contains("xml");
        }
    }

    /** Uma resposta a devolver. */
    public record Response(int status, String contentType, String body) {

        public static Response json(Object value) {
            return json(200, value);
        }

        public static Response json(int status, Object value) {
            return new Response(status, "application/json; charset=utf-8", Json.stringify(value));
        }

        public static Response xml(int status, String xml) {
            return new Response(status, "text/xml; charset=utf-8", xml);
        }

        public static Response html(String html) {
            return new Response(200, "text/html; charset=utf-8", html);
        }

        public static Response notFound() {
            return json(404, Json.obj("erro", "não encontrado"));
        }
    }

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    public static HttpServer server(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "http-" + port);
            t.setDaemon(true);
            return t;
        }));
        return server;
    }

    /** Liga um caminho (ex.: "/reservar") a um handler. */
    public static void route(HttpServer server, String path, Handler handler) {
        server.createContext(path, exchange -> {
            try (exchange) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Request req = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getRawQuery(), body,
                        exchange.getRequestHeaders().getFirst("Content-Type"));
                Response resp;
                try {
                    resp = handler.handle(req);
                } catch (IllegalArgumentException e) {
                    resp = Response.json(400, Json.obj("erro", e.getMessage()));
                } catch (Exception e) {
                    resp = Response.json(500, Json.obj("erro", String.valueOf(e.getMessage())));
                }
                byte[] out = resp.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", resp.contentType());
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(resp.status(), out.length == 0 ? -1 : out.length);
                if (out.length > 0) {
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(out);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ lado cliente

    /** Resultado de uma chamada HTTP. */
    public record Result(int status, String body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    public static Result post(String url, String contentType, String body, Duration timeout,
                              String... extraHeaders) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            b.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return send(b.build());
    }

    public static Result postJson(String url, Object value, Duration timeout) throws IOException {
        return post(url, "application/json; charset=utf-8", Json.stringify(value), timeout);
    }

    public static Result get(String url, Duration timeout) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build());
    }

    private static Result send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> r = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Result(r.statusCode(), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido", e);
        }
    }
}
