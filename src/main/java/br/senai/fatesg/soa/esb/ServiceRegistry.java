package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de serviços (o "catálogo" da SOA, papel do UDDI nos web services).
 *
 * Cada serviço, ao subir, se registra no barramento dizendo: meu nome, onde estou
 * (URL), que formato falo (JSON ou XML) e que operações ofereço (o contrato).
 * Depois manda um sinal de vida a cada 5 s. Quem consome NÃO precisa saber a URL
 * de ninguém: só o nome lógico. Isso é a transparência de localização.
 */
public class ServiceRegistry {

    /** Sem sinal de vida por esse tempo, o serviço é considerado fora do ar. */
    public static final long TIMEOUT_MS = 12_000;

    /** Um serviço registrado e o seu contrato. */
    public record ServiceInfo(String nome, String url, String formato, String descricao,
                              List<String> operacoes, long ultimoSinal) {

        public boolean online() {
            return System.currentTimeMillis() - ultimoSinal < TIMEOUT_MS;
        }

        public boolean xml() {
            return "XML".equalsIgnoreCase(formato);
        }

        ServiceInfo offline() {
            return new ServiceInfo(nome, url, formato, descricao, operacoes, 0);
        }
    }

    private final Map<String, ServiceInfo> services = new ConcurrentHashMap<>();
    private final MessageLog log;

    public ServiceRegistry(MessageLog log) {
        this.log = log;
    }

    /** Registro ou sinal de vida de um serviço. */
    @SuppressWarnings("unchecked")
    public void register(Map<String, Object> data) {
        String nome = Json.str(data, "nome");
        String url = Json.str(data, "url");
        if (nome == null || url == null) {
            throw new IllegalArgumentException("registro precisa de 'nome' e 'url'");
        }
        List<String> ops = new ArrayList<>();
        if (data.get("operacoes") instanceof List<?> list) {
            for (Object o : list) {
                ops.add(String.valueOf(o));
            }
        }
        String formato = Json.str(data, "formato") == null ? "JSON" : Json.str(data, "formato").toUpperCase();
        ServiceInfo novo = new ServiceInfo(nome, url, formato, String.valueOf(data.getOrDefault("descricao", "")),
                ops, System.currentTimeMillis());
        ServiceInfo antigo = services.put(nome, novo);

        if (antigo == null) {
            log.add("REGISTRO", nome, "ESB", "registrar", formato,
                    "Novo serviço " + nome + " em " + url + " | operações: " + String.join(", ", ops));
        } else if (!antigo.online()) {
            log.add("REGISTRO", nome, "ESB", "registrar", formato, nome + " voltou a ficar online em " + url);
        } else if (!antigo.url().equals(url)) {
            log.add("REGISTRO", nome, "ESB", "registrar", formato, nome + " mudou de endereço: " + url);
        }
    }

    public Optional<ServiceInfo> find(String nome) {
        return Optional.ofNullable(services.get(nome));
    }

    /** Chamada ao serviço falhou: marca como fora do ar até o próximo sinal de vida. */
    public void markOffline(String nome) {
        ServiceInfo s = services.get(nome);
        if (s != null && s.online()) {
            services.put(nome, s.offline());
            log.add("ERRO", "ESB", nome, "-", s.formato(), nome + " não respondeu: marcado como FORA DO AR");
        }
    }

    /** Roda periodicamente: avisa quando um serviço para de mandar sinal de vida. */
    public void checkTimeouts() {
        for (ServiceInfo s : services.values()) {
            if (s.ultimoSinal() != 0 && !s.online()) {
                services.put(s.nome(), s.offline());
                log.add("ERRO", "ESB", s.nome(), "-", s.formato(),
                        s.nome() + " parou de mandar sinal de vida: FORA DO AR");
            }
        }
    }

    public List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        services.values().stream()
                .sorted((a, b) -> a.nome().compareTo(b.nome()))
                .forEach(s -> list.add(Json.obj("nome", s.nome(), "url", s.url(), "formato", s.formato(),
                        "descricao", s.descricao(), "operacoes", s.operacoes(), "online", s.online())));
        return list;
    }
}
