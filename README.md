# MiBox Browser

Navegador simples para Android TV (Mi Box 4K): bloqueia anúncios, rastreadores, domínios maliciosos e todas as janelas pop-up.

## Como usar o controle remoto
- Setas: movem o ponteiro na tela (encostando na borda, a página rola)
- OK (clique rápido): clica
- OK (segurar ~1 segundo): abre o menu (endereço, voltar, recarregar, atualizar lista...)
- Voltar: volta uma página (na primeira página, fecha o app)

## Como gerar o APK (sem instalar nada no computador)
1. Crie uma conta grátis em github.com
2. Crie um repositório novo, **público**, chamado `mibox-browser`
3. Extraia este ZIP e arraste TODO o conteúdo (inclusive a pasta `.github`) para o repositório (Add file > Upload files) e clique em Commit
   - Se a pasta `.github` não subir: Add file > Create new file, digite `.github/workflows/build.yml` e cole o conteúdo do arquivo `workflow-build.yml`
4. Abra a aba **Actions**, aguarde o "Gerar APK" ficar verde (cerca de 5 a 10 minutos)
5. Em **Releases** (coluna da direita) estará o arquivo `MiBoxBrowser.apk`

## Instalar na Mi Box
1. Na Mi Box, instale o app **Downloader** (Play Store)
2. Configurações > Preferências do dispositivo > Segurança e restrições > Fontes desconhecidas > ative para o Downloader
3. No Downloader, digite: `github.com/SEU_USUARIO/mibox-browser/releases/latest/download/MiBoxBrowser.apk`
4. Instale. O app aparece na lista de aplicativos como "MiBox Browser"
