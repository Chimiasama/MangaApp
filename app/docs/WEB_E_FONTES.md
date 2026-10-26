# Plano e versão experimental de navegador, scrapers e recuperação de fontes

## Plano executado nesta versão

1. Fortalecer o scraper declarativo: seleção correta de links, resolução de URLs,
   busca codificada, caminhos configuráveis, propagação de falhas/cancelamento e
   remoção do capítulo fictício de fallback.
2. Adicionar navegador HTTPS, pesquisa direcionada em sites conhecidos, entrada
   por compartilhar link, captura do DOM renderizado e seleção/reordenação de
   imagens antes de abrir o leitor nativo.
3. Persistir capturas e integrá-las à biblioteca, histórico, marcadores e modos
   existentes do leitor. O usuário confirma o idioma; ele não é inferido do título.
4. Adicionar diagnóstico, pausa de fontes, pesquisa assistida de novo endereço,
   teste do candidato, aplicação explícita, importação de regras via HTTPS e um
   snapshot de rollback persistente. Preservar URLs canônicas ao mudar domínio.
5. Adicionar conector experimental para instâncias Consumet compatíveis indicadas
   pelo usuário. Nenhuma instância pública, catálogo fictício ou provedor é ativado
   por padrão. Testar o contrato de busca antes de habilitar.
6. Validar extração/configurações com fixtures e o fluxo de captura em WebView no
   emulador via GitHub Actions. Gerar APK debug para testes manuais.

## Como testar no app

Em **Explorar**, toque em **Pesquisar na web / gerenciar fontes**. Também há uma
entrada no painel de filtros.

### Navegar e ler

- Digite uma consulta ou cole um endereço HTTPS.
- “Sites conhecidos” acrescenta restrições `site:` à pesquisa aberta no Google.
  A interface é a própria página de busca; não extraímos nem reclassificamos seus
  resultados e não prometemos que todos eles sejam sites de leitura.
- Abra um capítulo e aguarde seu carregamento. Role a página original se precisar
  disparar o carregamento progressivo de imagens. Toque em **Analisar**.
- Confira a prévia. Desmarque imagens que não são páginas e ajuste a ordem com as
  setas. Opcionalmente informe um seletor CSS de imagens para tentar novamente.
- Confirme o idioma e escolha adicionar à biblioteca. **Ler N páginas** salva a
  captura e abre o leitor nativo com zoom, webtoon e opções já existentes.
- A captura salva URLs, não os bytes das imagens. Não equivale a um download
  offline. Links temporários podem expirar: reabra o capítulo e analise novamente.
- Cada captura é inicialmente um capítulo/entrada independente. Links de outros
  capítulos são candidatos para navegação, não uma série catalogada automaticamente.
- URLs `blob:`, imagens canvas, DRM e protocolos locais não são extraídos.
- No navegador externo, **Compartilhar → MangaApp** aceita links HTTPS em texto.
- Capturas podem ser removidas em **Regras / API** sem apagar histórico e downloads.
  Há limites de 200 capturas e 1.000 páginas por captura para limitar armazenamento.

### Recuperar uma fonte

Em **Reparar**, escolha uma fonte. O diagnóstico faz uma busca de teste: resposta
com resultados não garante que capítulos e imagens estejam funcionando. Falha
HTTP, timeout e conteúdo não encontrado são exibidos sem gerar registros fictícios.
**Pausar** exclui a fonte das pesquisas, preservando seus registros e downloads.

Para um domínio que mudou:
1. Toque em **Pesquisar novo endereço** e confira a informação encontrada.
2. Volte a **Reparar**, informe o candidato HTTPS e teste o endereço.
3. Confira o site no navegador. Uma página HTTP 200 com título não comprova sua
   identidade. O aplicativo não determina que um domínio é legítimo pelo nome.
4. Confirme explicitamente a aplicação. Os pedidos de rede passam ao novo host;
   os identificadores de favoritos e progresso continuam canônicos.
5. Teste uma obra já salva e uma pesquisa nova. Use **Desfazer última alteração**
   se necessário. A reversão também persiste após reiniciar o aplicativo.

A correção de domínio preserva os caminhos. Mudanças de slug, IDs, paginação,
protocolo de API ou estrutura HTML podem exigir atualização de regras ou código.
MangaDex e protocolos de API não aceitam uma troca genérica de domínio.

### Atualizar regras sem APK

Em **Regras / API**, cole uma lista JSON ou busque um arquivo HTTPS. O aplicativo
valida todas as regras antes de aplicar qualquer uma e mostra uma prévia com nomes,
versões e endereços. **Aplicar** substitui as fontes personalizadas e mantém as
fontes padrão. A última configuração pode ser restaurada em **Reparar**.

