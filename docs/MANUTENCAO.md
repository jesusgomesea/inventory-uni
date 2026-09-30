# Manutenção

Guia para quem vai mexer no código. O porquê das decisões está em [PROJETO.md](PROJETO.md).

## 1. Fluxo de uma tela

```
Angular (frontend/src/app)            Backend (backend/src/main/java/br/com/rdamasio/inventario)
 features/lista      ──GET /api/computadores──────► computador/BuscaServico ──► search/Computer
 features/ficha      ──GET /api/computadores/{id}─► computador/FichaServico ──► 6 consultas em paralelo
   edicao.ts         ──PUT /api/computadores/{id}─► computador/EdicaoServico ─► PUT /Computer/{id}
   detalhes.ts       ──GET …/{id}/detalhes────────► computador/DetalhesServico
   documentos.ts     ──/api/equipamentos/…────────► documento/DocumentoServico ─► banco + pasta
                                                     (e GET /Computer/{id} para checar permissão)
 core/interceptador-api.ts põe X-Inventario e X-Request-Id; 401 → tela de login
```

Toda chamada ao GLPI passa por `glpi/GlpiCliente.java`, com o `Session-Token` do técnico logado
(`sessao/SessaoAtual.java`).

## 2. Mapa

| Pasta | Papel |
|---|---|
| `glpi/` | Cliente HTTP do GLPI, sessão, colunas da busca (`OpcoesBusca`), catálogo de peças com cache (`Catalogo`) |
| `sessao/` | Login/logout, sessão GLPI na sessão HTTP, filtro anti-CSRF (`FiltroOrigem`) |
| `computador/` | Lista, ficha, edição, "ver mais", regra SSD × HDD |
| `documento/` | Documentos e termos do banco próprio (entidade, serviço, armazenamento em disco) |
| `opcoes/` | Listas para filtros e edição (status, locais, grupos, busca de usuários) |
| `simulador/` | GLPI de mentira, só no perfil `simulador` (dados em `resources/simulador/dados.json`) |
| `comum/` | Erros (`TratadorErros`), id da requisição, leitura tolerante de JSON (`Json`), paralelismo, cache |
| `resources/db/migration` | Schema do banco próprio (Flyway) |
| `frontend/src/styles.scss` | Todo o visual (Design System R Damásio). Componentes não têm CSS próprio |

## 3. Receitas

**Mostrar um campo novo do GLPI na ficha.** Acrescente em `Ficha.java` e preencha em `FichaServico.montar`
(o `base` tem os ids; o `exp`, os nomes). Espelhe em `frontend/src/app/core/modelos.ts` e mostre em
`ficha.page.ts`. Se o simulador precisar do campo, inclua em `dados.json`.

**Tornar um campo editável.** Acrescente em `EdicaoServico.Alteracao` (com o nome do campo no GLPI) e em
`edicao.ts` (`alteracao()` só manda o que mudou). Hardware nunca: o agente desfaz.

**Disco aparece como "não identificado".** Pegue o nome do modelo na ficha e acrescente um padrão em
`application.yml` → `inventario.discos.padroes-ssd` (ou `-hdd`). Reinicie o backend. SSD é testado antes de HDD.

**Nova categoria de documento.** Acrescente em `CategoriaDocumento.java`. Renomear uma existente exige
migration que atualize as linhas antigas (a coluna guarda o nome do enum).

**Documentos para monitores/impressoras.** Inclua o itemtype do GLPI em `DocumentoServico.TIPOS` e use o mesmo
componente `documentos.ts` na tela nova, trocando `'Computer'` pelo tipo.

**Mudar o schema.** Só por migration Flyway nova (`V2__...sql`), em SQL portável: os testes e o perfil local
usam H2 no modo PostgreSQL.

## 4. Armadilhas

- **A API do GLPI é irregular.** O mesmo campo vem como número, texto, `null` ou `"&nbsp;"`; listas vêm como
  array ou como objeto indexado. Leia sempre por `comum/Json` (`texto`, `numero`, `id`, `itens`).
- **Números das colunas da busca** (`forcedisplay[]=31`) podem mudar. Use `OpcoesBusca.Campo`, que confere
  tabela e coluna em `listSearchOptions`. Coluna com vários valores (IPs) chega como array ou com os
  separadores `$$##$$` / `$#$`: `BuscaServico.valores` trata os dois.
- **`+` na busca:** o PHP lê `+` como espaço. `Parametros` codifica com `URLEncoder` por isso; não troque por
  `UriComponentsBuilder.encode()`.
- **Memória e disco são dois níveis no GLPI:** a peça instalada (`Item_DeviceMemory`: tamanho e slot) e o modelo
  (`DeviceMemory`: tipo e frequência). O modelo vem do `Catalogo`, com cache de 1 h.
- **`inject()` depois de `await`** no Angular quebra (NG0203). Nos guardas, injete tudo antes do primeiro `await`
  (`core/sessao.ts`).
- **Download de arquivo enviado por usuário:** sempre por `comum/RespostaArquivo`. Só PDF e imagem abrem na tela;
  o resto baixa, com CSP sem script. Um HTML aberto no mesmo endereço rodaria com a sessão do técnico.
- **Linha vazia em `backend/config/application-local.yml`** sobrepõe o simulador (a pasta `config/` tem
  prioridade). Deixe comentado o que não tiver valor.
- **Nesta máquina:** a JVM precisa de `-Djdk.net.unixdomain.tmpdir=<pasta sem ~>` (o `.bat` já passa) e o
  `npm install` só funciona pelo espelho `--registry=https://registry.yarnpkg.com`.
- **O simulador não é o GLPI.** Ele imita o formato que esperamos do GLPI 10. Comportamento novo precisa ser
  conferido também contra o GLPI real.

## 5. Ainda não conferido contra o GLPI 10.0.7 real

Feito e testado só com o simulador. Ao ligar no GLPI real, confira:
- formato de `_networkports` (onde fica o `IPAddress`) e de `with_documents` (`assocID`, `headings`);
- se `with_softwares` + `expand_dropdowns` já traz os nomes (senão o `Catalogo` busca, até 400);
- se o `changeActiveEntities` com `"all"` é aceito (se não, fica só a entidade padrão; aparece no log);
- se editar cria o campo bloqueado como esperado (texto do aviso em `edicao.ts`);
- o campo do tipo de disco no modelo (`deviceharddrivetypes_id`), que pode não existir no 10.0.7.
