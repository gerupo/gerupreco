# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## O que é o app

**Super GeruApp** (`com.vacari.gerupreco`) — app Android nativo, pessoal, em **Java**, com três módulos escolhidos na tela inicial:

- **GeruPreço** — cadastro de produtos por código de barras e consulta do menor preço em estabelecimentos próximos, via API pública da **Nota Paraná**.
- **Rastreamento** — lista de produtos vigiados com preço-alvo, que notifica os aparelhos quando o preço cai. Ver *Rastreamento de preços*.
- **GeruRegra** — calculadora de regra de três com múltiplas linhas.

A interface é toda em **português**. Comentários e nomes de código também seguem o português em boa parte.

## Comandos

O wrapper do Gradle é usado para tudo. **O JDK importa**: o projeto compila com `sourceCompatibility`/`targetCompatibility` **25**, e qualquer JDK anterior falha com `error: invalid source release: 25`.

### Ache o JDK 25 antes de compilar — não presuma o caminho

**Este repositório é usado em mais de uma máquina, e elas não têm os mesmos JDKs.** Em algumas o JBR que acompanha o Android Studio já é 25; em outras esse mesmo JBR é 17 e quem tem o 25 é um JDK instalado à parte, em `C:\Program Files\Java`. Um caminho fixo que funcionou numa máquina falha na outra, e o erro (`invalid source release: 25`) parece problema do código.

Por isso: **detecte o caminho a cada sessão**, mesmo que uma anterior tenha funcionado com um valor fixo, e mesmo que este arquivo cite um caminho específico. `JAVA_HOME` não fica definido no ambiente do usuário — precisa ser exportado na sessão antes de chamar o wrapper.

```powershell
# Procura um JDK 25 entre o JBR do Android Studio e o que houver em C:\Program Files\Java.
$candidatos = @("C:\Program Files\Android\Android Studio\jbr") +
              (Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue).FullName

$env:JAVA_HOME = $candidatos |
    Where-Object { (Get-Content "$_\release" -ErrorAction SilentlyContinue) -match 'JAVA_VERSION="25' } |
    Select-Object -First 1

if (-not $env:JAVA_HOME) { throw "Nenhum JDK 25 encontrado. Procurados: $($candidatos -join '; ')" }

$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
"JAVA_HOME = $env:JAVA_HOME"
```

A versão sai do arquivo `release` da própria instalação (`JAVA_VERSION="25.0.4"`), que todo JDK tem. É de propósito **não** usar `java -version`: no PowerShell 5.1 essa saída vai para stderr, e `2>&1` sobre executável nativo embrulha cada linha num `ErrorRecord` — o teste passa a falhar pelo motivo errado.

`ANDROID_HOME` entra no mesmo bloco porque tem o mesmo problema: não há `local.properties` versionado, e sem ele o build falha com `SDK location not found`.

No Android Studio o JDK é controlado por `.gradle/config.properties` (`java.home`), que **não** é versionado — cada máquina aponta para o seu, e é por isso que o IDE compila numa máquina onde a linha de comando falha.

### Comandos do wrapper

```powershell
.\gradlew.bat assembleDebug          # APK debug -> app/build/outputs/apk/debug/app-v<versionCode>-debug.apk
.\gradlew.bat assembleRelease        # APK assinado com key.jks -> app/build/outputs/apk/release/app-v<versionCode>-release.apk
.\gradlew.bat testDebugUnitTest      # testes JVM
.\gradlew.bat testDebugUnitTest --tests "com.vacari.gerupreco.util.StringUtilTest"   # um teste só
```

Não há lint configurado além do padrão do AGP, nem testes instrumentados reais (`ExampleInstrumentedTest` é o esqueleto gerado).

### A rotina do servidor tem comandos próprios

`functions/` é Node, não Gradle, e não passa pelo JDK acima. Ela roda num **servidor doméstico**, por container ou por cron — o porquê está em *De onde a rotina sai importa mais que como ela roda*. Detalhes em [functions/README.md](functions/README.md).

O nome da pasta é herança de quando isto era uma Cloud Function. Renomear quebraria o clone e o container que já estão de pé no servidor, então ficou.

```powershell
npm --prefix functions install
npm test                              # roda sem rede e sem Firestore
npm --prefix functions run once:dry   # rodada simulada: le, nao grava, nao envia
npm --prefix functions run build      # imagem docker "geruprecotracking"
npm --prefix functions run docker     # sobe o container, expondo :3456
```

O `docker` expande `$GERUPRECO_CREDENTIALS` no `-v`, então essa variável precisa apontar para o JSON da conta de serviço antes de chamá-lo.

A CLI do Firebase está presa a uma versão no `package.json` da raiz — instalada no repositório, não na máquina, pelo mesmo motivo do JDK: as máquinas divergem. A primeira execução dela trava esperando resposta sobre coleta de dados de uso quando a saída não é um terminal — parece pendurada na rede. `$env:FIREBASE_CLI_DISABLE_ANALYTICS = "1"` e `--non-interactive` resolvem.

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

O Firestore também guarda o cadastro do rastreamento de preços (`tracking`, `trackingGroup`, `device`), lido pela rotina no servidor — ver *Rastreamento de preços*. Essa rotina é o único código do repositório que não é Android: vive em `functions/`, em Node, e é a única coisa que escreve nessas coleções sem passar pelo app.

Existiu um esqueleto de preço-alvo em SQLite (`Notification`, `NotificationRepository`, `NotificationActivity`) que **nunca chegou a funcionar**: não havia agendador nenhum no projeto, e a tela era inalcançável pela UI. Foi removido na v20, substituído pelo rastreamento. Se aparecer referência a ele em código ou commit antigo, é isso.

### Fluxo das telas

`MainActivity` → `LowestPriceProduct` (lista de produtos) → `LowestPriceActivity` (preços de um produto).
`MainActivity` → `TrackingActivity` (rastreamento de preços).
`MainActivity` → `SimpleProportionActivity` (regra de três).
`LowestPriceProduct` → `CartActivity` (carrinho) → `CartCompareActivity` (comparador, duas abas).

### Preços de um produto (`LowestPriceActivity`)

Lista as ofertas de um único GTIN, com os mesmos chips de janela de data das abas do carrinho (`PriceWindow`). A tela guarda a resposta inteira da API e recorta em memória — trocar o chip reordena na hora, sem consulta nova, pelo mesmo motivo descrito na aba Mercados.

