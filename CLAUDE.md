# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## O que é o app

**Super GeruApp** (`com.vacari.gerupreco`) — app Android nativo, pessoal, em **Java**, com dois módulos escolhidos na tela inicial:

- **GeruPreço** — cadastro de produtos por código de barras e consulta do menor preço em estabelecimentos próximos, via API pública da **Nota Paraná**.
- **GeruRegra** — calculadora de regra de três com múltiplas linhas.

A interface é toda em **português**. Comentários e nomes de código também seguem o português em boa parte.

## Comandos

O wrapper do Gradle é usado para tudo. **O JDK importa**: o projeto compila com `sourceCompatibility`/`targetCompatibility` **25**. O JBR que acompanha o Android Studio serve — está no 25.0.2. O JDK Oracle que antes ficava em `C:\Program Files\Java\jdk-25.0.4` **não está mais instalado**, e `JAVA_HOME` não está definido em nenhum escopo do ambiente: a variável precisa ser exportada na sessão antes de chamar o wrapper.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat assembleDebug          # APK debug -> app/build/outputs/apk/debug/app-v<versionCode>-debug.apk
.\gradlew.bat assembleRelease        # APK assinado com key.jks -> app/build/outputs/apk/release/app-v<versionCode>-release.apk
.\gradlew.bat testDebugUnitTest      # testes JVM
.\gradlew.bat testDebugUnitTest --tests "com.vacari.gerupreco.util.StringUtilTest"   # um teste só
```

Compilar com um JDK anterior falha com `error: invalid source release: 25`. No Android Studio isso é controlado por `.gradle/config.properties` (`java.home`), que **não** é versionado — cada máquina precisa apontar para um JDK 25.

O SDK do Android também precisa estar localizável: não há `local.properties` versionado, então o build depende de `ANDROID_HOME` (`$env:LOCALAPPDATA\Android\Sdk`), senão falha com `SDK location not found`.

Não há lint configurado além do padrão do AGP, nem testes instrumentados reais (`ExampleInstrumentedTest` é o esqueleto gerado).

## Stack e decisões de build

- **AGP 9.3.1 / Gradle 9.7 / Java 25.** O Android Studio 2026.1.3 declara compatibilidade conhecida até AGP 9.3.0; o sync funciona mesmo assim, mas se o IDE reclamar, fixar `9.3.0` resolve.
- **`compileSdk = 37`, `targetSdk = 36`** — separação deliberada: as APIs do Android 17 ficam disponíveis na compilação sem que as mudanças de comportamento de runtime sejam aplicadas. Subir o `targetSdk` é uma decisão à parte, e o ponto mais sensível é o fluxo de auto-atualização (`UpdateJob`), que instala APK via `FileProvider` + `REQUEST_INSTALL_PACKAGES`.
- **Sem Kotlin e sem Compose.** O plugin Kotlin está declarado com `apply false` na raiz e nunca é aplicado. Toda a UI é XML com Views clássicas.
- **Lombok** (`@Getter`/`@Setter`) nos modelos.
- Sintaxe do Groovy DSL usa **atribuição** (`namespace = '...'`), não `namespace '...'` — a forma antiga é deprecada no Gradle 9 e removida no 10.

## Arquitetura

Pacotes sob `com.vacari.gerupreco`, organizados por tipo (`activity`, `adapter`, `dialog`, `model`, `repository`, `retrofit`, `util`). Não há DI, ViewModel nem camada de domínio — Activities falam direto com repositórios estáticos e recebem resultado por `Callback<T>`.

**Duas fontes de dados distintas:**

1. **Firestore** (`ItemRepository`, coleção `item`) — catálogo de produtos do usuário. É a fonte de verdade do cadastro.
2. **Nota Paraná** via Retrofit (`RetrofitRequest`) — preços por código de barras. Somente leitura, externa.

3. **SQLite/ORMLite** (`DatabaseHelper`) — carrinho de compras (`CartRepository`, tabela `cart_item`). Local, sem sincronização.

Há ainda `NotificationRepository` para notificações de preço-alvo, mas **essa funcionalidade está desativada**: a entrada de menu e o item de contexto que levam à `NotificationActivity` estão comentados, então a tela é inalcançável pela UI.

### Fluxo das telas

`MainActivity` → `LowestPriceProduct` (lista de produtos) → `LowestPriceActivity` (preços de um produto).
`MainActivity` → `SimpleProportionActivity` (regra de três).
`LowestPriceProduct` → `CartActivity` (carrinho) → `CartCompareActivity` (comparador, duas abas).

## Carrinho de compras

Produtos entram por long press na lista (`Adicionar ao carrinho`) ou em lote pelo `AddByTagDialog` (`Adicionar por tag`, no menu da própria `CartActivity` — o catálogo vem do Firestore, então o diálogo só abre depois da consulta). O ícone na action bar da lista traz um badge com o total de **unidades**, não de linhas.

O `CartItem` guarda cópia de descrição/tamanho/unidade em vez de referenciar o `Item` do Firestore: a tela monta sem rede, e excluir o produto do catálogo não deixa linha órfã. Adicionar um produto já presente **incrementa a quantidade** em vez de duplicar a linha.

### O comparador e suas duas abas

O botão **Comparar** do rodapé é a única entrada. Ele abre a `CartCompareActivity`, que hospeda duas abas num `ViewPager2` sobre o mesmo carrinho e os mesmos preços:

| Aba | Ranqueia | Legenda | Lógica |
| --- | --- | --- | --- |
| **Mercados** | estabelecimentos | "Onde o carrinho inteiro sai mais barato" | `CartCompare` / `MarketQuoteFragment` |
| **Produtos** | itens entre si | "Qual produto rende mais por quilo ou litro" | `CartUnitPrice` / `UnitPriceFragment` |

Antes eram duas telas — a segunda escondida atrás de um ícone mudo na action bar do carrinho — e se confundiam: os dois nomes ("Comparar", "Custo-benefício") descreviam igualmente bem qualquer uma das duas. **As abas são nomeadas pelo substantivo do que cada uma ranqueia**, que é exatamente onde diferem; a legenda logo abaixo completa a frase e troca junto com a aba. Renomear as abas para algo genérico devolve a confusão.

Consequências do desenho que valem preservar:

- **A consulta é do host, não das abas.** `CartCompareActivity` chama o `CartPriceLoader` uma vez e guarda `cartItems`/`prices`; as abas só leem e ordenam. Como as duas consomem exatamente os mesmos GTINs, duas telas separadas faziam a mesma consulta duas vezes, com dois `ProgressDialog` seguidos. Não mover o carregamento para dentro de um fragment.
- **As abas se registram no host** (`registerTab`/`unregisterTab` em `onAttach`/`onDetach`) e ele chama `render()` nas que estiverem vivas. É de propósito não usar `findFragmentByTag("f" + position)`: essa tag é detalhe interno do `FragmentStateAdapter`.
- **`isLoaded()` segura o aviso de vazio.** Antes da consulta voltar o resultado está vazio por falta de dados, não por falta de oferta — sem a guarda, a aba pisca "nenhum estabelecimento tem os produtos".
- **A raiz de cada fragment é um `FrameLayout` que não rola.** Quem tem `fitsSystemWindows` é a raiz da Activity; promover o `RecyclerView` a raiz do fragment reabre a armadilha de insets descrita mais abaixo.
- **Fragments existem só aqui.** O resto do app é Activity pura com `findViewById`; `androidx.fragment` e `androidx.viewpager2` entraram no `build.gradle` por causa destas abas.

#### Aba Mercados

`CartCompare` é lógica pura e testada (`CartCompareTest`). Regras que valem preservar:

- **Agrupa por `estabelecimento.codigo`, nunca por nome.** Há três lojas distintas chamadas "MUFFATAO"; agrupar por nome fundiria filiais.
- **Uma chamada de API por produto.** Testei lista separada por vírgula, parâmetro repetido e `gtin[]` — nenhuma funciona. `CartPriceLoader` dispara em paralelo e junta as respostas; falha de rede num produto vira "sem preço" e não derruba o resto.
- **O filtro de data é local, não é o parâmetro `data` da API.** Aquele parâmetro é inconsistente (`data=7` devolve mais registros que `data=3`). A busca pede tudo com `data=-1` e a janela é recortada em memória — por isso trocar o chip reordena na hora, sem tráfego novo.
- **Ordenação:** completos primeiro pelo menor total; depois os incompletos, primeiro os que menos deixam faltar, e só então pelo total parcial. Um total baixo não vale nada se veio de um mercado que tem metade da lista.
- **Produto sem preço em lugar nenhum sai do cálculo de faltantes** e aparece num aviso à parte. Se contasse, jogaria todos os mercados para o grupo dos incompletos sem diferenciar ninguém.
- **A mesma loja pode aparecer duas vezes** na resposta quando o GTIN está cadastrado com e sem zero à esquerda; vale o menor preço.

#### Aba Produtos — preço por quilo e por litro

`CartUnitPrice` é lógica pura e testada (`CartUnitPriceTest`). Ranqueia os **produtos entre si**, não os mercados.

- **A API não devolve o tamanho da embalagem.** `Product` traz descrição, valores, data, distância, GTIN e estabelecimento — nada de gramas ou mililitros. A única fonte é o `size`/`unitMeasure` do próprio catálogo, copiado para o `CartItem`.
- **Normalizar não muda nada dentro de um mesmo produto.** A busca é por GTIN, e todas as ofertas de um GTIN têm o mesmo tamanho — dividir pelo volume dá a mesma ordem que o preço bruto. O que a divisão revela é a comparação **entre produtos diferentes**: a lata de 350 ml contra a garrafa de 1 L.
- **Vale o menor preço de cada produto**, venha de onde vier. O estabelecimento entra só como referência na linha, porque aqui a comparação não é entre mercados.
- **A quantidade do carrinho não entra na conta.** Seis latas não mudam o preço do litro.
- **Peso e volume caem na mesma lista ordenada.** São grandezas diferentes e comparar R$/kg de arroz com R$/L de sabão não diz nada sozinho; a lista única foi pedida assim, e cada linha carrega o rótulo da unidade.
- **Tamanho inválido sai como `unmeasured`, separado dos `unpriced`.** A ação é diferente: um se resolve no cadastro, o outro abrindo a janela de datas. Somar os dois mandaria o usuário procurar no lugar errado.
- **`UnitMeasureUtil` repete a convenção do `PriceUtil`**: o ponto é decimal e só vira separador de milhar quando há vírgula na string. Sem isso `1.5 L` viraria 15 L. Como `size` é texto livre, a leitura também tolera `"500g"` e descarta o que não tiver número.
- **`PriceWindow` monta os chips de janela de data uma vez, na Activity**, acima do pager: a janela vale para as duas abas, e trocá-la redesenha ambas. A preferência em `SharedPreferences` sobrevive de dentro da época das duas telas separadas — hoje ela só serve para lembrar a escolha entre visitas.

### Preço vem como texto com ponto decimal

`PriceUtil.parse` trata `"4.50"` como 4,50. Aplicar a regra pt-BR (ponto = milhar) transformava `3.11` em `311` e os totais saíam cem vezes maiores — o bug passou por revisão de código e só apareceu no aparelho. A vírgula só é considerada separador decimal quando de fato aparece na string.

### Novidades da versão

`ChangelogDialog.showIfNeeded()` exibe o que mudou, uma única vez por `versionCode`, com o controle guardado em `SharedPreferences`. O texto vive em `res/values/changelog.xml`, em dois `string-array` paralelos (títulos e descrições), reescritos a cada release.

É chamado de dentro de `MainActivity.configureActions()`, **não** do `onCreate`, pelo mesmo motivo do gate abaixo: só faz sentido mostrar novidades depois que a versão foi validada. A preferência é gravada **antes** de exibir o diálogo, porque o listener do Firestore pode chamar `configureActions()` mais de uma vez e empilharia diálogos.

### Gate de versão — não é bug

`MainActivity.configureActions()` **não** é chamado no `onCreate`. Quem chama é `UpdateJob.checkVerisonCode()`, e só quando a versão instalada está em dia com o documento `appVersion` do Firestore. Estando desatualizada, aparece o diálogo de atualização e os cards da tela inicial permanecem inertes. Isso é intencional: bloqueia o uso do app em versões antigas. **Não "conserte" adicionando a chamada no `onCreate`.**

Pelo mesmo motivo os cards em `activity_main.xml` não declaram `android:clickable="true"` — se declarassem, dariam feedback de toque enquanto ainda bloqueados.

## Modelo de dados

`Item` (Firestore): `id`, `barCode`, `description`, `size`, `unitMeasure`, `tags`.

**Firestore é schemaless** — adicionar um campo no POJO e salvar já o cria nos documentos. Não existe migração de schema a fazer no console. Documentos antigos apenas não têm o campo e voltam `null`; por isso `Item.getTags()` tem guarda de nulo em vez de depender do inicializador.

`unitMeasure` vem do `string-array` `unit_measurement` (`G`, `KG`, `ML`, `L`) em `res/values/unit.xml`.

### Acesso administrativo aos dados

O app **não usa autenticação** e as regras do Firestore permitem acesso público. Isso significa que a API REST funciona só com a chave em `app/google-services.json`, o que é útil para migrações em massa:

```
GET   https://firestore.googleapis.com/v1/projects/gerupreco/databases/(default)/documents/item?key=<API_KEY>
PATCH https://firestore.googleapis.com/v1/<document.name>?key=<API_KEY>&updateMask.fieldPaths=<campo>
```

Sempre usar `updateMask.fieldPaths` para não sobrescrever o documento inteiro, e salvar um dump antes de escrever em lote.

## Tags

Tags são livres (o usuário digita), com sugestão a partir das já usadas em outros produtos. O vocabulário atual segue **categoria + tipo** (`bebida` + `cerveja`, `laticinio` + `zero lactose`, `limpeza` + `roupa`), normalmente 2 por produto.

- A **cor sai de um hash do nome normalizado** sobre `R.array.tag_palette` (`TagUtil.colorFor`). Nada de cor é persistido, e a mesma tag tem sempre a mesma cor.
- Chips são construídos em código por `TagUtil.createChip` e usados tanto na lista quanto no diálogo de cadastro.
- Deduplicação é por nome normalizado, então `Bebida` e `bebida` são a mesma tag; a grafia já em uso é reaproveitada.

## Texto: acentos e ordenação

`StringUtil` centraliza as duas regras, e **elas devem ser usadas em qualquer busca ou ordenação nova**:

- `normalize()` — minúsculas, sem acentos, sem espaços nas pontas. Base da busca, que casa descrição **e** tags.
- `textComparator()` — `Collator` pt-BR com força `SECONDARY`. Sem ele, comparação direta de `String` joga "Água" para o fim da lista, longe de "Agua", porque ordena por code point Unicode.

## Design system — "Neon Utility Dark"

Tema escuro com primária mint e secundária roxa. Hierarquia vem de **camadas tonais**, não de sombras. Os tokens ficam em `res/values/`:

| Arquivo          | Conteúdo                                                |
| ---------------- | ------------------------------------------------------- |
| `colors.xml`     | paleta completa (surfaces, primary, secondary, outline) |
| `type.xml`       | `TextAppearance.GeruPreco.*`                            |
| `styles.xml`     | cards, campos, FAB, action bar, abas, diálogos, shapes  |
| `dimens.xml`     | escala de 8px (`space_*`) e raios (`radius_*`)          |
| `tag_colors.xml` | paleta das tags                                         |

Fontes **Plus Jakarta Sans** (estrutura) e **JetBrains Mono** (rótulos utilitários em caixa alta) estão embutidas em `res/font/`. Preferir os estilos existentes a declarar cor, tamanho e fonte soltos no layout.

## Armadilhas já encontradas

Todas custaram um ciclo de depuração; vale não repetir.

- **`fitsSystemWindows` num container que rola.** Promover `RecyclerView` a raiz do layout com `fitsSystemWindows="true"` faz o `ActionBarOverlayLayout` esticá-lo pela janela inteira e converter os insets em padding; junto com `clipToPadding="false"`, os itens passam a desenhar por baixo da action bar e da status bar. Manter sempre um container não-rolável na raiz absorvendo os insets.
- **Diálogos não herdam `windowSoftInputMode` da Activity.** Têm janela própria; sem `getWindow().setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)` o teclado cobre os botões Salvar/Cancelar.
- **Activities com campo de texto precisam de `android:windowSoftInputMode="adjustResize"`** no manifesto, ou o teclado cobre FAB e conteúdo.
- **`Spinner` precisa de largura folgada.** O padding da seta consome ~55dp; com pouco espaço, unidades de duas letras (`ML`, `KG`) simplesmente deixam de ser desenhadas enquanto as de uma letra (`G`, `L`) aparecem. Atenção especial ao trocar `layout_width="match_parent"`+peso por `0dp`+peso — a distribuição de largura resultante é bem diferente.
- **`SearchView` reexibe o teclado ao reassumir o foco.** Ao fechar um diálogo ou voltar de outra tela, o teclado volta sozinho. `LowestPriceProduct.clearSearchFocus()` é chamado antes de cada sobreposição; manter esse cuidado ao adicionar novas ações na lista.
- **`NotificationAdapter` faz cast de `<Switch>` para `android.widget.Switch`**, mas o AppCompat infla a tag como `SwitchCompat`. Isso estouraria em runtime — está latente só porque a tela é inalcançável.
- **A action bar da lista comporta 3 ações.** Um item com `showAsAction="never"` cria o botão de overflow e **empurra o scanner de código de barras para dentro dele**. Foi o que aconteceu ao pôr "Adicionar por tag" ali; por isso essa ação mora na `CartActivity`.
- **Item de menu com `actionLayout` não passa por `onOptionsItemSelected`.** O ícone do carrinho precisa de `setOnClickListener` na própria action view.
- **`Chip` com cor de fundo fixa não mostra seleção.** `setChipBackgroundColorResource` aplica a mesma cor a todos os estados; é preciso um `ColorStateList` com `state_checked` (ver `res/color/chip_window_background.xml`).

## Verificação em dispositivo

Há um aparelho físico conectado por adb (Wi-Fi). Como não existe emulador instalado nem suíte de testes de UI, **a verificação real é dirigir o app por adb**. Isso já pegou defeitos que passariam despercebidos numa leitura de código:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-v11-debug.apk"
& $adb shell monkey -p com.vacari.gerupreco -c android.intent.category.LAUNCHER 1

& $adb shell uiautomator dump /sdcard/uu.xml   # hierarquia com bounds e ids
& $adb shell screencap -p /sdcard/s.png        # captura
& $adb shell dumpsys input_method | Select-String "mInputShown="   # teclado visível?
```

