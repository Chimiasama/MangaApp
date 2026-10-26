# Tempos de pesquisa, histórico e abertura de mangás

## Medição comparativa

Linha de base: `b07029060435237f5ed6ed253a72037a4631d4f9`, com o teste
`LatencyBenchmarkTest` adicionado antes de modificar o código de produção.
Antes e depois foram executados no mesmo ambiente, com a mesma fixture.

| Caminho medido | Antes, mediana | Depois, mediana | Resultado |
| --- | ---: | ---: | --- |
| Pesquisa em três fontes | 247,289 ms | 84,411 ms | 65,9% menos tempo |
| Catálogo popular em três fontes | 244,498 ms | 83,732 ms | 65,8% menos tempo |
| Carga agregada de capítulos | 244,257 ms | 162,736 ms | 33,4% menos tempo |
| Primeira emissão do histórico, 1.000 registros | 4,207 ms | 7,237 ms | Sem melhoria demonstrada |

Cada cenário teve uma execução de aquecimento e sete amostras cronometradas com
`System.nanoTime()`. As fontes simuladas suspendem por 80 ms em cada consulta e
retornam um mangá ou capítulo. O banco Room é real, em memória, sob Robolectric
API 36. Os registros de histórico foram inseridos antes de cronometrar a consulta.
As amostras já aquecidas incluem o custo de leitura e escrita no cache, quando
esse caminho o utiliza. O teste confirma a quantidade e a ordem dos resultados.

Esses números medem o repositório no servidor de desenvolvimento. Não incluem
debounce da digitação (350 ms), renderização Compose, capas, download de páginas,
abertura de PDF, restauração do leitor ou latência de serviços reais. A medição de
capítulos não é o tempo completo de abrir um mangá e desenhar sua tela. As sete
amostras não sustentam conclusões sobre p95 ou estabilidade de produção.

O histórico não teve sua consulta SQL alterada. A diferença de poucos
milissegundos entre as execuções não demonstra uma otimização; requer mais
amostras e medição em aparelho antes de atribuir causa.

## Alterações

- Pesquisa e catálogo popular consultam fontes independentes em paralelo e
  continuam filtrando idioma, categoria, período e conteúdo adulto. A mesclagem e
  as escritas de cache seguem a ordem de registro das fontes. Falhas de uma fonte
  não eliminam os resultados das demais; cancelamento continua sendo propagado.
- A agregação inicia capítulos da fonte principal e consultas secundárias juntos.
  A fonte principal mantém a prioridade em duplicatas equivalentes. Fontes
  incompatíveis com o idioma selecionado não são consultadas.
- Ao reabrir detalhes, capítulos em cache no idioma selecionado ficam disponíveis
  enquanto ocorre a atualização. Uma falha ou resposta vazia da atualização
  preserva esses capítulos. O cache é filtrado e ordenado antes da exibição.
- Trocar de mangá cancela os observadores de favorito e progresso, o carregamento
  de metadados e as recomendações da seleção anterior. O estado da seleção é
  reiniciado antes de observar o próximo mangá.
- Itens de histórico usam a URL do mangá como chave estável na lista Compose.
  Essa alteração de identidade dos itens não foi medida como ganho de renderização.

O ganho de agregação depende de haver fontes secundárias agregáveis. No registro
padrão atual, MangaDash e as fontes HTML não permitem agregação; a fixture com
três fontes mede o caminho disponível para fontes adicionais. Não se deve aplicar
esse percentual automaticamente a uma abertura padrão com somente MangaDex.

A pesquisa ainda aguarda todas as fontes selecionadas antes de publicar a lista.
Uma fonte lenta pode continuar determinando a espera final. O intervalo de
1.500 ms por host em `SourceHttp` continua incidindo sobre consultas ao mesmo
site. Esse intervalo não participa das fontes simuladas do benchmark.

## Reprodução

Com JDK 21 e Android SDK configurados:

```sh
./gradlew :app:testDebugUnitTest --tests com.example.LatencyBenchmarkTest --console=plain
```

As amostras aparecem no `system-out` de
`app/build/test-results/testDebugUnitTest/TEST-com.example.LatencyBenchmarkTest.xml`.
Para repetir a linha de base, utilize um checkout separado do commit acima e
copie apenas `LatencyBenchmarkTest.kt` para ele. Preserve o XML antes de executar
outra bateria, pois o Gradle substitui os resultados.

```sh
./gradlew :app:testDebugUnitTest --console=plain
```

Os testes de regressão adicionais verificam início concorrente das fontes,
ordem dos resultados, falha parcial, cancelamento, prioridade da fonte principal,
uso do cache antes da resposta remota, retenção do cache após falha e remoção do
observador do mangá anterior. Os testes normais não consultam serviços externos.

Validação desta alteração: 144 testes passaram, sem falhas ou testes ignorados.

## Medição em aparelho pendente

Não havia aparelho ou emulador conectado em `adb devices` nesta execução.
Para medir a experiência completa, comparar a versão de base e a otimizada no
mesmo aparelho, mesma variante de build e mesmas condições de rede e energia.
Separar cache frio e aquecido; usar o mesmo banco, idioma, consulta e fontes.

Registrar, em pelo menos 30 repetições por cenário: envio da pesquisa até lista
visível; seleção do histórico até itens visíveis; toque no mangá até capítulos
utilizáveis; retomada do histórico até a primeira página renderizada. Separar
erros e timeouts das amostras bem-sucedidas e informar mediana, p95, contagem de
sucessos e quadros lentos. Capturar traces com Perfetto/Macrobenchmark para
distinguir rede, Room e trabalho na thread principal.
