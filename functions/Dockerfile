# Rotina de alertas de preco do Super GeruApp, como servico.
#
# A imagem existe para rodar no servidor de casa: a Nota Parana devolve dados
# forjados para IP de datacenter (ver README), entao o que decide se isto
# funciona e de qual rede o container sai - nao o que tem dentro dele.

FROM node:26-alpine

# Fuso do container. O codigo nao depende de tzdata - o horario de Brasilia e
# calculado como deslocamento fixo -03:00 -, mas o carimbo do log em UTC fica
# previsivel assim.
ENV TZ=UTC
ENV NODE_ENV=production

WORKDIR /app

# As dependencias primeiro, e so os manifestos: enquanto o package.json nao
# mudar, o npm ci sai do cache e o build so refaz a copia do codigo.
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# Os arquivos que a rodada precisa. src/ e o coracao; os tres pontos de entrada
# vem junto porque sao pequenos e permitem "docker exec ... node run-once.js"
# para uma rodada avulsa sem levantar outro container.
COPY src/ ./src/
COPY server.js run-once.js index.js ./

# A credencial NAO entra na imagem de proposito - ela e montada em tempo de
# execucao, ver o script "docker" do package.json. Um JSON de conta de servico
# copiado para ca ficaria numa camada da imagem, legivel por quem tiver a
# imagem, e da acesso de escrita ao Firestore e permissao de enviar push.
ENV GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/service-account.json

# Nao roda como root: o processo so precisa ler o proprio codigo e a credencial
# montada. A imagem node ja traz o usuario "node".
USER node

EXPOSE 3456

# Sobe o servico, que se agenda por dentro. Chamar run-once.js aqui faria o
# container terminar a cada rodada, e com --restart=unless-stopped isso vira um
# laco de reinicios batendo na Nota Parana sem parar.
CMD ["node", "server.js"]
