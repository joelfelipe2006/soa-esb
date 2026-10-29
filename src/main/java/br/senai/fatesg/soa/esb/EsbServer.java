package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Http.Response;
import br.senai.fatesg.soa.common.Json;
import br.senai.fatesg.soa.common.Log;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * O ENTERPRISE SERVICE BUS.
 *
 * É o único ponto que consumidores e serviços conhecem. Ele junta as peças:
 * <ul>
 *   <li>{@link ServiceRegistry}     registro e contratos dos serviços</li>
 *   <li>{@link ServiceInvoker}      mediação: localiza e chama o serviço</li>
 *   <li>{@link Transformer}         tradução JSON &lt;-&gt; XML/SOAP</li>
 *   <li>{@link ContentBasedRouter}  roteamento pelo conteúdo</li>
 *   <li>{@link Orchestrator}        processo de negócio com compensação</li>
 *   <li>{@link RetryQueue}          entrega garantida</li>
 *   <li>{@link MessageLog}          monitoramento (painel web)</li>
 * </ul>
 *
 * Endpoints:
 * <pre>
 *   POST /esb/registro   serviços se registram e mandam sinal de vida
 *   POST /esb/pedidos    consumidores pedem "fechar pedido"
 *   GET  /esb/estoque    consulta de produtos (encaminhada ao serviço de estoque)
 *   GET  /api/estado     dados do painel
 *   GET  /               painel web de monitoramento
 * </pre>
 */
public final class EsbServer {

    private final int port;
    private final Log console = new Log("ESB");
    private HttpServer server;

    public EsbServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        MessageLog log = new MessageLog();
        ServiceRegistry registry = new ServiceRegistry(log);
        ServiceInvoker invoker = new ServiceInvoker(registry, log);
        ContentBasedRouter router = new ContentBasedRouter(log);
        RetryQueue queue = new RetryQueue(invoker, log);
        Orchestrator orchestrator = new Orchestrator(invoker, router, queue, log);

        server = Http.server(port);

        Http.route(server, "/esb/registro", req -> {
            registry.register(req.json());
            return Response.json(Json.obj("ok", true));
        });

        Http.route(server, "/esb/pedidos", req -> {
            if (!"POST".equals(req.method())) {
                return Response.json(405, Json.obj("erro", "use POST"));
            }
            return Response.json(orchestrator.fecharPedido(req.json()));
        });

        Http.route(server, "/esb/estoque", req -> Response.json(orchestrator.consultarEstoque()));

        Http.route(server, "/api/estado", req -> {
            long since = 0;
            try {
                since = Long.parseLong(req.param("since") == null ? "0" : req.param("since"));
            } catch (NumberFormatException ignored) {
                // usa 0
            }
            return Response.json(Json.obj(
                    "servicos", registry.snapshot(),
                    "fila", queue.snapshot(),
                    "regras", router.describe(),
                    "mensagens", log.since(since)));
        });

        Http.route(server, "/", req -> "/".equals(req.path()) ? Response.html(Dashboard.HTML) : Response.notFound());

        server.start();
        queue.start();

        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitor");
            t.setDaemon(true);
            return t;
        });
        monitor.scheduleWithFixedDelay(registry::checkTimeouts, 2, 2, TimeUnit.SECONDS);

        console.ok("Barramento no ar na porta %d", port);
        console.info("Painel de monitoramento: http://localhost:%d", port);
        console.info("Aguardando os serviços se registrarem...");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
