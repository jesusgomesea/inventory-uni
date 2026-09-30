# Inventário GLPI: ficha única de computador

Documento de projeto: o problema, as decisões e o porquê. Para mexer no código, veja
[MANUTENCAO.md](MANUTENCAO.md). Para subir e usar, o [README](../README.md).

## 1. Problema e objetivo

No GLPI, a ficha de um computador fica espalhada em abas: Componentes (memória, disco), Sistemas
operacionais, Portas de rede → Nomes de rede → IP, Volumes, Documentos… Para responder "quanto de RAM
tem a máquina do caixa 2 e ela é SSD?" o técnico abre quatro abas.

**Objetivo:** uma tela que mostra o essencial de uma vez, permite editar os campos administrativos e
guardar documentos e termos do equipamento.

**O GLPI continua sendo a fonte da verdade dos ativos.** A aplicação lê e grava o cadastro no GLPI pela API.
O banco próprio guarda só o que o GLPI não tem: os documentos e termos anexados por aqui (§5).

## 2. O que aparece na ficha

| Bloco | Campos | Origem no GLPI (API legada `apirest.php`) |
|---|---|---|
| Cabeçalho | Nome, status, descrição | `Computer.name`, `states_id`, `comment` |
| Fatos | Responsável | `users_id` (Usuário) |
| Fatos | Último login | `contact` (informado pelo agente). Mostra na hora quando o responsável cadastrado não é quem usa |
| Fatos | Localização | `locations_id` (nome completo, ex.: "Loja 14 > Balcão") |
| Fatos | Modelo, serial, patrimônio | `manufacturers_id` + `computermodels_id`, `serial`, `otherserial` |
| Memória | Total e por slot | `Item_DeviceMemory`: `size` (MB) e `busID` (slot). `DeviceMemory`: tipo (DDR4) e frequência |
| Armazenamento | Capacidade, SSD/HDD, interface, uso dos volumes | `Item_DeviceHardDrive`: `capacity`. `DeviceHardDrive`: interface e modelo. `Item_Disk`: volumes |
| Sistema | Nome, versão, build, arquitetura | `Item_OperatingSystem` |
| Rede | IP principal, interfaces e MAC | `_networkports` → `NetworkName` → `IPAddress` |
| Documentos | Os do banco próprio e os já vinculados no GLPI | §5 |

**Ver mais informações** carrega sob demanda: processador, placa de vídeo, placa-mãe, BIOS, placas de rede,
softwares, antivírus, monitores e periféricos conectados, compra e garantia (Infocom), chamados e histórico.
Cada seção falha sozinha (ex.: perfil sem direito a ver chamados) sem derrubar as outras.

### Regra SSD × HDD

O GLPI Agent nem sempre informa o tipo do disco (no Windows, raramente). A regra fica no servidor
(`computador/RegraDisco.java`), do mais confiável ao menos confiável:
1. tipo informado no GLPI, se houver;
2. interface NVMe → SSD; rotação (rpm) informada → HDD;
3. nome do modelo bate com um padrão conhecido de SSD (ex.: `SA400` = Kingston A400), depois de HDD
   (ex.: `ST1000DM010` = Seagate). A tela marca **"tipo inferido"**;
4. nada bateu → **"não identificado"**. Não chutamos HDD para não enganar quem planeja upgrade.

Os padrões ficam em `application.yml` (`inventario.discos`), para incluir modelos novos sem mexer no código.

## 3. Arquitetura

```
Navegador (Angular)  ──►  Backend (Spring Boot, BFF)  ──►  GLPI 10 apirest.php   (ativos)
   só exibe                 sessão, App-Token, regras,  ──►  Banco próprio + pasta (documentos e termos)
                            montagem da ficha
```

Por que um backend intermediário e não o navegador falando direto com o GLPI:
- o **App-Token** do GLPI não pode ir para o navegador;
- a ficha precisa de 6 consultas e de regras. O backend faz as consultas em paralelo e devolve a ficha pronta;
- evita CORS no GLPI e permite cache das listas (locais, status, grupos).

Mesma pilha do HELP-AGENT (Java 21, Spring Boot 4.1, Angular 22, Design System R Damásio). A equipe mantém uma
tecnologia só, e a ferramenta pode virar módulo da mesma aplicação no futuro.

### Autenticação: cada técnico com a própria sessão do GLPI

O login é o do GLPI (usuário e senha, ou o token pessoal). O backend chama `initSession`, guarda o
`Session-Token` só no servidor (sessão HTTP, cookie `INVENTARIO_SESSAO`) e ativa todas as entidades do perfil.

Assim:
- o que cada técnico pode ver e editar segue o **perfil e as entidades dele no GLPI**; não há um segundo
  cadastro de permissões. Os documentos do banco próprio seguem a mesma regra: quem não vê a máquina no GLPI
  não vê os documentos dela;
- o histórico do GLPI registra **quem** mudou o quê.

## 4. Chamadas ao GLPI

