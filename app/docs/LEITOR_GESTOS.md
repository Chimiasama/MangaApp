# Leitor: zoom, gestos e navegação

## Comportamento

- **Webtoon:** pinça amplia em torno do ponto entre os dedos. Arrastar para cima/baixo continua a leitura mesmo ampliado; arrastar na horizontal move a área ampliada. As imagens permanecem contíguas.
- **Páginas LTR/RTL:** em 1×, o pager nativo cuida da passagem de páginas. Com zoom, um arrasto move a imagem nos dois eixos até os limites da imagem efetivamente exibida. Apenas um novo arrasto que comece na borda permite passar de página. RTL inverte a direção física de avanço.
- **Duplo toque:** alterna 1×/2,5× por padrão. Nas opções do leitor, é possível escolher o ciclo 1×/1,75×/2,75×/4×. Pinça responde diretamente; duplo toque é animado.
- **Capítulos:** no início/fim reais do conteúdo, uma puxada de 72 dp arma a transição. O indicador identifica o capítulo, confirma “Solte para abrir” e vibra ao atingir o limite. Recuar cancela. A troca ocorre ao soltar, sem temporizadores de espera. Não há transição além do primeiro/último capítulo.
- **Carregamento:** os botões de navegação ficam indisponíveis durante a troca. Uma resposta antiga não pode sobrescrever um pedido mais recente. Em caso de falha, a leitura atual é mantida e existe a ação “Tentar novamente”.
- **Retomada:** o DataStore guarda por obra o capítulo, página, posição proporcional, ampliação, modo e deslocamentos normalizados. O progresso/auto-marcador Room existente continua sendo atualizado. Não há migração destrutiva do banco para este recurso. Capítulos novos começam no topo, mantendo a ampliação e centralizando a imagem.
- **Controles:** toque central alterna a interface; o tempo de ocultação é configurável de 1 a 15 segundos, ou 0 para manter visível. Os controles não somem enquanto há interação ou uma folha de opções/capítulos aberta. Há atalhos de enquadramento, marcador, navegação e barras de páginas.
- **Capítulos no leitor:** a lista pesquisável abre sobre a leitura, sem voltar à tela de detalhes.
- **Imagens:** cada página com erro tem repetição independente. O pré-carregamento permanece sequencial e, perto do fim, inclui as duas primeiras imagens do próximo capítulo. O modo de economia evita esse pré-carregamento extra.

## Verificação automatizada

Com JDK 21, Android SDK 36.1 e Build Tools 36.0.0 configurados:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.example.Reader*Test'
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

A assinatura debug exige `debug.keystore` na raiz. Se ausente, gere uma chave **exclusivamente de desenvolvimento** como no workflow `.github/workflows/reader-checks.yml`. Não a versione nem use para release.

- `ReaderGeometryTest`: ponto focal, limites reais, posição proporcional, direção/limiares e ordenação de capítulos.
- `ReaderInteractionTest`: toques, pinças e arrastos reais injetados em Compose/Robolectric, incluindo rolagem com zoom, bordas, RTL, cancelamento, retomada após redimensionamento, repetição de imagem e visibilidade dos controles. Imagens são locais/simuladas: não dependem de serviços externos.
- `ReaderSessionTest`: persistência, carregamento concorrente, falha/repetição e reutilização do próximo capítulo pré-carregado.
- A suíte existente continua disponível. Imagens de teste são escritas em `app/build/outputs/roborazzi`, sem sobrescrever referências versionadas.

O workflow “Validar leitor Android” compila o APK, executa testes e Lint e disponibiliza APK/relatórios como artefatos. Um push não equivale a publicar uma versão na Play Store.

## Conferência em celular

A automação não substitui esta verificação de uso antes de uma versão de produção:

1. Ampliar um balão fora do centro, pinçar movendo os dedos e retornar a 1×.
2. Ler um webtoon longo com zoom, atravessando várias imagens; girar e retomar o mesmo trecho.
3. Em páginas LTR e RTL, explorar os quatro cantos sem troca acidental; iniciar outro arrasto na borda para avançar.
4. Armar uma puxada, recuar e cancelar; soltar uma nova puxada para abrir o próximo capítulo no topo.
5. Repetir com conexão lenta/sem rede, erros de imagem, modo de economia, capítulos locais e primeiro/último capítulo.
6. Fechar/reabrir o app e conferir posição, zoom, marcadores e preferências de controles.
