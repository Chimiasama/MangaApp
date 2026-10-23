# 🗺️ Plano de Implantação de Melhorias — Manga Reader

Este documento apresenta o plano estruturado de desenvolvimento e implantação de melhorias no aplicativo **Manga Reader**, contemplando aprimoramentos de UX/UI, desempenho, novas fontes de leitura, integração com APIs de rastreamento (AniList/MyAnimeList) e modernização arquitetural.

> **Nota:** A funcionalidade de importação de regras JSON via URL externa foi explicitamente descartada conforme escopo definido.

---

## 📅 Visão Geral das Fases

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ FASE 1: Otimizações de Desempenho e Recursos do Leitor (UX/UI & Performance)│
├─────────────────────────────────────────────────────────────────────────────┤
│ FASE 2: Expansão de Fontes Gratuitas de Leitura (Consumet & Comick API)     │
├─────────────────────────────────────────────────────────────────────────────┤
│ FASE 3: Sincronização e Metadados com AniList & MyAnimeList (Jikan)         │
├─────────────────────────────────────────────────────────────────────────────┤
│ FASE 4: Modernização Arquitetural (Injeção de Dependência com Hilt)         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 FASE 1: Otimizações de Desempenho e UX/UI do Leitor

### 1.1 Pré-carregamento Inteligente de Páginas (Pre-fetching)
- **Objetivo:** Eliminar o atraso de carregamento de imagem ao avançar de página no leitor.
- **Implementação:**
  - Criar um `PagePreloaderManager` utilizando a instância do `Coil` (`imageLoader.enqueue`).
  - No `ReaderScreen`, monitorar o índice atual (`currentPageIndex`) e disparar o pré-carregamento assíncrono das próximas 2 páginas e das 2 páginas anteriores.
  - Ao atingir as últimas páginas de um capítulo, realizar pré-carregamento da capa e das 2 primeiras páginas do capítulo seguinte.

### 1.2 Gerenciamento e Limpeza de Cache de Imagens
- **Objetivo:** Permitir ao usuário controlar o armazenamento interno consumido por capas e páginas salvas em cache.
- **Implementação:**
  - Adicionar na tela `SettingsScreen` uma opção "Espaço ocupado em Cache".
  - Calcular o tamanho do diretório de cache do Coil (`context.cacheDir`).
  - Criar o botão "Limpar Cache de Imagens" com callback no `SettingsViewModel` invocando `Coil.imageLoader(context).diskCache?.clear()`.

### 1.3 Filtro e Ordenação de Capítulos na Tela de Detalhes
- **Objetivo:** Facilitar a navegação em obras longas (com centenas de capítulos).
- **Implementação:**
  - Na `MangaDetailScreen`:
    - Adicionar botão de alternância de ordem (`Crescente ⬆️` / `Decrescente ⬇️`).
    - Adicionar campo de busca rápida de capítulos por número ou título.
    - Adicionar filtro rápido por idioma (`Todos`, `PT-BR`, `EN`, `ES`).

### 1.4 Aprimoramento de Controles e Modos do Leitor
- **Objetivo:** Oferecer personalização avançada durante a leitura.
- **Implementação:**
  - Adicionar seletor de cor de fundo do leitor: Preto OLED (`#000000`), Cinza Escuro (`#121212`), Sépia (`#F4ECD8`) e Branco (`#FFFFFF`).
  - Implementar modo de inversão de cores (Filtro de Inversão Noturna em nível de Canvas/GraphicsLayer no Compose).
  - Exibir indicador sutil com nível de bateria e relógio no overlay de controles do leitor.

---

## 🌐 FASE 2: Expansão de Fontes Gratuitas de Leitura

### 2.1 Integração com Consumet API (`Manga Read Provider`)
- **Objetivo:** Adicionar fonte pública e gratuita agregadora de capítulos em JSON.
- **Implementação:**
  - Criar `ConsumetApiService` via Retrofit com suporte aos endpoints `/manga/mangadex` e `/manga/mangakakalot`.
  - Criar a classe `ConsumetMangaSource` implementando a interface `MangaSource`.
  - Mapear respostas DTO para `MangaModel` e `ChapterModel`.
  - Registrar a nova fonte no `sourceRegistry` do `MangaRepository`.

### 2.2 Integração com Comick API (`api.comick.fun`)
- **Objetivo:** Adicionar acervo de webtoons, manhuas e mangás traduzidos em PT-BR e EN.
- **Implementação:**
  - Criar `ComickApiService` com suporte a buscas de títulos, detalhes e lista de páginas.
  - Tratar cabeçalhos de requisição (`User-Agent` personalizado) para garantir estabilidade e evitar limitações.
  - Criar `ComickSource` implementando `MangaSource`.
  - Adicionar suporte a identificadores de tradução PT-BR na busca.

---

## 📚 FASE 3: Sincronização de Progresso com AniList & MyAnimeList (Jikan)

### 3.1 Conexão com AniList GraphQL API
- **Objetivo:** Permitir ao usuário fazer login com sua conta AniList e atualizar automaticamente seu progresso de leitura na nuvem.
- **Implementação:**
  - Configurar cliente GraphQL / Retrofit para a API `https://graphql.anilist.co`.
  - Criar fluxo de autenticação OAuth2 no `SettingsScreen` (Redirecionamento Web/Deep Link para capturar token de acesso).
  - Ao concluir a leitura de um capítulo no `MangaViewModel` (`persistCurrentScrollPosition` / `navigateChapter`), disparar mutação GraphQL atualizando `SaveMediaListEntry(mediaId, progress)`.

### 3.2 Metadados e Recomendações com Jikan API (MyAnimeList)
- **Objetivo:** Exibir dados de populares, tendências e recomendações sem necessidade de autenticação.
- **Implementação:**
  - Criar `JikanApiService` comunicando com `https://api.jikan.moe/v4`.
  - Adicionar seção "Relacionados & Recomendados" na `MangaDetailScreen` utilizando a lista de recomendações do MyAnimeList.

---

## 🏗️ FASE 4: Modernização Arquitetural com Hilt

### 4.1 Injeção de Dependências com Android Hilt
- **Objetivo:** Simplificar a instanciação de ViewModels, Repositories e DAOs, removendo Factories manuais.
- **Implementação:**
  - Adicionar dependências `@hilt/android-gradle-plugin` e `com.google.dagger:hilt-android`.
  - Criar classe `MangaApplication : Application` anotada com `@HiltAndroidApp`.
  - Criar módulos de injeção Dagger/Hilt:
    - `DatabaseModule`: Prove `AppDatabase`, `MangaDao`, `ProgressDao`, `DownloadDao`, `BookmarkDao`.
    - `NetworkModule`: Prove OkHttpClient, Retrofit, `MangaDexApiService`, `ConsumetApiService`, `ComickApiService`.
    - `RepositoryModule`: Prove `MangaRepository` e `UserPreferencesRepository`.
  - Anotar `MainActivity` com `@AndroidEntryPoint` e substituir `by viewModels { factory }` por `hiltViewModel()`.

---

## 📍 Localização deste Documento no Projeto

Este plano de implantação está localizado em:
`app/docs/PLANO_DE_IMPLANTACAO.md`

*(Caminho relativo à raiz do repositório: `app/docs/PLANO_DE_IMPLANTACAO.md`)*
