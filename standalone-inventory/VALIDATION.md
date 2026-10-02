# Validação

## Ambiente

- JDK Temurin 25.0.3.
- Hytale Server instalado: `0.7.0-pre.5`.
- Shared source consultado: `159da504e6075dd963674c8eff334c87a7425588`.
- Base da investigação: TrueBackpack `6dbd937`.

## Resultados automatizados

`./gradlew.bat build --offline`: **aprovado** para o núcleo e o companion.
`verifyInventory`: **19 cenários aprovados**, cobrindo seleção sem retirada de itens,
snapshot/metadados, origem alterada, contêiner substituído, limites/quantidades inválidos,
bloqueio de acesso, projeção/codec de slots, IDs duplicados, ordem, handles antigos,
fábricas por sessão e envelope/codec/geração dos eventos.

Esses testes verificam validação antes da movimentação. Não executam a transferência
`InventoryUtils.moveItem` num jogador conectado e não simulam as telas nativas.

Boot completo em diretórios isolados, servidor `0.7.0-pre.5`, autenticação offline,
bind restrito a loopback e `--boot-command stop`: **aprovado**, exit code 0 nos dois casos.

| Execução | Evidência |
| --- | --- |
| Núcleo + companion, sem TrueBackpack | Ambos habilitados; núcleo registra 2 páginas e 1 botão de extensão. Servidor chegou a `Hytale Server Booted!` e encerrou normalmente. Log: `run-smoke/standalone/server-output.log`, linhas 574–595. |
| Núcleo + companion + TrueBackpack corrigido | Os três habilitados; `DefaultQA:TrueBackpack` registra seus sistemas. Servidor chegou ao boot e encerrou normalmente. Log: `run-smoke/compatibility/server-output.log`, linhas 634–658. |
| Módulos nativos | `Hytale:Crafting` e `Hytale:Memories` habilitados em ambos os boots. Não houve falha de carregamento dos plugins do protótipo. |
| Empacotamento | JAR principal contém apenas classes do CustomInventory e seus assets/manifest; companion contém apenas o exemplo. Nenhuma classe do servidor ou do TrueBackpack foi empacotada. |
| Isolamento de fonte/branch | `hytale-shared-source` continua limpo. A investigação adiciona somente `standalone-inventory/`; não muda os arquivos herdados do TrueBackpack. |

Artefatos: `build/libs/CustomInventory-0.1.0.jar` e
`example-extension/build/libs/CustomInventoryExample-0.1.0.jar`.
Hashes SHA-256: núcleo `2784a683ec5c4ec3a5f3ebc582e1aad304fd6710c8235a80a0100aedfd89d1af`,
companion `8b0c19506039364e2a5aa0fc814b0daff67abf36795cb2a1a4b819f53ea8dd27`.

As tentativas iniciais com `--bare` encontraram duas falhas do servidor: criação de permissões
fecha o writer antes do flush e `ServerManager` exige um listener mesmo nesse modo.
Os plugins haviam carregado, mas não se declarou esses boots aprovados. Os logs foram preservados
em `server-output-first-attempt.log` e `server-output-bare-listener-failure.log` no diretório
standalone. O teste final usa boot normal e permissões vazias próprias do teste. Nenhum código
do jogo foi alterado. Os logs finais também contêm avisos de assets nativos e flags antigas
do TrueBackpack; não houve correção desses assets fora do escopo.

No TrueBackpack: compilação e JAR passaram; `verifyBackpackArmorVisibility` passou em
192 combinações e quatro ciclos de defaults; `verifyBackpackWorkbenchInventory` passou
em 14 cenários. `check` geral falha numa tarefa preexistente que referencia a classe ausente
`BackpackCustomizationVerification`.

## Verificações em cliente ainda pendentes

Nenhum cliente conectado foi controlado nesta execução. Os itens abaixo são o roteiro de
aceitação; não são resultados alegados como aprovados.

| Caso | Resultado esperado |
| --- | --- |
| `/custominventory` sem TrueBackpack/exemplo | Página abre; os cinco grupos refletem inventário/capacidade reais. |
| Selecionar e mover uma pilha | Quantidade total, durabilidade e metadados preservados; inventário nativo mostra o mesmo estado. |
| Equipar, desequipar e trocar armadura/utilitário | Filtros originais e atualizações de equipamento aplicados. |
| Mover item do hotbar ativo | Interações `SwapFrom` mantidas pelo fluxo nativo. |
| Item bloqueado ou seleção alterada | Movimento rejeitado sem retirar nem duplicar o item. |
| Fechar com seleção ativa, desconectar/reconectar | Item permanece no contêiner; nenhum cursor privado para recuperar. |
| Inventário modificado por outro sistema | Refresh automático mostra estado novo; seleção anterior torna-se inválida. |
| Instalar o exemplo | Página e botão aparecem; botão atua apenas sobre o jogador atual. |
| Remover contribuição e clicar Refresh | Navegação atualiza; callback removida não executa. |
| ESC/Close → abrir inventário nativo TAB | Fluxo original de TAB/pocket crafting continua disponível. |
| Bench de crafting | Abrir, fabricar, mudar categoria e fechar normalmente. |
| Processing bench | Abrir, inserir/retirar materiais, iniciar processamento e fechar. |
| Contêiner/baú | Abrir, transferir itens e fechar normalmente. |
| Memorium/Memories | Abrir UI, consultar/depositar memórias e fechar normalmente. |
| Com TrueBackpack instalado | Mochila, banco, Aparência, Visibilidade e Craft/Upgrade mantêm comportamento. |
| Olhos do TrueBackpack | Cada olho alterna preview e modelo no mundo sem mudar defesa; respeita ALL/HELMET_ONLY/NONE. |

Um boot bem-sucedido confirma carregamento/registro dos plugins, não os resultados visuais
ou esses fluxos de interação. A garantia estrita de que o inventário nativo nunca aparece
não é oferecida por este protótipo.
