# Busca de mangás

A busca usa a fonte selecionada e o idioma salvo. Digitar agenda uma consulta após 350 ms; confirmar pelo teclado ou pelo ícone de busca consulta imediatamente e registra o histórico.

Somente a requisição mais recente pode publicar resultados, erros ou encerrar o indicador de carregamento. Isso impede que o catálogo inicial ou uma pesquisa anterior sobrescreva “Dragon Ball” quando a rede responde fora de ordem. Pedidos anteriores são cancelados; o identificador da requisição também protege contra fontes que não cooperam com cancelamento.

Na MangaDex, se a consulta no idioma escolhido não encontra títulos, a busca global continua disponível. Falhas de rede/HTTP não são convertidas em “nenhum mangá encontrado”: se não houver resultados já conhecidos no catálogo local ou no cache, o app apresenta erro e permite tentar novamente. Uma resposta válida vazia continua sendo tratada como ausência de resultados.

Verificação reproduzível, sem depender da rede:

```sh
./gradlew :app:testDebugUnitTest --tests com.example.BrowseSearchTest --tests com.example.BrowseInputTest --tests com.example.MangaSearchTest
```

Os testes verificam digitação/confirmação, debounce, respostas fora de ordem, erro de rede e a conversão HTTP/JSON da MangaDex. A fixture `mangadex-dragon-ball.json` preserva os campos relevantes de uma resposta real de Dragon Ball, com descrições reduzidas para manter o teste pequeno. Não são adicionados títulos fictícios ao catálogo do app.