Use IDs estáveis, únicos e não reservados. Aumente `version` ao distribuir uma
atualização. Versões anteriores são rejeitadas no import; rollback usa o snapshot.
Se uma regra existente mudar de domínio, o host de rede é atualizado mantendo
sua origem canônica, para preservar URLs da biblioteca.

Exemplo de formato, **não de uma fonte real pronta**:

```json
[
  {
    "id": "meu_site",
    "name": "Meu site autorizado",
    "baseUrl": "https://seu-dominio.example",
    "version": 1,
    "lang": "pt-br",
    "isNsfw": false,
    "popularPath": "/mangas",
    "searchPath": "/buscar?q={query}",
    "listSelector": ".manga-card",
    "titleSelector": "h3",
    "coverAttrSelector": "img",
    "linkAttrSelector": "a",
    "detailDescSelector": ".sinopse",
    "chapterListSelector": ".chapter-list li",
    "chapterTitleSelector": "a",
    "chapterLinkSelector": "a",
    "pageImageSelector": ".reader img",
    "pageImageAttr": "data-src"
  }
]
```

Este importador é declarativo. Não instala APKs de extensões, JavaScript remoto ou
código do GitHub. Uma URL HTTPS não é assinatura de autenticidade: use uma origem
confiável e confira a prévia. Não existe feed oficial automático configurado nesta
versão. A ferramenta fornece o caminho para distribuir e aplicar correções sem APK.

### API experimental

Informe a raiz HTTPS de uma instância Consumet sua/autorizada, o identificador do
provedor e o idioma real atendido por ele. O conector implementa o contrato:

- `GET /manga/{provider}/{query}` → objeto com `results`.
- `GET /manga/{provider}/info?id=...` → metadados e `chapters`.
- `GET /manga/{provider}/read?chapterId=...` → lista de objetos com `img`.

O teste inicial verifica resultados de busca para `dragon`. Depois de ativar,
selecione o idioma e a fonte nos filtros e teste detalhes, capítulos e páginas.
Não há endpoint de populares nesse contrato; o catálogo inicial fica vazio até
pesquisar. Uma resposta de busca válida não comprova o restante do contrato.
O idioma vem da configuração do provedor, não é traduzido pelo app.
A versão implementada não inclui autenticação específica da instância.
Os testes automatizados usam fixtures desse contrato; não validam um servidor
real do usuário nem a disponibilidade atual de cada provedor.

## Segurança e limites

O navegador não tem ponte JavaScript para APIs nativas, acesso a arquivos locais,
conteúdo Android ou conteúdo misto HTTP. A página pode executar seu JavaScript
normal dentro da WebView para carregar o conteúdo; a extração recebe somente dados
HTML. Login ocorre no próprio site. Cookies de imagens usam um CookieJar consultado
por destino HTTPS, sem encaminhar o cookie de um domínio a outro em redirects.
Não contornamos login, paywall, CAPTCHA ou DRM.

A extração é heurística: recipientes conhecidos de leitor têm prioridade; fora
deles, dimensões das imagens ajudam a formar candidatos. Anúncios ainda podem
entrar e páginas pequenas podem ficar de fora. Por isso existe prévia e seletor
manual. A mudança de página durante a captura invalida o resultado em trânsito.
Nenhuma rotina promete compatibilidade universal ou proteção contra queda real do
servidor. Downloads existentes continuam sendo a opção para leitura offline.

## Próximas etapas que dependem de configuração/validação adicional

- Feed curado e assinado, com chave pública no app, atualização periódica opt-in
  e testes de amostras antes da ativação. Não aceitar domínios encontrados em
  pesquisa como correção automática confiável.
- Catálogo de capacidades por fonte: leitura isolada, lista de capítulos e
  acompanhamento de lançamentos. A extração genérica atual não oferece toda essa
  catalogação automaticamente.
- Vinculação revisada entre versões da mesma obra. **Outras fontes**, nos detalhes,
  busca o título em todas as fontes sem copiar capítulos/progresso por suposição.
- Suwayomi, Komga e Kavita têm protocolos próprios e exigem conectores, servidor e
  credenciais de teste apropriados. Não são apresentados como compatíveis com o
  conector Consumet. Extensões Mihon também não são instaláveis por este importador.
- Medição de captura, primeira imagem e retomada em aparelho, separando cache,
  rede e renderização. Os testes funcionais não comprovam ganho de desempenho.

## Verificação

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

O workflow `reader-checks.yml` também roda `connectedDebugAndroidTest` em Android
API 35. `WebSourceToolsTest` usa documentos e JSON controlados; `WebBrowserReaderTest`
carrega HTML em uma WebView real, analisa a página, abre a prévia e verifica a
persistência da importação, sem depender de Google ou de uma fonte externa.