- **A preferência da janela é uma só para o app todo** (`PriceWindow.load/save`, `SharedPreferences` `cart_compare`). Ver um preço listado numa tela e sumido na outra passaria impressão de resultado inconsistente.
- **`PriceOffers.arrange` é lógica pura e testada** (`PriceOffersTest`): filtra pela janela e ordena por preço crescente e, no empate, data decrescente. O empate é a regra aqui, não a exceção — a lista é o histórico de notas do mesmo GTIN, e a mesma loja aparece várias vezes pelo mesmo valor; sem o desempate a nota de duas semanas atrás ficava na frente da de ontem. Preço ilegível e registro sem data não somem da lista, vão para o fim.
- **A flag `loaded` segura o aviso de vazio**, pelo mesmo motivo do `isLoaded()` das abas: antes da consulta voltar, vazio é falta de dado, não falta de oferta. Com a janela em "Qualquer data" o aviso troca de texto — mandar afrouxar um filtro que já está aberto manda procurar no lugar errado.
- **`PriceWindow.revealSelected` rola a fila até o chip marcado ao montar.** A fila não cabe na largura do aparelho e a janela guardada costuma ser uma das últimas; sem isso a tela abre mostrando só chips apagados, o que se lê como "nenhuma janela escolhida". Vale para as abas do carrinho também.

## Carrinho de compras

Produtos entram por long press na lista (`Adicionar ao carrinho`), **arrastando a linha para a direita** (`SwipeToCart`), ou em lote pelo `AddByTagDialog` (`Adicionar por tag`, no menu da própria `CartActivity` — o catálogo vem do Firestore, então o diálogo só abre depois da consulta). O ícone na action bar da lista traz um badge com o total de **unidades**, não de linhas.

### Arrastar para os dois lados, e a marca de canto

Arrastar a linha para a direita adiciona ao carrinho; para a esquerda, tira. Os dois gestos são atalhos do long press e caem nos mesmos `addToCart(position)` / `removeFromCart(position)` da `LowestPriceProduct` — a `SwipeToCart.Host` que a tela monta só acrescenta o `notifyItemChanged`.

- **Remover só é liberado no produto que já está no carrinho.** `getSwipeDirs` consulta `Host.isInCart`; num produto que não está lá o card simplesmente não cede para a esquerda, e essa resistência diz "não há o que remover" sem precisar de texto.
- **Remover tira a linha inteira, com a quantidade que tiver.** A lista marca presença, não quantidade; quem ajusta unidade é a tela do carrinho. Pelo mesmo motivo o long press mantém "Adicionar ao carrinho" visível mesmo no produto já presente — ali ele soma uma unidade, como o arrasto para a direita — e só acrescenta "Remover do carrinho" quando há o que remover.
- **O `ItemTouchHelper` supõe que a linha some depois do swipe**, e aqui o produto continua no catálogo. Sem o `notifyItemChanged(position)` que o `Host` faz, o card fica parado fora da tela.
- **O fundo é desenhado no canvas**, em `onChildDraw`, e não por uma view atrás do card: o item é um `MaterialCardView` solto no `RecyclerView`, e um fundo real exigiria envolver cada linha num container só para isso. O recorte é na área revelada, mas o retângulo arredondado cobre a linha inteira — senão o canto redondo acompanharia o dedo pelo meio do card.

O limiar é 35% da largura, abaixo do padrão de 50%, porque a lista é usada de pé no mercado com uma mão só. Vale lembrar que **o gesto tem que começar longe das bordas laterais**: encostado nelas, a navegação por gestos do sistema captura como "voltar".

#### As duas marcas de canto

O card carrega duas marcas triangulares independentes, uma em cada canto superior:

| canto | marca | drawable | cores |
| --- | --- | --- | --- |
| **direito** | está no carrinho (`item_in_cart`) | `ic_cart_corner_mark` | `primary_container` + `on_primary` |
| **esquerdo** | preço rastreado (`item_tracked`) | `ic_tracking_corner_mark` | `secondary` + `on_secondary` |

As cores do carrinho repetem o fundo do arrasto para a direita — a marca é o resultado visível daquele gesto.

**Os cantos são opostos porque os estados são independentes.** Um produto pode estar no carrinho, rastreado, ou nas duas coisas, e nesse caso as duas marcas aparecem juntas. Empilhar as duas no mesmo canto obrigaria uma a esconder a outra, e a informação que some é justamente a que diferencia as linhas.

- **As marcas desenham por cima do conteúdo**, então o cabeçalho recebe `corner_mark_inset` de margem **de cada lado que estiver marcado** — à direita para o chip de tamanho/unidade não ficar sob a marca do carrinho, à esquerda para a descrição não ficar sob a de rastreado. Os dois recuos são calculados separadamente no bind; um só, aplicado aos dois lados, encolheria o cabeçalho à toa no caso mais comum, que é ter uma marca só.
- **O raio do triângulo é o `radius_lg` do card** (16). Mudar um sem o outro deixa a marca desalinhada da borda arredondada. O arco da marca esquerda é o mesmo, com o sentido de varredura invertido (`sweep 0`).
- **Os glifos vivem dentro de um `<group>` do vetor**, encolhidos e deslocados para caber no triângulo: o que sair dele é desenhado sobre o fundo escuro do card e some.
- **As escalas dos dois glifos são diferentes de propósito** (0,55 no carrinho, 0,75 na seta). A seta do `ic_trending_down_24` é um traço baixo e vazado; no mesmo fator ela se lê como menor, e as duas marcas ficam visivelmente desiguais. O que precisa casar é o peso visual dentro do triângulo, não o número.
- **A borda do card responde só ao carrinho** (`outline_variant` → `primary_container`). É ela que faz o produto no carrinho saltar quando a lista é percorrida de relance; se mudasse também por rastreamento, passaria a significar "tem alguma marca", que não ajuda a decidir nada no mercado.
- **Quem sabe o estado é o adapter, não o banco.** `ItemAdapter.setCartBarCodes` e `setTrackedBarCodes` guardam os códigos de barras em dois `Set` separados. A Activity refaz o do carrinho no `onResume` e a cada `onCartChanged` — inclusive na volta da `CartActivity`, que pode ter esvaziado tudo. Consultar o SQLite dentro do `onBindViewHolder` seria uma query por linha rolada, e para o rastreamento seria pior: a lista vive no Firestore.
- **`loadTracking()` roda no `onResume`, não no `onCreate`.** O rastreamento pode ser apagado na `TrackingActivity`, e a lista continuaria marcando um produto que ninguém mais vigia. É uma leitura do Firestore por retomada da tela, sobre uma coleção de poucos documentos — o mesmo motivo pelo qual o carrinho é remarcado ali.

O `CartItem` guarda cópia de descrição/tamanho/unidade em vez de referenciar o `Item` do Firestore: a tela monta sem rede, e excluir o produto do catálogo não deixa linha órfã. Adicionar um produto já presente **incrementa a quantidade** em vez de duplicar a linha.

### O comparador e suas duas abas

O botão **Comparar** do rodapé é a única entrada. Ele abre a `CartCompareActivity`, que hospeda duas abas num `ViewPager2` sobre o mesmo carrinho e os mesmos preços:

| Aba | Ranqueia | Legenda | Lógica |
| --- | --- | --- | --- |
| **Mercados** | estabelecimentos | "Onde o carrinho inteiro sai mais barato" | `CartCompare` / `MarketQuoteFragment` |
| **Produtos** | ofertas entre si | "Qual produto rende mais por quilo ou litro" | `CartUnitPrice` / `UnitPriceFragment` |

