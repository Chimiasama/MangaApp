# Histórico, identificação da edição e períodos de publicação

## Histórico de busca

O app salvava o histórico principalmente ao confirmar pelo teclado ou pelo ícone de busca. Digitar uma consulta ao vivo e abrir uma obra podia não registrá-la. Além disso, o histórico só aparecia se o campo estivesse vazio.

Agora, a consulta é registrada ao confirmar, encerrar a edição, fechar o teclado, sair da tela ou abrir uma obra. Não é necessário pressionar a tecla de busca. A lista persistida no Room fornece as cinco consultas mais recentes; a comparação ignora maiúsculas/minúsculas ASCII e normaliza espaços. Uma trava evita duplicatas entre confirmações simultâneas. Ao tocar no campo, as consultas aparecem numa única linha horizontal, inclusive se houver texto digitado. Ao encerrar a edição, somem. Há remoção individual e limpeza do histórico.

## Identificação e diagnóstico

Os detalhes mostram a fonte, o ano original quando informado, os idiomas dos capítulos carregados e um link para a página da obra no site original. Na MangaDex, o link abre o site público, não a API. Cada capítulo informa explicitamente seu idioma; ausência de metadados aparece como “Idioma não informado”.

A API da MangaDex informa quantidade de páginas e links de leitura externa. Esses dados passam a ser preservados nos modelos e no Room. Registros externos/sem páginas são identificados na lista; quando existe uma versão hospedada para o mesmo capítulo/idioma, ela tem prioridade. O botão inicial escolhe capítulos potencialmente legíveis no app. Arquivos já baixados continuam tendo prioridade sobre indisponibilidade remota.

O leitor preserva erros HTTP em vez de convertê-los numa lista vazia. As mensagens distinguem:

- link externo: as páginas estão em outro site; existe opção de abrir a página original;
- zero páginas ou resposta sem imagens: a fonte não disponibilizou páginas, sem presumir a causa;
- HTTP 401/403: acesso recusado, possivelmente login/verificação/região, sem afirmar DRM;
- HTTP 404/410: conteúdo indisponível, removido ou link alterado;
- HTTP 429: limite de consultas;
- HTTP 5xx: falha do servidor;
- erros de conexão e timeout.

Falhas ao baixar a imagem pelo Coil também mostram a causa disponível. O erro do capítulo inclui fonte e idioma e permite abrir a fonte. Falhas ao listar capítulos deixam de aparecer simplesmente como ausência de traduções. Não há comprovação automática de licenciamento ou DRM; essas causas só podem ser confirmadas com informação explícita do provedor.

## Ano original da obra

Os períodos são 1900–1950, 1951–1960, 1961–1970 e assim por diante até o ano atual. São combinados com nome, categoria, fonte e idioma. Datas de envio de capítulos nunca são usadas como ano original. Obras sem ano informado ficam fora de um filtro de período.

- MangaDex: ano `attributes.year`. A API aceita ano exato, então uma década combina consultas por ano, mantendo os outros critérios. Cada ano recebe até 100 resultados. Para períodos históricos longos, o app consulta o catálogo ordenado por ano crescente, até ultrapassar o limite do período (no máximo 1.000 registros examinados).
- MangaDash: `ano_lancamento` e filtro nativo `ano`, usando os anos disponíveis no formulário. A quantidade de resultados segue a página do catálogo da fonte.
- WeebCentral: o ano vem dos metadados dos cartões/detalhes. O filtro se aplica ao catálogo carregado (primeira página de até 50 registros), sem sugerir que pesquisou exaustivamente o arquivo da fonte.
- LeerCapítulo: os dados consultados não fornecem ano original confiável; suas obras ficam fora do filtro de período. A limitação é informada na tela de filtros.

Não se associa uma obra automaticamente ao primeiro resultado do MyAnimeList para preencher datas, evitando confundir edições e obras com títulos parecidos.

## Persistência e testes

A migração 8 → 9 adiciona ano aos mangás e metadados de acesso aos capítulos, preservando favoritos, histórico e progresso. A cadeia 7 → 8 → 9 também é testada. Atualizar metadados de capítulos preserva as marcações de leitura.

`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`

Há testes para histórico sem confirmação pelo teclado, toque nas sugestões, cinco consultas após reabertura, deduplicação, escolha de período, parâmetros HTTP, fronteiras de ano, anos ausentes, fonte/idioma visíveis, mensagens HTTP, links externos, zero páginas e migrações.

Teste externo opcional:

`./gradlew :app:testDebugUnitTest -PliveSources=true --tests com.example.LiveDiscoveryCheck`

Verifica duas edições de Phoenix do Osamu Tezuka na MangaDex, o intervalo histórico, ano da edição na WeebCentral e ano nativo da MangaDash. Para o capítulo em inglês de Phoenix, obtém URLs pela API e decodifica a primeira imagem. Não armazena capítulos no repositório nem garante disponibilidade permanente dos sites.

Verificação em 25/10/2026 neste ambiente: as quatro verificações externas passaram. MangaDex listou Phoenix (1966), com capítulos em PT-BR, EN, RU e VI. O capítulo inglês DAWN listou 340 páginas; a primeira imagem respondeu 404 na primeira tentativa, mas em uma verificação posterior respondeu 200 e foi decodificada. O caso evidencia indisponibilidade transitória na entrega da imagem, sem prova de DRM. WeebCentral declarou 1954 para sua edição de Phoenix. A busca MangaDex por Phoenix em 1951–1960 retornou 2 resultados; 1900–1950 retornou 101 obras. MangaDash retornou 19 obras de 2025 na página consultada. Os valores e a disponibilidade podem mudar.
