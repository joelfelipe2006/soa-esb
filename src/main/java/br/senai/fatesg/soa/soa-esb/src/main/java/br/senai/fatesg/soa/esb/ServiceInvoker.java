package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Http;
import br.senai.fatesg.soa.common.Json;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Mediação: é aqui que o barramento chama um serviço de verdade.
 *
 * <ol>
 *   <li>Resolve o NOME lógico para o endereço real, consultando o registro.</li>
 *   <li>Se o serviço fala XML, traduz a mensagem canônica (ver {@link Transformer}).</li>
 *   <li>Faz a chamada HTTP, traduz a resposta de volta e registra tudo no monitor.</li>
 * </ol>
 */
public class ServiceInvoker {

    /** O serviço não está registrado, está fora do ar ou não respondeu. */
    public static class ServiceUnavailableException extends Exception {
        private static final long serialVersionUID = 1L;

        public ServiceUnavailableException(String message) {
            super(message);
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final ServiceRegistry registry;
    private final MessageLog log;

    public ServiceInvoker(ServiceRegistry registry, MessageLog log) {
        this.registry = registry;
        this.log = log;
    }

    public Map<String, Object> invoke(String servico, String operacao, Map<String, Object> mensagem)
            throws ServiceUnavailableException {
        ServiceRegistry.ServiceInfo s = registry.find(servico)
                .orElseThrow(() -> new ServiceUnavailableException(servico + " não está registrado no ESB"));
        if (!s.online()) {
            throw new ServiceUnavailableException(servico + " está fora do ar");
        }
        try {
            return s.xml() ? invokeXml(s, operacao, mensagem) : invokeJson(s, operacao, mensagem);
        } catch (IOException e) {
            registry.markOffline(servico);
            throw new ServiceUnavailableException(servico + " não respondeu (" + e.getClass().getSimpleName() + ")");
        }
    }

    private Map<String, Object> invokeJson(ServiceRegistry.ServiceInfo s, String operacao,
                                           Map<String, Object> mensagem) throws IOException {
        log.add("CHAMADA", "ESB", s.nome(), operacao, "JSON", "POST " + s.url() + "/" + operacao + " " + Json.stringify(mensagem));
        Http.Result r = Http.postJson(s.url() + "/" + operacao, mensagem, TIMEOUT);
        if (!r.ok()) {
            throw new IllegalStateException(s.nome() + " respondeu HTTP " + r.status() + ": " + r.body());
        }
        Map<String, Object> resposta = Json.parseObject(r.body());
        log.add("RESPOSTA", s.nome(), "ESB", operacao, "JSON", r.body());
        return resposta;
    }

    private Map<String, Object> invokeXml(ServiceRegistry.ServiceInfo s, String operacao,
                                          Map<String, Object> mensagem) throws IOException {
        String xml = Transformer.canonicoParaSoap(operacao, mensagem);
        log.add("TRANSFORMADO", "ESB", s.nome(), operacao, "JSON→XML",
                "JSON canônico virou SOAP/XML; campos renomeados (" + Transformer.descricaoMapeamento() + ")");
        log.add("CHAMADA", "ESB", s.nome(), operacao, "XML", "POST " + s.url() + " (envelope SOAP " + operacao + ")");
        Http.Result r = Http.post(s.url(), "text/xml; charset=utf-8", xml, TIMEOUT, "SOAPAction", operacao);
        Map<String, Object> resposta = Transformer.soapParaCanonico(r.body());
        log.add("RESPOSTA", s.nome(), "ESB", operacao, "XML", "resposta SOAP recebida (HTTP " + r.status() + ")");
        log.add("TRANSFORMADO", "ESB", "ESB", operacao, "XML→JSON", "resposta virou JSON canônico: " + Json.stringify(resposta));
        return resposta;
    }
}