Antes eram duas telas — a segunda escondida atrás de um ícone mudo na action bar do carrinho — e se confundiam: os dois nomes ("Comparar", "Custo-benefício") descreviam igualmente bem qualquer uma das duas. **As abas são nomeadas pelo substantivo do que cada uma ranqueia**, que é exatamente onde diferem; a legenda logo abaixo completa a frase e troca junto com a aba. Renomear as abas para algo genérico devolve a confusão.

Consequências do desenho que valem preservar:

- **A consulta é do host, não das abas.** `CartCompareActivity` chama o `CartPriceLoader` uma vez e guarda `cartItems`/`prices`; as abas só leem e ordenam. Como as duas consomem exatamente os mesmos GTINs, duas telas separadas faziam a mesma consulta duas vezes, com dois `ProgressDialog` seguidos. Não mover o carregamento para dentro de um fragment.
- **As abas se registram no host** (`registerTab`/`unregisterTab` em `onAttach`/`onDetach`) e ele chama `render()` nas que estiverem vivas. É de propósito não usar `findFragmentByTag("f" + position)`: essa tag é detalhe interno do `FragmentStateAdapter`.
- **`isLoaded()` segura o aviso de vazio.** Antes da consulta voltar o resultado está vazio por falta de dados, não por falta de oferta — sem a guarda, a aba pisca "nenhum estabelecimento tem os produtos".
- **A raiz de cada fragment é um `FrameLayout` que não rola.** Quem tem `fitsSystemWindows` é a raiz da Activity; promover o `RecyclerView` a raiz do fragment reabre a armadilha de insets descrita mais abaixo.
- **Fragments existem só aqui.** O resto do app é Activity pura com `findViewById`; `androidx.fragment` e `androidx.viewpager2` entraram no `build.gradle` por causa destas abas.

#### Filtro de mercado

O ícone na action bar abre um diálogo com dois campos encadeados — **Mercado** e **Endereço** — mais **Limpar** e **Aplicar**. O filtro vale para as duas abas, como a janela de datas, e o que está valendo aparece escrito acima do pager.

- **O recorte é do host, e acontece antes das abas lerem.** `CartCompareActivity` guarda a resposta inteira em `allPrices` e devolve em `getPrices()` o mapa já filtrado. Por isso `CartCompare` e `CartUnitPrice` não sabem que o filtro existe: continuam recebendo preços por código de barras, só que menos. Levar o filtro para dentro delas duplicaria a regra nas duas.
- **Identidade é `estabelecimento.codigo`, nunca o nome.** O nome só agrupa a primeira escolha, e é por isso que existe o segundo campo: há três lojas chamadas "MUFFATAO". "Todos os endereços" resolve para o conjunto de códigos das filiais daquele nome — é o único caso em que o nome vale sozinho.
- **A lista de mercados sai da resposta inteira, sem recortar pela janela de datas.** Se respeitasse a janela, a lista mudaria a cada troca de chip e um mercado já escolhido poderia sumir dela continuando aplicado.
- **`MarketFilter.apply` preserva as chaves sem oferta.** O produto que a loja filtrada não vende continua no mapa com lista vazia, e por isso segue aparecendo como faltante em vez de sumir da comparação.
- **Com filtro ativo os textos mudam** — o aviso de vazio e o de "sem preço". "Em nenhum estabelecimento" passaria a ser falso: os outros mercados podem ter o produto, só não estão sendo olhados. Mandar afrouxar a janela de datas seria mandar procurar no lugar errado.
- **A ação só aparece depois da consulta** (`onPrepareOptionsMenu` + `invalidateOptionsMenu` no retorno): a lista de mercados sai das ofertas que voltaram.
- **Reabrir o diálogo mostra o filtro que está valendo.** Sem restaurar a seleção, ele voltaria no primeiro mercado da lista e "Aplicar" trocaria o filtro sem o usuário ter escolhido nada. O `Spinner` reavisa a seleção restaurada, e a guarda por nome em `MarketFilterDialog` impede que esse reaviso jogue o endereço de volta para "todos".
- **`MarketFilter` é lógica pura e testada** (`MarketFilterTest`).

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
- **A unidade da lista é a oferta — um produto num estabelecimento —, não o produto.** O mesmo item aparece uma vez por mercado que o vende, e todas as ofertas concorrem entre si pela posição. Guardar só o menor preço de cada produto respondia "qual produto rende mais", mas escondia por quanto ele sai nos outros mercados, que é o que decide onde comprar.
- **Uma linha por estabelecimento, com o menor preço dele.** O mesmo mercado aparece várias vezes na resposta — uma por nota registrada, e mais uma quando o GTIN está cadastrado com e sem o zero à esquerda. Sem esse agrupamento a lista viraria o histórico de notas do produto, com a mesma loja repetida em datas diferentes. Agrupa por `estabelecimento.codigo`, nunca por nome, pelo mesmo motivo da aba Mercados.
- **A quantidade do carrinho não entra na conta.** Seis latas não mudam o preço do litro.
- **Peso e volume caem na mesma lista ordenada.** São grandezas diferentes e comparar R$/kg de arroz com R$/L de sabão não diz nada sozinho; a lista única foi pedida assim, e cada linha carrega o rótulo da unidade.
- **Tamanho inválido sai como `unmeasured`, separado dos `unpriced`.** A ação é diferente: um se resolve no cadastro, o outro abrindo a janela de datas. Somar os dois mandaria o usuário procurar no lugar errado.
- **`UnitMeasureUtil` repete a convenção do `PriceUtil`**: o ponto é decimal e só vira separador de milhar quando há vírgula na string. Sem isso `1.5 L` viraria 15 L. Como `size` é texto livre, a leitura também tolera `"500g"` e descarta o que não tiver número.
- **`PriceWindow` monta os chips de janela de data uma vez, na Activity**, acima do pager: a janela vale para as duas abas, e trocá-la redesenha ambas. A preferência em `SharedPreferences` sobrevive de dentro da época das duas telas separadas — hoje ela só serve para lembrar a escolha entre visitas.

### Preço vem como texto com ponto decimal

`PriceUtil.parse` trata `"4.50"` como 4,50. Aplicar a regra pt-BR (ponto = milhar) transformava `3.11` em `311` e os totais saíam cem vezes maiores — o bug passou por revisão de código e só apareceu no aparelho. A vírgula só é considerada separador decimal quando de fato aparece na string.

### A Nota Paraná devolve dados forjados quando acha que é raspagem

Não responde erro: **troca a resposta inteira por registros gerados**, no mesmo formato do JSON legítimo. Descrições e nomes de loja saem embaralhados (`BJLACJA REKEADG TRAKIRAS DORANGJ`, `TEEA DOS ALSIMENTOOS`) e os preços são aleatórios. Foi isso que pôs uma cerveja de 355 ml a R$ 0,54 no topo da aba Produtos.

