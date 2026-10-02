# Investigação: inventário independente

## Resultado

**Viabilidade parcial nas APIs públicas examinadas.** Uma página de inventário independente,
extensível e ligada aos contêineres originais é viável. Não foi encontrado um gancho público
suportado que impeça o inventário nativo de aparecer ao pressionar TAB. Portanto o protótipo
abre por `/custominventory`; ele não satisfaz a exigência de substituição completa.

Essa conclusão está limitada ao código e ao protocolo disponíveis, não é uma afirmação sobre
possíveis APIs futuras ou implementações internas do cliente que não estão neste checkout.

Análise em 2026-10-02: shared source `159da504e6075dd963674c8eff334c87a7425588`
(`Sync 2026-10-01`), servidor instalado `0.7.0-pre.5`. A branch
`investigation/standalone-inventory` foi criada a partir de `6dbd937`, da branch
`feature/task-complexApproach`, após a correção dos olhos. Os arquivos anteriores do TrueBackpack
permanecem iguais a essa base; o novo projeto tem build, manifest e JAR próprios.

## Abertura e limite da substituição

Os caminhos abaixo são relativos a `D:/Projetos/Mods/hytale-shared-source`. Esse diretório
foi usado somente para leitura.

| API/classe | Evidência e consequência |
| --- | --- |
| `Protocol/Hytale.Protocol/Packets/Interface/InterfacePackets.cs:67,490` | `Page.Inventory` existe, mas `SetPage` é servidor → cliente. Não há evento público de abertura por TAB para cancelar antes da renderização. A pesquisa não encontrou chamada Java direta a `Page.Inventory`. |
| `HytaleServer/CoreServer/src/main/java/com/hypixel/hytale/server/core/entity/entities/player/windows/WindowManager.java:81` | `clientOpenWindow` comenta que o cliente já alterou a UI e reserva o ID zero para a janela solicitada pelo cliente. |
| `HytaleServer/builtin/Crafting/src/main/java/com/hypixel/hytale/builtin/crafting/window/FieldCraftingWindow.java:51` | A implementação de pocket crafting menciona explicitamente a abertura dos menus TAB sem latência. Isso sustenta a inferência de que o menu abre localmente, antes da resposta do servidor. |
| `HytaleServer/builtin/Crafting/src/main/java/com/hypixel/hytale/builtin/crafting/CraftingPlugin.java:138` | Registra `WindowType.PocketCrafting` como janela solicitável pelo cliente. |
| `HytaleServer/CoreServer/src/main/java/com/hypixel/hytale/server/core/io/handlers/game/GamePacketHandler.java:689` | Recebe `ClientOpenWindow` e devolve os dados da janela com `UpdateWindow`. Não cria previamente uma página customizada de inventário. |
| `HytaleServer/CoreServer/src/main/java/com/hypixel/hytale/server/core/io/adapter/PacketAdapters.java:47,54,99` | Filtros públicos podem suprimir pacotes. Interceptar pocket crafting seria uma redireção após a abertura local; não comprova ausência de um frame nativo e muda o fluxo de crafting. |
| `HytaleServer/CoreServer/src/main/java/com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/OpenPageInteraction.java:45` | O validador se limita às páginas abertas por essa interação; não intercepta o atalho nativo do cliente. |
| `Protocol/Hytale.Protocol/Packets/Interface/InterfacePackets.cs:73` | Os componentes de HUD não incluem um interruptor da página de inventário. Ocultar Hotbar não impede a abertura do inventário. |
| `README.md:22` | O cliente disponibilizado contém bibliotecas de interop em `HytaleClient/Lib`; não contém o código da UI nativa nem do roteamento de entrada. |

Não há assets da página de inventário nativo disponíveis para uma substituição isolada.
O diretório Noesis contém demos e HUDs, não um template de inventário substituível.
Os nomes `KeyBinding`, `KeyGesture` e `InputActionCommandBehavior` em `UITypes` não
demonstram precedência ou cancelamento do atalho nativo.

A API Noesis atual é experimental: `UIModule.java:26,34` exige
`hytale.serverside_ui_preview`; `PageManager.java:122` marca `dev_setPage` como inadequado
para produção. A [documentação oficial de PageManager](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/pages/PageManager)
confirma a disponibilidade de `openCustomPage` e a condição experimental de `dev_setPage`.
Não usamos essa API experimental como suposta solução para o atalho.

O gancho que falta seria, por exemplo, um evento cancelável **antes** da abertura do inventário
nativo ou um registro público do provedor da página/ação de inventário no cliente. Com isso,
o mod poderia registrar uma fábrica customizada exclusivamente para esse fluxo.

## Arquitetura viável

1. `CustomInventoryPlugin` independente, com comando explícito e `InteractiveCustomUIPage`.
2. `InventoryRegistry` recebe páginas e botões identificados por `namespace:id`.
3. Cada abertura cria conteúdo próprio para o jogador. A navegação monta o conteúdo em um
   seletor delimitado, e os eventos levam o ID da página e uma geração da montagem.
