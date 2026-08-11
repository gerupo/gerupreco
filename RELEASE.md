# Como liberar uma versão do Super GeruApp

Guia operacional para um agente de IA publicar uma nova versão. O app **não está em nenhuma loja**: ele se atualiza sozinho baixando o APK assinado direto do GitHub, e o Firestore é quem diz qual é a versão vigente.

## Como a distribuição funciona

`UpdateJob` escuta a coleção `appVersion` no Firestore. O documento tem dois campos:

| Campo | Exemplo |
|---|---|
| `versionCode` | `13` |
| `url` | `https://github.com/gerupo/gerupreco/raw/refs/heads/main/app/release/app-v13-release.apk` |

Na abertura do app, `UpdateJob.checkVerisonCode()` compara o `versionCode` instalado com o do Firestore:

- **instalado >= Firestore** → chama `MainActivity.configureActions()` e a tela inicial funciona.
- **instalado < Firestore** → mostra o diálogo de atualização, baixa o APK da `url` e instala. **Os cards da home ficam inertes até atualizar.**

Isso significa que **o Firestore é o gatilho de produção**. Assim que ele muda, todo aparelho com versão anterior é obrigado a atualizar.

## A regra de ouro da ordem

> **Atualize o Firestore por último, e só depois de confirmar que a URL do APK responde.**

Se o `versionCode` subir no Firestore antes do APK estar acessível no GitHub, todo mundo (inclusive você) recebe o diálogo de atualização, o download falha e **o app fica inutilizável** — o gate bloqueia a home e não há como sair pela UI.

## Pré-requisitos

```powershell
# O projeto compila com Java 25, entao JAVA_HOME precisa apontar para um JDK 25.
# Nesta maquina o unico e o JBR do Android Studio (OpenJDK 25.0.2).
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# git não está no PATH; vem embutido no GitHub Desktop
$git = "C:\Users\vacar\AppData\Local\GitHubDesktop\app-3.6.3\resources\app\git\cmd\git.exe"
```

Confirme a versão antes de confiar no caminho — `& "$env:JAVA_HOME\bin\java.exe" -version` tem de dizer 25. Com JDK anterior o build falha com `error: invalid source release: 25`. Confira também o número da versão do GitHub Desktop, que muda a cada atualização do app.

> **Ao editar arquivos deste repositório por linha de comando, cuidado com acentuação.** No PowerShell 5.1, `Get-Content -Raw` lê em ANSI e `Set-Content -Encoding utf8` grava em UTF-8: a combinação corrompe todo texto acentuado. Prefira ferramentas de edição de arquivo a manipulação de string no shell.

## Passo a passo

### 1. Subir o versionCode

Em `app/build.gradle`, incremente **uma** linha:

```groovy
def appVersionCode = 13   // era 12
```

Ela alimenta `versionCode` e o `archivesName` (`app-v13`), então o nome do arquivo sai correto sozinho. O `versionName` (`3.1.1`) é cosmético e só muda se você quiser.

### 2. Escrever as novidades da versão

Ao abrir o app pela primeira vez depois de atualizar, o usuário vê um diálogo com o que mudou. O conteúdo está em `app/src/main/res/values/changelog.xml`, em dois arrays **paralelos**:

```xml
<string-array name="changelog_titles">
    <item>Título curto da novidade</item>
</string-array>

<string-array name="changelog_descriptions">
    <item>Uma ou duas frases explicando, na perspectiva de quem usa.</item>
</string-array>
```

**Reescreva os dois a cada versão** — eles descrevem só a versão atual, não o histórico. A posição N de um corresponde à N do outro; deixar os dois vazios desliga o diálogo naquela versão.

Escreva do ponto de vista de quem usa ("Segure um produto para adicionar ao carrinho"), não do código ("adicionado CartRepository"). Bugs que nunca chegaram a ser publicados não são novidade para ninguém — não os liste.

O controle é por `versionCode` em `SharedPreferences` (`ChangelogDialog`): o diálogo reaparece sozinho quando o `versionCode` instalado passa do valor guardado. Não há nada a resetar manualmente.

### 3. Rodar os testes

```powershell
.\gradlew.bat testDebugUnitTest
```