`DecoyFilter.clean` roda dentro de `RetrofitRequest.searchLowestPrice`, o único funil das duas telas — nenhuma delas tem como saber que a resposta veio forjada. A separação foi medida sobre 1880 registros baixados da API (1486 legítimos, 394 forjados):

| campo | legítimo | forjado |
| --- | --- | --- |
| `estabelecimento.codigo` | 43 chars, com maiúsculas | 81–90 chars, só minúsculas |
| `local` | geohash da loja (11) | ecoa o da consulta (9) |
| `nm_fan` | preenchido em 55% | sempre vazio |
| `uf` | sempre PR | PR, PE e ES misturados |

A regra descreve o registro **forjado**, não o legítimo, de propósito: se o formato do identificador mudar, o filtro para de reconhecer a falsificação em vez de descartar o catálogo inteiro como suspeito.

Duas consequências que atrapalham o diagnóstico:

- **A resposta envenenada vem inteira forjada**, nunca misturada. Depois da limpeza sobra lista vazia e o produto aparece como "sem preço" — o que é o desejado, mas é indistinguível de produto sem oferta.
- **A aba Mercados escondia o problema.** Cada loja forjada carrega um único produto, então cai no grupo dos incompletos e afunda no ranking; a aba Produtos ordena as ofertas só pelo preço por unidade, e o valor absurdo ia direto para o primeiro lugar. Divergência entre as duas abas sobre o mesmo carrinho é sintoma disso.

O gatilho é volume de requisições por IP, e a punição gruda: uma vez marcado, **toda** consulta volta forjada por um bom tempo, inclusive as sequenciais. O carrinho dispara uma por produto de uma vez, então é ele que atrai a marcação — a tela de produto único quase sempre escapa, e é por isso que a busca direta mostra preço são enquanto o carrinho não.

Daí o `RequestSpacer`, um interceptor no `RetrofitConfig` que garante 400 ms entre consultas. Medido contra a API com o mesmo conjunto de códigos de barras:

| | resultado |
| --- | --- |
| 9 consultas em paralelo | 5 × HTTP 503, e 1 das 4 restantes veio inteira forjada |
| 10 consultas a cada 400 ms | 168 registros, nenhum forjado, nenhum erro |

O estado do espaçador é estático porque o limite é por IP e o `RetrofitConfig` constrói um cliente HTTP novo a cada chamada. Ele dorme segurando o monitor de propósito — liberar durante a espera deixaria o carrinho sair em rajada de novo. A espera acontece antes do `proceed`, então os timeouts de conexão e leitura (6 s cada) só começam depois e a fila não provoca timeout.

**Espaçar não substitui o filtro**, porque não desfaz a marcação: uma vez punido, o IP recebe resposta forjada mesmo consultando devagar. O `DecoyFilter` é o que garante que preço inventado não chega na tela; o `RequestSpacer` só evita chegar naquele estado. O custo é o carrinho ficar mais lento — 20 produtos gastam 8 s só de espera.

Para conferir dados suspeitos por fora do app, a consulta é
`https://menorpreco.notaparana.pr.gov.br/api/v1/produtos?local=6g9fp8frx&offset=0&raio=20&data=-1&ordem=0&gtin=<gtin>`.
Poucas chamadas e espaçadas — repetir em rajada marca o IP e passa a devolver lixo, inclusive para o aparelho na mesma rede.

### Novidades da versão

`ChangelogDialog.showIfNeeded()` exibe o que mudou, uma única vez por `versionCode`, com o controle guardado em `SharedPreferences`. O texto vive em `res/values/changelog.xml`, em dois `string-array` paralelos (títulos e descrições), reescritos a cada release.

É chamado de dentro de `MainActivity.configureActions()`, **não** do `onCreate`, pelo mesmo motivo do gate abaixo: só faz sentido mostrar novidades depois que a versão foi validada. A preferência é gravada **antes** de exibir o diálogo, porque o listener do Firestore pode chamar `configureActions()` mais de uma vez e empilharia diálogos.

### Gate de versão — não é bug

`MainActivity.configureActions()` **não** é chamado no `onCreate`. Quem chama é `UpdateJob.checkVerisonCode()`, e só quando a versão instalada está em dia com o documento `appVersion` do Firestore. Estando desatualizada, aparece o diálogo de atualização e os cards da tela inicial permanecem inertes. Isso é intencional: bloqueia o uso do app em versões antigas. **Não "conserte" adicionando a chamada no `onCreate`.**

Pelo mesmo motivo os cards em `activity_main.xml` não declaram `android:clickable="true"` — se declarassem, dariam feedback de toque enquanto ainda bloqueados.

## Rastreamento de preços

Lista de produtos vigiados com preço-alvo. Quando o preço cai até o alvo, os aparelhos são notificados por push.

**A parte que roda no aparelho é só o cadastro.** Quem consulta a Nota Paraná, compara com o alvo e dispara a notificação é uma rotina agendada fora do app, às **09:00, 12:00, 15:00 e 18:00**. Por isso não há nenhuma consulta de preço na `TrackingActivity`: o que a tela mostra é exatamente o que a rotina vai usar na próxima rodada.

A rotina vive em **[`functions/`](functions/README.md)**, em Node — o único JavaScript do repositório.

### As três coleções

```
tracking/{id}         barCode, description, targetPrice, groupId, scope,
                      deviceId, active, lastNotifiedPrice, lastNotifiedAt, lastCheckedAt
trackingGroup/{id}    name, targetPrice, scope, deviceId, active
device/{ANDROID_ID}   name, fcmToken, lastSeen
```

- **`description` é cópia do catálogo**, não referência, pelo mesmo motivo do `CartItem`: a tela monta sem uma segunda consulta, e excluir o produto do catálogo não deixa linha órfã.
- **Preço é `Double` porque o Firestore não tem tipo decimal.** Toda leitura passa por `BigDecimal` antes de virar texto.
- **Grupo manda em alvo e alcance.** Um `tracking` com `groupId` ignora o próprio `targetPrice` e `scope` — quem vale é o do grupo. Os campos ficam desligados no diálogo, e não escondidos: sumir da tela faria parecer que o rastreamento perdeu o alvo, quando ele só passou a vir de outro lugar.
- **Quem resolve essa regra é `TrackingPlan`, e mais ninguém.** Ela já esteve reescrita em quatro lugares — a lista, o diálogo, o filtro de visibilidade e a rotina do servidor — e bastou um deles divergir para a tela mentir: a lista mostrava o alvo do grupo (R$ 5,90) enquanto o diálogo do mesmo produto mostrava o alvo próprio, obsoleto (R$ 6,00), num campo desabilitado ao lado de um aviso dizendo que o alvo vinha do grupo. Nada na tela dizia qual dos dois o servidor usaria.
- **`TrackingPlan` não importa nada do Android de propósito**, e é isso que permite testá-la em teste de unidade (`TrackingPlanTest`) — a segunda metade da defesa. O equivalente no servidor é o `resolvePlan` do `trackingRules.js`: são duas linguagens, a duplicação ali é inevitável, mas as duas são cobertas por teste.
- **O produto guarda um `targetPrice` próprio mesmo dentro de um grupo**, e ele fica obsoleto de propósito — é o que ele reassume quando o grupo é removido (`releaseFromGroup`). Por isso a regra não é "apagar o próprio", e sim **nunca exibir um valor que não está em vigor**.
- **Apagar um grupo solta os produtos antes**, herdando o alvo que o grupo ditava (`TrackingRepository.releaseFromGroup`). Na ordem inversa, uma falha na segunda escrita deixaria produtos apontando para um grupo inexistente, sem alvo e sem aviso.
- **Entrar num grupo rearma o aviso** (`lastNotifiedPrice`/`lastNotifiedAt` a nulo), pelo mesmo motivo de mudar o alvo na mão: o alvo passou a vir do grupo, e o último preço avisado valia para o anterior. **O alvo e o escopo próprios não são apagados** — é o que o diálogo de edição já fazia, e é o que permite ao produto voltar com alvo próprio quando o grupo for removido.

