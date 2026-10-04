# CustomInventory

Mod independente de inventário extensível. Reproduz o layout nativo do jogo a partir
dos arquivos `.ui` e texturas do cliente instalado: preview, equipamento e estatísticas
à esquerda; crafting, memórias coletadas ou mochila nativa acima do storage/hotbar.
As três abas hexagonais ficam ao lado do personagem, como na interface original.
O núcleo não depende do TrueBackpack.

A solicitação nativa de pocket crafting abre essa CustomUI somente no modo Adventure,
também disponível por `/custominventory` nesse modo. Uma nova solicitação com a página
aberta a fecha; Creative conserva o inventário original. Sair de Adventure dispensa
o inventário customizado. Os grids usam drag/drop com os IDs das seções originais; o servidor
valida origem e quantidade e solicita a movimentação ao jogo. Os controles de olho
alteram a visibilidade das armaduras, preservando o equipamento e seus atributos.
Páginas e botões de extensões acrescentam controles após as três abas nativas.

A barra superior inclui inventário, mapa, a versão do runtime pelo manifesto oficial
e o estado desabilitado de ferramentas criativas.
As dicas laterais exibem os atalhos padrão da referência nativa e os ícones de mouse.
A ação Largar é um botão clicável que descarta a pilha selecionada pelo hover; o atalho
G não é capturado pela CustomUI. A API pública expõe `KeyDown` somente para campos de
texto, e a página não recebe as teclas remapeadas de cada cliente. Memórias bloqueadas exibem
o alerta nativo. Hover no slot utilitário ou em Z abre um seletor de quatro posições
e a opção de desequipar, sem remover itens do contêiner. Os grids da roda recebem e
movem itens usando os contêineres reais; clique duplo seleciona o utilitário ativo.
Cada posição da roda possui uma identidade de drag própria, traduzida para a seção
Utility real antes da movimentação. Cada origem preserva seu snapshot mesmo após ocultar
o círculo ou clicar no destino; movimentações parciais acompanham a quantidade que o
motor realmente retirou da origem.
O círculo usa a arte nativa, com segmentos dourados e hover azul. Uma máscara transparente
oculta o hover quadrado dos grids interativos, enquanto grids de exibição desenham os
ícones. A seta e o destaque dourado de Z aparecem somente durante o hover central;
ficam ocultos com o seletor fechado. As áreas de
interação são grids e botões de CustomUI, sem o hit testing angular do widget do cliente.

A interceptação é experimental: a abertura por TAB, seu fechamento e a ausência de um
frame do inventário nativo ainda precisam de validação com um cliente conectado.
Leia [TECHNICAL-ANALYSIS.md](TECHNICAL-ANALYSIS.md) para o fluxo e seus limites.

## Build e servidor local

Requer JDK 25 e Hytale Server 0.7.0-pre.5.1 ou posterior. O projeto usa o JAR oficial como dependência
de compilação e runtime de desenvolvimento; o artefato do mod não inclui classes do servidor.

```powershell
cd D:\Projetos\Mods\Hytale-CustomInventory
.\gradlew.bat assemble prepareRunServer --offline
.\gradlew.bat runServer --console=plain
```

`runServer` compila e instala `CustomInventory-dev.jar` em `run/mods`, abre um console
interativo e escuta em `127.0.0.1:5520`, com autenticação `authenticated`.
Mundos, permissões e autenticação existentes em `run` são preservados. Uma instalação
nova precisa da autenticação normal do servidor; use os comandos `/auth` do console.

| Configuração | Propriedade Gradle / variável |
| --- | --- |
| JAR do servidor | `-PhytaleServerJar=<caminho>` / `HYTALE_SERVER_JAR` |
| Assets.zip | `-PhytaleAssetsZip=<caminho>` / `HYTALE_ASSETS_ZIP` |
| Diretório de execução | `-PserverRunDir=<caminho>`; padrão `run` |
| Bind | `-PserverBind=127.0.0.1:5521` |
| Autenticação | `-PserverAuthMode=authenticated` |
| Comando após boot | `-PserverBootCommand=stop` |

No Windows, o JAR padrão vem da instalação pre-release do launcher; os assets são
resolvidos junto desse pacote. Instale `build/libs/CustomInventory-0.1.0.jar` no diretório
de mods de outro servidor. Evite instalar duas versões do núcleo no mesmo servidor.

## API de extensão

Uma extensão declara `SupremoSama:CustomInventory` em `Dependencies` ou
`OptionalDependencies`, compila contra o núcleo com `compileOnly` e não empacota outra
cópia da API. O núcleo funciona sozinho.