Não publique com teste vermelho. `CartCompareTest` cobre a lógica de ranking do carrinho, que é fácil de quebrar sem perceber.

### 4. Gerar o APK assinado

```powershell
.\gradlew.bat clean assembleRelease
```

Sai em `app/build/outputs/apk/release/app-v13-release.apk`.

A assinatura é automática: existe um `signingConfigs.release` em `app/build.gradle` apontando para `key.jks` na raiz (alias `gerupreco`, senha `facada123`, a mesma em `readmeKey.txt`).

### 5. Conferir a assinatura

**Não pule.** Se o certificado divergir do da versão anterior, o Android recusa a instalação por cima e o usuário fica travado no diálogo de atualização.

```powershell
$bt = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$apksigner = Join-Path $bt "apksigner.bat"

& $apksigner verify --print-certs "app\build\outputs\apk\release\app-v13-release.apk"
& $apksigner verify --print-certs "app\release\app-v12-release.apk"
```

O `SHA-256 digest` das duas tem de ser **idêntico**. O esperado é:

```
7683eebd8b5c055296ed51be4d002639168f634362ba7b72729ab4c4215616e5
CN=Thiago Vacari, L=Cascavel, ST=Pr
```

### 6. Publicar o APK na pasta versionada

A URL do Firestore aponta para `app/release/`, que **é versionado no git** — não é a saída do Gradle.

```powershell
Copy-Item "app\build\outputs\apk\release\app-v13-release.apk" "app\release\app-v13-release.apk" -Force
Copy-Item "app\build\outputs\apk\release\output-metadata.json" "app\release\output-metadata.json" -Force
Copy-Item "app\build\outputs\apk\release\baselineProfiles\0\app-v13-release.dm" "app\release\baselineProfiles\0\" -Force
Copy-Item "app\build\outputs\apk\release\baselineProfiles\1\app-v13-release.dm" "app\release\baselineProfiles\1\" -Force
```

Mantenha os APKs antigos: são o caminho de rollback.

> `Copy-Item` de uma pasta que já existe no destino **aninha** em vez de mesclar (`baselineProfiles\baselineProfiles\...`). Copie os arquivos `.dm` individualmente, como acima.

### 7. Testar o APK assinado no aparelho

Teste **a release**, não a debug — é ela que vai para o usuário. As duas têm chaves diferentes, então instalar uma por cima da outra falha com `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; desinstale antes se precisar trocar.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\release\app-v13-release.apk"
& $adb shell dumpsys package com.vacari.gerupreco | Select-String "versionCode"
& $adb shell monkey -p com.vacari.gerupreco -c android.intent.category.LAUNCHER 1
```

Confirme que os cards da home respondem ao toque (o gate liberou), que o diálogo de novidades aparece, e exercite o fluxo que mudou. Feche o app com `am force-stop` e reabra: as novidades **não** podem reaparecer. Depois cheque se não houve crash:

```powershell
& $adb logcat -d -b crash | Select-String "gerupreco"
```

Detalhes de como dirigir o app por adb estão no `CLAUDE.md`.

### 8. Commitar e **empurrar**

```powershell
& $git add -A
& $git commit -m "mensagem descrevendo a versão"
& $git push origin main
```

O push é **obrigatório** antes do Firestore: a URL aponta para `refs/heads/main` no GitHub.

As credenciais já estão configuradas: `credential.helper=manager` com `credential.credentialStore=wincredman`, e o token fica no Cofre do Windows como `git:https://github.com`. O push funciona sem prompt, inclusive num terminal não interativo.

Se um dia voltar a pedir autenticação, ela **precisa** de uma janela interativa — o navegador tem de abrir. Duas armadilhas ao tentar isso a partir de um agente:

- A janela nova herda o ambiente de quem a criou. Se `GCM_INTERACTIVE=never` estiver setado, o GCM não abre o navegador e cai no prompt de senha, que o GitHub **rejeita** (`Password authentication is not supported`). Force `GCM_INTERACTIVE=auto` e `GCM_GITHUB_AUTHMODES=browser`.
- O `PATH` herdado pode ser anterior à instalação do Git; aí o GCM falha com `Failed to locate 'git.exe' executable on the path`.