Notas que economizam tempo:

- **Obter coordenadas do `uiautomator dump`, nunca estimar por pixel da captura.** E **remedir depois de abrir o teclado** — o layout desloca, e um toque com coordenadas antigas cai numa tecla.
- As Activities além da `MainActivity` são `exported="false"`; `am start` direto falha com `SecurityException`. É preciso navegar pela UI.
- Um elemento pode existir na hierarquia com o texto certo e mesmo assim **não ser desenhado**. Quando a suspeita for essa, ler os pixels da região (`System.Drawing.Bitmap.GetPixel`) distingue "não renderizado" de "renderizado sem contraste".
- Long press: `input swipe <x> <y> <x> <y> 900`.

### Conectar o aparelho por Wi-Fi

Pareamento e conexão usam **portas diferentes**: a do diálogo "Parear com código" só serve para o `adb pair`, e a de conexão é outra.

```powershell
& $adb pair 192.168.3.91:<porta-do-pareamento> <codigo-de-6-digitos>

# O mDNS padrao falha com "mdns daemon unavailable"; o backend interno resolve
$env:ADB_MDNS_OPENSCREEN = "1"
& $adb kill-server; & $adb start-server
& $adb mdns services        # descobre o IP:porta de _adb-tls-connect._tcp
& $adb connect 192.168.3.91:<porta-de-conexao>
```

