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

## Faixa histórica de 1900–1950

A busca dessa faixa usa o catálogo do MangaDex em ordem crescente de ano, percorre
as páginas necessárias e para ao ultrapassar 1950. Os limites 1900 e 1950 são inclusivos.
Obras anteriores, posteriores ou sem ano não são inseridas nessa faixa. “Todos” os
idiomas não acrescenta restrição de tradução e nenhum título é obrigatório.

Isso substitui a sequência de 51 consultas de ano exato. Uma verificação da implementação
anterior na fonte isolada encontrou 101 obras não adultas nessa faixa; não se tratava
de inexistência de acervo. A reprodução determinística identificou que falhas de uma
fonte podiam ser escondidas por respostas vazias ou modernas de outra fonte, resultando
numa falsa mensagem de zero correspondências após o filtro de ano.

Erros transitórios têm tentativas limitadas, respeitando cancelamento e esperas longas
de rate limit. Se uma página posterior falhar, os registros já recebidos são preservados.
O agregador mantém os resultados das fontes saudáveis e dados salvos das fontes que
falharam. A interface informa quais consultas ficaram incompletas, inclusive quando
nenhum dos dados disponíveis corresponde ao período. Nesse caso, oferece nova tentativa
em vez de apresentar a busca como uma confirmação de que não existem obras.

Validação online em 28/10/2026: consulta sem título, todas as fontes, idioma Todos
e período 1900–1950 retornou 101 obras datadas no intervalo, vindas do MangaDex,
sem aviso de consulta incompleta. O teste online fica separado dos testes de CI.

## Resumo e estados da pesquisa

O cabeçalho mostra um resumo discreto dos filtros efetivos, inclusive valores padrão:
data, fonte, idioma dos capítulos, origem e classificação. O painel de filtros mostra
esse mesmo resumo para a seleção em edição, antes de tocar em “Ver resultados”.
Inclusões, exclusões, modo de combinação e tratamento de obras sem classificação
aparecem quando aplicáveis. O idioma padrão continua sendo português.

A tela diferencia uma consulta concluída sem correspondências, ausência de internet,
tempo de consulta esgotado e falha ou resposta incompleta das fontes. Ausência de rede
validada pelo Android apresenta “Sem conexão com a internet” e aguarda a reconexão
para refazer a busca com os mesmos filtros. Uma fonte inacessível com a internet ativa
não é identificada como falta de internet. A consulta tem limite de dois minutos;
cancelamentos causados por uma nova busca não viram resultados vazios nem mensagens
de erro da busca anterior. Dados parciais continuam acompanhados do aviso de consulta
incompleta. O log `BrowseSearch` registra a categoria de falha, fonte, data e idioma.

Os avisos de consulta incompleta preservam a causa por fonte: status HTTP (como
403, 429 ou 503), timeout, DNS, TLS, conexão recusada ou resposta em formato
inesperado. O log registra a exceção original. O aviso não afirma que está
exibindo dados quando não há correspondências. Uma resposta real do catálogo
em português, capturada em 28/10/2026, também é reproduzida em teste com o
conversor de produção e retorna Tank Tankuro (1935) e Sazae-san (1946).
Esses testes de resposta gravada não verificam disponibilidade atual da fonte,
da rede do aparelho ou do servidor das capas.

A tela também mostra a mensagem integral de cada exceção e de suas causas,
incluindo detalhes como ECONNRESET e EACCES. Mensagens longas podem ser roladas
e selecionadas; “Copiar erro completo” copia o texto original exibido, sem
truncamento, para permitir compartilhar o diagnóstico diretamente do celular.

## Compatibilidade da conexão com a MangaDex

Após a consulta completa funcionar no navegador do mesmo aparelho enquanto o app
recebia `SocketException: Connection reset`, a API e as imagens de
`api.mangadex.org` e `uploads.mangadex.org` passaram a usar um cliente HTTPS com
HTTP/1.1 e um pool de conexões separado. As demais fontes mantêm o protocolo
negociado pelo cliente original. Os filtros, cabeçalhos e validações de
certificado e nome do servidor são preservados. Os avisos da MangaDex identificam
o perfil utilizado para permitir conferir qual versão foi testada no aparelho.

Este é um ajuste de compatibilidade para verificar no aparelho afetado; o reset
observado não comprova, por si só, que HTTP/2 seja a causa. Os testes usam um
servidor HTTPS local que oferece HTTP/2 e HTTP/1.1, conferem o protocolo efetivamente
utilizado, o retorno das duas obras em português, os parâmetros originais da
consulta, as capas e a rejeição de certificados não confiáveis.

O modo HTTP/1.1 não eliminou o reset no aparelho relatado, portanto a causa ainda
não foi atribuída ao protocolo HTTP. As falhas da MangaDex agora incluem uma
sequência por chamada: resolução DNS e seus endereços, IP e porta selecionados,
tipo de proxy, início/fim do TLS, protocolo negociado, envio dos cabeçalhos e
recebimento da resposta. Isso permite identificar em qual etapa a conexão caiu.
O diagnóstico também captura falhas durante a leitura do corpo da resposta,
após a chegada dos cabeçalhos. Não registra consultas, cabeçalhos, cookies ou
conteúdo recebido; os detalhes continuam disponíveis em “Copiar erro completo”.

O relatório do aparelho mostrou DNS e TCP concluídos para os dois IPv4 públicos da
MangaDex, com encerramento imediato na negociação TLS antes de qualquer envio HTTP.
O perfil específico da MangaDex passa a oferecer TLS 1.2 com as cifras modernas de
`RESTRICTED_TLS`, preservando certificado, nome do servidor e SNI. Isso é uma tentativa
de compatibilidade com a negociação identificada no aparelho, não uma confirmação
de que TLS 1.3 seja a causa. As demais fontes preservam o TLS original. Os testes
conferem TLS 1.2 efetivamente negociado na API e nas capas e TLS 1.3 na fonte externa,
além dos filtros originais e da rejeição de certificados não confiáveis.

## Diagnóstico comparativo manual

Na tela de erro, “Testar conexão com a MangaDex” compara três clientes dentro do
mesmo app e rede: o perfil atual de compatibilidade, o OkHttp sem as restrições
experimentais e o `HttpsURLConnection` nativo. Todos fazem o mesmo GET público
`/manga?year=1935&limit=1`, com a identificação real `MangaReaderApp/1.1.0`.
Não há troca de DNS, proxy, navegador simulado ou certificados ignorados.

O teste só roda por iniciativa do usuário, espaça as chamadas, usa tempos-limite
e suspende os clientes seguintes ao receber HTTP 403 ou 429. O relatório pode
ser cancelado ou copiado e registra HTTP, resultado válido da API e X-Request-ID
quando disponível. Nenhum resultado desse teste muda automaticamente o cliente
usado na busca normal nem os filtros selecionados.

Um cliente que recebe dados dentro do app fornece evidência contra um bloqueio
total do aplicativo. A comparação não prova intenção de bloqueio pelo servidor:
reset no TLS também pode envolver filtros intermediários ou interoperabilidade.
A confirmação pelo serviço exige análise dos logs da MangaDex. A documentação
oficial informa API pública, requisitos e bloqueios por abuso/IP:
https://api.mangadex.org/docs/2-limitations/ . Quando a negociação TLS falha antes
de HTTP, ainda não há X-Request-ID para correlacionar a chamada do lado do serviço.
