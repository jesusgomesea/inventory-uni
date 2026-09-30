/**
 * Formatação para a tela. O GLPI guarda memória e disco em MB (base 1024); mostramos em GB/TB com no máximo
 * uma casa, sem casa quando é inteiro ("16 GB", "7,5 GB", "1,8 TB").
 */

const numero1 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
const numero0 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });

export function tamanho(mb: number | null | undefined): string {
  if (mb == null || mb <= 0) return '—';
  const gb = mb / 1024;
  if (gb >= 1000) return `${numero1.format(gb / 1024)} TB`;
  if (gb >= 100) return `${numero0.format(gb)} GB`;
  if (gb >= 1) return `${numero1.format(gb)} GB`;
  return `${numero0.format(mb)} MB`;
}

/** Bytes (arquivos anexados) em KB/MB. */
export function bytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${numero0.format(n / 1024)} KB`;
  return `${numero1.format(n / 1024 / 1024)} MB`;
}

/** "2026-09-30 08:12:03" (GLPI) ou ISO (banco próprio) → "30/09/2026 08:12". */
export function dataHora(s: string | null | undefined): string {
  if (!s) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?/.exec(s);
  if (!m) return s;
  return m[4] ? `${m[3]}/${m[2]}/${m[1]} ${m[4]}:${m[5]}` : `${m[3]}/${m[2]}/${m[1]}`;
}

export function data(s: string | null | undefined): string {
  return dataHora(s).slice(0, 10);
}

/** Slot da memória: "1" → "Slot 1"; nomes como "ChannelA-DIMM0" ficam como vieram. */
export function slot(s: string | null): string {
  if (!s) return 'Slot ?';
  return /^\d+$/.test(s) ? `Slot ${s}` : s;
}

/** Percentual usado de um volume (0–100). */
export function usoPercentual(totalMb: number, livreMb: number): number {
  if (totalMb <= 0) return 0;
  return Math.round(((totalMb - livreMb) / totalMb) * 100);
}
