package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transformação de mensagens (Message Translator).
 *
 * Dentro do barramento todos usam o MODELO CANÔNICO: JSON com nomes de campo
 * padronizados (pedidoId, cliente, valor...). Sistemas que falam outra língua
 * recebem a mensagem traduzida pelo ESB. Duas traduções acontecem aqui:
 *
 * <ol>
 *   <li><b>Formato:</b> JSON  &lt;-&gt;  XML/SOAP</li>
 *   <li><b>Vocabulário:</b> "cliente" vira "nomeDestinatario", "valor" vira "valorTotal"...</li>
 * </ol>
 *
 * Assim, nem a loja precisa aprender SOAP, nem o sistema legado precisa ser alterado.
 */
public final class Transformer {

    public static final String LEGADO_PREFIX = "fat";
    public static final String LEGADO_NS = "http://fatesg.senai.br/faturamento";

    /** Nome canônico -> nome no sistema legado de faturamento. */
    private static final Map<String, String> PARA_LEGADO = Map.of(
            "pedidoId", "numeroPedido",
            "cliente", "nomeDestinatario",
            "valor", "valorTotal",
            "produto", "descricaoItem",
            "quantidade", "qtdItens");

    /** Nome no legado -> nome canônico (respostas). */
    private static final Map<String, String> DO_LEGADO = Map.of(
            "numeroNF", "numeroNota",
            "situacao", "situacaoNota",
            "dataEmissao", "dataEmissao");

    private Transformer() {
    }

    /** Mensagem canônica (JSON) -> envelope SOAP do legado. */
    public static String canonicoParaSoap(String operacao, Map<String, Object> canonico) {
        Map<String, Object> campos = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : canonico.entrySet()) {
            String nome = PARA_LEGADO.get(e.getKey());
            if (nome != null) {
                Object v = e.getValue();
                if (v instanceof Double d) {
                    v = String.format(java.util.Locale.ROOT, "%.2f", d);
                }
                campos.put(nome, v);
            }
        }
        return Xml.soapEnvelope(LEGADO_PREFIX, LEGADO_NS, operacao, campos);
    }

    /** Resposta SOAP do legado -> mensagem canônica (JSON). */
    public static Map<String, Object> soapParaCanonico(String xml) {
        Document doc = Xml.parse(xml);
        Element op = Xml.soapOperation(doc);
        if ("Fault".equals(op.getLocalName())) {
            Map<String, String> f = Xml.fields(op);
            throw new IllegalStateException("SOAP Fault do legado: " + f.getOrDefault("faultstring", "erro"));
        }
        Map<String, Object> canonico = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : Xml.fields(op).entrySet()) {
            canonico.put(DO_LEGADO.getOrDefault(e.getKey(), e.getKey()), e.getValue());
        }
        return canonico;
    }

    /** Resumo de uma linha para logs: "cliente=nomeDestinatario, valor=valorTotal...". */
    public static String descricaoMapeamento() {
        StringBuilder sb = new StringBuilder();
        PARA_LEGADO.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ", ").append(k).append("→").append(v));
        return sb.toString();
    }
}
