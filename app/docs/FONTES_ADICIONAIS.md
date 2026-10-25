# Fontes adicionais verificadas

Verificação em 25/10/2026. PT-BR continua sendo a seleção padrão. As fontes novas não mudam a preferência de idioma: escolha EN ou ES, ou Todos, em Explorar → Filtros.

| Fonte ativa | Idiomas | Evidência de leitura pelo código de produção |
| --- | --- | --- |
| MangaDex | PT-BR, EN, ES e outros | Integração existente mantida |
| MangaDash | PT-BR | Capítulos em imagens ou PDF, pelo mesmo leitor; consulte LEITURA_PDF.md |
| WeebCentral | EN | Dragon Ball: 520 capítulos listados; capítulo inicial com 36 páginas e final (Dragon Ball Z 325) com 18 |
| LeerCapítulo | ES | Dragon Ball Full Color: 520 capítulos listados; capítulo inicial com 33 páginas e final (519) com 21 |

Nas duas fontes novas, a verificação remota baixou e decodificou a capa e as imagens inicial/final dos capítulos inicial e final. Isso verifica bytes de imagem e não apenas HTTP 200 ou presença de URLs. GTO e Slam Dunk também foram encontrados nas buscas das duas fontes; a leitura dessas duas obras não foi verificada nesta rodada. Os números refletem o retorno dos servidores na data do teste, não uma garantia permanente de disponibilidade ou completude editorial. Não houve teste em aparelho físico.

## Comportamento

- Cada fonte nova possui adaptador próprio para busca, detalhes, capítulos e páginas. Não utiliza o importador genérico de regras.
- Os capítulos mantêm o idioma declarado pela fonte; PT-BR não passa a exibir inglês ou espanhol.
- As novas fontes mantêm suas edições e sequências separadas. Não completam automaticamente lacunas em outra edição. Isso evita misturar versões coloridas, sagas e traduções.
- O identificador inclui a fonte e o caminho. A ordem de leitura segue a sequência do arquivo da fonte, inclusive quando uma saga reinicia a numeração exibida.
- O domínio do URL do capítulo determina a fonte que entrega as páginas. Uma falha em uma fonte não procura aquele identificador em outra API.
- Documentos, imagens do leitor, capas e downloads usam a identificação HTTP testada. Nos testes de imagens, pedidos sem essa identificação receberam 403; com ela, os arquivos puderam ser decodificados.
- A busca em fontes secundárias antigas só aceita um título correspondente; não usa mais o primeiro resultado arbitrário como se fosse a obra pedida.
- As requisições de documentos têm cancelamento, timeout e intervalo mínimo de 1,5 s por host. Ausência de imagens ou uma página de verificação gera erro, sem fabricar capítulos ou páginas.

## Candidatas que ainda não estão ativas

- **Mugiwaras Oficial:** o acesso testado ao capítulo 1 de One Piece retornou uma tela de acesso, sem páginas para o leitor.
- **Tsundoku Traduções:** as amostras consultadas foram principalmente novels; não foi validado um arquivo de páginas de mangá.
- **MangaFire:** a página inicial respondeu, mas isso não confirmou a entrega dos capítulos.
- **MangaDash:** a integração de PDF foi adicionada; consulte [LEITURA_PDF.md](LEITURA_PDF.md). A fonte PT-BR agora faz parte do registro padrão.
- **Comick/Consumet:** permanecem desativados por padrão; as consultas anteriores não comprovaram entrega funcional.

## Verificação reproduzível

A suíte normal usa HTML sintético para verificar parsing, idioma, ordem, erros e roteamento. Não depende da disponibilidade dos sites.

Para repetir os três testes remotos de catálogo e imagens com os adaptadores reais:

```bash
./gradlew :app:testDebugUnitTest -PliveSources=true --tests com.example.LiveAdditionalSourceCheck
```

Esse comando acessa serviços externos e pode falhar quando o site mudar, limitar requisições ou estiver indisponível. Não é executado na Action padrão.

Referência de pesquisa dos formatos das fontes: [Keiyoushi extensions-source](https://github.com/keiyoushi/extensions-source). Os adaptadores do app são próprios, não carregam APKs de extensões externas.
