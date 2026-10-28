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
