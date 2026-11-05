# Interface mais limpa

A navegação continua em Explorar, Pesquisar, Biblioteca e Ajustes. Cada destino tem uma função diferente; nenhuma fonte ou função de leitura foi removida.

- Ajustes: grupos recolhíveis de tela inicial, aparência, leitura, bateria, armazenamento, atualizações e diagnóstico. Todos começam fechados, e a expansão usa estado salvável do Compose.
- Obra: sinopse limitada a três linhas, com expansão; ferramentas de seleção, download em lote, busca e agrupamento ficam em “Opções dos capítulos”. Idiomas, ordenação, leitura e acompanhamento de downloads continuam disponíveis sem abrir ferramentas. Uma busca de capítulos ativa permanece indicada no cabeçalho mesmo com o grupo fechado.
- Busca: resumo de filtros em uma linha; tocar nele abre a folha com a configuração completa. Chips removíveis e badge permanecem.
- Cards: capa e título, sem repetir o primeiro gênero; títulos mantêm duas linhas e reticências. Origem e favorito permanecem.
- Tema: superfícies mais discretas, contraste maior no texto secundário, tipografia consistente e barra de navegação que respeita o tema claro/escuro.

Não há mudanças de rede, armazenamento de capítulos ou lógica do leitor.

## Verificação manual no celular

1. Explorar e Pesquisar: trocar filtros, abrir obra e favoritar.
2. Obra: expandir/recolher sinopse e opções; filtrar capítulos, selecionar e baixar em lote; acompanhar em Biblioteca → Downloads.
3. Ajustes: abrir cada grupo, alterar tema e modo de leitura; conferir claro/escuro e tamanho de fonte maior.
4. Leitor: abrir capítulo em retrato/paisagem e conferir controles, avanço e retorno à obra.

Os testes de interface usam Robolectric, sem aparelho físico. As imagens de conferência usam dados sintéticos.

Validação: `assembleDebug` e suíte de 253 testes concluídos com sucesso. Há testes de expansão/restauração, troca de tema e acesso ao download em lote. Conferidas imagens renderizadas dos ajustes (claro/escuro) e da tela da obra.
