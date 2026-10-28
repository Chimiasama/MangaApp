# Teclado na busca

O tratamento anterior de Voltar limpava a busca/filtros sem verificar a edição do campo ou a visibilidade do teclado. Alguns teclados/OEMs entregam esse evento ao aplicativo antes de fechar o IME.

Agora, enquanto o campo está focado ou o IME está visível, Voltar solicita o fechamento do teclado e remove o foco, preservando a busca, a categoria e os resultados. Com a edição encerrada e o teclado fechado, o próximo Voltar mantém a função de limpar filtros. O sistema também pode consumir o primeiro Voltar diretamente para fechar o IME; nesse caso o app não recebe esse evento nem limpa a pesquisa.

O ícone “Ocultar teclado” fica disponível no campo durante a edição ou enquanto o teclado está visível. Não limpa nem reenvia a consulta. Buscar, abrir filtros, selecionar um histórico ou abrir uma obra também encerra a edição. `adjustResize`, `imePadding` e o consumo dos insets do Scaffold coordenam a área disponível; a navegação inferior some durante o IME na busca e retorna após seu fechamento.

A lógica usa foco e insets, sem depender de um modelo de celular ou da altura fixa de um teclado, atendendo teclados flutuantes, teclado físico e telas dobráveis.

Testes locais de Compose verificam Voltar antes de limpar filtros, o botão de ocultar e a preservação da busca e dos resultados em layouts estreito e largo (673 × 841 dp). Testes instrumentados verificam o IME real no emulador Android, o Voltar do sistema, o evento entregue diretamente ao app, o botão explícito e a recuperação de espaço na tela. Não substituem teste físico em Galaxy Z Fold4/One UI.

Restauração da correção de `e1f882b` após regressão identificada no Galaxy Z Fold4. Nesta atualização, os testes locais em larguras estreita/larga são executados; os testes instrumentados são mantidos para um aparelho ou emulador com IME real.
