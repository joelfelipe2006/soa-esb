package br.senai.fatesg.soa.client;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Json;
import br.senai.fatesg.soa.common.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A Loja: o CONSUMIDOR dos serviços.
 *
 * Repare no que ela NÃO sabe: onde está o estoque, que existe um sistema de
 * pagamento PIX e outro de cartão, que o faturamento fala XML. Ela só conhece
 * o endereço do barramento e faz uma chamada: "fechar pedido".
 */
public class LojaClient {

    private final String esbUrl;
    private final Log log = new Log("LOJA");
    private String cliente = "Maria";

    public LojaClient(String esbUrl) {
        this.esbUrl = esbUrl;
    }

    public void run() throws IOException {
        ajuda();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        System.out.print("loja> ");
        System.out.flush();
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !executar(line)) {
                break;
            }
            System.out.print("loja> ");
            System.out.flush();
        }
    }

    /** Retorna false para sair. */
    public boolean executar(String line) {
        String[] p = line.split("\\s+");
        switch (p[0].toLowerCase()) {
            case "comprar", "c" -> {
                if (p.length < 2) {
                    log.warn("Uso: comprar <produto> [quantidade] [pix|cartao]");
                    break;
                }
                int qtd = 1;
                String forma = "PIX";
                for (int i = 2; i < p.length; i++) {
                    if (p[i].matches("\\d+")) {
                        qtd = Integer.parseInt(p[i]);
                    } else {
                        forma = p[i].toUpperCase();
                    }
                }
                comprar(p[1], qtd, forma);
            }
            case "estoque", "e" -> estoque();
            case "cliente" -> {
                if (p.length > 1) {
                    cliente = line.substring(line.indexOf(' ') + 1).trim();
                }
                log.info("Cliente atual: %s", cliente);
            }
            case "ajuda", "?" -> ajuda();
            case "sair", "q" -> {
                return false;
            }
            default -> log.warn("Comando desconhecido. Digite 'ajuda'.");
        }
        return true;
    }

    private void comprar(String produto, int qtd, String forma) {
        Map<String, Object> pedido = Json.obj("cliente", cliente, "produto", produto,
                "quantidade", qtd, "formaPagamento", forma);
        log.out("POST %s/esb/pedidos %s", esbUrl, Json.stringify(pedido));
        try {
            Http.Result r = Http.postJson(esbUrl + "/esb/pedidos", pedido, Duration.ofSeconds(20));
            Map<String, Object> resp = Json.parseObject(r.body());
            if ("APROVADO".equals(Json.str(resp, "status"))) {
                log.ok("%s APROVADO | R$ %.2f | %s | nota %s", Json.str(resp, "pedidoId"),
                        Json.num(resp, "valor", 0), Json.str(resp, "pagamento"), Json.str(resp, "notaFiscal"));
            } else if ("REJEITADO".equals(Json.str(resp, "status"))) {
                log.warn("%s REJEITADO | %s", Json.str(resp, "pedidoId"), Json.str(resp, "motivo"));
            } else {
                log.warn("Erro: %s", r.body());
            }
        } catch (IOException e) {
            log.warn("ESB fora do ar (%s): %s", esbUrl, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void estoque() {
        try {
            Map<String, Object> resp = Json.parseObject(Http.get(esbUrl + "/esb/estoque", Duration.ofSeconds(10)).body());
            if (resp.containsKey("erro")) {
                log.warn("%s", Json.str(resp, "erro"));
                return;
            }
            Log.plain("  Produto      Preço        Disponível");
            for (Object o : (List<Object>) resp.getOrDefault("produtos", List.of())) {
                Map<String, Object> m = (Map<String, Object>) o;
                Log.plain("  %-12s R$ %8.2f   %s", Json.str(m, "produto"), Json.num(m, "preco", 0), Json.str(m, "disponivel"));
            }
        } catch (IOException e) {
            log.warn("ESB fora do ar (%s): %s", esbUrl, e.getMessage());
        }
    }

    private void ajuda() {
        Log.plain("");
        Log.plain("=== Loja (consumidor) - conhece só o ESB: %s ===", esbUrl);
        Log.plain("  comprar <produto> [qtd] [pix|cartao]   ex.: comprar teclado 2 pix");
        Log.plain("  estoque                                consulta produtos pelo ESB");
        Log.plain("  cliente <nome>                         muda o nome do cliente");
        Log.plain("  sair");
        Log.plain("  Produtos: teclado, mouse, monitor, notebook");
        Log.plain("");
    }
}
