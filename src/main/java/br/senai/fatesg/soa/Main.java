package br.senai.fatesg.soa;

import br.senai.fatesg.soa.client.LojaClient;
import br.senai.fatesg.soa.common.Log;
import br.senai.fatesg.soa.esb.EsbServer;
import br.senai.fatesg.soa.services.BaseService;
import br.senai.fatesg.soa.services.EstoqueService;
import br.senai.fatesg.soa.services.FaturamentoLegadoService;
import br.senai.fatesg.soa.services.NotificacaoService;
import br.senai.fatesg.soa.services.PagamentoService;

import java.net.BindException;
import java.util.List;

/**
 * Ponto de entrada. Cada componente roda como um PROCESSO separado:
 *
 * <pre>
 *   java -cp out br.senai.fatesg.soa.Main esb
 *   java -cp out br.senai.fatesg.soa.Main estoque
 *   java -cp out br.senai.fatesg.soa.Main pagamento-pix
 *   java -cp out br.senai.fatesg.soa.Main pagamento-cartao
 *   java -cp out br.senai.fatesg.soa.Main faturamento
 *   java -cp out br.senai.fatesg.soa.Main notificacao
 *   java -cp out br.senai.fatesg.soa.Main loja
 * </pre>
 *
 * Opções: --porta N, --esb http://IP:8080 (onde está o barramento),
 * --host IP (endereço que o serviço anuncia), --sem-cor.
 */
public final class Main {

    private static final List<String> COMPONENTES = List.of(
            "esb", "estoque", "pagamento-pix", "pagamento-cartao", "faturamento", "notificacao", "loja", "tudo");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !COMPONENTES.contains(args[0])) {
            uso();
            System.exit(args.length == 0 ? 0 : 1);
        }
        String componente = args[0];
        Integer porta = null;
        String esb = "http://localhost:8080";
        String host = "localhost";
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--porta" -> porta = Integer.parseInt(args[++i]);
                case "--esb" -> esb = semBarraFinal(args[++i]);
                case "--host" -> host = args[++i];
                case "--sem-cor" -> Log.setColors(false);
                case "--cor" -> Log.setColors(true);
                default -> {
                    System.err.println("Opção desconhecida: " + args[i]);
                    uso();
                    System.exit(1);
                }
            }
        }
        if (!esb.startsWith("http")) {
            esb = "http://" + esb;
        }

        try {
            switch (componente) {
                case "esb" -> new EsbServer(porta != null ? porta : 8080).start();
                case "loja" -> {
                    new LojaClient(esb).run();
                    System.exit(0);
                }
                case "tudo" -> tudo(esb, host);
                default -> servico(componente, host, porta, esb).start();
            }
        } catch (BindException e) {
            System.err.println("A porta já está em uso. Feche o outro processo ou use --porta <outra>.");
            System.exit(1);
        }
        Thread.currentThread().join(); // mantém o processo vivo (servidores rodam em threads)
    }

    static BaseService servico(String nome, String host, Integer porta, String esb) {
        return switch (nome) {
            case "estoque" -> new EstoqueService(host, porta != null ? porta : 8081, esb);
            case "pagamento-pix" -> new PagamentoService("PIX", host, porta != null ? porta : 8082, esb);
            case "pagamento-cartao" -> new PagamentoService("CARTAO", host, porta != null ? porta : 8083, esb);
            case "faturamento" -> new FaturamentoLegadoService(host, porta != null ? porta : 8084, esb);
            case "notificacao" -> new NotificacaoService(host, porta != null ? porta : 8085, esb);
            default -> throw new IllegalArgumentException(nome);
        };
    }

    /** Plano B: barramento e todos os serviços num processo só (para testes rápidos). */
    private static void tudo(String esb, String host) throws Exception {
        new EsbServer(8080).start();
        for (String s : List.of("estoque", "pagamento-pix", "pagamento-cartao", "faturamento", "notificacao")) {
            servico(s, host, null, esb).start();
        }
    }

    private static String semBarraFinal(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static void uso() {
        System.out.println("""
                Uso: run <componente> [opções]
                  Componentes: esb | estoque | pagamento-pix | pagamento-cartao | faturamento | notificacao | loja | tudo
                  --porta <n>        porta HTTP (padrões: esb 8080, estoque 8081, pix 8082, cartão 8083,
                                     faturamento 8084, notificação 8085)
                  --esb <url>        endereço do barramento (padrão http://localhost:8080)
                  --host <ip>        endereço que o serviço anuncia ao ESB (padrão localhost)
                  --sem-cor          desliga as cores do terminal
                """);
    }
}
