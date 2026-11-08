# Publicação e atualizações pelo GitHub

O app consulta `repos/Chimiasama/MangaApp/releases/latest`, compara a tag com `BuildConfig.VERSION_NAME` e oferece o APK anexado. Um artefato de Actions sozinho não entra nessa consulta. Toda versão publicada precisa aumentar `versionName` e `versionCode`.

## Assinatura fixa, sem Secret obrigatório

Os workflows antigos geravam um novo `debug.keystore` em cada execução. Isso faz o Android rejeitar atualização sobre uma instalação com outra assinatura, mesmo quando a versão aumenta. O log da compilação 42119579510 confirma que uma chave nova foi gerada. Não é possível recuperar a chave privada a partir do APK ou do certificado público.

Ambos os workflows usam o script `.github/scripts/prepare-debug-signing.sh`:

1. Com `MANGA_SIGNING_KEYSTORE_BASE64`, restauram a chave configurada no Secret.
2. Sem o Secret, usam a chave **pública de teste** fixa em `config/signing/mangareader-public-test.keystore`. Isso permite gerar o APK sem configuração adicional. A chave é intencionalmente pública, não uma credencial privada de produção.

O Secret é opcional. Ele deve conter um keystore com alias `androiddebugkey` e senha de armazenamento/chave `android`, conforme o perfil do projeto. Uma chave configurada inválida causa erro em vez de ser substituída silenciosamente. Os dois workflows e o build `release` sem chave privada usam a mesma identidade de teste.

Com a chave correta disponível e uma autenticação administrativa própria no GitHub CLI, execute na pasta em que ela está:

```sh
base64 < debug.keystore | gh secret set MANGA_SIGNING_KEYSTORE_BASE64 --repo Chimiasama/MangaApp
```

Também é possível cadastrar o conteúdo base64 em Settings → Secrets and variables → Actions → New repository secret. Não publique o arquivo ou seu base64 no repositório.

**Use a chave que assinou a instalação existente.** Uma chave local diferente estabelece uma identidade fixa para as próximas versões, mas não torna compatíveis APKs antigos de assinatura diferente. Se a chave temporária original não foi preservada, a atualização sobre ela não pode ser garantida; não desinstale o app sem antes proteger seus dados.

## Publicar

Para publicar usando o Secret opcional ou a assinatura de teste:

```sh
gh workflow run release.yml --repo Chimiasama/MangaApp --ref main -f tag_name=v1.1.1
```

O workflow publica uma Release estável, marcada como Latest, com o APK e a tag da versão. Também pode ser acionado por tags `v*`; commits comuns não publicam mais cópias com o mesmo número de versão e um hash no final.

No celular: Ajustes → Atualizações → Verificar atualizações no GitHub. O repositório padrão é `chimiasama/MangaApp`. O download usa o APK público da Release, sem login necessário no telefone. A instalação ainda exige a confirmação normal do Android.
