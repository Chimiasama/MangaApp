# Organização da busca e dos filtros

A classificação fica separada em público editorial, gêneros, temas, formatos, origem
e publicação. Idioma dos capítulos, fonte, título e ano continuam independentes.
A origem corresponde ao idioma original da obra, nunca ao idioma de tradução.

## Incluir e excluir

Cada opção pode ficar neutra, incluída ou excluída. Selecionar o estado já ativo volta
para neutro; mudar de incluir para excluir remove a inclusão anterior.

- **Todos os incluídos:** todas as inclusões precisam corresponder à mesma obra.
- **Qualquer incluído:** basta corresponder a uma inclusão, mesmo que esteja em outro grupo.
- Uma correspondência em qualquer exclusão sempre remove a obra, em ambos os modos.
- Sem inclusões, todos os candidatos da fonte/idioma/ano são aceitos, exceto os excluídos.
- Exemplo: fonte MangaDex, nenhuma inclusão, tema Ninja excluído → consultar o catálogo
  do MangaDex sem exigir título e excluir sua etiqueta Ninja.

As seleções da folha são rascunhos. **Ver resultados** aplica tudo de uma vez;
fechar a folha sem aplicar descarta o rascunho. Os chips acima dos resultados mostram
`+` para inclusões e `−` para exclusões e permitem remover uma condição individual.
Limpar classificação mantém fonte, idioma e ano; limpar filtros restaura a busca geral.

## Normalização e dados ausentes

Rótulos portugueses são ligados a nomes equivalentes das fontes por aliases exatos,
normalizando acentos e pontuação. “Ninja” não é equivalente a “Artes marciais”.
Não se procura uma palavra no título ou na sinopse para inventar uma classificação.

Inclusões exigem uma correspondência conhecida. Por padrão, exclusões também ocultam
obras sem os dados de classificação necessários; a interface informa a quantidade e
permite desativar esse comportamento. Exclusões se baseiam nas etiquetas fornecidas
pela fonte, não numa interpretação do conteúdo ou numa garantia editorial de completude.

O MangaDex oferece etiquetas próprias e campos de público, idioma original e status.
As etiquetas incluem Ninja (`theme:ninja`), cujo UUID foi conferido em `/manga/tag`.
Inclusões/exclusões compatíveis são enviadas ao endpoint de catálogo; exclusões usam OR.
Combinações de “qualquer incluído” entre campos que a API só combina com AND são aplicadas
sobre um conjunto mais amplo de candidatos, evitando transformar OR em AND por acidente.
A expressão completa também é conferida localmente. A consulta retorna a página de
catálogo disponível; não constitui uma exportação de todo o acervo da fonte.

Outras fontes podem fornecer menos campos. Quando necessário, seus detalhes são
consultados para enriquecer a classificação; falhas deixam o dado desconhecido, sem
preencher valores imaginários. O aviso da fonte informa essas limitações.
O ano, o status e o idioma original são preservados no cache. A migração 10 → 11 mantém
favoritos, histórico, capítulos lidos e downloads.

A proposta etária permanece exclusivamente em [planos/classificacao-etaria.md](plans/classificacao-etaria.md).
