package br.senai.fatesg.soa.esb;

import br.senai.fatesg.soa.common.Json;
import br.senai.fatesg.soa.common.Log;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Monitoramento: todo passo de toda mensagem que atravessa o barramento fica
 * registrado aqui. Como TUDO passa pelo ESB, ele enxerga a integração inteira:
 * é uma das grandes vantagens (e responsabilidades) de um barramento central.
 *
 * Alimenta o log do terminal e o painel web.
 */
public class MessageLog {

    private static final int MAX = 300;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Um passo registrado. */
    public record Entry(long seq, String hora, String tipo, String de, String para,
                        String operacao, String formato, String detalhe) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Log console = new Log("ESB");
    private long seq;

    /**
     * @param tipo REGISTRO, RECEBIDO, ROTEADO, CHAMADA, RESPOSTA, TRANSFORMADO,
     *             FILA, ENTREGUE, COMPENSACAO, CONCLUIDO, ERRO
     */
    public void add(String tipo, String de, String para, String operacao, String formato, String detalhe) {
        Entry e;
        synchronized (this) {
            e = new Entry(++seq, LocalTime.now().format(TIME), tipo, de, para, operacao, formato, detalhe);
            entries.addLast(e);
            while (entries.size() > MAX) {
                entries.removeFirst();
            }
        }
        String line = String.format("%-12s %s -> %s  %s", tipo, de, para, detalhe);
        switch (tipo) {
            case "ROTEADO" -> console.route(line);
            case "TRANSFORMADO" -> console.xform(line);
            case "FILA" -> console.queue(line);
            case "ERRO", "COMPENSACAO" -> console.warn(line);
            case "RECEBIDO", "RESPOSTA" -> console.in(line);
            case "CHAMADA", "ENTREGUE" -> console.out(line);
            default -> console.ok(line);
        }
    }

    /** Entradas com número de sequência maior que {@code after}. */
    public synchronized List<Map<String, Object>> since(long after) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : entries) {
            if (e.seq() > after) {
                list.add(Json.obj("seq", e.seq(), "hora", e.hora(), "tipo", e.tipo(), "de", e.de(),
                        "para", e.para(), "operacao", e.operacao(), "formato", e.formato(), "detalhe", e.detalhe()));
            }
        }
        return list;
    }
}
