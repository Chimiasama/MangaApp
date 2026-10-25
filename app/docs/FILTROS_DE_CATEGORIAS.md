# Busca por categorias

Categorias não são consultas por título. Ao escolher Shounen sem digitar um nome, o app consulta obras classificadas como Shounen. Ao digitar um nome, ambos os critérios se aplicam. Resultados vazios mantêm os critérios; o app não volta a uma lista sem filtro.

- MangaDex: `publicationDemographic[]` para Shounen/Shoujo/Seinen/Josei; `includedTags[]` com IDs obtidos de `/manga/tag` para gêneros e temas. A busca mantém o idioma e recebe até 100 resultados, ordenados por seguidores.
- WeebCentral: `included_tag` no endpoint de busca. Os valores são lidos do formulário da fonte; os cartões incluem as tags completas quando disponíveis.
- LeerCapítulo: `genre` no catálogo. O valor é lido das opções publicadas pela fonte.
- MangaDash: `categoria` na API do catálogo, com valores lidos do formulário da fonte.
- Fontes adicionais sem suporte nativo usam apenas categorias declaradas nos resultados; nunca inferem categorias pelo título ou pela sinopse. Uma categoria não oferecida pela fonte não dispara uma consulta sem filtro.

Os resultados continuam sujeitos ao catálogo, à classificação, ao idioma e à primeira página de cada fonte. Nem toda fonte classifica a mesma obra da mesma forma. Os resultados de um filtro nativo confirmam a categoria consultada; abrir a obra busca seus metadados completos e exibe suas categorias acima da sinopse. Nomes equivalentes são padronizados (Shonen/Shounen, Action/Ação, etc.), sem confundir Shounen Ai com Shounen. Não é necessário associar automaticamente a obra ao primeiro resultado do MyAnimeList.

Os carrosséis usam categorias declaradas; não são completados com obras aleatórias ou palavras do título. O cache mantém as categorias conhecidas e também respeita a categoria selecionada ao buscar obras guardadas.

## Validação

`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`

Testes verificam parâmetros HTTP reais via Retrofit, combinação de nome/categoria, respostas vazias sem remoção do filtro, normalização, roteamento pelo repositório, formulários das fontes e categorias visíveis na tela de detalhes.

Verificação opcional contra as fontes (depende de rede/disponibilidade):

`./gradlew :app:testDebugUnitTest -PliveSources=true --tests com.example.LiveCategoryCheck`

Essa verificação abre os detalhes de uma obra retornada por cada fonte HTML para comparar a classificação com o filtro solicitado. Não baixa capítulos ou PDFs.

Verificação em 25/10/2026 neste ambiente: os quatro testes externos passaram. MangaDex retornou 100 obras para Shounen e 100 para Ação; buscas combinadas retornaram 12 resultados relacionados a Naruto, 20 a Dragon Ball e 3 a Jujutsu Kaisen. WeebCentral retornou 32 obras, LeerCapítulo 30 e MangaDash 7 para Shounen; as categorias foram confirmadas nos detalhes de uma obra de cada uma dessas três fontes. Esses números refletem o catálogo disponível e não garantem todas as edições, capítulos ou acesso futuro.