- **O aparelho recusa injeção de eventos.** `input tap` estoura `SecurityException: INJECT_EVENTS` — trava da HyperOS/MIUI. Leitura funciona (`uiautomator dump`, `screencap`), mas dirigir a UI exige ligar **Opções do desenvolvedor → Depuração USB (Configurações de segurança)**, que no Xiaomi pede conta Mi e chip com dados. Sem isso, a alternativa é pedir para o usuário navegar e só ler a tela.
- **O aparelho tem a *release* instalada.** Instalar a debug por cima falha com `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (chaves diferentes), e desinstalar antes apagaria o carrinho em SQLite e as preferências. Para testar sem perder dados, gere e instale a **release**.

## Publicar uma versão

Passo a passo completo em **[RELEASE.md](RELEASE.md)**. O resumo: subir `appVersionCode` no `app/build.gradle`, `assembleRelease`, copiar o APK para `app/release/`, commitar, **empurrar**, e só então apontar o documento `appVersion` do Firestore para o novo `versionCode` e URL.

A ordem importa: o Firestore é o gatilho de produção. Mudá-lo antes do APK estar acessível no GitHub deixa o app inutilizável, porque o gate de versão bloqueia a home e o download falha.

## Segurança

`key.jks` e `readmeKey.txt` (que contém a senha do keystore em texto plano) estão no repositório e **não** constam do `.gitignore` — e a senha também está no `signingConfigs` do `app/build.gradle`. Qualquer pessoa com acesso ao repo pode assinar builds como se fossem oficiais e, como o app instala APK de uma URL pública sem verificação extra, isso distribui código direto para os aparelhos. Vale rotacionar a chave, removê-los do versionamento e manter o repositório privado.