```java
InventoryRegistry registry = CustomInventoryPlugin.get().getInventoryRegistry();
InventoryRegistry.Registration page = registry.registerInventoryPage(
        new InventoryPageDefinition("example:appearance", "Aparência", 100,
                context -> new MyAppearanceContent()));
InventoryRegistry.Registration button = registry.registerInventoryButton(
        new InventoryButtonDefinition("example:refresh", "Atualizar", 100,
                InventoryContext::requestRefresh));

// No shutdown da extensão:
button.close();
page.close();
```

Cada fábrica cria um `InventoryContent` próprio por jogador/sessão. Monte os comandos
sob o seletor recebido e use `InventoryEventBindings.bind(...)` para os eventos.
As callbacks executam na thread do mundo; `onDismiss` libera listeners e timers.
Player, equipamento, storage e hotbar continuam visíveis quando outra página é
selecionada; o conteúdo da extensão ocupa o painel central superior.
IDs usam `namespace:id`, com ordem por `order` e ID. Registros removidos deixam de
aceitar callbacks; mudanças aparecem ao reabrir ou quando a extensão chama
`InventoryContext.requestRefresh()`. A geração da montagem rejeita eventos antigos.

Quando presente no checkout, o projeto opcional `example-extension` demonstra uma
página e um botão sem mudanças no núcleo. Seu JAR é
`example-extension/build/libs/CustomInventoryExample-0.1.0.jar`. O build também funciona
sem essa pasta; `runServer` instala somente o núcleo.

## Escopo e validação

Storage, Hotbar, Armor, Utility e a aba Backpack projetam os contêineres originais,
com capacidade atual. Utility mostra o slot utilizável e as duas molduras decorativas
da tela nativa. A movimentação valida acesso, slots, quantidade e snapshot de origem, depois
delega a `InventoryUtils.moveItem`. Drag/drop aceita os metadados nativos, inclusive
quantidades parciais e campos inteiros opcionais com valor `null`. Snapshots separados
por grid e slot evitam perder a origem ao clicar no destino ou continuar uma colocação
parcial. A ação Largar também valida o snapshot e a quantidade e delega ao handler
nativo de `DropItemStack`, que agenda a execução nas regras do jogo. A interação real
de split, descarte e outros gestos precisa de validação em cliente.
Tools, Abilities e RuneBag não têm painéis próprios.

Pocket crafting lista receitas Fieldcraft, respeita conhecimento e memórias, mostra
ingredientes e o preview do item e delega a execução ao `CraftingManager` do jogador.
Ingredientes específicos usam o mesmo fundo arredondado nativo dos recursos genéricos,
preservando as tooltips de item.
Inclui busca, filtro de receitas desconhecidas e controles de fabricar uma, dez ou
todas as unidades possíveis. A aba de memórias mostra os registros carregados pelo
jogador e sua capacidade; a mochila nativa oferece retirar/depositar tudo, completar
pilhas e ordenar. A mochila cresce até quatro linhas visíveis (36 slots); capacidades
maiores usam rolagem interna, mantendo o inventário abaixo na tela. O grid permanece
montado durante atualizações para preservar a posição da rolagem.
O painel do jogador atualiza vida, estamina, mana, defesa e hotbar
sem recriar o preview ou os grids. O seletor Utility conserva seu estado de apresentação
e evita comandos periódicos quando ele não mudou. Aparência, upgrades, armazenamento extra e regras
específicas do TrueBackpack pertencem às extensões.

As medidas e texturas vêm da interface original, incluindo os painéis de 410 e
715 pixels, intervalo de 38 pixels, slots de 74 pixels e abas de 70 pixels. A igualdade
visual por pixel ainda precisa de comparação em cliente, na mesma resolução e escala
de UI das imagens de referência. Animações, gestos e a ordem do catálogo de receitas
também precisam de validação nessa comparação.

O filtro trata apenas `ClientOpenWindow(PocketCrafting)` e `CloseWindow(0)` de conexões
previamente redirecionadas. Outras solicitações nativas e IDs positivos seguem o jogo.
Um observador de saída cancela somente tarefas de redireção pendentes quando outra
janela ou página é aberta; os pacotes de saída continuam intactos.
Bench/crafting/Memorium, drag/drop, visibilidade e renderização precisam de validação
funcional em cliente.

Com PacketInspector instalado opcionalmente, use `/packetlog start inventory`, teste
TAB, ESC, reabertura, benches e Memories, depois `/packetlog stop`. Esse preset inclui
os campos dos pacotes, necessários para confirmar o comportamento real do cliente.

O projeto não contém suíte automatizada, verificadores ou script de smoke test.
As correções de interação são verificadas por compilação e empacotamento; sua validação
funcional permanece pendente em cliente conectado.
Use `runServer` para executar o mod e conferir os comportamentos no próprio jogo.
