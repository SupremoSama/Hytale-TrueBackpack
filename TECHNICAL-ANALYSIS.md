# Análise técnica do CustomInventory

## Resultado e limite

É possível criar uma CustomUI independente, extensível e ligada aos inventários reais,
e redirecionar pelo servidor a solicitação de pocket crafting associada ao TAB pela fonte.
O núcleo implementa essa interceptação por `PacketAdapters` e porta os layouts e
texturas nativos do cliente instalado, sem depender do TrueBackpack ou de suas regras
de mochilas.

A captura disponível continha quatro ciclos `204 → 201 → 202` entre 5.411 pacotes,
mas somente metadados. Ela confirma o ciclo de janelas; não identifica o tipo solicitado
nem a tecla usada. A associação com `PocketCrafting` vem do código de `FieldCraftingWindow`.

Isso ainda não prova substituição completa no cliente. O inventário pode começar a
abrir localmente antes do pacote chegar ao servidor. O comportamento visual, o segundo
TAB para fechar e a ausência de um frame nativo continuam pendentes. A conclusão está
limitada às APIs/protocolo examinados; `hytale-shared-source` é somente referência de leitura.

## Fluxo implementado

| Entrada | Comportamento do mod |
| --- | --- |
| `ClientOpenWindow` (204), `PocketCrafting`, Adventure | Cancela a solicitação e agenda abertura; com a CustomUI já aberta, agenda seu fechamento. Pedidos repetidos antes da abertura são agrupados. |
| `PocketCrafting` em Creative ou com modo ainda desconhecido | Segue o handler nativo, sem cancelamento. |
| `SetGameMode` de saída | Atualiza o snapshot de modo; invalida redireções pendentes e dispensa somente o inventário próprio ao sair de Adventure. |
| `ClientOpenWindow` de outros tipos | Segue o handler nativo. |
| `CloseWindow` (202), ID 0, conexão previamente redirecionada | Agenda o fechamento de uma janela nativa 0 existente, ou absorve o fechamento órfão da solicitação cancelada. Esse pacote não fecha a CustomUI. |
| `CloseWindow` com ID positivo ou conexão não redirecionada | Segue o handler nativo. |
| `CustomPageEvent` (219), `Dismiss` | O `PageManager` nativo dispensa a CustomUI e chama seu cleanup. |
| `DropItemStack` nativo | Segue o handler do jogo sem interceptação; a CustomUI não produz esse pacote ao pressionar G. |

O ID 0 é reservado pelo jogo à janela solicitada pelo cliente. Depois de cancelar a
abertura de pocket crafting, o cliente pode enviar seu fechamento sem existir uma
janela no servidor; por isso o tratamento é delimitado às conexões redirecionadas.
Não se usa todo `CloseWindow(0)` como sinal de fechar o inventário customizado.

O mod verifica o jogador/contexto na thread do mundo e usa o ciclo de páginas existente.
O comando `/custominventory` oferece abertura explícita para desenvolvimento.
Requisições repetidas pendentes são agrupadas; uma nova abertura com a página já ativa
a fecha pelo `PageManager`. O observador de `SetGameMode` mantém um snapshot por conexão
antes do cancelamento; o contexto ECS é novamente validado na thread do mundo. `PlayerReadyEvent`
e jogadores já conectados no startup semeiam esse snapshot. Até o modo ser conhecido,
o fluxo permanece nativo. Não se pressupõe que o cliente envie 204 ao pressionar TAB
com uma página customizada ativa; esse percurso precisa ser confirmado em cliente.

Um observador de saída invalida tarefas pendentes quando o servidor envia `SetPage`,
abre uma janela com ID positivo ou abre inicialmente outra CustomUI. Assim, um pedido
antigo de inventário não cobre uma bench ou página recém-aberta. Nenhum pacote de saída
é cancelado. O descarte de uma tarefa por referência/mundo alterado libera seu token,
permitindo que uma abertura posterior seja agendada normalmente.

## APIs relevantes

Os caminhos seguintes são relativos à fonte oficial consultada:

