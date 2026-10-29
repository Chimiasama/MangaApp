# Explorar, Pesquisar, home configurável e controles do leitor

## Estrutura e comportamento

A navegação em `ui/MangaApp.kt` tem Explorar, Pesquisar, Biblioteca e Ajustes.
`HomeScreen` mostra somente seções; sua lupa abre Pesquisar. `BrowseScreen` mantém
a busca, os resultados e o teclado. O ViewModel é compartilhado, sem limpar a busca
ao trocar de aba ou voltar para a home. Voltar primeiro encerra a edição do teclado.
Recomendações dos detalhes também abrem Pesquisar.

`CatalogFilters` continua sendo a regra única de inclusão/exclusão. A folha usa
`SearchFilterControls` e `CatalogFilterEditor`: gêneros, temas, idioma, fonte
inicialmente recolhida, demais classificações recolhíveis e faixa de anos.
Chips de classificação alternam neutro → incluir → excluir → neutro. Exclusões
prevalecem; E/OU só muda como combinar inclusões. O rodapé fixa limpar/aplicar,
e a prévia da quantidade usa o repositório existente com debounce e cancelamento.
O valor é limitado aos resultados que a fonte entrega, não um total global do site.

O badge conta classificações distintas, fonte, ano e idiomas diferentes do padrão
Português; Todos não impõe uma restrição de idioma. Busca vazia e filtros padrão
não mostram a antiga descrição cinza. Os cinco gêneros rápidos usam a mesma regra.

As fontes ficam sempre selecionáveis. Capacidades de idioma estão em
`MangaSource.supportedLanguages`; HTML declara conjuntos em `HtmlMangaSource.Site.languages`,
MangaDash declara seu conjunto, e MangaDex resolve os idiomas pela API.
Não existe regra de interface por nome de fonte. Um idioma não declarado pode
resultar em lista vazia do provedor; não se reetiquetam capítulos estrangeiros.
Nesse caso a busca oferece tentar todos os idiomas, preservando os outros filtros.

## Home e persistência

`home/HomeSection.kt` define seções, tipos, ordenação, normalização, JSON e deduplicação.
`UserPreferencesRepository` salva a lista JSON em `home_sections_v1`. Mudanças em
outras preferências não recarregam as seções. Busca e home têm estados separados
no `MangaViewModel`. O histórico atualiza Continuar lendo sem fazer consultas de rede.
As seções padrão incluem Continuar lendo, Destaques & Populares, Shounen Épico e
Atualizados recentemente. Continuar lendo só aparece com histórico e fica primeiro
por padrão para reservar esses mangás na deduplicação.

`HomeSectionsEditor`, dentro de Ajustes, permite ativar, criar, editar, excluir,
restaurar e reordenar segurando a alça. Também há ações de acessibilidade para mover
para cima/baixo. Nome, classificação, fonte, idioma, ano, ordenação e limite de
10/20/30 itens são persistidos. A deduplicação respeita a ordem e o limite visível
antes de reservar itens para a seção seguinte.

A home reutiliza os catálogos dos repositórios. Seções que compartilham fonte,
idioma e ano usam o mesmo conjunto de candidatos; filtros são aplicados por
`CatalogFilters.matches`. Isso não procura o catálogo inteiro para cada seção.
A ordem popular é a ordem recebida. Mais recente usa a data de atualização
quando fornecida (o DTO MangaDex já recebido agora preserva `updatedAt`).
As fontes atuais não fornecem notas nesse fluxo: Melhor nota mantém a ordem
original quando não há notas, com aviso no editor. Nenhuma nota é inventada,
e não foram adicionados endpoints ou transportes de rede.

## Leitor e ajustes visuais

`ReaderScreen` mantém gestos, modos, progresso, navegação e marcadores.
`ReaderPageNavigation` desenha a navegação horizontal com slider discreto e balão
durante o arrasto. A barra superior compacta tem uma linha; a inferior tem
anterior, slider, contador e próximo. Ambas ficam sobre a página e respeitam insets.
O tamanho compacto, slider embaixo e informações extras desligadas são padrões.
O timeout padrão passa a três segundos, preservando valores já salvos e a opção
zero para não esconder. Interagir com os controles pausa o timeout.
Zoom/hora/bateria são opcionais no canto; Início fica no menu do leitor.
As opções novas são persistidas junto de `ReaderOptions` no DataStore.

`MangaCard` mantém título em duas linhas com reticências e reduz fonte/coração.
`LibraryScreen` usa abas horizontalmente roláveis. `SettingsScreen` encurta
as descrições; detalhes de DataStore/Room/scraping ficam no diagnóstico.

