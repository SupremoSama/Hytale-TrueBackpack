# CustomInventory — protótipo independente

Abra com `/custominventory`. Clique num item para selecioná-lo e depois no slot de destino
para solicitar a transferência da pilha. Clicar novamente na origem cancela a seleção.
Refresh atualiza as contribuições registradas; Close/ESC fecha a página.

Este é o protótipo da alternativa tecnicamente viável: uma CustomUI independente, aberta
por comando. **O inventário nativo de TAB continua disponível.** As APIs públicas examinadas
não oferecem cancelamento antes de sua abertura local. Leia
[a análise técnica](TECHNICAL-ANALYSIS.md) para o ciclo de abertura, fontes e limites.

## Build e instalação

Requer JDK 25 e um JAR oficial do Hytale Server. O projeto não usa nem compila
`hytale-shared-source`, não importa TrueBackpack e não precisa do plugin Gradle do projeto pai.

```powershell
cd D:\Projetos\Mods\Hytale-CustomInventory\standalone-inventory
.\gradlew.bat build --offline
```

No Windows, o build usa por padrão o servidor pre-release instalado pelo launcher.
Para outra instalação, informe `-PhytaleServerJar=C:/caminho/HytaleServer.jar` ou a variável
`HYTALE_SERVER_JAR`. O build usa o JAR apenas como dependência de compilação; ele não é
copiado para os artefatos do mod.

Instale `build/libs/CustomInventory-0.1.0.jar` no diretório de mods do servidor.
Opcionalmente, instale `example-extension/build/libs/CustomInventoryExample-0.1.0.jar`
para demonstrar uma página e um botão adicionados por outro plugin.
Os JARs `-sources` contêm somente o código-fonte. O TrueBackpack herdado na raiz do
checkout não faz parte deste build e não é necessário para instalar CustomInventory.

## API de extensão

O plugin consumidor declara `SupremoSama:CustomInventory` em `Dependencies` ou em
`OptionalDependencies`, conforme a integração seja obrigatória ou opcional. Ele compila
contra o JAR do núcleo com `compileOnly` e não empacota outra cópia das classes de API.
O núcleo não depende do consumidor.

```java
InventoryRegistry registry = CustomInventoryPlugin.get().getInventoryRegistry();

InventoryRegistry.Registration pageRegistration = registry.registerInventoryPage(
        new InventoryPageDefinition("example:appearance", "Aparência", 100,
                context -> new MyAppearanceContent()));

InventoryRegistry.Registration buttonRegistration = registry.registerInventoryButton(
        new InventoryButtonDefinition("example:refresh", "Atualizar", 100,
                InventoryContext::requestRefresh));

// No shutdown do plugin consumidor:
buttonRegistration.close();
pageRegistration.close();
```

`InventoryContent` recebe o contexto do jogador, builders de UI, um seletor de montagem
e `InventoryEventBindings`. A fábrica cria uma sessão nova para cada abertura; conteúdo
não deve ser compartilhado entre jogadores. Use o seletor recebido para seus comandos,
e `events.bind(...)` para seus eventos. As callbacks executam na thread do mundo.
Ao trocar/fechar uma página, `onDismiss` libera recursos da sessão.

```java
public void build(InventoryContext context, UICommandBuilder commands,
                  InventoryEventBindings events, String selector) {
    commands.append(selector, "Pages/MeuMod/Appearance.ui");
    events.bind(CustomUIEventBindingType.Activating, "#Apply",
                "Apply", "", true);
}
```

Registros rejeitam IDs duplicados e exigem `namespace:id`. A navegação é ordenada por
`order`, depois por ID. Registros novos aparecem ao reabrir ou clicar Refresh; removidos
deixam de aceitar callbacks. O envelope usa a sessão do conteúdo para rejeitar eventos
antigos. O exemplo completo está em `example-extension/src/main`.

## Escopo atual

Storage, Hotbar, Armor, Utility e Backpack usam os contêineres originais com suas capacidades
atuais. A movimentação delega a `InventoryUtils.moveItem` após validar a seleção e o bloqueio
nativo de acesso. O servidor continua responsável por filtros, metadados, salvamento,
eventos, equipamento e sincronização.

Não há listeners de abertura nativa, filtros de pacotes, overrides de assets nativos,
nem alterações de rotas de benches/crafting/Memorium. A paridade completa com o inventário
original — Tools, Abilities, RuneBag, atalhos, menus contextuais, drag/split e personalização
do cliente — depende de expansão e testes específicos.

Resultados automatizados e verificações em jogo estão em [VALIDATION.md](VALIDATION.md).

O boot isolado pode ser repetido com `./scripts/SmokeTest.ps1`. Para incluir o TrueBackpack:

```powershell
.\scripts\SmokeTest.ps1 -TrueBackpackJar D:/Projetos/Mods/Hytale-TrueBackpack/build/libs/TrueBackpack-0.3.6.jar -TestPort 5528
```

O script usa somente `run-smoke`, cria um mundo de teste, escuta em loopback e encerra o
servidor após o boot. Ele não usa o diretório `run` dos outros mods.
