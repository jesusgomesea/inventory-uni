package br.com.rdamasio.inventario.comum;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.slf4j.MDC;

/**
 * Chamadas ao GLPI em paralelo, em threads virtuais (Java 21). A ficha precisa de 6 a 10 consultas; em sequência
 * seriam ~1 s, em paralelo ficam no tempo da mais lenta. Leva o MDC (id da requisição) para a thread nova, assim o
 * log das chamadas paralelas continua com o {@code [req=...]} de quem pediu.
 */
public final class Paralelo {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private Paralelo() {
    }

    public static <T> CompletableFuture<T> rodar(Supplier<T> tarefa) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return CompletableFuture.supplyAsync(() -> {
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                return tarefa.get();
            } finally {
                MDC.clear();
            }
        }, EXECUTOR);
    }

    /** Espera o resultado, relançando o erro original (e não o CompletionException que o embrulha). */
    public static <T> T esperar(CompletableFuture<T> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido", e);
        } catch (ExecutionException | CompletionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof RuntimeException r) throw r;
            throw new IllegalStateException(causa);
        }
    }
}
