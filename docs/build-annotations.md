# Correção das anotações de build

## Exceção do KSP

O build 42793756538 concluía com sucesso, mas o KSP 2.3.5 lançava uma NullPointerException na thread AWT durante o processamento de anotações. O GitHub registrava essa exceção como anotação de erro.

O defeito foi confirmado no upstream: https://github.com/google/ksp/issues/2763.
A atualização para KSP 2.3.6 contém a correção específica: https://github.com/google/ksp/releases/tag/2.3.6. Kotlin, AGP, Room e Moshi permanecem nas versões existentes; não foi necessário mascarar saída ou capturar a exceção.

## Actions e sistema operacional

Todos os workflows usam Actions mantidas com runtime Node.js 24:

- `actions/checkout@v7`
- `actions/setup-java@v6` (JDK 21 preservado)
- `gradle/actions/setup-gradle@v6`
- `actions/upload-artifact@v7`
- `softprops/action-gh-release@v3`, no workflow de publicação

O `reactivecircus/android-emulator-runner@v2` já usa Node.js 24 no ref atual. Os arquivos `action.yml` upstream foram conferidos antes da atualização. Inputs de build, assinatura, artefatos e release foram preservados.

Os jobs usam `ubuntu-24.04`, incluindo a Action principal. Isso define um ambiente explícito e reproduzível, em vez de depender da mudança automática de `ubuntu-latest` para Ubuntu 26.

Não há filtros de log, supressão de warnings, `continue-on-error` ou variáveis para forçar runtime obsoleto. A verificação inclui compilação e testes com KSP reexecutado sem build cache, além de uma nova execução real da Action e consulta das anotações pela API do GitHub.
