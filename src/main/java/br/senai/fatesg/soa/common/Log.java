package br.senai.fatesg.soa.common;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Log colorido no terminal, com o nome do componente (ESB, ESTOQUE, ...).
 * Cada cor marca um tipo de passo, para a plateia acompanhar a mensagem na demo.
 */
public final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String RED = "\u001B[91m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BLUE = "\u001B[94m";
    private static final String MAGENTA = "\u001B[95m";
    private static final String CYAN = "\u001B[96m";

    private static volatile boolean colors = defaultColors();

    private final String component;

    public Log(String component) {
        this.component = component;
    }

    /** No Windows, só usa cor no Windows Terminal (o cmd antigo mostra lixo no lugar das cores). */
    private static boolean defaultColors() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return !windows || System.getenv("WT_SESSION") != null;
    }

    public static void setColors(boolean enabled) {
        colors = enabled;
    }

    public void info(String fmt, Object... a)  { print(CYAN, "INFO", fmt, a); }
    public void ok(String fmt, Object... a)    { print(GREEN, " OK ", fmt, a); }
    /** Mensagem que chegou. */
    public void in(String fmt, Object... a)    { print(BLUE, " <- ", fmt, a); }
    /** Mensagem enviada. */
    public void out(String fmt, Object... a)   { print(GREEN, " -> ", fmt, a); }
    /** Decisão de roteamento. */
    public void route(String fmt, Object... a) { print(YELLOW, "ROTA", fmt, a); }
    /** Transformação de formato. */
    public void xform(String fmt, Object... a) { print(MAGENTA, "XFRM", fmt, a); }
    /** Fila de reentrega. */
    public void queue(String fmt, Object... a) { print(GRAY, "FILA", fmt, a); }
    public void warn(String fmt, Object... a)  { print(RED, "WARN", fmt, a); }

    /** Bloco de texto cru (ex.: o XML recebido pelo legado), sem horário. */
    public void block(String text) {
        synchronized (Log.class) {
            for (String line : text.split("\n")) {
                System.out.println(colors ? MAGENTA + "        " + line + RESET : "        " + line);
            }
        }
    }

    public static void plain(String fmt, Object... a) {
        synchronized (Log.class) {
            System.out.println(a.length == 0 ? fmt : String.format(fmt, a));
        }
    }

    private void print(String color, String tag, String fmt, Object... args) {
        String msg = args.length == 0 ? fmt : String.format(fmt, args);
        String line = LocalTime.now().format(TIME) + " [" + tag + "] " + component + " | " + msg;
        synchronized (Log.class) {
            System.out.println(colors ? color + line + RESET : line);
        }
    }
}
