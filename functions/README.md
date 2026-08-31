# Rotina de alertas de preço

A parte do módulo **Rastreamento** que roda fora do aparelho. O app só faz o
cadastro: quem consulta a Nota Paraná, compara com o alvo e dispara a
notificação é esta rotina, às **09:00, 12:00, 15:00 e 18:00** (horário de
Brasília).

> **Por que num servidor doméstico, e não na nuvem.** Isto começou como Cloud
> Function. Medido em 30/08/2026: a Nota Paraná devolveu **100% de registros
> forjados em todas as rodadas** saídas dela — inclusive na primeira, antes de
> qualquer volume acumulado — enquanto os mesmos GTINs consultados de um IP
> residencial no mesmo minuto voltaram íntegros. É o tratamento dado a IP de
> datacenter, não limite de requisições.
>
> No dia seguinte, o mesmo código no container de casa: **`0 registro(s)
> forjado(s)`** e os dois alertas esperados. A função foi apagada, e com ela o
> `firebase.json`, o `.firebaserc` e a dependência `firebase-functions`.
>
> **Não adianta tentar de novo em outra região ou provedor** sem antes conferir
> uma rodada de log: `sobraram 0` em todos os GTINs significa que nada mudou.

```
functions/
  src/run.js            uma rodada: lê, consulta, decide, notifica, grava
  src/trackingRules.js  a decisão (janela de 24h, alvo, "uma vez por queda")
  src/notaParana.js     a consulta por GTIN
  src/requestSpacer.js  400ms entre consultas
  src/decoyFilter.js    descarte dos registros forjados
  src/priceUtil.js      "4.50" é 4,50
  src/message.js        o texto do alerta
  src/notify.js         o formato da mensagem FCM

  src/schedule.js       quando sai a próxima rodada

  server.js             serviço de longa duração   (o container roda este)
  run-once.js           uma rodada e sai           (para agendar por cron)
  docker-run.js         sobe o container
  Dockerfile            imagem do serviço
```

Os dois pontos de entrada compartilham `src/run.js`. O que muda entre eles é
apenas quem agenda — a decisão de alerta é a mesma linha de código nos dois.

## Rodando no servidor de casa

É aqui que a rotina roda de verdade. Precisa de **Node 18 ou mais novo** (o
código usa `fetch` e `AbortSignal.timeout` nativos).

### 1. Copiar o código

Só a pasta `functions/` importa. Clonar o repositório inteiro também serve, e
facilita atualizar depois com `git pull`.

```bash
git clone <repo> ~/gerupreco
cd ~/gerupreco/functions
npm install --omit=dev
```

### 2. Credencial

Fora do Google Cloud não existe credencial implícita. No **console do Firebase →
Configurações do projeto → Contas de serviço → Gerar nova chave privada**, e
guarde o JSON **fora do repositório**:

```bash
mkdir -p ~/.config/gerupreco
mv ~/Downloads/gerupreco-*.json ~/.config/gerupreco/service-account.json
chmod 600 ~/.config/gerupreco/service-account.json
```

> Essa chave **escreve no Firestore e envia push como o projeto**. Ela não tem
> nada a ver com a chave de API que está no `google-services.json`, que é
> pública por natureza. Nunca versionar, nunca colar em log. O `.gitignore` da
> pasta já barra os nomes prováveis, mas o lugar certo dela é fora do repo.

### 3. Conferir antes de acordar os aparelhos

```bash
export GOOGLE_APPLICATION_CREDENTIALS=~/.config/gerupreco/service-account.json
npm run once:dry
```

A simulação lê o Firestore de verdade, **não grava nada e não envia push** — só
registra o que teria acontecido. É como saber se o IP do servidor é aceito antes
de depender dele.

Depois, uma rodada real:

```bash
npm run once
```

### 4. Agendar — opção A: cron do sistema

Quem repete é o cron do sistema, não um laço dentro do Node: o cron sobrevive a
reinício da máquina e a processo morto.

```cron
0 9,12,15,18 * * * cd /home/SEU_USUARIO/gerupreco/functions &&   GOOGLE_APPLICATION_CREDENTIALS=/home/SEU_USUARIO/.config/gerupreco/service-account.json   /usr/bin/node run-once.js >> /var/log/gerupreco.log 2>&1
```

Com systemd, um `.timer` com `OnCalendar=09,12,15,18:00` e um `.service`
`Type=oneshot` fazem o mesmo, com a vantagem de o log ir para o journal.

### 5. Códigos de saída do `run-once.js`

O script sai com código diferente para cada desfecho, para o cron ou um monitor
distinguirem sem ler o texto:

| código | significado |
| --- | --- |
| `0` | rodada normal |
| `1` | exceção não tratada |
| `2` | `GOOGLE_APPLICATION_CREDENTIALS` não definida |
| `3` | **tudo voltou forjado** — este IP está sendo tratado como raspagem |

O `3` é o que importa vigiar. Sem ele, um IP marcado é indistinguível de
"nenhum produto atingiu o alvo": os dois terminam em silêncio.

### 6. Agendar — opção B: Docker

Alternativa ao cron, e o caminho mais curto se o servidor já tiver Docker: uma imagem, um container que sobe junto com a máquina, e
nada instalado no host além do Docker.

```bash
cd ~/gerupreco/functions
npm run build      # docker build --no-cache -t geruprecotracking .
npm run docker     # sobe com --restart=unless-stopped, publicando :3456
```