A entrada `LegacyGeneric:target=GitHub - https://api.github.com/...` no Cofre é do GitHub Desktop e **não** serve para o git de linha de comando.

### 9. Confirmar que a URL responde

```powershell
$url = "https://github.com/gerupo/gerupreco/raw/refs/heads/main/app/release/app-v13-release.apk"
$r = Invoke-WebRequest $url -Method Head -MaximumRedirection 5
"$($r.StatusCode) - $([math]::Round($r.Headers.'Content-Length'[0]/1MB,2)) MB"
```

Tem que voltar **200** e o tamanho tem que bater com o arquivo local (~24,5 MB). O GitHub leva alguns segundos para servir o blob depois do push; se der 404, espere e repita.

### 10. Só agora: atualizar o Firestore

Sem autenticação — as regras são públicas e a chave está em `app/google-services.json`.

```powershell
$key = (Get-Content "app\google-services.json" -Raw | ConvertFrom-Json).client[0].api_key[0].current_key

# Descubra o id do documento (é único na coleção)
$doc = (Invoke-RestMethod "https://firestore.googleapis.com/v1/projects/gerupreco/databases/(default)/documents/appVersion?key=$key").documents[0]

$body = @{ fields = @{
    versionCode = @{ integerValue = "13" }
    url = @{ stringValue = "https://github.com/gerupo/gerupreco/raw/refs/heads/main/app/release/app-v13-release.apk" }
} } | ConvertTo-Json -Depth 5

$uri = "https://firestore.googleapis.com/v1/$($doc.name)?key=$key" +
       "&updateMask.fieldPaths=versionCode&updateMask.fieldPaths=url"

Invoke-RestMethod $uri -Method Patch -Body $body -ContentType "application/json"
```

**Sempre use `updateMask.fieldPaths`.** Sem ele o PATCH substitui o documento inteiro.

`versionCode` é `integerValue` e o valor vai **como string** no JSON — é assim que a API REST do Firestore representa inteiros. Gravar como `stringValue` quebra o `UpdateJob`, que desserializa para `int`.

### 11. Validar em produção

```powershell
(Invoke-RestMethod "https://firestore.googleapis.com/v1/projects/gerupreco/databases/(default)/documents/appVersion?key=$key").documents[0].fields
```

Depois abra o app num aparelho com a versão **anterior** e confirme que o diálogo aparece, baixa e instala.

## Rollback

O Firestore é o interruptor. Para voltar atrás, aponte-o para a versão anterior:

```powershell
# versionCode = 12, url = .../app-v12-release.apk
```

Os aparelhos que já atualizaram continuam na 13 (o app só força atualização, nunca downgrade), mas param de receber o diálogo. Por isso os APKs antigos ficam no repositório.

## Armadilhas

- **Compilar com JDK anterior ao 25** falha com `error: invalid source release: 25`. Exporte o `JAVA_HOME` do JBR.
- **`assembleRelease` sem o `signingConfigs`** gera APK não assinado, que o Android recusa instalar. O bloco já existe no `build.gradle`; se alguém removê-lo, o build "passa" e só quebra na instalação.
- **Não confie no nome do arquivo para saber a versão.** Confirme com `dumpsys package` ou pelo `output-metadata.json`.
- **`REQUEST_INSTALL_PACKAGES`**: na primeira atualização o app pede permissão de "instalar apps desconhecidos". É esperado.
- **Subir o `targetSdk`** mexe com esse fluxo de auto-instalação via `FileProvider`. Trate como mudança de risco, separada de uma release comum.

## Segurança — dívida conhecida

`key.jks` e `readmeKey.txt` (senha em texto plano) estão **versionados no repositório**, e agora a senha também está no `app/build.gradle`. Qualquer pessoa com acesso ao repo assina builds como se fossem oficiais e, como o app instala APK a partir de uma URL pública sem verificação extra, isso é um caminho direto para distribuir código malicioso aos aparelhos.

Corrigir de verdade exige rotacionar a chave, tirar os arquivos do versionamento e mover as credenciais para fora do repo (variável de ambiente ou `~/.gradle/gradle.properties`). Enquanto não for feito, **mantenha o repositório privado**.