#### Grupo nasce pelo produto, não por um botão

Não há botão de "novo grupo". Grupo é sempre **um nome escrito**, com as grafias já usadas aparecendo como sugestão — **o mesmo arranjo do campo de tags**, e pela mesma razão: grupo aqui é um nome, não um cadastro que se abre antes de usar.

São dois campos, e os dois criam:

| onde | campo |
| --- | --- |
| `TrackProductDialog` — cadastro/edição do rastreamento | `AutoCompleteTextView` `track_group`, vazio = sem grupo |
| `AddToGroupDialog` — long press na tela de rastreamento | o diálogo inteiro é esse campo |

O `TrackProductDialog` usava um `Spinner`, e com ele **só dava para entrar em grupo que já existia**: criar um exigia sair do cadastro. Trocar por campo escrito é o que fecha esse buraco — e é a razão de o campo aceitar nome livre em vez de listar.

Antes existia um FAB no rodapé que criava o grupo vazio. Ele pedia que o usuário criasse primeiro e só depois lembrasse de voltar nos produtos para povoá-lo — e **grupo sem produto não faz nada**: nenhuma consulta, nenhum alerta, uma linha na lista que não significa coisa alguma.

- **Nome novo cria o grupo; nome existente reaproveita**, com deduplicação por nome normalizado e a grafia já cadastrada — igual às tags. Sem isso "Cervejas" e "cervejas" viveriam como dois grupos, cada um com o próprio alvo, e nada na lista denunciaria a duplicata.
- **O grupo novo nasce com um alvo, sempre.** Nascer sem ele deixaria o produto mudo: dentro do grupo quem manda é o alvo dele, e o produto sairia da rodada do servidor sem nada na tela explicando por que parou de avisar. De onde vem o alvo depende do campo: no `AddToGroupDialog` é o do produto que criou (o diálogo só pede o nome); no `TrackProductDialog` são os campos de alvo e alcance da própria tela.
- **No `TrackProductDialog` o campo de grupo tem três estados**, e o que muda entre eles é de quem são o alvo e o alcance: **vazio** → do produto; **nome que já existe** → do grupo, e os campos ficam desligados; **nome novo** → os campos seguem ligados e o que estiver neles vira o alvo e o alcance do grupo a ser criado. O texto sob os campos troca junto e diz qual dos três está valendo — sem ele, campo ligado e campo desligado seriam a única pista.
- **O produto guarda alvo e alcance mesmo entrando num grupo novo.** É o que permite a ele voltar com alvo próprio quando o grupo for removido, em vez de ficar sem nenhum.
- **O grupo é gravado antes do produto.** Na ordem inversa o produto ficaria com um `groupId` de algo que talvez não chegasse a existir. Se a criação do grupo falhar, o rastreamento é salvo **sem** grupo em vez de se perder: ele tem alvo próprio e continua avisando, e o aviso diz o que faltou.
- **A ação só aparece quando o produto ainda não está num grupo.** Num produto já agrupado ela ofereceria "adicionar" ao que já está dentro; trocar ou sair do grupo é pelo `Spinner` do diálogo de edição.
- **A sugestão abre em dois gatilhos — clique e foco** (`setOnClickListener` + `setOnFocusChangeListener` com `post`). O primeiro toque num campo sem foco só pede o foco: o clique não chega, e a lista não abria justamente na primeira vez, que é quando o usuário mais precisa ver o que já existe. O `post` é necessário porque no instante do foco a janela do popup ainda não tem onde se ancorar.
- **`TrackingGroupDialog` só edita, nunca cria.** Sem o FAB, nenhum grupo sem id chega nele — por isso o título é fixo, o botão de remover está sempre presente e a gravação é sempre `update`. Manter os ramos de "novo" faria um leitor supor que ainda se cria grupo por ali.

### `lastNotifiedPrice` é a regra inteira de "uma vez por queda"

Nulo significa **armado**. O servidor notifica quando o menor preço fica abaixo do alvo e ou o campo está nulo, ou o preço caiu ainda mais que o já avisado; e volta o campo a nulo assim que o preço sobe acima do alvo, rearmando para a próxima queda. Sem isso, quatro rodadas por dia repetiriam o mesmo aviso enquanto a promoção durasse, e o usuário desligaria as notificações do app.

O app zera esse campo em dois pontos, e os dois importam: ao **mudar o alvo** (o preço já avisado valia para o alvo anterior) e ao **retomar um rastreamento pausado** (enquanto pausado o preço pode ter subido e caído de novo).

### Escopo: geral e particular

O app **não tem autenticação**, então "notificar um usuário específico" só pode significar "notificar um aparelho". A identidade é o `Settings.Secure.ANDROID_ID` (`DeviceIdentity`), escolhido por sobreviver a atualizações e reinstalações — some só em reset de fábrica, quando o aparelho de fato virou outro. O token do FCM **não serve** para isso: muda sozinho.

- `GERAL` → o servidor publica no tópico **`geral`**, e todo aparelho se inscreve nele na abertura. **Esse nome é contrato com o servidor**; mudar de um lado só deixa o alerta sem ninguém escutando.
- `PARTICULAR` → o servidor manda para o `fcmToken` do `device` correspondente.
- **Escopo desconhecido ou ausente conta como geral.** Alerta que chega a todo mundo incomoda; alerta que não chega a ninguém passa despercebido.

#### O seletor tem duas opções, e só pode ter duas

`TrackingScopes.options()` devolve **"Todos os aparelhos"** e **"Este aparelho"** — nada mais.

O seletor já listou todo aparelho registrado, com o nome de cada um, e havia "Renomear este aparelho" no menu da tela justamente para distinguir três "Redmi Note 12". Isso permitia criar daqui um alerta mirado no celular de outra pessoa, e quem recebia não tinha como saber de onde veio nem como desligar: o cadastro vive no aparelho que o criou. **Alcance só se escolhe para si ou para todos.**

