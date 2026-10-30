package br.senai.fatesg.soa.services;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Http.Response;
import br.senai.fatesg.soa.common.Json;
import com.sun.net.httpserver.HttpServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço de Pagamento (JSON). O mesmo código roda como DOIS serviços:
 * "pagamento-pix" e "pagamento-cartao". Quem decide qual chamar é o ESB,
 * pelo conteúdo do pedido.
 *
 * Regra de negócio para a demo: cartão tem limite de R$ 5.000,00.
 */
public class PagamentoService extends BaseService {

    public static final double LIMITE_CARTAO = 5000.00;

    private final String tipo;
    private final AtomicInteger transacoes = new AtomicInteger();

    /** @param tipo "PIX" ou "CARTAO" */
    public PagamentoService(String tipo, String host, int porta, String esbUrl) {
        super("pagamento-" + tipo.toLowerCase(), host, porta, esbUrl);
        this.tipo = tipo.toUpperCase();
    }

    @Override
    protected String formato() {
        return "JSON";
    }

    @Override
    protected String descricao() {
        return "CARTAO".equals(tipo) ? "Pagamento com cartão de crédito (limite R$ 5.000)" : "Pagamento instantâneo PIX";
    }

    @Override
    protected List<String> operacoes() {
        return List.of("cobrar");
    }

    @Override
    protected void rotas(HttpServer server) {
        Http.route(server, "/cobrar", req -> Response.json(cobrar(req.json())));
    }

    private Map<String, Object> cobrar(Map<String, Object> m) {
        double valor = Json.num(m, "valor", 0);
        String pedido = Json.str(m, "pedidoId");
        log.in("cobrar R$ %.2f de %s (pedido %s)", valor, Json.str(m, "cliente"), pedido);
        if ("CARTAO".equals(tipo) && valor > LIMITE_CARTAO) {
            log.warn("RECUSADO: R$ %.2f passa do limite do cartão (R$ %.2f)", valor, LIMITE_CARTAO);
            return Json.obj("aprovado", false, "motivo",
                    String.format("limite do cartão excedido (R$ %.2f > R$ %.2f)", valor, LIMITE_CARTAO));
        }
        String id = ("CARTAO".equals(tipo) ? "CRT-" : "PIX-") + String.format("%05d", transacoes.incrementAndGet());
        log.ok("APROVADO: transação %s", id);
        return Json.obj("aprovado", true, "transacaoId", id);
    }
}
