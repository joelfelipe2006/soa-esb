package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orquestração do processo de negócio "Fechar Pedido" (papel do BPEL na SOA).
 *
 * A loja faz UMA chamada ao barramento. O ESB coordena os serviços na ordem certa:
 *
 * <pre>
 *  1. Estoque.reservar         (JSON)
 *  2. Pagamento.cobrar         (roteado por conteúdo: PIX ou cartão)
 *       recusado? -> Estoque.liberar   (COMPENSAÇÃO: desfaz o passo 1)
 *  3. Faturamento.emitirNota   (sistema legado: o ESB traduz JSON <-> XML/SOAP)
 *  4. Notificacao.enviar       (assíncrono, pela fila de entrega garantida)
 * </pre>
 *
 * Nenhum serviço conhece os outros. Toda a lógica de integração mora aqui.
 */
public class Orchestrator {

    private final ServiceInvoker invoker;
    private final ContentBasedRouter router;
    private final RetryQueue queue;
    private final MessageLog log;
    private final AtomicInteger numeracao = new AtomicInteger();

    public Orchestrator(ServiceInvoker invoker, ContentBasedRouter router, RetryQueue queue, MessageLog log) {
        this.invoker = invoker;
        this.router = router;
        this.queue = queue;
        this.log = log;
    }

    public Map<String, Object> fecharPedido(Map<String, Object> entrada) {
        // ---- 0. valida e monta a mensagem canônica
        String cliente = texto(entrada, "cliente", "Cliente");
        String produto = texto(entrada, "produto", null);
        int quantidade = (int) Json.num(entrada, "quantidade", 1);
        String forma = texto(entrada, "formaPagamento", "PIX").toUpperCase();
        if (produto == null) {
            throw new IllegalArgumentException("informe o produto");
        }
        if (quantidade < 1) {
            throw new IllegalArgumentException("quantidade precisa ser maior que zero");
        }
        String pedidoId = String.format("PED-%04d", numeracao.incrementAndGet());
        Map<String, Object> pedido = Json.obj("pedidoId", pedidoId, "cliente", cliente, "produto", produto,
                "quantidade", quantidade, "formaPagamento", forma);
        log.add("RECEBIDO", "loja", "ESB", "fecharPedido", "JSON", pedidoId + " " + Json.stringify(pedido));

        // ---- 1. reservar no estoque
        Map<String, Object> reserva;
        try {
            reserva = invoker.invoke("estoque", "reservar",
                    Json.obj("pedidoId", pedidoId, "produto", produto, "quantidade", quantidade));
        } catch (ServiceInvoker.ServiceUnavailableException e) {
            return rejeitar(pedido, "Estoque indisponível: " + e.getMessage());
        }
        if (!Json.bool(reserva, "ok")) {
            return rejeitar(pedido, Json.str(reserva, "mensagem"));
        }
        double valor = Json.num(reserva, "precoUnitario", 0) * quantidade;
        pedido.put("valor", valor);

        // ---- 2. cobrar (o ESB escolhe o serviço pelo conteúdo)
        Map<String, Object> pagamento;
        String servicoPagamento;
        try {
            servicoPagamento = router.route("pagamento", pedido);
            pagamento = invoker.invoke(servicoPagamento, "cobrar",
                    Json.obj("pedidoId", pedidoId, "cliente", cliente, "valor", valor));
        } catch (ServiceInvoker.ServiceUnavailableException | RuntimeException e) {
            compensarEstoque(pedido);
            return rejeitar(pedido, "Pagamento indisponível: " + e.getMessage());
        }
        if (!Json.bool(pagamento, "aprovado")) {
            compensarEstoque(pedido);
            return rejeitar(pedido, "Pagamento recusado: " + Json.str(pagamento, "motivo"));
        }
        pedido.put("transacao", Json.str(pagamento, "transacaoId"));

        // ---- 3. emitir nota no sistema LEGADO (XML/SOAP)
        String numeroNota;
        try {
            Map<String, Object> nota = invoker.invoke("faturamento", "emitirNota", pedido);
            numeroNota = Json.str(nota, "numeroNota");
        } catch (ServiceInvoker.ServiceUnavailableException e) {
            // O pagamento já foi feito: não desfaz. A nota é emitida quando o legado voltar.
            queue.enqueue("faturamento", "emitirNota", pedido);
            numeroNota = "pendente (legado fora do ar, na fila)";
        } catch (RuntimeException e) {
            numeroNota = "erro: " + e.getMessage();
            log.add("ERRO", "faturamento", "ESB", "emitirNota", "XML", e.getMessage());
        }

        // ---- 4. notificar o cliente (assíncrono, entrega garantida)
        String msg = String.format("Olá, %s! Pedido %s aprovado: %d x %s, R$ %.2f via %s. Nota fiscal: %s.",
                cliente, pedidoId, quantidade, produto, valor, forma, numeroNota);
        queue.enqueue("notificacao", "enviar", Json.obj("pedidoId", pedidoId, "cliente", cliente, "mensagem", msg));

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("status", "APROVADO");
        resposta.put("pedidoId", pedidoId);
        resposta.put("cliente", cliente);
        resposta.put("produto", produto);
        resposta.put("quantidade", quantidade);
        resposta.put("valor", valor);
        resposta.put("pagamento", servicoPagamento + " (" + Json.str(pagamento, "transacaoId") + ")");
        resposta.put("notaFiscal", numeroNota);
        resposta.put("notificacao", "na fila de entrega garantida");
        log.add("CONCLUIDO", "ESB", "loja", "fecharPedido", "JSON",
                pedidoId + " APROVADO, R$ " + String.format("%.2f", valor) + ", nota " + numeroNota);
        return resposta;
    }

    /** Consulta de estoque exposta pelo barramento (a loja não sabe onde o estoque está). */
    public Map<String, Object> consultarEstoque() {
        log.add("RECEBIDO", "loja", "ESB", "consultarEstoque", "JSON", "consulta de produtos");
        try {
            return invoker.invoke("estoque", "consultar", Json.obj());
        } catch (ServiceInvoker.ServiceUnavailableException e) {
            return Json.obj("erro", e.getMessage());
        }
    }

    /** Desfaz a reserva do estoque quando um passo seguinte falha (transação de compensação). */
    private void compensarEstoque(Map<String, Object> pedido) {
        log.add("COMPENSACAO", "ESB", "estoque", "liberar", "JSON",
                Json.str(pedido, "pedidoId") + ": desfazendo a reserva do estoque");
        try {
            invoker.invoke("estoque", "liberar", Json.obj("pedidoId", pedido.get("pedidoId"),
                    "produto", pedido.get("produto"), "quantidade", pedido.get("quantidade")));
        } catch (ServiceInvoker.ServiceUnavailableException e) {
            queue.enqueue("estoque", "liberar", Json.obj("pedidoId", pedido.get("pedidoId"),
                    "produto", pedido.get("produto"), "quantidade", pedido.get("quantidade")));
        }
    }

    private Map<String, Object> rejeitar(Map<String, Object> pedido, String motivo) {
        log.add("ERRO", "ESB", "loja", "fecharPedido", "JSON", Json.str(pedido, "pedidoId") + " REJEITADO: " + motivo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "REJEITADO");
        r.put("pedidoId", pedido.get("pedidoId"));
        r.put("motivo", motivo);
        return r;
    }

    private static String texto(Map<String, Object> m, String k, String padrao) {
        String v = Json.str(m, k);
        return v == null || v.isBlank() ? padrao : v.trim();
    }
}
