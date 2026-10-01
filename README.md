# Inventário GLPI

Ficha única de computador sobre o GLPI 10: nome, responsável, local, status, memória por slot, disco SSD/HDD,
sistema, modelo, serial e IP numa tela só, com edição dos campos administrativos e documentos e termos
guardados por equipamento. O resto (processador, softwares, garantia, chamados, histórico) fica em
"Ver mais informações".

- Projeto e decisões: [docs/PROJETO.md](docs/PROJETO.md)
- Antes de mexer no código: [docs/MANUTENCAO.md](docs/MANUTENCAO.md)

## Como subir

Precisa de JDK 21 e Node.js no PATH (os mesmos do HELP-AGENT).

> **Login em espera (30/09/2026):** a tela abre direto, sem login. O backend usa uma **conta de serviço** do GLPI
> para todos. Para religar o login de cada técnico: `inventario.login.habilitado: true` (ver
> [docs/PROJETO.md](docs/PROJETO.md) §3).

**Só para ver a tela, sem GLPI** (simulador com dados fictícios):

```bash
iniciar-inventario.bat simulador
```

**Com o GLPI de verdade:**

1. No GLPI: **Configurar → Geral → API**. Confira "Habilitar API REST" e "Habilitar login com token externo".
   Adicione um **cliente de API** para esta aplicação, com a faixa de IP desta máquina, e copie o **App-Token**.
2. Crie (ou escolha) um **usuário de serviço** no GLPI, com um perfil que veja e altere computadores nas
   entidades das lojas. Nele: Preferências → Chaves de acesso remoto → gere o **Token de API**.
3. Abra `backend/config/application-local.yml`, tire o `#` das linhas e preencha o endereço (terminando em
   `/apirest.php`), o App-Token e o token da conta de serviço. Essa pasta está fora do git: os tokens não vão
   para o repositório.
4. Rode:

```bash
iniciar-inventario.bat
```

A tela fica em `http://localhost:4300` e, na rede, em `http://<ip-desta-máquina>:4300`. Para parar:

```bash
parar-inventario.bat
```

As portas (4300 e 8091) são diferentes das do HELP-AGENT (80 e 8080), que roda nesta mesma máquina.

## Como usar

- **Entrar:** com o login em espera, não há tela de login. Quando for religado: o mesmo usuário e senha do GLPI,
  ou o token pessoal (GLPI → seu nome → Preferências → Chaves de acesso remoto).
- **Lista:** busque por nome, serial, patrimônio, usuário, último login ou IP. Atalho `/` vai para a busca;
  Enter com um único resultado abre a ficha. Filtros de status e local (o local inclui os sublocais).
- **Ficha:** **Editar** altera nome, status, responsável, local, técnico, grupo, patrimônio e descrição direto no
  GLPI. Hardware, sistema e IP vêm do GLPI Agent e são só leitura.
- **Documentos e termos:** arraste o arquivo, escolha o tipo (termo de responsabilidade, nota fiscal, laudo…) e
  anexe. O termo registra o responsável da época. Remover pede motivo e o arquivo continua guardado
  ("Mostrar removidos"). Os documentos que já estavam no GLPI aparecem embaixo, só para abrir.
- **Gerar termo:** em "Documentos e termos", os botões **Movimentação** e **Substituição** abrem o modelo da TI
  (pasta `Model Termos Eqp`) já preenchido com a máquina. Complete cargo, setor e chamado, imprima pelo botão do
  próprio modelo, colha a assinatura e anexe o termo assinado na ficha.

## Onde ficam os dados

| O quê | Onde |
|---|---|
| Cadastro dos computadores | No GLPI (esta aplicação não guarda cópia) |
| Documentos e termos anexados aqui | Banco H2 em `backend/dados/inventario-local*` e arquivos em `backend/dados/documentos/` |
| Log | `backend/dados/logs/inventario.log` (gira em 10 MB, guarda 14 dias) |

**Backup:** copie a pasta `backend/dados/` inteira, com a aplicação parada. Banco e arquivos andam juntos:
um sem o outro não serve. O simulador usa `backend/dados/simulador/`, separado dos dados reais.

Em servidor com PostgreSQL: rode sem o perfil `local` e defina `DB_URL`, `DB_USER`, `DB_PASSWORD`,
`GLPI_URL`, `GLPI_APP_TOKEN` e `DOCUMENTOS_PASTA`.

## Verificação

```bash
cd backend && ./mvnw -q test
```

```bash
cd frontend && npx ng build
```
