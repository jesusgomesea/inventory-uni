# Inventário GLPI

Ficha única de computador sobre o GLPI 10: nome, responsável, local, status, memória por slot, disco SSD/HDD,
sistema, modelo, serial e IP numa tela só, com edição dos campos administrativos e documentos e termos
guardados por equipamento. O resto (processador, softwares, garantia, chamados, histórico) fica em
"Ver mais informações".

- Projeto e decisões: [docs/PROJETO.md](docs/PROJETO.md)
- Antes de mexer no código: [docs/MANUTENCAO.md](docs/MANUTENCAO.md)

## Como subir

Precisa de JDK 21 e Node.js no PATH (os mesmos do HELP-AGENT).

**Só para ver a tela, sem GLPI** (simulador com dados fictícios, login `tecnico` / `tecnico`):

```bash
iniciar-inventario.bat simulador
```

**Com o GLPI de verdade:**

1. No GLPI: **Configurar → Geral → API**. Confira "Habilitar API REST" e "Habilitar login com credenciais"
   (ou "com token externo", se preferirem o token pessoal). Adicione um **cliente de API** para esta aplicação,
   com a faixa de IP desta máquina, e copie o **App-Token**.
2. Abra `backend/config/application-local.yml`, tire o `#` das linhas, preencha o endereço
   (terminando em `/apirest.php`) e o App-Token. Essa pasta está fora do git: o token não vai para o repositório.
3. Rode:

```bash
iniciar-inventario.bat
```

A tela fica em `http://localhost:4300` e, na rede, em `http://<ip-desta-máquina>:4300`. Para parar:

```bash
parar-inventario.bat
```

As portas (4300 e 8091) são diferentes das do HELP-AGENT (80 e 8080), que roda nesta mesma máquina.

## Como usar

- **Entrar:** o mesmo usuário e senha do GLPI. Quem entra por LDAP/SSO e não consegue pela API usa o token
  pessoal (GLPI → seu nome → Preferências → Chaves de acesso remoto).
- **Lista:** busque por nome, serial, patrimônio, usuário, último login ou IP. Atalho `/` vai para a busca;
  Enter com um único resultado abre a ficha. Filtros de status e local (o local inclui os sublocais).
- **Ficha:** **Editar** altera nome, status, responsável, local, técnico, grupo, patrimônio e descrição direto no
  GLPI. Hardware, sistema e IP vêm do GLPI Agent e são só leitura.
- **Documentos e termos:** arraste o arquivo, escolha o tipo (termo de responsabilidade, nota fiscal, laudo…) e
  anexe. O termo registra o responsável da época. Remover pede motivo e o arquivo continua guardado
  ("Mostrar removidos"). Os documentos que já estavam no GLPI aparecem embaixo, só para abrir.

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
