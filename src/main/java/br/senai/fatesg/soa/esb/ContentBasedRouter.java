package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Roteamento baseado em conteúdo (Content-Based Router).
 *
 * Quem envia não escolhe o destino: o ESB olha o CONTEÚDO da mensagem e decide.
 * Aqui, o campo "formaPagamento" decide se a cobrança vai para o serviço de PIX
 * ou para o de cartão. Para incluir um novo meio de pagamento (ex.: boleto),
 * basta uma regra nova aqui: a loja não muda nada.
 */
public class ContentBasedRouter {

    /** Uma regra: se a condição vale, a mensagem vai para o destino. */
    public record Rule(String descricao, Predicate<Map<String, Object>> condicao, String destino) {
    }

    private final Map<String, List<Rule>> rules = new LinkedHashMap<>();
    private final MessageLog log;

    public ContentBasedRouter(MessageLog log) {
        this.log = log;
        addRule("pagamento", "formaPagamento = PIX",
                m -> "PIX".equalsIgnoreCase(Json.str(m, "formaPagamento")), "pagamento-pix");
        addRule("pagamento", "formaPagamento = CARTAO",
                m -> "CARTAO".equalsIgnoreCase(Json.str(m, "formaPagamento")), "pagamento-cartao");
    }

    public final void addRule(String canal, String descricao, Predicate<Map<String, Object>> condicao, String destino) {
        rules.computeIfAbsent(canal, k -> new ArrayList<>()).add(new Rule(descricao, condicao, destino));
    }

    /** Decide o destino de uma mensagem do canal informado. */
    public String route(String canal, Map<String, Object> mensagem) {
        for (Rule r : rules.getOrDefault(canal, List.of())) {
            if (r.condicao().test(mensagem)) {
                log.add("ROTEADO", "ESB", r.destino(), canal, "-",
                        "regra '" + r.descricao() + "' → destino " + r.destino());
                return r.destino();
            }
        }
        throw new IllegalArgumentException("nenhuma regra de " + canal + " aceita: " + Json.stringify(mensagem));
    }

    public List<String> describe() {
        List<String> list = new ArrayList<>();
        rules.forEach((canal, rs) -> rs.forEach(r -> list.add(canal + ": " + r.descricao() + " → " + r.destino())));
        return list;
    }
}