| API/classe | Papel |
| --- | --- |
| `Protocol/Hytale.Protocol/Packets/Window/WindowPackets.cs` | IDs e tipos de `ClientOpenWindow`, `CloseWindow` e ações de janela. |
| `Protocol/Hytale.Protocol/Packets/Interface/InterfacePackets.cs` | `CustomPage`, `CustomPageEvent` e páginas nativas. |
| `CoreServer/.../io/adapter/PacketAdapters.java` | Filtro de entrada com cancelamento antes do handler. |
| `CoreServer/.../io/handlers/game/GamePacketHandler.java` | Handler original das janelas solicitadas pelo cliente. |
| `CoreServer/.../entity/entities/player/windows/WindowManager.java` | Reserva do ID 0, criação/fechamento e validação das janelas. |
| `builtin/Crafting/.../FieldCraftingWindow.java` | Pocket crafting menciona abertura local de TAB sem latência. Isso sustenta a limitação de renderização anterior à resposta do servidor. |
| `CoreServer/.../entity/entities/player/pages/PageManager.java` | Abertura, acknowledgment, eventos, troca e dispensa da CustomUI. |
| `CoreServer/.../entity/entities/player/pages/InteractiveCustomUIPage.java` | Codec e ciclo da página interativa. |
| `CoreServer/.../inventory/InventoryUtils.java` | Movimentação original, inclusive filtros, consistência de habilidades e hotbar ativo. |
| `CoreServer/.../io/handlers/game/InventoryPacketHandler.java` | Descarte nativo de `DropItemStack`, com execução agendada na thread do mundo. |
| `CoreServer/.../modules/entity/player/PlayerSendInventorySystem.java` | Sincronização dos inventários alterados e das janelas. |

A fonte compartilhada não contém o roteamento de entrada completo do cliente. Os
layouts nativos foram consultados na instalação do launcher, em
`Client/Data/Game/Interface/InGame/Pages/Inventory`, e os estilos comuns em
`InGame/Common.ui` e `Common/Container.ui`. Nenhum arquivo da fonte ou da instalação
do jogo precisa ser modificado ou compilado para este projeto.

## Layout nativo

`InventoryPage.ui`, `CharacterPanel.ui`, `StoragePanel.ui`, `BasicCraftingPanel.ui`,
`ContainerPanel.ui` e `Memories/MemoriesPanel.ui` são as referências da composição.
Os recursos copiados mantêm os bytes das texturas originais; os estilos do contêiner
comum também existem no pacote de CustomUI do servidor.

O shell conserva a raiz de 1611 pixels, painel do jogador de 410 × 720, coluna central
de 715 pixels e intervalo horizontal de 38 pixels. A coluna usa alinhamento inferior;
o inventário ocupa 452 pixels, o crafting e as memórias 423. A altura da mochila é
`70 + ceil(min(max(capacidade, 1), 36) / 9) * 76`: cresce até quatro linhas e 374 pixels.
Acima de 36 slots, o `ItemGrid` mantém a capacidade real e rola dentro desse painel.
O padding lateral reserva espaço para a barra nativa, sem alterar as nove colunas.
As abas de 70 × 70 ficam em Top 4, 71 e 138 no host do personagem. As extensões
acrescentam abas depois dessas três posições.

Os slots usam as texturas, margens, atalhos, silhuetas de armadura e indicador de hotbar
nativos. Storage e hotbar compartilham uma coluna centralizada de 684 pixels; a altura
do storage é medida pelo grid, como no markup nativo, sem um anchor de altura fixo.
As tooltips dos controles usam a textura nativa, fonte de 16 pixels e padding de 24 pixels.
Utility expõe somente o slot utilizável; as outras duas posições são as
molduras decorativas da tela original. A igualdade por pixel depende de uma renderização
em cliente na mesma resolução/escala, não apenas de medidas ou hashes de textura.

