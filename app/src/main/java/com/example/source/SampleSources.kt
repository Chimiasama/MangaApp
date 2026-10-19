package com.example.source

object SampleSources {

    val sampleScraperRulesJson = """
    [
      {
        "id": "manga_pt_scraper",
        "name": "Manga PT Scraper (Regras JSON)",
        "baseUrl": "https://manga-pt-example.com",
        "isNsfw": false,
        "lang": "pt-BR",
        "listSelector": "div.manga-card",
        "titleSelector": "h3.title, a.title-link",
        "coverAttrSelector": "img.cover-img",
        "linkAttrSelector": "a.manga-link",
        "detailDescSelector": "div.synopsis, p.description",
        "detailAuthorSelector": "span.author-name",
        "chapterListSelector": "ul.chapters-list > li",
        "chapterTitleSelector": "span.chapter-title",
        "chapterLinkSelector": "a.chapter-url",
        "pageImageSelector": "div.reader-images img",
        "pageImageAttr": "src"
      },
      {
        "id": "hentai_adult_source",
        "name": "Hentai Vault (+18 Restricted)",
        "baseUrl": "https://hentai-vault-example.com",
        "isNsfw": true,
        "lang": "pt-BR",
        "listSelector": "div.adult-item",
        "titleSelector": "h4.item-title",
        "coverAttrSelector": "img.thumb",
        "linkAttrSelector": "a.item-link",
        "detailDescSelector": "p.adult-desc",
        "detailAuthorSelector": "span.artist",
        "chapterListSelector": "div.chapter-row",
        "chapterTitleSelector": "span.ch-name",
        "chapterLinkSelector": "a.ch-link",
        "pageImageSelector": "div.adult-reader img",
        "pageImageAttr": "src"
      }
    ]
    """.trimIndent()