O que caiu junto, e não deve voltar por engano:

- **A renomeação inteira** — o diálogo, o item de menu, `DeviceRepository.rename` e as strings. Sem lista de aparelhos não há o que nomear.
- **O cache de aparelhos** (`cached`/`searchAll`/`nameOf`) e o aquecimento dele em `TrackingRegistration`. Existiam só para escrever o rótulo do seletor.
- **A consulta de aparelhos na frente do `load()` da `TrackingActivity`.** Eram três encadeadas; hoje são duas, e os grupos continuam vindo antes dos produtos porque estes leem o alvo deles.
- **A coleção `device` continua sendo escrita**, e isso não é resíduo: o `fcmToken` gravado ali é como o servidor acerta um alerta particular. O que sumiu foi a leitura pelo app.

Uma consequência que confunde se pegar de surpresa: **o nome no Firestore ainda é o modelo de fábrica**, gravado por `DeviceRepository.register` só quando o documento não existe. Na tela o aparelho é sempre "Este aparelho"; o nome real fica para quem abrir o console conseguir dizer de qual celular é cada documento — "Este aparelho" repetido em toda linha não diria nada.

#### O que cada aparelho enxerga da coleção

`TrackingScopes.isVisible` é o outro lado da mesma regra, e mora no mesmo arquivo de propósito: escolher alcance e ver alcance são a mesma decisão lida das duas pontas, e separá-las deixaria uma mudar sem a outra.

- **Alerta geral aparece em todo aparelho, e qualquer um edita ou apaga.** Ele vale para todos, então não tem dono. É o que permite cadastrar no celular e ajustar no tablet.
- **Alerta particular só aparece no aparelho que ele acerta.** Noutro celular seria uma linha que a pessoa não pode desligar e cujo alerta ela nunca vai receber, e apagar por engano tiraria o aviso de quem depende dele.
- **Dentro de um grupo a visibilidade é a do grupo**, porque o alcance também é. Esconder o grupo e deixar os produtos dele na lista mostraria linhas com alvo vindo de um lugar invisível. Um produto **geral** dentro de um grupo particular de outro aparelho **não aparece** — o grupo é quem manda.
- **Grupo que sumiu do cadastro é caso à parte:** o produto órfão cai no próprio escopo, do mesmo jeito que a linha da lista cai no próprio alvo.
- O filtro roda nas **duas** telas que leem o cadastro — `TrackingActivity` e o `loadTracking()` da `LowestPriceProduct`, que alimenta a marca de canto e o "já rastreado" do long press. Por isso o `loadTracking()` da lista de produtos encadeia grupos antes de produtos, em vez de disparar os dois em paralelo.

**Isso é recorte de tela, não de segurança.** As regras do Firestore são públicas: qualquer um com a chave do repositório lê e escreve a coleção inteira. O filtro existe para a lista não mostrar o que não dá para operar dali.

Por isso não existe mais o rótulo "outro aparelho": o que ele nomearia nunca chega na tela.

### A janela é de 24 horas, e os dados atrasam um dia

A rotina só considera ofertas das últimas 24 horas. Medido na API em 28/08/2026, sobre dois produtos do catálogo: **nenhuma nota do próprio dia aparece** — o lote de ontem é o mais novo que existe (Omo líquido: 3 ofertas de ontem, 12 em 13 dias; filtro Melita: 10 de ontem, 20 em 13 dias).

Duas consequências: o alerta fala do **preço de ontem**, não do de hoje; e produto de giro lento passa dias sem oferta nenhuma na janela, o que é **silêncio, não erro**. Sem esse recorte, uma nota barata de meses atrás dispararia alerta para sempre.

### De onde a rotina sai importa mais que como ela roda

**A Nota Paraná devolve dados forjados para IP de datacenter, e não é limite de volume.** Medido em 30/08/2026, com a função implantada em `southamerica-east1`:

| origem | resultado |
| --- | --- |
| Cloud Function, na época (IP do Google) | 100% forjado em **todas** as rodadas — `sobraram 0` em todo GTIN |
| máquina de casa (IP residencial) | 11 e 43 registros, **zero** forjados, no mesmo minuto |
| servidor de casa, em 31/08 | `0 registro(s) forjado(s)`, e os dois alertas esperados |

O detalhe que fecha o diagnóstico: **a primeira rodada registrada já veio inteira forjada**, no horário das 18:00 do agendamento original, antes de qualquer volume acumulado. Não foi raspagem detectada por frequência — é o tratamento dado à origem.

Por isso a rotina **roda num servidor doméstico**. Consultar dali é usar a API como o próprio app já faz todo dia na tela do carrinho.

São **dois pontos de entrada sobre o mesmo `src/run.js`** — muda só de onde vem a credencial (um JSON de conta de serviço apontado por `GOOGLE_APPLICATION_CREDENTIALS`) e quem agenda:

| entrada | quem agenda | uso |
| --- | --- | --- |
| `server.js` | ele mesmo, por dentro | é o que o container roda |
| `run-once.js` | cron do sistema | uma rodada e sai |

**O container roda o `server.js`, e não o `run-once.js`.** Um container que executa e termina, com `--restart=unless-stopped`, reinicia em laço fechado — e cada volta é uma consulta a mais na Nota Paraná. Dentro do container não há cron, então o processo fica de pé e se agenda sozinho (`src/schedule.js`, lógica pura e testada, com o offset fixo de Brasília pelo mesmo motivo do `message.js`: o alpine pode não ter `tzdata`).

**A porta 3456 é o `/health`**, e não enfeite: um serviço que só escreve em log é invisível de fora do container, e "não chegou alerta nenhum" continuaria sem resposta. Ele devolve a última rodada, o resumo dela e a próxima. Há também um `POST /run`, **desligado por padrão** — disparar uma rodada envia push de verdade, e a porta pode acabar encaminhada para fora sem ninguém lembrar; ligar exige definir `TRACKING_TOKEN`.

**A credencial não entra na imagem.** É montada em tempo de execução (`-v`, e o `npm run docker` já faz isso). Copiada com `COPY`, ficaria numa camada legível por quem tiver a imagem — e ela dá escrita no Firestore e permissão de enviar push. Não é a mesma coisa que a chave de API do `google-services.json`, que é pública por desenho.

Duas consequências que valem preservar:

- **A saída do `run-once.js` tem código 3 reservado** para "tudo voltou forjado". Sem ele, IP marcado e "nenhum produto atingiu o alvo" terminam os dois em silêncio, e é o cron que precisa perceber a diferença.
- **Não se resolve isso com proxy residencial ou rotação de IP.** A API está deliberadamente entregando dado falso para datacenter; contornar isso é burlar o controle antiabuso de um serviço público. Rodar da rede de casa não é contorno — é o uso normal.