O `npm run docker` monta a credencial em tempo de execução, procurando por
padrão em `~/.config/gerupreco/service-account.json` — o mesmo lugar do passo 2,
então quem seguiu o guia não precisa configurar nada. Para outro caminho:

```bash
GERUPRECO_CREDENTIALS=/outro/lugar/chave.json npm run docker
```

Para ligar o gatilho manual, defina `TRACKING_TOKEN` — ele é repassado ao
container só quando existe.

Ele **confere o caminho antes de chamar o docker**, e isso evita duas mensagens
inúteis. Com a variável vazia, o docker reclamava do formato do `-v`
(`empty section between colons`) em vez de dizer que faltava a credencial. E
apontando para um arquivo inexistente, o docker **cria um diretório vazio ali** —
o container sobe, e a falha só aparece bem depois, dizendo outra coisa.

O container sobe com **`--user` no uid de quem chamou**. A credencial montada é
`chmod 600` e pertence ao dono no host, enquanto a imagem roda como `node`
(uid 1000): quando os dois não coincidem, a leitura falha com
`EACCES: permission denied` apontando o arquivo — mensagem que não menciona uid
nenhum, e que empurra para a solução errada, a de afrouxar o `600`.

**A chave não entra na imagem de propósito.** Copiada com `COPY`, ela ficaria
numa camada legível por qualquer um que tenha a imagem, e ela dá escrita no
Firestore e permissão de enviar push. Se ainda assim você preferir embutir, é
uma linha no `Dockerfile` — mas aí a imagem passa a ser um segredo, e não pode
sair da máquina.

#### O container é um serviço, não uma tarefa

Ele **não** roda `run-once.js`. Um container que executa e termina, com
`--restart=unless-stopped`, reinicia em laço fechado, e cada volta é uma consulta
a mais na Nota Paraná. Então o `server.js` fica de pé e se agenda por dentro,
nos mesmos 09/12/15/18 — dentro do container não há cron.

#### A porta 3456

Um serviço que só escreve em log é invisível de fora do container, e "não chegou
alerta nenhum" continuaria sem resposta. O `/health` responde isso:

```bash
curl -s localhost:3456/health
```

```json
{
  "lastRunAt": "2026-08-31T15:00:04.312Z",
  "lastSummary": { "checked": 3, "notified": 1, "failed": 0, "discarded": 0 },
  "nextRunAt": "2026-08-31T18:00:00.000Z",
  "running": false
}
```

`discarded` alto com `notified` em zero é o retrato do IP marcado — o mesmo sinal
que o código de saída 3 dá no `run-once.js`.

Há também um gatilho manual, **desligado por padrão**: disparar uma rodada envia
push de verdade, e a porta pode acabar encaminhada para fora sem ninguém
lembrar. Para ligar, defina `TRACKING_TOKEN` no `docker run` e chame:

```bash
curl -X POST -H "Authorization: Bearer $TRACKING_TOKEN" localhost:3456/run
```

Uma rodada avulsa sem mexer no serviço também dá, pelo container que já está de
pé:

```bash
docker exec geruprecotracking node run-once.js --dry-run
```

## Comandos de desenvolvimento

```powershell
npm --prefix functions install
npm test                        # 39 testes, sem rede e sem Firestore
npm --prefix functions run once:dry    # rodada simulada, precisa da credencial
```

Os testes rodam sem rede e sem Firestore, então valem em qualquer máquina — é
onde a regra de "uma vez por queda", a janela de 24 horas e o descarte de
forjados são verificados.

## O que ler no log

A rodada termina com uma linha de resumo:

```
Rodada concluida: 12 produto(s) avaliado(s), 1 alerta(s), 0 consulta(s) com falha,
0 registro(s) forjado(s) descartado(s).
```

O número que importa é o último. **Registro forjado descartado com zero alerta é
o retrato de um IP marcado como raspagem**, não de um mercado sem promoção — a
Nota Paraná troca a resposta inteira por dados gerados quando pune volume por
IP, o `DecoyFilter` esvazia a lista e o resultado é silêncio, indistinguível de
"nenhum produto atingiu o alvo". É por isso que o descarte é registrado em
`warn` a cada GTIN, e não só contado.

O risco é maior aqui que no aparelho: a função sai de um IP de datacenter do
Google, compartilhado e sem histórico. O `requestSpacer` reduz a chance de ser
marcado, mas não desfaz a marcação — uma vez punido, o IP recebe resposta
forjada por um bom tempo mesmo consultando devagar.

## Contratos com o app

Três nomes precisam bater dos dois lados; mudar um só deixa o alerta sem
ninguém escutando, sem erro nenhum aparecer:

| valor | aqui | no app |
| --- | --- | --- |
| tópico dos alertas gerais | `notify.TOPIC_ALL` = `geral` | `TrackingRegistration.TOPIC_ALL` |
| canal de notificação | `notify.CHANNEL_ID` = `price_alerts` | `strings.xml`, `tracking_channel_id` |
| escopo particular | `trackingRules.SCOPE_DEVICE` = `PARTICULAR` | `Tracking.SCOPE_DEVICE` |

A mensagem carrega título e texto **em `notification` e em `data`**: com o app
em segundo plano quem desenha a notificação é o sistema, a partir do
`notification`, e o `GeruMessagingService` nem é chamado; com o app aberto a
entrega cai no serviço e o desenho sai do `data`. Mandar só um dos dois deixa
metade dos casos mudo.
