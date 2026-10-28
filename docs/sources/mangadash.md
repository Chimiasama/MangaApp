# MangaDash: diagnóstico e correção da integração

## Por que não aparecia nenhum mangá

A integração adicionada em `d8a1fb5` usava `https://mangadash.org`, domínio que retornou
**NXDOMAIN** em consultas independentes ao Google DNS e ao Cloudflare DNS. O endereço
ativo confirmado é **https://mangadash.net**. A falha de rede era capturada pelo scraper
e convertida em uma lista vazia, dando a impressão de que o problema estava nos filtros.

Mesmo trocando apenas o domínio, a integração continuaria incompleta:

- O catálogo atual usa cartões `.card-new`, com título e capa fora da âncora `.card-overlay`.
  Os testes antigos utilizavam HTML inventado com `.manga-card`, diferente do site real.
- Os capítulos usam `.chapter-row` e `data-chapter-number`. Extrair todos os dígitos do
  texto da linha misturava número do capítulo, data e visualizações.
- O leitor publica o PDF no JSON `script#chapterViewerData`, campo `pdfUrl`.
  Procurar apenas links ou iframes com extensão `.pdf` não encontra esse endereço.
- Devolver um endereço PDF diretamente ao Coil não converte o arquivo em imagens.

## O que foi mantido e corrigido

**MangaDash e PDF foram mantidos**, pois o catálogo e os PDFs públicos puderam ser
acessados. Um PDF de capítulo respondeu HTTP 206, `application/pdf` e assinatura `%PDF-1.7`.

O adaptador agora usa o domínio `.net`, os seletores reais, o ano de publicação dos
cartões/detalhes, o filtro `ano` do site e o número explícito dos capítulos. O idioma
`all` aceita a fonte, cujos capítulos são identificados como `pt-br`.

O PDF do JSON é baixado com identificação e Referer do capítulo, validado e convertido
com `PdfRenderer`. O leitor recebe URIs de imagens locais. Downloads parciais são
removidos, a conversão respeita cancelamento, recursos são fechados e bitmaps têm
limite de memória. Um marcador de conclusão permite reutilizar páginas já convertidas. O download offline
copia essas imagens locais para a biblioteca permanente, sem tentar enviá-las ao cliente HTTP.
PDFs sem acesso público continuam sujeitos às regras do site; não há contorno de login
ou capítulos restritos.

Falhas da fonte agora são propagadas; na consulta de catálogo, sem outra fonte disponível
nem cache, o app informa erro de conexão em vez de apresentar uma falsa busca sem resultados.

## Verificação e manutenção

Páginas públicas utilizadas para conferir a estrutura:

- https://mangadash.net/mangas
- https://mangadash.net/mangas?q=dragon
- https://mangadash.net/mangas?ano=1984
- https://mangadash.net/manga/367-dragon-ball

Diagnóstico de DNS: `https://dns.google/resolve?name=mangadash.org&type=A`
e `https://cloudflare-dns.com/dns-query?name=mangadash.org&type=A`.
O resultado descreve a verificação desta correção e pode mudar no futuro.

Os testes regulares usam fragmentos sanitizados da estrutura observada sem depender da rede. O teste instrumentado `PdfRendererHelperDeviceTest` usa um PDF
original de duas páginas em branco para conferir a conversão nativa e o cache no Android. Para verificar explicitamente o site,
a busca, o arquivo real, a conversão com Poppler (`pdftoppm`) e a reabertura do cache:

```sh
./gradlew :app:testDebugUnitTest -PliveSources=true --tests com.example.LiveMangaDashCheck
```

O Robolectric/JVM com Android 16 não executa `PdfRenderer` corretamente neste ambiente
(`FileDescriptor.getOwnerId$` indisponível). Por isso o teste nativo fica em `androidTest`,
para rodar num dispositivo/emulador conectado. A Action manual **Validar PDF nativo do MangaDash**
executa o mesmo teste num emulador Android 10 (API 29), além da opção local:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.PdfRendererHelperDeviceTest
```

Não trocar novamente por `.org` nem substituir os fixtures por estruturas presumidas.
Se o site mudar, conferir catálogo, detalhes, capítulos e `chapterViewerData` juntos.
