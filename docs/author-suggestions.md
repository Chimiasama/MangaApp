# Outras obras do autor

A tela da obra mostra um carrossel “Mais de [autor]” acima dos controles e da lista de capítulos. Tocar numa capa abre os detalhes daquela obra, mantendo o idioma escolhido. Sem autor conhecido, sem outras obras, ou em caso de falha na consulta opcional, não aparece seção vazia nem mensagem de erro adicional.

- MangaDex: preserva os IDs dos relacionamentos `author` no modelo e consulta `authors[]`, com o idioma dos capítulos. Confere os relacionamentos devolvidos; artistas não são confundidos com autores.
- MangaDash: usa o catálogo `mangas?autor=...`, o mesmo filtro publicado nos links de autor do site. Não usa uma pesquisa por título para inventar correspondências. Só retorna sugestões nos idiomas suportados pela fonte.
- Outras fontes: podem declarar `supportsAuthorRecommendations` e implementar `MangaSource.getAuthorManga`; o padrão é retornar vazio. Não há busca especulativa ou mistura de fontes por nomes semelhantes.

O repositório obtém os detalhes quando o catálogo não trouxe autor, remove a obra aberta, duplicatas e conteúdo incompatível com a preferência adulta; limita a 12 capas. Os dados de autor não exigem migração do Room. Sem metadados disponíveis offline, a seção pode ficar oculta.

A busca de sugestões roda separada da consulta de capítulos e é cancelada quando a obra/idioma muda. Os resultados são vinculados à URL/fonte da obra para evitar um carrossel antigo em outro título. Ao abrir uma sugestão, a lista volta ao topo e limpa a busca/seleção de capítulos da obra anterior. Observadores de favoritos, progresso e recomendações anteriores são cancelados ao trocar de obra.

## Verificação

Consulta pública em 08/11/2026: a MangaDex retornou 26 registros em PT-BR para o ID de Toriyama Akira de Dragon Ball, incluindo Dr. Slump, SandLand e Jaco. Esse número descreve o catálogo naquele momento, não garante disponibilidade permanente dos capítulos. A MangaDash publicou o link por autor e retornou Dragon Ball; se for a única obra, o carrossel fica oculto.

Testes cobrem filtro por identidade/idioma, exclusão de artistas, ausência de autor, catálogo da MangaDash, remoção da obra atual/duplicatas/adultos e abertura da sugestão pela interface. O transporte de rede, o leitor e o mecanismo de downloads são reutilizados.

No celular: abra Dragon Ball pela MangaDex em Português, role até “Mais de Toriyama Akira”, abra Dr. Slump ou Jaco e confira os capítulos. Confira também uma obra sem autor e uma combinação sem sugestões, que não devem mostrar um carrossel vazio.

Validação concluída: `assembleDebug` e suíte completa de 258 testes, sem falhas; repetidos os testes de sugestões, interface e mapeamento após a revisão final. Não houve teste em aparelho físico. A publicação de APK continua dependendo da configuração de assinatura anteriormente pausada.
