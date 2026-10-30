package br.senai.fatesg.soa.services;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Http.Response;
import br.senai.fatesg.soa.common.Json;
import br.senai.fatesg.soa.common.Log;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Base de todo serviço da arquitetura.
 *
 * Um serviço SOA é uma unidade de negócio com um CONTRATO público (nome,
 * operações, formato). Ele não conhece os outros serviços nem os consumidores:
 * só sabe o endereço do barramento, onde se registra e para onde manda sinal de vida.
 */
public abstract class BaseService {

    private static final long HEARTBEAT_MS = 4000;

    protected final String nome;
    protected final String host;
    protected final int porta;
    protected final String esbUrl;
    protected final Log log;
    private HttpServer server;
    private volatile boolean registrado;
    private volatile boolean rodando;

    protected BaseService(String nome, String host, int porta, String esbUrl) {
        this.nome = nome;
        this.host = host;
        this.porta = porta;
        this.esbUrl = esbUrl;
        this.log = new Log(nome.toUpperCase());
    }

    /** "JSON" ou "XML". */
    protected abstract String formato();

    protected abstract String descricao();

    protected abstract List<String> operacoes();

    /** Liga as rotas HTTP das operações. */
    protected abstract void rotas(HttpServer server);

    public String url() {
        return "http://" + host + ":" + porta;
    }

    /** O contrato público do serviço (o "WSDL" simplificado). */
    public Map<String, Object> contrato() {
        return Json.obj("nome", nome, "url", url(), "formato", formato(),
                "descricao", descricao(), "operacoes", operacoes());
    }

    public void start() throws IOException {
        server = Http.server(porta);
        rotas(server);
        Http.route(server, "/contrato", req -> Response.json(contrato()));
        server.start();
        rodando = true;
        log.ok("%s no ar em %s (formato %s)", nome, url(), formato());
        log.info("Operações: %s", String.join(", ", operacoes()));

        Thread hb = new Thread(this::heartbeatLoop, "sinal-de-vida-" + nome);
        hb.setDaemon(true);
        hb.start();
    }

    public void stop() {
        rodando = false;
        if (server != null) {
            server.stop(0);
        }
    }

    /** Registro no ESB + sinal de vida periódico. */
    private void heartbeatLoop() {
        while (rodando) {
            try {
                Http.Result r = Http.postJson(esbUrl + "/esb/registro", contrato(), Duration.ofSeconds(3));
                if (r.ok() && !registrado) {
                    log.ok("Registrado no ESB (%s). Mandando sinal de vida a cada %d s", esbUrl, HEARTBEAT_MS / 1000);
                }
                registrado = r.ok();
            } catch (IOException e) {
                if (registrado) {
                    log.warn("Perdi contato com o ESB (%s). Tentando de novo...", esbUrl);
                } else if (!rodando) {
                    return;
                }
                registrado = false;
            }
            try {
                Thread.sleep(registrado ? HEARTBEAT_MS : 2000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
