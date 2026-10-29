package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entrega garantida (Guaranteed Delivery / store-and-forward).
 *
 * Mensagens assíncronas (ex.: notificar o cliente) entram nesta fila. O ESB
 * tenta entregar na hora; se o serviço estiver fora do ar, a mensagem FICA
 * GUARDADA e o ESB tenta de novo a cada 3 s, até conseguir. Quem enviou não
 * espera e não perde nada.
 */
public class RetryQueue {

    private static final long INTERVAL_SECONDS = 3;

    /** Uma mensagem esperando entrega. */
    private static final class Pending {
        final String id;
        final String servico;
        final String operacao;
        final Map<String, Object> mensagem;
        final AtomicInteger tentativas = new AtomicInteger();

        Pending(String id, String servico, String operacao, Map<String, Object> mensagem) {
            this.id = id;
            this.servico = servico;
            this.operacao = operacao;
            this.mensagem = mensagem;
        }
    }

    private final ConcurrentLinkedQueue<Pending> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final ServiceInvoker invoker;
    private final MessageLog log;
    /** Uma thread só faz as entregas: a mesma mensagem nunca é entregue duas vezes ao mesmo tempo. */
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fila-reentrega");
        t.setDaemon(true);
        return t;
    });

    public RetryQueue(ServiceInvoker invoker, MessageLog log) {
        this.invoker = invoker;
        this.log = log;
    }

    public void start() {
        worker.scheduleWithFixedDelay(this::flush, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** Guarda a mensagem e tenta entregar já. */
    public String enqueue(String servico, String operacao, Map<String, Object> mensagem) {
        Pending p = new Pending("MSG-" + String.format("%03d", ids.incrementAndGet()), servico, operacao, mensagem);
        queue.add(p);
        log.add("FILA", "ESB", servico, operacao, "JSON", p.id + " guardada na fila de entrega garantida");
        worker.execute(this::flush);
        return p.id;
    }

    private void flush() {
        for (Pending p : queue) {
            int tentativa = p.tentativas.incrementAndGet();
            try {
                invoker.invoke(p.servico, p.operacao, p.mensagem);
                queue.remove(p);
                log.add("ENTREGUE", "ESB", p.servico, p.operacao, "JSON",
                        p.id + " entregue" + (tentativa > 1 ? " na tentativa " + tentativa : ""));
            } catch (ServiceInvoker.ServiceUnavailableException e) {
                if (tentativa == 1 || tentativa % 5 == 0) {
                    log.add("FILA", "ESB", p.servico, p.operacao, "JSON",
                            p.id + " continua na fila (" + e.getMessage() + "). Tentativa " + tentativa);
                }
            } catch (RuntimeException e) {
                queue.remove(p);
                log.add("ERRO", "ESB", p.servico, p.operacao, "JSON", p.id + " rejeitada pelo serviço: " + e.getMessage());
            }
        }
    }

    public List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Pending p : queue) {
            list.add(Json.obj("id", p.id, "servico", p.servico, "operacao", p.operacao,
                    "tentativas", p.tentativas.get(), "mensagem", p.mensagem));
        }
        return list;
    }
}
