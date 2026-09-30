// Preenchimento automático dos modelos de termo (pasta "Model Termos Eqp"). Injetado pelo TermoServico logo antes
// do </body>, com os dados da ficha em window.__termo. Os modelos não têm id nos campos, então achamos cada
// campo pelo texto do rótulo (<label>) dentro do bloco .campo, e só dentro da seção indicada. Se a TI mudar o
// texto de um rótulo no modelo, o campo simplesmente deixa de ser preenchido (o termo continua funcionando).
(function () {
  var d = window.__termo || {};

  function norm(s) {
    return (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
  }

  // campo cujo rótulo começa com `rotulo`, dentro de `escopo` (elemento) ou do documento
  function campo(rotulo, escopo) {
    var blocos = (escopo || document).querySelectorAll('.campo');
    for (var i = 0; i < blocos.length; i++) {
      var l = blocos[i].querySelector('label');
      if (l && norm(l.textContent).indexOf(norm(rotulo)) === 0) {
        return blocos[i].querySelector('input[type=text], input[type=date], input:not([type]), select, textarea');
      }
    }
    return null;
  }

  function por(el, valor) {
    if (!el || valor == null || valor === '') return;
    if (el.tagName === 'SELECT') {
      for (var i = 0; i < el.options.length; i++) {
        if (norm(el.options[i].text) === norm(valor)) { el.selectedIndex = i; break; }
      }
    } else {
      el.value = valor;
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // seção cujo título (h2) contém o texto; null = documento inteiro
  function secao(titulo) {
    var hs = document.querySelectorAll('section h2');
    for (var i = 0; i < hs.length; i++) {
      if (norm(hs[i].textContent).indexOf(norm(titulo)) >= 0) return hs[i].parentElement;
    }
    return null;
  }

  function preencher() {
    (d.campos || []).forEach(function (c) {
      var escopo = c.secao ? secao(c.secao) : null;
      if (c.secao && !escopo) return; // seção não existe neste modelo
      por(campo(c.rotulo, escopo), c.valor);
    });
    if (d.data) { var dt = document.getElementById('campoData'); if (dt) dt.value = d.data; }
    // cabeçalho: "Chamado GLPI" e "No do Termo" não têm .campo, ficam em .doc-code
    var codigos = document.querySelectorAll('.doc-code input');
    if (d.chamado && codigos[1]) codigos[1].value = d.chamado;
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', preencher);
  else preencher();
})();
