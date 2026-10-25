# PDF como capítulo no leitor

MangaDash é registrado como uma fonte PT-BR. Em Explorar → Filtros, selecione Português e MangaDash. A busca, detalhes, capítulos e botão de leitura seguem o fluxo normal do app. A fonte pode entregar imagens ou PDF; o adaptador verifica os dados de cada capítulo.

Um PDF é baixado completo para o armazenamento privado antes de abrir. Depois, cada página é apresentada ao mesmo leitor por um Fetcher Coil próprio. Zoom, duplo toque, pan, modos paginado e webtoon, posição, favoritos de página e troca de capítulos continuam usando o fluxo existente. Não é necessário abrir um aplicativo externo. A primeira abertura pode demorar mais que a de um capítulo de imagens.

## Armazenamento e memória

- Os bytes do PDF são copiados por streaming para um arquivo temporário. Erros HTTP, HTML, PDFs danificados e arquivos protegidos por senha não são publicados como capítulos válidos.
- Somente páginas solicitadas são renderizadas com `android.graphics.pdf.PdfRenderer`, fora da thread da interface. Não se converte o capítulo inteiro em JPEGs.
- As páginas têm fundo branco, largura de até 3072 pixels e orçamento de até 8 milhões de pixels por bitmap. Há no máximo duas renderizações simultâneas; descritores e páginas nativas são fechados depois de cada uso.
- O PDF tem limite de 256 MB. O cache de documentos tem limite de 512 MB e é incluído na limpeza e contagem de cache dos Ajustes. Cancelar o carregamento cancela a requisição e remove arquivos parciais.
- **Baixar capítulo** salva uma cópia persistente do PDF e registra o número real de páginas. Essa cópia continua legível sem rede e depois de limpar o cache. Excluir o download remove a cópia persistente.
- As referências de páginas aceitam somente arquivos privados do app. A posição de leitura continua identificada pelo mangá, capítulo e índice da página, não pelo nome do arquivo temporário.

## Testes

Os testes locais verificam o adaptador MangaDash e preservam a suíte de busca, idioma e gestos existente. Os testes instrumentados usam um PDF sintético de três páginas, sem depender de servidores, e verificam renderização real no Android, integração Coil, ordem/cores, zoom, mudança de página/capítulo, cópia offline, limpeza de cache, cancelamento e erros.

O Robolectric não implementa a API nativa usada pelo PdfRenderer neste projeto; por isso a Action usa também um emulador Android 35 para esses testes. Para executar em um emulador ou aparelho conectado:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Para incluir a verificação remota do capítulo 1 de Dragon Ball no MangaDash:

```bash
./gradlew :app:connectedDebugAndroidTest -PliveSources=true
```

A Action manual possui a opção `live_pdf` para essa verificação remota. As execuções padrão continuam sem depender da disponibilidade da fonte. O teste remoto busca a obra e os capítulos pelo adaptador real, baixa o PDF e renderiza a primeira e a última página no Android.