4. O conteúdo nativo projeta os contêineres reais em `ItemGridSlot[]`, sem criar outro inventário.
5. A seleção guarda um snapshot no servidor; o item continua em seu slot original até a transferência.
6. Mudanças dos contêineres solicitam atualização na thread do mundo. A dispensa remove listeners
   e a seleção; não há item real preso num cursor mantido pela página.
7. Registros retornam handles removíveis. A lista aparece ao reabrir ou usar Refresh; a página
   rejeita eventos de conteúdo removido ou de uma montagem antiga.

As extensões são código de plugins confiáveis: o escopo de seletores evita colisões acidentais,
não funciona como isolamento de segurança contra um plugin malicioso com acesso ao servidor.
O núcleo não importa TrueBackpack nem exige outros mods. O exemplo depende do núcleo,
mas o núcleo funciona sem o exemplo.

## Estado, movimentação e sincronização

| API/classe (prefixo `HytaleServer/CoreServer/src/main/java/com/hypixel/hytale/server/core/`) | Uso |
| --- | --- |
| `entity/entities/player/pages/PageManager.java:277,369` | Abertura, atualização, eventos, acknowledgment e dispensa da CustomUI. |
| `entity/entities/player/pages/InteractiveCustomUIPage.java:44,94` | Codec dos eventos do mod, usando o ciclo já existente. |
| `ui/builder/UICommandBuilder.java:26,127` e `UIEventBuilder.java:40` | Montagem de layouts, slots e bindings de navegação/conteúdo. |
| `inventory/InventoryComponent.java:52,105,119,195` | IDs dos contêineres ECS e listeners nativos que marcam alterações para salvar/sincronizar. |
| `modules/entity/player/PlayerSendInventorySystem.java:75,87` | Envia o inventário alterado e atualiza janelas normalmente. |
| `inventory/InventorySystems.java:41,59` | Emite `InventoryChangeEvent` para os sistemas do jogo e de outros mods. |
| `inventory/InventoryUtils.java:321,334,345,421` | Move itens através das regras do jogo, incluindo ability cores e interações do slot ativo. |
| `io/handlers/game/InventoryPacketHandler.java:416,428` | Fluxo original: thread do mundo, checagem de `PreventInventoryAccess`, depois `InventoryUtils.moveItem`. O protótipo segue essas condições. |
| `inventory/container/ItemContainer.java:511,528` | Transações originais com locks, filtros, empilhamento, troca e sobras. Não reimplementamos esses algoritmos. |
| `ui/ItemGridSlot.java:11` | Projeção para exibição. BSON arbitrário do servidor é removido somente da cópia enviada à UI. |

O protótipo permite apenas as seções nativas explicitamente exibidas; não aceita IDs de janelas
arbitrários. Valida slots, quantidade, bloqueio de acesso e snapshot de origem antes de delegar
a movimentação. Os contêineres, metadados, salvamento e eventos continuam pertencendo ao jogo.

O servidor atual inclui Storage, Hotbar, Armor, Utility, Tools, Backpack, Abilities e RuneBag.
A capacidade e a disponibilidade são obtidas do estado atual. Paridade completa de gestos,
tooltips, menus contextuais, seleção de habilidades e atalhos nativos não faz parte da prova mínima.

## UIs nativas preservadas

Benches usam `OpenBenchPageInteraction.java:145` / `OpenProcessingBenchInteraction.java:95`
com `Page.Bench` e janelas nativas. Contêineres seguem `OpenContainerInteraction.java:104`.
Memorium/Memories registra `WindowType.Memories` em `MemoriesPlugin.java:124` e também possui
páginas próprias. Pocket crafting mantém seu registro e sua solicitação original.

O protótipo não registra filtros de pacotes, não altera esses registros, não força páginas por tick,
não oculta HUDs e não instala um bloqueio global de inventário. A abertura explícita da CustomUI
usa a troca normal de página do jogo; após fechar, os fluxos nativos permanecem disponíveis.
Isso fornece evidência estrutural de compatibilidade. A afirmação de que todas essas telas
funcionam visualmente depende dos testes em um cliente conectado.

## Limites e validação

Compilação, testes de servidor e boot de plugins não validam a renderização de `ItemGrid` ou
`CharacterPreviewComponent` no cliente. A ausência do código de UI do cliente impede provar a
semântica completa de arrastar/dividir pilhas e a ausência de frames nativos usando apenas essa fonte.

O protótipo usa seleção por clique seguida de clique no destino para reduzir esse alcance.
As regras de movimentação ainda são as do jogo. Não suprimimos pocket crafting como tentativa
de cumprir um requisito que esse método não garante.

Há uma falha preexistente no `check` geral do TrueBackpack: a tarefa
`verifyBackpackCustomization` referencia a classe ausente
`com.supremosan.truebackpack.BackpackCustomizationVerification`. A correção dos olhos foi
validada separadamente com 192 combinações de políticas/slots/estados, quatro ciclos de defaults,
os 14 cenários existentes de inventário e geração do JAR. Os olhos atualizam preferências nativas
na sessão; o próximo sync de preferências do cliente pode substituí-las.

Os resultados concretos do build, boot e o roteiro de testes em jogo são registrados em
`VALIDATION.md`. Não se declara verificação visual concluída sem cliente conectado.
