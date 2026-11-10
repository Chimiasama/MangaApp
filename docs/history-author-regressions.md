# Correções de histórico e sugestões — 1.1.2

## Card fantasma em Continuar lendo

A home consultava o progresso e buscava os metadados uma vez por URL. Sem o registro do mangá, criava um card com título “Continuar lendo”, capa e fonte vazias. Retirar um favorito apagava esse mesmo registro de metadados. Além disso, mudanças de título/capa não atualizavam a home enquanto as URLs do histórico fossem iguais.

A nova consulta Room faz um JOIN reativo entre mangás e progresso: registros órfãos não produzem cards. Alterar metadados ou excluir progresso atualiza a seção sem reiniciar o app. Retirar dos favoritos agora altera apenas `isFavorite`, preservando os dados necessários ao histórico, aos marcadores e à leitura. Não há exclusão em massa nem migração de schema.

A exclusão do histórico também invalida salvamentos da sessão antiga do leitor. Escritas e exclusões são serializadas; um flush atrasado não recria o histórico removido. Abrir deliberadamente a obra para ler novamente inicia uma revisão válida e permite salvar progresso normal.

## Sugestões de autor ausentes

A implementação da MangaDex tinha o método de consulta por autor, mas não habilitava `supportsAuthorRecommendations`. O repositório retornava vazio antes de consultar a API. Os testes anteriores consultavam diretamente a fonte, sem passar por essa decisão do repositório.

A capacidade agora está habilitada e há um teste que usa o registro real da MangaDex, Room sem autor em cache, Retrofit com respostas controladas e o ViewModel para conferir a chegada de Dr. Slump à tela de detalhes.

Outra correção preserva o idioma salvo do capítulo ao retomar pelo histórico/marcador, usando a preferência do app quando faltar esse dado. O filtro de idioma da consulta de autor usa a mesma normalização dos capítulos, inclusive o padrão PT-BR para idioma vazio.

Consulta pública em 10/11/2026: a MangaDex retornou 26 registros com traduções PT-BR para o autor de Dragon Ball, incluindo Dr. Slump, SandLand e Jaco. Não foi necessário alterar transporte, DNS ou TLS.

## Conferir no celular

- Retirar favorito, apagar downloads e excluir o histórico: o card deve sumir da home sem reiniciar. Itens órfãos das versões anteriores já ficam ocultos ao instalar a correção.
- Retirar somente o favorito de uma obra ainda no histórico: ela continua com o título e a capa reais.
- Abrir Dragon Ball na MangaDex em Português: conferir “Mais de Toriyama Akira” acima dos capítulos. Repetir após retomar a leitura pela home.
- Uma obra sem autor ou sem outras obras disponíveis continua sem carrossel vazio.

Validação: `assembleDebug` gerou 1.1.2 (versionCode 4); a suíte completa concluiu 262 testes sem falhas. Os testes novos usam Room e ViewModel reais sob Robolectric; a API pública foi conferida separadamente. Não houve teste em aparelho físico.
