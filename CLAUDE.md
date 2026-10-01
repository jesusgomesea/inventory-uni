# Inventário GLPI — CLAUDE.md

Ficha única de computador sobre o GLPI 10.0.7 (API legada `apirest.php`), com edição dos campos administrativos
e documentos/termos por equipamento num banco próprio. Mesma pilha e visual do HELP-AGENT.

- Visão geral e como subir: [README.md](README.md)
- Projeto e decisões: [docs/PROJETO.md](docs/PROJETO.md)
- **Antes de mudar código:** [docs/MANUTENCAO.md](docs/MANUTENCAO.md) — fluxo, mapa, receitas, armadilhas

## Regras de trabalho neste repositório

- O GLPI é a fonte da verdade dos ativos. O banco próprio guarda só documentos e termos (e o que vier a ser
  índice de leitura). Não criar cópia editável do cadastro.
- Toda chamada ao GLPI passa por `glpi/GlpiCliente` com a sessão de `SessaoAtual`; permissão é a do GLPI.
  **Login em espera** (30/09/2026): `inventario.login.habilitado: false`, todos usam a conta de serviço. Não apagar
  o código do login (pacote `sessao/`, `features/entrar`): é para religar.
- Hardware, sistema e IP são só leitura (o GLPI Agent desfaz edições).
- Manter o código documentado: todo arquivo novo começa com comentário dizendo seu papel; comentários explicam o
  porquê. Mudou comportamento ou armadilha → atualizar `docs/MANUTENCAO.md` (e o README, se for de uso).
- Português em nomes, mensagens e comentários.
- Segredo nunca em arquivo versionado (`backend/config/` está no `.gitignore`).
- Schema só por migration Flyway nova, em SQL portável (H2 no perfil local e nos testes).
- Visual só por tokens em `frontend/src/styles.scss` (Design System R Damásio); componentes sem CSS próprio.
- O HELP-AGENT de produção roda nesta máquina nas portas 80/8080: não mexer nelas. Este sistema usa 4300/8091.

## Verificação

```bash
cd backend && ./mvnw -q test
```

```bash
cd frontend && npx ng build
```

Nesta máquina: JVM precisa de `-Djdk.net.unixdomain.tmpdir=<pasta sem ~>` (o `.bat` já passa); `npm install` só
pelo espelho `--registry=https://registry.yarnpkg.com`. Para testar sem GLPI: `iniciar-inventario.bat simulador`
(login `tecnico` / `tecnico`).
