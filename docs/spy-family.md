# Verificação de Spy × Family — 29/10/2026

Teste explícito `LiveSpyFamilyCheck`, usando parsers e repositório do app, fontes
reais e Room em memória. A consulta `spy`, fonte `all`, idioma `pt-br`, sem ano ou
classificações encontrou seis resultados, incluindo MangaDex e MangaDash.

| Edição | Capítulos nativos PT-BR encontrados | Primeiro |
| --- | --- | --- |
| MangaDash — SPY x FAMILY | 164 registros, incluindo extras, até 140.1 | 1 |
| MangaDex — SPY×FAMILY | 13 | 112.2 |
| MangaDex — Official Colored | 4 | 1 |

MangaDash: https://mangadash.net/manga/118-spy-x-family.
O HTML contém os 164 registros e o parser/agregador preservou os 164. Isso não
significa uma sequência sem lacunas de todos os números inteiros: há extras e
partes com numeração decimal. O capítulo 1 forneceu um PDF público de 71 páginas;
o teste converteu todas em imagens e decodificou a primeira e a última. A conversão
do teste foi feita por Poppler, independentemente do PdfRenderer usado pelo APK.

MangaDex principal: `6b958848-c885-4735-9201-12ee77abcb3c`. A API retornou 24
registros PT-BR: 13 hospedados e 11 externos. Os capítulos 1, 2, 3 e 3.5 retornam
links oficiais para o Manga Plus, sem páginas hospedadas para leitura nativa.
Outros externos são 134, 138, 139.1, 139.2, 140, 141 e 142. O app exclui links
externos deliberadamente; não há um corte numérico em 112 nem truncamento pela
paginação neste caso. A API não informa por que os demais capítulos não estão
disponíveis. Não é possível atribuir isso a uma remoção específica por direitos
autorais apenas com esse retorno.

Edição colorida: `76eb5273-5869-4f5a-8b43-853f55efd6cd`, com capítulos nativos
PT-BR 1–4 na verificação. As edições e fontes permanecem separadas.

## Efeito dos filtros

MangaDex classifica a obra principal como Romance; MangaDash fornece Ação,
Comédia, Sobrenatural e Cotidianos. Repetindo a busca com `genre:romance`, a
filtragem final usada pelo ViewModel manteve MangaDex e excluiu MangaDash.
Isso é coerente com a combinação por E e com os metadados de cada fonte.
O teste não estabelece quais filtros estavam selecionados no aparelho do usuário.

Para encontrar o arquivo desde o primeiro capítulo no APK atual: Pesquisar,
limpar classificações, selecionar Fonte MangaDash e idioma Português, buscar
`spy` e abrir o cartão com selo MANGADASH. Não é necessário misturar capítulos
de edições distintas nem alterar o leitor nativo.

## Reprodução

`./gradlew :app:testDebugUnitTest -PliveSources=true --tests com.example.LiveSpyFamilyCheck`

O teste remoto não integra a suíte offline habitual. Somente a infraestrutura de
teste usa o proxy do ambiente, com TLS validado; o APK não recebe alteração de
transporte, identidade, DNS, proxy, fontes ou filtros. O teste passou em 40,557 s.