| Ação | Chamada |
|---|---|
| Lista e busca | `GET /search/Computer` com `criteria` e `forcedisplay` (uma chamada por página) |
| Ficha | `GET /Computer/{id}` cru e com `expand_dropdowns`, `with_networkports`, `with_documents`; subitens `Item_DeviceMemory`, `Item_DeviceHardDrive`, `Item_OperatingSystem`, `Item_Disk` |
| Modelos de peça | `GET /DeviceMemory/{id}`, `/DeviceHardDrive/{id}`… (cache de 1 h) |
| Ver mais | subitens `Item_Device*`, `ComputerAntivirus`, `Computer_Item`; `with_softwares`, `with_infocoms`, `with_tickets`, `with_logs` |
| Editar | `PUT /Computer/{id}` com `{"input": {...}}` |
| Documento do GLPI | `GET /Document/{id}` (dados) e com `Accept: application/octet-stream` (arquivo). Só leitura |
| Listas | `GET /State`, `/Location`, `/Group` (cache de 10 min por técnico); `search/User` enquanto digita |

**Armadilha:** os números das colunas da busca podem mudar com plugins e versões. O backend lê
`listSearchOptions` e confere a tabela e a coluna de cada número antes de usar (`glpi/OpcoesBusca.java`).

## 5. Documentos e termos (banco próprio)

O pedido: guardar documentos e termos por equipamento fora do GLPI. Como já estamos lendo os dados do
equipamento, o banco próprio guarda o documento ligado ao **tipo e id do GLPI** (`Computer` 101).

| Decisão | Por quê |
|---|---|
| Arquivo no disco, dados no banco (tabela `documento_equipamento`) | Banco pequeno e rápido; o arquivo fica numa pasta que entra no backup |
| SHA-256 de cada arquivo | Prova que o arquivo guardado é o que foi enviado (importante num termo) |
| Grava o **responsável da época** | Num termo de responsabilidade é quem assinou; não muda quando a máquina troca de dono |
| Remoção lógica, com motivo obrigatório | Termo é registro. Some da lista, mas continua guardado e aparece em "Mostrar removidos" |
| Permissão = permissão da máquina no GLPI | Antes de listar, anexar, baixar ou remover, o backend lê a máquina com a sessão do técnico |
| Só extensões de documento (PDF, imagem, Office, ZIP…) até 50 MB | Nada executável; arquivo servido com CSP e `nosniff` para não rodar script |
| Categorias fixas: termo de responsabilidade, termo de devolução, nota fiscal, garantia, laudo, contrato, foto, outro | Permitem filtrar e, no futuro, cobrar "máquinas sem termo" |
| Os documentos que já estão no GLPI aparecem na ficha (só leitura) | Nada se perde; não duplicamos no GLPI o que é guardado aqui |

O tipo do equipamento é genérico (`/api/equipamentos/{tipo}/{id}/documentos`). Hoje só `Computer` é aceito;
para monitores e impressoras basta liberar o tipo (`DocumentoServico.TIPOS`) e criar a tela.

## 6. Edição: o que pode e o que não pode

| Editável na ficha | Somente leitura |
|---|---|
| Nome, status, descrição, responsável, técnico, grupo técnico, localização, patrimônio | Memória, discos, sistema, IP, serial, modelo |

O hardware vem do **GLPI Agent**: editado à mão, o próximo inventário desfaria. Nos campos editáveis, em
máquinas do inventário, o GLPI 10 cria um "campo bloqueado" para o agente não sobrescrever. A tela avisa.

Na gravação, o backend manda só o que mudou e confere o `date_mod`: se alguém alterou a máquina no GLPI
enquanto o técnico editava, recusa (409) e a tela oferece recarregar, em vez de sobrescrever calado.

## 7. Fases

| Fase | Entrega | Situação |
|---|---|---|
| 1 | Login com o GLPI, ficha, regra SSD/HDD | Feito |
| 2 | Lista com busca e filtros de status e local | Feito |
| 3 | Edição dos campos administrativos com controle de conflito | Feito |
| 4 | Documentos e termos no banco próprio; documentos do GLPI só leitura | Feito |
| 5 | Validar contra o GLPI 10.0.7 real (formatos da API, campos bloqueados) | Falta: precisa do App-Token |
| 6 (opcional) | Índice local atualizado a cada N minutos: filtros "HDD", "RAM < 8 GB", "sem termo", relatórios de upgrade | Não iniciado |
| 7 | Termo de responsabilidade em PDF: **integrar o gerador que já existe** (repositório próprio, em preparação), não criar outro | Aguardando o repositório |

Filtros por RAM e tipo de disco não estão na lista porque a busca do GLPI não soma memória nem sabe o tipo do
disco; isso pede o índice da fase 6.

## 8. Ambiente confirmado (30/09/2026)

| Pergunta | Resposta | Consequência no projeto |
|---|---|---|
| Versão do GLPI | 10.0.7 | Só a API legada `apirest.php` (a API v2 é do GLPI 11). A troca futura fica isolada em `glpi/GlpiCliente.java` |
| Inventário | GLPI Agent (inventário nativo) | Hardware só leitura; editar cria campo bloqueado no GLPI, e a tela avisa |
| API REST | Habilitada | Falta cadastrar um cliente de API para esta aplicação e gerar o App-Token |
| Responsável | Campo **Usuário** (`users_id`) | Mostrado como "Responsável"; `contact` aparece como "Último login" |
