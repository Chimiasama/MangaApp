# Páginas ausentes e downloads completos

O capítulo 471 em português de Dragon Ball (Official Colored), ID
`cb8e39ff-fcdc-495d-807a-e4cd56c09adb`, retorna 14 páginas na API. Na investigação,
a página 2 original e a versão `data-saver` responderam HTTP 200 e decodificaram.
As 14 imagens originais também foram transferidas e decodificadas com sucesso.
Esse resultado não reproduz a falha de rede do aparelho; confirma que a página
não está ausente no manifesto.

A recuperação antes cobria API e capas, mas não os URLs dos nós At-Home.
Agora os URLs de páginas registrados a partir do manifesto também usam o cliente
rastreado e a recuperação nativa. Para páginas conhecidas, a espera de 30 segundos
é por URL; uma página recuperada não bloqueia as seguintes. Se a alternativa nativa
também falhar ou responder 403/429, há pausa geral de 30 segundos nesse cliente. Um registro limitado associa URL a capítulo,
índice e quantidade original de páginas. Em reset/timeout/protocolo ou HTTP
404/410/5xx, atualiza o manifesto e tenta até duas rotas: a página original no
servidor atual, quando mudou, e a mesma página na versão compactada. Não troca
rotas para 403/429, falhas de certificado ou cancelamentos. Mudanças na quantidade
de páginas invalidam o remapeamento para evitar deslocar a numeração. Atualizações
simultâneas são agrupadas por dez segundos, sem guardar histórico ilimitado.
Falhas na atualização têm espera de dez segundos; 403/429 suspendem novas atualizações
por um minuto, inclusive para outras páginas, para evitar insistência na API.

O botão de tentar novamente ignora a leitura dos caches de memória/disco e pede
uma rota atualizada para a página. Pode usar a versão compactada, mantendo o índice,
quando a original não carrega. A mudança não reinicia o capítulo nem a posição.

Downloads agora verificam HTTP, tamanho transferido e decodificação da imagem antes
de promover um arquivo temporário ao arquivo final e contabilizar a página.
Respostas HTML, transferências interrompidas e arquivos vazios não contam como
páginas baixadas. Falhas de leitura/decodificação de páginas MangaDex conhecidas
podem recuperar a mesma página por outra rota; não há repetição ilimitada.
O Worker recebe o resultado real, sem sinalizar sucesso para um download incompleto.

Ao abrir um download, exige todos os arquivos numerados de zero até o total esperado
e imagens válidas. Um download antigo incompleto ou corrompido é marcado como falho
e volta à resolução online. Não apresenta uma lista encurtada como se fosse o
capítulo inteiro. Repetir o download conserva arquivos válidos e repõe os demais.

Os testes locais cobrem página 2 pela rota compactada, troca de servidor,
403/429, mudança na contagem, recuperação HTTPS do nó de páginas, cache ignorado
no botão de repetir, falha na página intermediária, retomada e reparo de download
antigo corrompido. Um teste usa PNG gerado pelo Android para verificar a decodificação
real; o antigo fixture PNG de um pixel tinha CRC inválido.

## Capas da pesquisa

As capas usam o mesmo transporte com negociação TLS/HTTP automática. A recuperação
HTTPS nativa tem controle por URL, assim como nas páginas: recuperar uma
capa não impede que outras capas da grade sejam recuperadas. Uma resposta 2xx
libera também uma nova consulta do mesmo URL; apenas tentativas em andamento
ou sem sucesso mantêm o intervalo por URL de 30 segundos.
Falhas da alternativa nativa e respostas 403/429 ainda suspendem novas tentativas
nativas por 30 segundos. Consultas da API também têm controle por URL: catálogo, detalhes e feed não
bloqueiam a recuperação uns dos outros. Uma falha da alternativa nativa ou
HTTP 403/429 ainda suspende novas recuperações por 30 segundos no cliente.

Para miniaturas MangaDex `.256.jpg` e `.512.jpg`, HTTP 404/410/5xx permite uma única
tentativa da capa original, no mesmo domínio e preservando cabeçalhos. Não altera
outras fontes, não tenta contornar 403/429 e não ignora validação de certificados.
Os testes simulam reset em duas capas distintas, ausência da miniatura e recusas HTTP.

## Catálogo real e falhas de capítulos

A lista de 24 obras de demonstração foi removida do fluxo MangaDex. Uma falha
na API não gera resultados artificiais nem troca silenciosamente o idioma da
lista de populares. Registros antigos com as URLs exatas das capas dessa lista, sem idioma
original e com publicação desconhecida (metadados da demonstração), são ignorados no fallback de pesquisa/cache; favoritos e histórico não são apagados.
Respostas reais atualizam os metadados normalmente.

Falhas do feed de capítulos são propagadas até a tela quando não há capítulos
válidos no cache ou nas fontes agregadas. A tela mostra erro e permite repetir;
“nenhum capítulo” fica reservado a consultas concluídas sem resultados. O modo
debug também exibe os detalhes copiáveis dessa falha. A verificação remota opcional
`LiveMangaDexReadFlowCheck` usa catálogo, quatro capas reais, o feed PT-BR de
Dragon Ball (Official Colored) e decodifica a página 2 do capítulo 471.

Validação remota em 29/10/2026: 50 obras populares, quatro capas decodificadas,
522 capítulos PT-BR após filtragem/deduplicação, 14 páginas no capítulo 471 e
página 2 decodificada. A suíte de 210 testes locais mais essa verificação remota
passou sem falhas. O teste remoto usa o proxy do ambiente somente na infraestrutura
de teste; o APK não recebe configuração de proxy nem mudança de confiança TLS.

## Reset TLS no aparelho e transporte Chromium

O diagnóstico do aparelho em 29/10/2026 mostrou reset antes de qualquer HTTP em
OkHttp e HttpsURLConnection, inclusive na consulta isolada por 1935; o navegador
na mesma rede ainda abriu JSON. Isso não determina, por si só, quem interrompeu
TLS. O transporte automático do OkHttp não voltou a forçar TLS 1.2.

A recuperação HTTPS agora usa o HttpEngine do Android quando disponível
(Android 14+ ou Android 12/13 com extensão S 7+). Ele fornece a pilha Chromium
sem adicionar biblioteca. É inicializado de forma preguiçosa com contexto da
Application, para funcionar também em WorkManager. Nos aparelhos sem suporte,
conserva a alternativa HttpsURLConnection. Continua restrito às consultas GET
MangaDex e páginas registradas, após falha de transporte; nunca após HTTP 403/429
ou falha de validação de certificado. Usa o mesmo endereço, identificação e
cabeçalhos do app, com os validadores padrão do motor e sem configuração de DNS,
proxy ou cookies de navegador. A adaptação mantém streaming, cancelamento,
prazos e fechamento do corpo usados por Retrofit, Coil e downloads.

O diagnóstico manual mostra Chromium separadamente, inclusive quando indisponível.
Testes locais verificam o adaptador HttpURLConnection, consulta comparativa, guarda
de versão e regressões de TLS/cancelamento. Eles não executam o HttpEngine real
do firmware do celular. As consultas remotas deste ambiente passam por proxy e
não reproduzem o caminho TLS direto do aparelho; a eficácia desta alternativa
precisa ser confirmada nele.

Referências: https://developer.android.com/reference/android/net/http/HttpEngine
 e https://developer.android.com/develop/connectivity/cronet.