O `DecoyFilter` e o espaçamento de requisições foram **portados do app** (`functions/src/decoyFilter.js`, `functions/src/requestSpacer.js`) e continuam valendo em qualquer origem, porque a marcação por volume também existe e não some quando o IP é bom. Lá o filtro **registra em log quando descarta**: se o IP for marcado, tudo volta forjado, a limpeza esvazia a resposta e o sintoma é nenhum alerta disparar — silêncio idêntico ao de "nenhum produto atingiu o alvo". É o número que se olha primeiro na linha de resumo da rodada.

**Duas rodadas nunca se sobrepõem:** o `server.js` recusa uma nova enquanto a anterior não terminou (`state.running`). O espaçador é estado de processo, não limite global, então duas rodadas ao mesmo tempo dobrariam a taxa contra a API. Nenhum alerta se perde numa rodada recusada — `lastNotifiedPrice` só é gravado depois do envio confirmado, e a próxima rodada reavalia tudo.

### O que a rotina decide, e o que fica de fora

`functions/src/trackingRules.js` é lógica pura e testada, como o `CartCompare` do carrinho: recebe o rastreamento, o grupo e as ofertas, e devolve se há alerta e que estado gravar. `run.js` faz a rodada em volta dela.

- **Erro de consulta não é "sem oferta".** Produto cujo GTIN falhou fica sem ser avaliado e sem `lastCheckedAt` novo. Tratar falha como lista vazia rearmaria alertas já avisados, e o mesmo aviso voltaria na rodada seguinte.
- **Envio falho não grava `lastNotifiedPrice`.** O alerta continua armado e a próxima rodada tenta de novo; gravar antes perderia o aviso em silêncio.
- **Alerta particular sem token não vira alerta geral.** Aviso destinado a um aparelho chegando em todos é pior que aviso nenhum, então fica sem enviar — o token reaparece assim que o app for aberto naquele aparelho. É o único caso em que escopo particular não cai no `geral`.
- **A gravação é campo a campo (`update`), nunca o documento inteiro.** A tela pode ter mudado alvo, escopo ou descrição enquanto a rodada consultava a API, e um `set` com os dados lidos no começo desfaria a edição do usuário.
- **O gatilho HTTP vem desligado.** O `POST /run` do `server.js` só funciona com `TRACKING_TOKEN` definido: disparar uma rodada envia push para todos os aparelhos, e a porta 3456 pode acabar encaminhada para fora sem ninguém lembrar. Para uma rodada avulsa sem push, `docker exec geruprecotracking node run-once.js --dry-run`.

### Detalhes de UI que custaram decisão

- **Os cards da tela inicial viraram faixas horizontais** (ícone à esquerda, texto à direita, `module_icon_size_compact`). O bloco alto com o glifo empilhado não cabia três vezes na tela, e rolar para escolher módulo esconderia justamente o que a tela oferece.
- **Long press num produto já rastreado abre o cadastro existente**, em vez de criar outro. Duas linhas do mesmo código de barras renderiam duas notificações na mesma queda, e nada na lista denunciaria a duplicata.
- **`TrackingActivity` encadeia três consultas de propósito**: aparelhos antes de tudo (senão a linha de um alerta particular mostraria o `ANDROID_ID` cru no lugar do nome), depois grupos, depois produtos — que leem o alvo dos grupos.
- **A flag `loaded` segura o aviso de vazio**, pelo mesmo motivo das abas do carrinho.
- **`POST_NOTIFICATIONS` é permissão de runtime a partir do Android 13.** Sem ela o alerta chega ao aparelho e morre em silêncio — de novo indistinguível de "nada atingiu o alvo". É pedida em `configureActions`, junto dos cards, porque numa versão bloqueada pelo gate o usuário não chega a usar nada disso.
- **`configureActions` pode ser chamado mais de uma vez** pelo listener do Firestore, então o registro e o pedido de permissão têm guarda (`trackingReady`). Sem ela, um segundo diálogo do sistema empilharia sobre o primeiro ainda sem resposta.
- **O id do canal de notificação vive em `strings.xml`** (`tracking_channel_id`). O manifesto precisa dele como canal padrão do FCM — é o caminho usado quando o sistema desenha a notificação sozinho, com o app fechado, sem passar pelo `GeruMessagingService`. Duas cópias soltas divergiriam.
- **A BoM do Firebase entrou junto com o Messaging.** As bibliotecas compartilham código interno e versões escolhidas a mão divergem com facilidade; a BoM 34.18.0 fixa firestore 26.6.0 (era 26.5.0, pinado a mão) e messaging 25.1.2.

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
- **Espaço entre um filtro fixo e a lista tem que ficar fora do container que rola.** Os chips de janela de data ficavam colados no primeiro card ao rolar: o `paddingTop` do `RecyclerView` não segura nada, porque com `clipToPadding="false"` o card sobe por dentro do próprio padding até encostar. A faixa vem de `layout_marginBottom` no `HorizontalScrollView` (tela de preços) e de `layout_marginTop` no `ViewPager2` (comparador) — 12dp nas duas, sempre fora da área que rola.
- **Diálogos não herdam `windowSoftInputMode` da Activity.** Têm janela própria; sem `getWindow().setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)` o teclado cobre os botões Salvar/Cancelar.
- **Activities com campo de texto precisam de `android:windowSoftInputMode="adjustResize"`** no manifesto, ou o teclado cobre FAB e conteúdo.
- **`Spinner` precisa de largura folgada.** O padding da seta consome ~55dp; com pouco espaço, unidades de duas letras (`ML`, `KG`) simplesmente deixam de ser desenhadas enquanto as de uma letra (`G`, `L`) aparecem. Atenção especial ao trocar `layout_width="match_parent"`+peso por `0dp`+peso — a distribuição de largura resultante é bem diferente.
- **`SearchView` reexibe o teclado ao reassumir o foco.** Ao fechar um diálogo ou voltar de outra tela, o teclado volta sozinho. `LowestPriceProduct.clearSearchFocus()` é chamado antes de cada sobreposição; manter esse cuidado ao adicionar novas ações na lista.
- **A action bar da lista comporta 3 ações.** Um item com `showAsAction="never"` cria o botão de overflow e **empurra o scanner de código de barras para dentro dele**. Foi o que aconteceu ao pôr "Adicionar por tag" ali; por isso essa ação mora na `CartActivity`.
- **Item de menu com `actionLayout` não passa por `onOptionsItemSelected`.** O ícone do carrinho precisa de `setOnClickListener` na própria action view.
- **`Chip` com cor de fundo fixa não mostra seleção.** `setChipBackgroundColorResource` aplica a mesma cor a todos os estados; é preciso um `ColorStateList` com `state_checked` (ver `res/color/chip_window_background.xml`).

## Verificação em dispositivo

A verificação é feita num aparelho físico por adb sobre Wi-Fi. Como não existe emulador instalado nem suíte de testes de UI, **a verificação real é dirigir o app por adb**. Isso já pegou defeitos que passariam despercebidos numa leitura de código.

