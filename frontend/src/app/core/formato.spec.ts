import { dataHora, slot, tamanho, usoPercentual } from './formato';

describe('formato', () => {
  it('converte MB do GLPI em GB/TB', () => {
    expect(tamanho(16384)).toBe('16 GB');
    expect(tamanho(7680)).toBe('7,5 GB');
    expect(tamanho(228936)).toBe('224 GB');
    expect(tamanho(1907729)).toBe('1,8 TB');
    expect(tamanho(512)).toBe('512 MB');
    expect(tamanho(0)).toBe('—');
  });

  it('formata datas do GLPI e do banco', () => {
    expect(dataHora('2026-09-30 08:12:03')).toBe('30/09/2026 08:12');
    expect(dataHora('2026-09-30T20:17:42.68')).toBe('30/09/2026 20:17');
    expect(dataHora('2024-02-10')).toBe('10/02/2024');
  });

  it('nomeia slots', () => {
    expect(slot('1')).toBe('Slot 1');
    expect(slot('ChannelA-DIMM0')).toBe('ChannelA-DIMM0');
  });

  it('calcula uso do volume', () => {
    expect(usoPercentual(228000, 66120)).toBe(71);
  });
});
