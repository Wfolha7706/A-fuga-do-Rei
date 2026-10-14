# Jungle Climb: A Fuga do Rei

Jogo arcade de plataforma e escalada 100% nativo em **Kotlin**, projetado para alto desempenho em dispositivos móveis Android (API 24+ / Android 7.0 a 14+), rodando a **60 FPS cravados** em orientação **Paisagem (Landscape)**.

---

## 🎮 Destaques Técnicos

1. **Zero Dependência Externa (Gráficos e Áudio Vetoriais/Sintetizados)**:
   - **Áudio em Tempo Real**: `SoundEngine.kt` gera ondas PCM em tempo real via `AudioTrack` e `VibrationEffect` do Android SDK. Não há arquivos `.mp3`, `.wav` ou `.ogg` que possam corromper ou inflar o APK.
   - **Renderização 2D Nativa**: Todo o visual (Aventureiro Leo, Gorila Rei Korg, Maya, barris rolantes, pedras, esteiras rolantes e efeitos de partículas) é desenhado proceduralmente via `Canvas` com aceleração por hardware e espaço de coordenadas virtual fixo (`1280x720`).
   - **Tamanho Ultraleve**: O APK final pesa apenas ~2 MB.

2. **12 Fases Completas Divididas em 4 Mundos Temáticos**:
   - **Mundo 1: Canteiro Caótico** (Fases 1 a 3) — Vigas metálicas inclinadas, escadas de obra e barris rolantes clássicos. Chefe: Guindaste do Rei.
   - **Mundo 2: Selva Vertical** (Fases 4 a 6) — Plataformas com lacunas que exigem saltos precisos, cipós oscilantes e pedras quicantes. Chefe: Copa Selvagem.
   - **Mundo 3: Fábrica Abandonada** (Fases 7 a 9) — Esteiras rolantes motorizadas, plataformas móveis e pisos elétricos com alternância periódica. Chefe: Fornalha Central.
   - **Mundo 4: Fortaleza do Gorila** (Fases 10 a 12) — Desafio máximo unindo armadilhas temporizadas, esteiras velozes e confrontos com o Rei Korg através de ativação de alavancas de sustentação.

3. **Controles Mobile Multitouch Responsivos**:
   - Direcionais contínuos (Esquerda, Direita, Subir e Descer) e botão dedicado de Pulo.
   - Suporte completo a múltiplos toques simultâneos com rastreamento por ID de ponteiro (`ACTION_POINTER_UP`).
   - Opção de redimensionamento dos botões (Pequeno, Médio, Grande) nas Configurações.

4. **Persistência Segura (`SaveManager`)**:
   - Salvamento local via `SharedPreferences`.
   - Gravação de progresso de fases desbloqueadas (1 a 12), pontuação máxima global, recorde individual por fase e medalhas de desempenho (Bronze, Prata e Ouro).

---

## 📁 Estrutura do Projeto

```text
JungleClimb/
├── .github/
│   └── workflows/
│       └── android-apk.yml          # CI/CD: Compilação automática do APK no GitHub Actions
├── app/
│   ├── build.gradle.kts             # Módulo Android, SDK 34, MinSdk 24, assinatura de release
│   └── src/main/
│       ├── AndroidManifest.xml      # Fullscreen, sensorLandscape, permissão de vibração
│       └── java/com/jungleclimb/afugadorei/
│           ├── MainActivity.kt      # Ciclo de vida, modo imersivo e tratamento de botão Voltar
│           ├── SaveManager.kt       # Persistência de progresso, recordes e opções
│           ├── SoundEngine.kt       # Sintetizador de áudio PCM mono 22050Hz + feedback tátil
│           ├── Entities.kt          # Jogador, Gorila, Barris, Inimigos e Plataformas
│           ├── LevelData.kt         # Arquitetura das 12 fases e chefes
│           └── GameView.kt          # Game Loop 60 FPS, física, colisões, multitouch e menus
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties # Configuração do Gradle 8.5
├── build.gradle.kts                 # Plugins Android e Kotlin raiz
├── settings.gradle.kts              # Repositórios Maven/Google e nome do projeto
├── gradle.properties                # Otimizações de memória e AndroidX
├── setup_and_build.sh               # Script de compilação para Linux/macOS/Git Bash
├── setup_and_build.bat              # Script de compilação para Windows
└── README.md                        # Documentação do projeto
```

---

## 🚀 Como Compilar o APK

### Opção 1: Direto na Nuvem via GitHub Actions (Zero Instalação)
1. Crie um repositório no seu GitHub e suba este código:
   ```bash
   git init
   git add .
   git commit -m "Initial commit - Jungle Climb"
   git remote add origin https://github.com/SEU_USUARIO/JungleClimb.git
   git push -u origin main
   ```
2. Acesse a aba **Actions** no seu GitHub. O fluxo **Build Jungle Climb APK** será acionado automaticamente.
3. Ao finalizar (~2 minutos), clique na execução e baixe o artefato **JungleClimb-Android-APK** pronto para instalar no smartphone!

---

### Opção 2: Compilação Local no Computador
Caso você tenha o OpenJDK 17 e o Android SDK / Gradle instalados:

**No Linux / macOS / Git Bash:**
```bash
chmod +x setup_and_build.sh
./setup_and_build.sh
```
Ou diretamente:
```bash
gradle assembleRelease
```

**No Windows:**
Execute o script:
```cmd
setup_and_build.bat
```

O arquivo `.apk` final será gerado em:
```text
app/build/outputs/apk/release/app-release.apk
```

---

## 📲 Instalação no Dispositivo Android

Após transferir o arquivo para o celular ou via cabo USB (ADB):
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 🕹️ Controles e Gameplay

- **Correr**: Botões ◀ e ▶
- **Escadas**: Botões ▲ e ▼ quando alinhado com uma escada
- **Pulo**: Botão `PULO` (pula sobre barris para ganhar 100 pontos extras)
- **Martelo**: Ao coletar o item de martelo, Leo destrói qualquer barril ou inimigo com um toque por 7.5 segundos!
- **Alavancas do Chefe**: Nas fases 3, 6, 9 e 12, passe por todas as alavancas rosas no topo para derrubar o Rei Korg e concluir o mundo!
