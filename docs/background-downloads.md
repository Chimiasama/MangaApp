# Downloads em segundo plano

O acompanhamento fica em **Biblioteca → Downloads**. A tela da obra tem resumo
de concluídos/total e o botão **Acompanhar downloads**, que abre essa aba. O ícone
Biblioteca indica pedidos pendentes; se só houver falhas, mostra `!`.

## Problemas encontrados

O lote anterior só criava DownloadEntity quando ChapterDownloadManager começava a
transferir páginas. Fila, espera por conexão, obtenção de URLs e preparação de PDF
não apareciam no app. Falhas nessa preparação podiam terminar no WorkManager sem
registro na lista. A biblioteca também chamava qualquer estado não concluído de
“Baixando”, incluindo falhas.

ChapterDownloadWorker construía MangaRepository sem appContext; a fonte MangaDash
ficava sem cacheDir e não conseguia preparar PDFs. O caminho individual criado
pela Activity fornecia esse contexto, explicando a diferença observada.

## Comportamento corrigido

- Os registros QUEUED são persistidos antes do agendamento; PREPARING identifica
  obtenção/conversão das páginas, DOWNLOADING contém contadores reais de páginas,
  RETRYING aguarda nova tentativa e FAILED permanece visível com opção de repetir.
- A preparação do worker usa o contexto da aplicação, incluindo cache do MangaDash.
  Resolução de páginas sem sucesso também atualiza o banco. Cancelamento cooperativo
  preserva espera; a saída inesperada de uma fase ativa registra falha.
- Lote e botão individual usam a mesma fila WorkManager. KEEP por capítulo evita
  substituir trabalho ativo; concluídos não são reenfileirados, e arquivos parciais
  válidos continuam sendo aproveitados pelo gerenciador existente.
- Até dois capítulos são preparados/transferidos por vez para limitar memória dos
  PDFs; os demais aguardam. O lote é persistente e respeita conectividade e a
  execução em segundo plano concedida pelo Android. A interface não promete início
  imediato: “Na fila” pode significar espera por execução ou conexão.
- Excluir cancela o trabalho único antes de remover arquivos/registro. Exclusão e
  adição de lotes são serializadas; callbacks tardios só atualizam registros
  existentes, sem recriar os excluídos.
- Downloads incompletos não abrem como leitura offline; preparação, falha e retry
  têm rótulos próprios. Ativos aparecem antes dos concluídos na lista.

Não houve alteração de esquema Room: os estados são strings na coluna existente.
Transporte MangaDex/HTTP3, fontes e validação das páginas baixadas permanecem iguais.

## Atualização de uma fila antiga

Trabalhos pendentes já persistidos usam o worker corrigido quando executados. Como
os lotes antigos não registravam todos os itens no banco do app, tocar novamente
em Baixar todos após atualizar recompõe a visibilidade da fila. Trabalhos ativos
com o mesmo capítulo são mantidos e os já concluídos são preservados. Trabalhos
antigos que já terminaram com falha precisam dessa nova solicitação.

## Verificação

DownloadQueueTest valida visibilidade antes do agendamento, deduplicação, preservação
de progresso/concluídos, falha de agendamento e contagens. ChapterDownloadWorkerTest
exercita o worker real com servidor local, preparação visível antes da resposta,
download de PDF, armazenamento e estados de retry/falha. O teste usa um shadow
somente para a ponte PdfRenderer nativa: Robolectric API 36 não implementa o método
Android FileDescriptor.getOwnerId$. O restante do helper, bitmaps, validação e Room
é real. PDFs públicos do MangaDash foram decodificados separadamente com Poppler
nos testes remotos, incluindo as 71 páginas do primeiro capítulo de Spy × Family.
Os testes de interface verificam o atalho da obra, abertura da aba Downloads,
estados visíveis e ação de repetir. A execução pelo agendador em aparelho físico
continua sendo uma verificação manual.

No celular: solicitar alguns capítulos ou o lote, conferir fila/preparação e total
na obra, abrir Acompanhar downloads, sair do app e retornar, repetir um capítulo
com falha, excluir um pendente e abrir um concluído sem conexão.

Validação final: assembleDebug e a suíte completa de 226 testes passaram.
Depois da proteção para excluir registros sem diretório de páginas, assembleDebug
foi repetido com os testes de fila e de integridade/retomada de capítulos locais,
também sem falhas. O caminho de conexão MangaDex validado anteriormente não mudou.
