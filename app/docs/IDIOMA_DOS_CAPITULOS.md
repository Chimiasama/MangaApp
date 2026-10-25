# Idioma dos capítulos

O idioma selecionado é um filtro, não uma preferência para preencher lacunas. PT-BR mostra exclusivamente capítulos cujo idioma informado pela fonte é `pt-br`; português europeu, inglês, espanhol, russo e registros sem idioma confirmado não entram nessa lista. Inglês e espanhol seguem o mesmo princípio (o seletor ES inclui variantes regionais de espanhol). Apenas **Todas** remove essa restrição.

O MangaDex consulta somente os idiomas solicitados e verifica `translatedLanguage` na resposta. Não busca traduções estrangeiras para completar capítulos ausentes. A agregação entre fontes também valida o idioma do capítulo e não faz solicitações extras em inglês. No modo Todas, traduções de idiomas diferentes com o mesmo número de capítulo são preservadas.

A tela aplica a mesma regra à contagem, lista, leitura inicial, continuação e downloads em lote. Se nenhum capítulo corresponder, mostra a mensagem de ausência naquele idioma e oferece trocar a seleção. A pesquisa de títulos e o catálogo continuam com seu comportamento atual; encontrar uma obra não significa que todos os seus capítulos estejam traduzidos.

O idioma agora acompanha os capítulos no cache. A migração do banco de versão 7 para 8 adiciona a coluna sem apagar dados. Registros antigos ficam com idioma desconhecido até serem atualizados pela fonte; o app não presume que um título em português comprove o idioma das páginas. Eles continuam disponíveis em Todas, e progresso e downloads não são apagados pela migração.

Testes cobrem respostas contendo traduções estrangeiras, listas esparsas, ausência de PT-BR, escolha explícita de Todas, propagação dos metadados, agregação, ação Ler agora, estado vazio e migração com progresso salvo.