    class DemoMockSource(
        override val id: String,
        override val name: String,
        override val baseUrl: String,
        override val isNsfw: Boolean,
        override val lang: String = "pt-BR"
    ) : MangaSource {

        private val items = if (isNsfw) {
            listOf(
                MangaModel(
                    url = "$baseUrl/manga/hentai_1",
                    id = "hentai_1",
                    title = "[+18] Secret Romance Uncensored",
                    coverUrl = "https://picsum.photos/seed/nsfw1/300/450",
                    description = "Conteúdo maduro e restrito para maiores de 18 anos.",
                    genres = listOf("Hentai", "+18", "Romance", "Adulto"),
                    isNsfw = true,
                    sourceId = id,
                    author = "Red Studio"
                ),
                MangaModel(
                    url = "$baseUrl/manga/hentai_2",
                    id = "hentai_2",
                    title = "[+18] Midnight Stories Vol 2",
                    coverUrl = "https://picsum.photos/seed/nsfw2/300/450",
                    description = "Coletânea de histórias adultas.",
                    genres = listOf("Hentai", "Drama", "+18", "Adulto"),
                    isNsfw = true,
                    sourceId = id,
                    author = "Akira X"
                )
            )
        } else {
            listOf(
                MangaModel(
                    url = "$baseUrl/manga/naruto_ninja",
                    id = "naruto_ninja",
                    title = "Naruto Shippuden: O Caminho Ninja",
                    coverUrl = "https://picsum.photos/seed/naruto/300/450",
                    description = "A jornada de um jovem ninja determinado a se tornar Hokage de sua vila com jutsus e batalhas épicas.",
                    genres = listOf("Ninja", "Shounen", "Ação", "Artes Marciais"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Masashi Kishimoto"
                ),
                MangaModel(
                    url = "$baseUrl/manga/cyberpunk_scifi",
                    id = "cyberpunk_scifi",
                    title = "Cyber Horizon 2099",
                    coverUrl = "https://picsum.photos/seed/scifi1/300/450",
                    description = "Em uma metrópole futurista cheia de ciborgues e inteligências artificiais, uma androide busca sua humanidade.",
                    genres = listOf("Sci-Fi", "Seinen", "Cyberpunk", "Ação"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Kenji Sato"
                ),
                MangaModel(
                    url = "$baseUrl/manga/bloom_into_you",
                    id = "bloom_into_you",
                    title = "Bloom Into You (Florescer em Você)",
                    coverUrl = "https://picsum.photos/seed/yuri1/300/450",
                    description = "Uma emocionante história de romance e descoberta entre duas estudantes do ensino médio.",
                    genres = listOf("Yuri", "Shoujo", "Romance", "Escolar"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Nio Nakatani"
                ),
                MangaModel(
                    url = "$baseUrl/manga/fruits_basket",
                    id = "fruits_basket",
                    title = "Fruits Basket",
                    coverUrl = "https://picsum.photos/seed/shoujo1/300/450",
                    description = "Tohru Honda descobre o segredo da família Sohma, cujos membros se transformam em animais do zodíaco chinês.",
                    genres = listOf("Shoujo", "Romance", "Drama", "Sobrenatural"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Natsuki Takaya"
                ),
                MangaModel(
                    url = "$baseUrl/manga/solo_leveling",
                    id = "solo_leveling",
                    title = "Solo Leveling (Ascensão Solitária)",
                    coverUrl = "https://picsum.photos/seed/solo/300/450",
                    description = "Em um mundo onde caçadores enfrentam monstros mortais, Sung Jinwoo se torna o caçador mais forte do mundo.",
                    genres = listOf("Ação", "Fantasia", "Isekai", "Shounen"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Chugong"
                ),
                MangaModel(
                    url = "$baseUrl/manga/one_piece",
                    id = "one_piece",
                    title = "One Piece",
                    coverUrl = "https://picsum.photos/seed/onepiece/300/450",
                    description = "Monkey D. Luffy viaja pelos mares em busca do tesouro lendário One Piece para se tornar o Rei dos Piratas.",
                    genres = listOf("Aventura", "Shounen", "Comédia", "Ação"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Eiichiro Oda"
                ),
                MangaModel(
                    url = "$baseUrl/manga/basilisk_ninja",
                    id = "basilisk_ninja",
                    title = "Basilisk: A Lenda dos Ninjas Koga e Iga",
                    coverUrl = "https://picsum.photos/seed/ninja2/300/450",
                    description = "Dois clãs rivais de ninjas disputam a sucessão do xogunato em combates mortais com técnicas lendárias.",
                    genres = listOf("Ninja", "Seinen", "Ação", "Drama"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Futaro Yamada"
                ),
                MangaModel(
                    url = "$baseUrl/manga/jujutsu_kaisen",
                    id = "jujutsu_kaisen",
                    title = "Jujutsu Kaisen",
                    coverUrl = "https://picsum.photos/seed/jujutsu/300/450",
                    description = "Yuji Itadori engole um objeto amaldiçoado e entra no mundo dos Feiticeiros Jujutsu.",
                    genres = listOf("Sobrenatural", "Shounen", "Ação", "Escolar"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Gege Akutami"
                ),
                MangaModel(
                    url = "$baseUrl/manga/citrus_yuri",
                    id = "citrus_yuri",
                    title = "Citrus: Amizade e Paixão",
                    coverUrl = "https://picsum.photos/seed/yuri2/300/450",
                    description = "Yuzuko descobre novos sentimentos ao conviver com a conselheira estudantil da nova escola.",
                    genres = listOf("Yuri", "Romance", "Drama", "Escolar"),
                    isNsfw = false,
                    sourceId = id,
                    author = "Saburouta"
                ),
                MangaModel(
                    url = "$baseUrl/manga/steins_gate_scifi",
                    id = "steins_gate_scifi",
                    title = "Steins;Gate: Viagem no Tempo",
                    coverUrl = "https://picsum.photos/seed/scifi2/300/450",
                    description = "Um grupo de amigos acidentalmente cria um micro-ondas capaz de enviar mensagens para o passado.",
                    genres = listOf("Sci-Fi", "Mistério", "Seinen", "Thriller"),
                    isNsfw = false,
                    sourceId = id,
                    author = "5pb. & Nitroplus"
                )
            )
        }

        override suspend fun getPopularManga(): List<MangaModel> = items

        override suspend fun searchManga(query: String): List<MangaModel> {
            return items.filter { it.title.contains(query, ignoreCase = true) || it.genres.any { g -> g.contains(query, ignoreCase = true) } }
        }

        override suspend fun getMangaDetails(mangaUrl: String): MangaModel {
            return items.find { it.url == mangaUrl } ?: items.first()
        }

        override suspend fun getChapters(mangaUrl: String): List<ChapterModel> {
            val list = mutableListOf<ChapterModel>()
            for (i in 15 downTo 1) {
                list.add(
                    ChapterModel(
                        id = "ch_$i",
                        title = "Capítulo 0$i - Batalha Decisiva",
                        chapterUrl = "$mangaUrl/capitulo-$i",
                        chapterNumber = i.toFloat(),
                        dateUpload = "2026-07-${10 + (i % 15)}"
                    )
                )
            }
            return list
        }

        override suspend fun getPages(chapterUrl: String): List<String> {
            return listOf(
                "https://picsum.photos/seed/manga_p1/800/1200",
                "https://picsum.photos/seed/manga_p2/800/1200",
                "https://picsum.photos/seed/manga_p3/800/1200",
                "https://picsum.photos/seed/manga_p4/800/1200",
                "https://picsum.photos/seed/manga_p5/800/1200"
            )
        }
    }
}
