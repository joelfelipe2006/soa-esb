package br.senai.fatesg.soa.services;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Http.Response;
import br.senai.fatesg.soa.common.Json;
import com.sun.net.httpserver.HttpServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serviço de Estoque (JSON): reserva, libera e consulta produtos. */
public class EstoqueService extends BaseService {

    private record Produto(String nome, double preco, int disponivel) {
    }

    private final Map<String, Produto> produtos = new LinkedHashMap<>();

    public EstoqueService(String host, int porta, String esbUrl) {
        super("estoque", host, porta, esbUrl);
        add("teclado", 250.00, 10);
        add("mouse", 90.00, 20);
        add("monitor", 1200.00, 5);
        add("notebook", 4500.00, 3);
    }

    private void add(String nome, double preco, int qtd) {
        produtos.put(nome, new Produto(nome, preco, qtd));
    }

    @Override
    protected String formato() {
        return "JSON";
    }

    @Override
    protected String descricao() {
        return "Controle de estoque de produtos";
    }

    @Override
    protected List<String> operacoes() {
        return List.of("reservar", "liberar", "consultar");
    }

    @Override
    protected void rotas(HttpServer server) {
        Http.route(server, "/reservar", req -> Response.json(reservar(req.json())));
        Http.route(server, "/liberar", req -> Response.json(liberar(req.json())));
        Http.route(server, "/consultar", req -> Response.json(consultar()));
    }

    private synchronized Map<String, Object> reservar(Map<String, Object> m) {
        String nome = String.valueOf(Json.str(m, "produto")).toLowerCase();
        int qtd = (int) Json.num(m, "quantidade", 1);
        log.in("reservar %d x %s (pedido %s)", qtd, nome, Json.str(m, "pedidoId"));
        Produto p = produtos.get(nome);
        if (p == null) {
            log.warn("Produto '%s' não existe", nome);
            return Json.obj("ok", false, "mensagem", "Produto '" + nome + "' não existe");
        }
        if (p.disponivel() < qtd) {
            log.warn("Estoque insuficiente de %s: pedido %d, disponível %d", nome, qtd, p.disponivel());
            return Json.obj("ok", false, "mensagem",
                    "Estoque insuficiente de " + nome + " (disponível: " + p.disponivel() + ")");
        }
        produtos.put(nome, new Produto(nome, p.preco(), p.disponivel() - qtd));
        log.ok("Reservado. %s agora tem %d disponível(is)", nome, p.disponivel() - qtd);
        return Json.obj("ok", true, "precoUnitario", p.preco(), "restante", p.disponivel() - qtd);
    }

    private synchronized Map<String, Object> liberar(Map<String, Object> m) {
        String nome = String.valueOf(Json.str(m, "produto")).toLowerCase();
        int qtd = (int) Json.num(m, "quantidade", 1);
        Produto p = produtos.get(nome);
        if (p != null) {
            produtos.put(nome, new Produto(nome, p.preco(), p.disponivel() + qtd));
            log.warn("COMPENSAÇÃO: liberei %d x %s do pedido %s (disponível: %d)",
                    qtd, nome, Json.str(m, "pedidoId"), p.disponivel() + qtd);
        }
        return Json.obj("ok", p != null);
    }

    private synchronized Map<String, Object> consultar() {
        log.in("consultar produtos");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Produto p : produtos.values()) {
            list.add(Json.obj("produto", p.nome(), "preco", p.preco(), "disponivel", p.disponivel()));
        }
        return Json.obj("produtos", list);
    }
}
