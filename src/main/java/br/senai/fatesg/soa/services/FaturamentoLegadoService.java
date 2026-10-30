package br.senai.fatesg.soa.services;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Http.Response;
import br.senai.fatesg.soa.common.Xml;
import com.sun.net.httpserver.HttpServer;
import org.w3c.dom.Element;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sistema LEGADO de faturamento: emite nota fiscal.
 *
 * Simula um sistema antigo que ninguém quer (ou pode) alterar: só entende
 * XML/SOAP, com o próprio vocabulário (numeroPedido, nomeDestinatario,
 * valorTotal). Se receber JSON, recusa. Quem faz a ponte é o ESB.
 * É o cenário típico de integração em bancos e no governo (ex.: NF-e via SOAP).
 */
public class FaturamentoLegadoService extends BaseService {

    private static final String PREFIX = "fat";
    private static final String NS = "http://fatesg.senai.br/faturamento";

    private final AtomicInteger notas = new AtomicInteger((int) (System.currentTimeMillis() / 1000 % 9000) + 1000);

    public FaturamentoLegadoService(String host, int porta, String esbUrl) {
        super("faturamento", host, porta, esbUrl);
    }

    @Override
    protected String formato() {
        return "XML";
    }

    @Override
    protected String descricao() {
        return "Sistema legado de nota fiscal (só XML/SOAP)";
    }

    @Override
    protected List<String> operacoes() {
        return List.of("emitirNota");
    }

    @Override
    protected void rotas(HttpServer server) {
        // Um único endpoint SOAP: a operação vem dentro do envelope.
        Http.route(server, "/", req -> {
            if (!"/".equals(req.path())) {
                return Response.notFound();
            }
            if (!req.isXml()) {
                log.warn("Recebi algo que não é XML (%s). Este sistema só fala SOAP!", req.contentType());
                return Response.xml(415, fault("Client", "Este sistema só aceita XML/SOAP (text/xml)"));
            }
            log.in("Envelope SOAP recebido:");
            log.block(req.body());
            Element op = Xml.soapOperation(Xml.parse(req.body()));
            if (!"emitirNota".equals(op.getLocalName())) {
                return Response.xml(500, fault("Client", "Operação desconhecida: " + op.getLocalName()));
            }
            return Response.xml(200, emitirNota(Xml.fields(op)));
        });
    }

    private String emitirNota(Map<String, String> campos) {
        String numero = "NF-" + notas.incrementAndGet();
        log.ok("Nota %s emitida para %s, pedido %s, valor %s",
                numero, campos.get("nomeDestinatario"), campos.get("numeroPedido"), campos.get("valorTotal"));
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("numeroNF", numero);
        resposta.put("situacao", "AUTORIZADA");
        resposta.put("dataEmissao", LocalDate.now().toString());
        return Xml.soapEnvelope(PREFIX, NS, "emitirNotaResponse", resposta);
    }

    private static String fault(String code, String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<soap:Envelope xmlns:soap=\"" + Xml.SOAP_NS + "\"><soap:Body><soap:Fault>"
                + "<faultcode>soap:" + code + "</faultcode><faultstring>" + Xml.escape(message) + "</faultstring>"
                + "</soap:Fault></soap:Body></soap:Envelope>";
    }
}
