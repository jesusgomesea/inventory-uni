package br.com.rdamasio.inventario.comum;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache simples com validade, em memória. Suficiente para o volume daqui (listas de locais, status e o catálogo de
 * modelos de peças), sem trazer uma biblioteca de cache. Limpa tudo quando passa de {@code limite} entradas.
 */
public final class CacheTempo<K, V> {

    private record Entrada<V>(V valor, long expiraEm) {
    }

    private final ConcurrentHashMap<K, Entrada<V>> mapa = new ConcurrentHashMap<>();
    private final long validadeNanos;
    private final int limite;

    public CacheTempo(Duration validade, int limite) {
        this.validadeNanos = validade.toNanos();
        this.limite = limite;
    }

    public V obter(K chave, Supplier<V> carregar) {
        long agora = System.nanoTime();
        Entrada<V> e = mapa.get(chave);
        if (e != null && e.expiraEm() - agora > 0) return e.valor();
        V valor = carregar.get();
        if (mapa.size() >= limite) mapa.clear();
        mapa.put(chave, new Entrada<>(valor, agora + validadeNanos));
        return valor;
    }

    public void remover(K chave) {
        mapa.remove(chave);
    }
}