**Nem sempre é o mesmo aparelho, nem sempre a mesma rede.** IP, portas e até o que o aparelho permite mudam de sessão para sessão — nada disso deve ser presumido do que funcionou antes, nem deste arquivo. Comece sempre por `adb devices`; se vier vazio, ver *Conectar o aparelho por Wi-Fi* abaixo.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-v<versionCode>-debug.apk"
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
- **Confirme se a injeção de eventos é permitida antes de planejar em cima dela**, com `input tap 1 1` num canto inerte: sai `exit=0` quando funciona e `SecurityException: INJECT_EVENTS` quando não. Aparelho Xiaomi só injeta com **Opções do desenvolvedor → Depuração USB (Configurações de segurança)** ligado, o que pede conta Mi e chip com dados — e essa opção some ao trocar de aparelho ou ao resetar as opções de desenvolvedor. Sem injeção, leitura (`uiautomator dump`, `screencap`) continua valendo e a saída é pedir para o usuário navegar enquanto se lê a tela.
- **Para flagrar algo que só existe durante um gesto** — o fundo revelado por um swipe, por exemplo —, encadeie o gesto e a captura numa chamada só, para o aparelho controlar o tempo: `adb shell "input swipe 200 625 520 625 2000 & sleep 1.4; screencap -p /sdcard/mid.png"`. Dois comandos adb separados não acertam a janela.

### Conectar o aparelho por Wi-Fi

**Não guarde IP nem porta de sessões passadas** — o aparelho pode ser outro, a rede pode ser outra, e as portas do adb sem fio mudam sozinhas. O IP atual quem tem é o usuário, na tela *Depuração sem fio*.

Pareamento e conexão usam **portas diferentes**: a do diálogo "Parear dispositivo com código de pareamento" só serve para o `adb pair`, e a de conexão é a de *Endereço IP e porta*, no topo da tela.

```powershell
& $adb pair <ip>:<porta-do-pareamento> <codigo-de-6-digitos>

# O mDNS padrao falha com "mdns daemon unavailable"; o backend interno resolve
$env:ADB_MDNS_OPENSCREEN = "1"
& $adb kill-server; & $adb start-server
& $adb mdns services        # descobre o IP:porta de _adb-tls-connect._tcp
& $adb connect <ip>:<porta-de-conexao>
```

- **Porta e código de pareamento expiram junto com o diálogo.** Fechar a tela invalida os dois, e reabrir gera outros. `adb pair` num par velho falha com `protocol fault (couldn't read status message)` — que parece problema de rede e é só validade vencida. Peça os dois valores de uma vez, com a tela aberta, e pareie na hora.
- **Porta TCP aberta não significa adb conectável.** `Test-NetConnection` pode dar `TcpTestSucceeded: True` na porta de *pareamento* enquanto `adb connect` recusa — foi assim que uma porta de pareamento passou por porta de conexão.
- **O `adb mdns services` pode listar mais de um `_adb-tls-connect._tcp` para o mesmo aparelho**, e a primeira linha não é necessariamente a viva (a outra costuma recusar a conexão). Vale tentar cada uma até `adb devices` mostrar `device`. E o mDNS só enxerga o aparelho depois do pareamento — antes disso a lista vem vazia, mesmo com tudo certo.
- **O aparelho de teste costuma ter a *release* instalada.** Instalar a debug por cima falha com `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (chaves diferentes), e desinstalar antes apagaria o carrinho em SQLite e as preferências. Para testar sem perder dados, gere e instale a **release** — reinstalar o mesmo `versionCode` por cima funciona e preserva os dados, sem precisar subir versão nem tocar no Firestore.
- **Testar mexe em dados de verdade.** O carrinho é o do usuário: um gesto de teste que adiciona produto fica lá depois. Vale avisar o que o teste deixou para trás em vez de limpar por conta própria.

## Publicar uma versão

Passo a passo completo em **[RELEASE.md](RELEASE.md)**. O resumo: subir `appVersionCode` no `app/build.gradle`, `assembleRelease`, copiar o APK para `app/release/`, commitar, **empurrar**, e só então apontar o documento `appVersion` do Firestore para o novo `versionCode` e URL.

A ordem importa: o Firestore é o gatilho de produção. Mudá-lo antes do APK estar acessível no GitHub deixa o app inutilizável, porque o gate de versão bloqueia a home e o download falha.

### O commit não termina a release — o Firestore termina

**Sempre que um commit subir o `appVersionCode`, atualize o Firestore na mesma sessão, logo depois do push, sem esperar que peçam.** Enquanto o documento `appVersion` apontar para o código anterior, a versão nova existe no repositório e não chega a aparelho nenhum: o `UpdateJob` só oferece atualização quando o `versionCode` do Firestore é maior que o instalado. O sintoma é silencioso — nada quebra, o app simplesmente continua na versão velha —, e foi exatamente assim que a v18 ficou commitada e empurrada por um dia sem estar publicada.

Isso vale só para commit que muda o `appVersionCode`. Commit que não mexe na versão não tem APK novo em `app/release/` e não tem o que publicar.

Antes de gravar, confirme as duas coisas que a *regra de ouro* do RELEASE.md protege — o push chegou ao `origin/main` e a URL do GitHub devolve um APK com o mesmo SHA-256 do arquivo local (passo 9). Só então o PATCH do passo 10, sempre com `updateMask.fieldPaths` e com o `versionCode` como `integerValue`.

```powershell
$v   = [int](Select-String -Path "app\build.gradle" -Pattern 'def appVersionCode = (\d+)').Matches[0].Groups[1].Value
$key = (Get-Content "app\google-services.json" -Raw | ConvertFrom-Json).client[0].api_key[0].current_key
$fs  = (Invoke-RestMethod "https://firestore.googleapis.com/v1/projects/gerupreco/databases/(default)/documents/appVersion?key=$key").documents[0].fields
"build.gradle=$v  firestore=$($fs.versionCode.integerValue)  url=$($fs.url.stringValue)"
```

Um `versionCode` menor que o do `build.gradle` nessa saída é o sinal de release pendente de publicação.

## Segurança

As regras do Firestore são públicas, e com o rastreamento isso passou a expor também os **tokens do FCM** na coleção `device`. Um token sozinho não permite enviar push (isso exige a credencial do servidor), então o risco é baixo — mas a lista de aparelhos e os produtos vigiados ficam legíveis por qualquer um com a chave da API, que está no repositório.

`key.jks` e `readmeKey.txt` (que contém a senha do keystore em texto plano) estão no repositório e **não** constam do `.gitignore` — e a senha também está no `signingConfigs` do `app/build.gradle`. Qualquer pessoa com acesso ao repo pode assinar builds como se fossem oficiais e, como o app instala APK de uma URL pública sem verificação extra, isso distribui código direto para os aparelhos. Vale rotacionar a chave, removê-los do versionamento e manter o repositório privado.
