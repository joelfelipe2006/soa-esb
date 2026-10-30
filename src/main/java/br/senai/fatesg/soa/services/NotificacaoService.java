package br.senai.fatesg.soa.services;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Http.Response;
import br.senai.fatesg.soa.common.Json;
import br.senai.fatesg.soa.common.Log;
import com.sun.net.httpserver.HttpServer;

import java.util.List;
import java.util.Map;

/**
 * Serviço de Notificação (JSON): "envia" a mensagem ao cliente
 * (aqui, imprime no terminal como se fosse um e-mail).
 * Recebe mensagens de forma ASSÍNCRONA, pela fila de entrega garantida do ESB.
 */
public class NotificacaoService extends BaseService {

    public NotificacaoService(String host, int porta, String esbUrl) {
        super("notificacao", host, porta, esbUrl);
    }

    @Override
    protected String formato() {
        return "JSON";
    }

    @Override
    protected String descricao() {
        return "Envio de e-mail ao cliente";
    }

    @Override
    protected List<String> operacoes() {
        return List.of("enviar");
    }

    @Override
    protected void rotas(HttpServer server) {
        Http.route(server, "/enviar", req -> Response.json(enviar(req.json())));
    }

    private Map<String, Object> enviar(Map<String, Object> m) {
        log.in("Mensagem do pedido %s", Json.str(m, "pedidoId"));
        Log.plain("        +--------------------------------------------------------------");
        Log.plain("        | E-MAIL para: %s", Json.str(m, "cliente"));
        Log.plain("        | %s", Json.str(m, "mensagem"));
        Log.plain("        +--------------------------------------------------------------");
        return Json.obj("enviado", true);
    }
}
