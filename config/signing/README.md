# Assinatura pública de teste

`mangareader-public-test.keystore` é uma chave de teste deliberadamente pública,
criada para builds pessoais do APK. Alias e senhas: `androiddebugkey` / `android`.
Ela não contém a chave privada usada pelas instalações antigas e não é uma chave
de produção. Não a use para publicar na Play Store nem para proteger um app público.

O arquivo é fixo: não regenere a chave em cada Action. A identidade deve continuar
igual para permitir atualização entre builds que usam esta chave.

Quando `MANGA_SIGNING_KEYSTORE_BASE64` estiver configurado, os workflows usam esse
Secret em vez da chave de teste. Nunca comite uma chave privada real neste diretório.