A barra de navegação e as dicas laterais reutilizam os layouts, traduções e texturas
do cliente. Os `HotkeyLabel` ficaram vazios na página customizada; foram substituídos
por labels explícitas dos padrões TAB/M/B/ESC, SHIFT e glyphs de mouse nativos.
Essas labels não capturam teclas nem representam remapeamentos que o servidor não recebe.
O binding público `KeyDown` é exclusivo de `TextField`; não há binding global ou de
`ItemGrid` para G. A dica de G foi substituída pelo botão Largar, que usa a pilha
selecionada pelo hover e o descarte nativo. Os demais gestos continuam apresentados
pelos modificadores e ícones de mouse da referência.
As tooltips de ordenar e ações da mochila usam as traduções nativas de atalho não
atribuído, para evitar o parâmetro `{keybind}` sem valor.
Inventário/Voltar fecham a página; Mapa abre `Page.Map` se habilitado no mundo.
Ferramentas criativas ficam desabilitadas no inventário Adventure. O marcador de quest
aparece na aba Memórias bloqueada. Receitas conhecidas preservam o fundo de qualidade
e usam `IsItemUncraftable` para indisponibilidade; placeholders continuam sem esse fundo.

O seletor de utilitário abre por hover, centra o círculo de 310 pixels no slot e fica
acima das estatísticas. Quatro grids recebem drag/drop nos índices reais e clique duplo
seleciona o utilitário, com evento nativo cancelável e `SetActiveSlot`;
X seleciona -1, sem retirar itens. O grid central da roda também recebe drops.
Os grids interativos das quatro posições ficam sob uma máscara transparente para
ocultar o overlay quadrado de hover do cliente. Grids separados, sem hit testing,
exibem os mesmos ícones; o feedback de hover permanece no segmento azul da arte nativa.
A seta e a moldura dourada central usam o estado de hover do centro; o seletor
fechado conserva a moldura normal, sem seta ou efeito dourado permanente.
A projeção do grid
central mapeia seu índice zero ao slot ativo e preserva a origem capturada de um drag.
Ao desequipar, um drop usa a primeira posição vazia; contêiner cheio rejeita esse drop.
O widget angular nativo é controlado pelo cliente e não tem binding legado demonstrado:
a implementação usa grids e botões suportados e os assets originais, sem prometer a mesma área
de hit testing nem animação. Hover e sincronização de seleção precisam de reteste conectado.

## Arquitetura e autoridade

`CustomInventoryPlugin` registra o filtro seletivo, comando e `InventoryRegistry`.
`InventoryShellPage` mantém player/equipamento e storage/hotbar montados enquanto troca
o conteúdo central. Páginas e botões têm IDs `namespace:id` e estado próprio por sessão.
Os registros retornam handles idempotentes, e o host verifica o token registrado e a
geração da montagem antes de despachar callbacks de extensões. Controles persistentes
usam o token da instância da página; drag start/cancel não recriam os grids.

`NativeInventoryContent` exibe cópias dos contêineres ECS nos grids originais. Os eventos
SlotClicking/Dropped/DragCancelled usam bindings sem bloquear a interface. O codec
aceita as duas famílias de metadados Source/DragSource e ItemStack/DragItemStack,
priorizando os campos explícitos de drag quando presentes. Os inteiros opcionais
usam um `FunctionCodec` sobre `Codec.INTEGER`: isso permite `null` explícito sem a
rejeição do codec primitivo antes de executar o handler.
A projeção central e cada posição da roda Utility têm identificadores CustomUI distintos.
O controller converte a origem visual para o índice real antes de chamar o motor; os
identificadores visuais nunca são enviados a `InventoryUtils.moveItem`. O snapshot
capturado é guardado por grid e slot, inclusive após ocultar a roda. Clicar num destino
vazio não apaga a origem; eventos de cancelamento visual também preservam a origem
necessária para concluir o drag. Após uma movimentação submetida, o controller observa
a quantidade efetivamente retirada e atualiza apenas esse componente do snapshot,
preservando a comparação de metadados. Um item substituído continua sendo rejeitado.
A seleção guarda um snapshot no servidor sem retirar o item. Antes de mover, valida seção permitida, capacidade,
quantidade, contêiner, item atual e `PreventInventoryAccess`. A execução usa
`InventoryUtils.moveItem`; metadados, filtros, salvamento, eventos e sincronização
continuam pertencendo ao jogo. Uma submissão validada não garante transferência:
o motor pode recusá-la ou adiá-la conforme suas regras.

O botão Largar usa o mesmo controle de acesso, snapshot e quantidade antes de chamar
`InventoryPacketHandler.handle(DropItemStack)`. O handler nativo agenda o descarte e
mantém os eventos, filtros, permissões e criação de itens no mundo sob autoridade do
jogo. O mod não remove a pilha nem cria uma entidade manualmente. A resposta de
submissão indica que o pedido foi encaminhado, sem garantir o resultado da tarefa
agendada.