## Conferência manual no celular

1. Em Explorar, confirme a ausência de campo de busca. Use a lupa e confira as
   quatro abas. Pesquise, alterne para Biblioteca/Ajustes e volte; consulta,
   filtros e resultados devem permanecer. Com teclado aberto, Voltar só deve
   fechá-lo; o próximo Voltar retorna à home, conservando a busca.
2. Na folha de filtros, toque três vezes em um gênero: incluir, excluir, limpar.
   Confira E/OU, fonte recolhida, faixa de anos, campos de ano, Sem data definida,
   badge, chips removíveis e rodapé visível inclusive com teclado.
3. Selecione cada fonte com os idiomas disponíveis. Para uma combinação vazia,
   use Tentar com todos os idiomas e confira que os demais filtros permanecem.
4. Em Ajustes/Tela inicial, crie/edite uma seção, mude filtros e quantidade,
   segure a alça para ordenar, desative e exclua outra seção. Feche/reabra o app
   e confira a persistência. Restaure o padrão e confira histórico/duplicatas.
5. Confira capas/títulos e o coração; navegue por todas as abas da Biblioteca
   num celular estreito, incluindo Estatísticas.
6. Leia um capítulo em retrato e paisagem, webtoon, páginas RTL e LTR. Toque no
   centro; confira altura das barras, imagem sem redimensionar, anterior/próximo,
   capítulos, rotação e marcador. Arraste o slider e confira o balão.
7. Teste compacto/normal, slider em cima/embaixo, informações extras e timeout.
   Confira que os controles não desaparecem durante um toque/arrasto. Reinicie
   o app e valide preferências, posição, zoom e marcadores.
8. Faça a conferência nativa nas quatro fontes, incluindo PDF da MangaDash,
   leitura/download offline e recuperação de uma página que falhar.

A validação automatizada não substitui a conferência física no Galaxy Fold 4,
especialmente teclado Samsung, dobra/rotação e modo imersivo.

## Seletor único de filtros rápidos da home

O topo de Explorar usa um botão Fonte / Idioma / Gênero e uma única lista horizontal
de opções. Trocar de categoria só troca as opções visíveis; as seleções das três
categorias permanecem no HomeUiState do ViewModel. A categoria e os três estados
de rolagem usam estado salvo do Compose. Um resumo pequeno mostra as escolhas,
e Limpar filtros restaura as seções configuradas.

HomeQuickFilters mantém fonte, idioma de capítulos e classificação de forma
independente. Com filtros rápidos ativos, fonte/idioma substituem temporariamente
os da configuração da seção, e a classificação rápida é combinada por E com os
critérios da seção. A consulta usa os repositórios existentes, com debounce curto
e reaproveitamento do mesmo catálogo entre seções. Os filtros são aplicados antes
da deduplicação e do limite de itens. Não compara idioma com originalLanguage,
nem deduz gênero pelo título/sinopse. Exclusões da seção continuam prevalecendo.
Sem filtros rápidos, as definições persistidas da home continuam valendo normalmente.

Ao escolher um idioma específico, a home não reutiliza um catálogo de cache cujo
idioma dos capítulos não pode ser validado. Um zero legítimo permanece vazio;
relaxar só o idioma conserva fonte e gênero. Continuar lendo aparece na home sem
filtros rápidos, para não misturar histórico sem idioma verificado com resultados.
Ver mais com seleção rápida abre a pesquisa global com essa mesma combinação;
sem seleção rápida, abre os filtros da seção. Abrir uma obra da home mantém o
idioma escolhido nos detalhes sem modificar a preferência de idioma da pesquisa.
O transporte da MangaDex/HTTP3 e os mecanismos de leitura/download não mudaram.

Validação manual: escolha MangaDash, mude para Idioma e selecione Inglês, depois
escolha Romance em Gênero; confira o resumo, volte à categoria Fonte e confirme
que MangaDash segue marcado. Uma fonte sem capítulos EN deve ficar vazia, sem
retornar obras PT-BR do cache. Teste também MangaDex + Inglês + Romance, abra uma
obra e confira o idioma dos capítulos; volte à home, alterne abas/rotação e use
Ver mais e Limpar filtros.

Verificação desta mudança: assembleDebug e a suíte completa de 219 testes passaram.
Os dois testes de interface foram repetidos com renderização do tema escuro;
a imagem do seletor único foi inspecionada. As alterações não incluem arquivos
de transporte, fontes HTTP, leitura de páginas ou download.