Alterações dos contêineres solicitam refresh na thread do mundo. Navegação libera os
listeners do conteúdo anterior; os painéis persistentes continuam ativos até fechamento,
desconexão, remoção do mundo ou shutdown. Nenhum item real fica
preso num cursor privado. O escopo da API evita colisões acidentais entre extensões;
plugins consumidores continuam sendo código confiável com acesso ao servidor.

`PlayerInventoryPanel` porta o CharacterPreviewComponent e as estatísticas nativas.
Uma tarefa de um segundo envia somente valores alterados, sem remount. O seletor
Utility também mantém um snapshot de apresentação; atualizações periódicas sem mudança
não enviam comandos ou novos acknowledgments. Isso reduz as janelas em que o
`PageManager` descarta eventos de dados enquanto espera confirmação de uma atualização.
Os olhos de armadura atualizam `PlayerSettings`, respeitam a política do mundo e
`PreventInventoryAccess`, preservam as demais preferências e marcam o equipamento
para sincronização; não retiram peças. O cleanup cancela a tarefa.

`PocketCraftingContent` permite somente receitas Fieldcraft, filtra conhecimento,
preserva os bloqueios de memórias e mostra ingredientes e preview 3D. A busca usa
nomes no idioma do jogador, com fallback para IDs. Receitas desconhecidas podem ser
mostradas como placeholders, mas não podem ser selecionadas ou fabricadas. A busca
envia `@SearchQuery` tanto no binding de `ValueChanged` quanto no codec;
o prefixo é preservado pelo cliente. O campo `Payload` das extensões permanece separado.
Os controles de fabricar uma, dez ou todas as unidades calculam materiais sobre o inventário
combinado nativo e revalidam o pedido antes de chamar `CraftingManager.craftItem`.
Uma bench ativa impede essa execução. A ordem das receitas continua ordenada pelo ID
do item de saída e da receita; não foi comprovada equivalência com a ordenação do cliente.

`CollectedMemoriesContent` projeta `PlayerMemories`, incluindo os registros carregados,
capacidade e placeholders vazios. Não usa o banco global de memórias depositadas. O
refresh periódico detecta mudanças nesses registros e no contador das abas.

`BackpackInventoryContent` projeta a seção nativa `InventoryComponent.BACKPACK_SECTION_ID`.
Drag/drop compartilha os snapshots e a validação do controlador dos painéis persistentes.
Updates na mesma aba atualizam os slots sem remover o grid ou duplicar seus bindings;
`KeepScrollPosition` preserva a rolagem quando os itens mudam. Trocas de aba remontam o
conteúdo normalmente. A rolagem e sua preservação ainda precisam de reteste em cliente.
Os quatro controles chamam `InventoryUtils.takeAll`, `putAll`, `quickStack` e a ordenação
nativa do contêiner. Ao sair da aba, seu listener e snapshot são removidos. As abas de
mochila e memórias ficam bloqueadas quando suas capacidades são zero. Aparência,
upgrades, armazenamento extra e regras específicas do TrueBackpack ficam nas extensões.

## UIs relacionadas e limitações

As rotas de benches, processing benches, baús e Memories usam tipos de janela distintos
ou janelas abertas pelo servidor. Esses tipos e seus IDs positivos não são redirecionados.
A arquitetura fornece evidência de separação, mas não substitui testes dessas telas.

O pocket crafting associado à solicitação interceptada usa a página de receitas do
núcleo. O fluxo de crafting está implementado, mas a execução com jogador conectado
ainda precisa de validação. Benches de crafting mantêm suas rotas próprias.

O protótipo não garante interceptação de caminhos que não passem pelo adapter configurado,
prioridade perante filtros de outros plugins, fechamento por TAB ou ausência de flash.
Esses pontos, a renderização de `ItemGrid` e a interação com as telas nativas precisam
ser conferidos com um jogador conectado. As correções de interação são verificadas
por compilação e empacotamento; o comportamento de máscara, hover, split, drag/drop e
descarte permanece pendente de validação no jogo. O projeto não contém testes
automatizados nem verificadores que tentem substituir essa execução no jogo.
