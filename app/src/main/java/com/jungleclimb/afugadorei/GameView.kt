package com.jungleclimb.afugadorei

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

enum class ScreenState {
    SPLASH, MAIN_MENU, LEVEL_SELECT, HIGH_SCORES, SETTINGS, CREDITS,
    PLAYING, PAUSED, LEVEL_VICTORY, GAME_OVER, CAMPAIGN_VICTORY
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var renderThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var surfaceReady = false

    private val saveManager = SaveManager(context)
    private val soundEngine = SoundEngine(context, saveManager)

    private var screenState = ScreenState.SPLASH
    private var splashTimer = 0f

    // Escala responsiva da tela física para a resolução virtual 1280x720
    private var scaleX = 1f
    private var scaleY = 1f

    // Estado da Campanha e Fase Atual
    private var currentLevelNum = 1
    private lateinit var level: LevelSpec
    private lateinit var player: Player
    private lateinit var boss: BossGorilla
    private val barrels = mutableListOf<BarrelObstacle>()
    private val particles = mutableListOf<Particle>()

    private var lives = 3
    private var score = 0
    private var campaignTotalScore = 0
    private var levelTimer = 100f
    private var elapsedLevelTime = 0f
    private var checkpointReached = false
    private var lostLifeInCurrentLevel = false
    private var lastMedalEarned = 0
    private var cameraShakeTimer = 0f

    // Estado dos botões de toque (Multitouch seguro)
    private var touchLeft = false
    private var touchRight = false
    private var touchUp = false
    private var touchDown = false
    private var touchJump = false
    private var jumpConsumed = false

    // Botões de toque na tela (calculados conforme tamanho escolhido nas configurações)
    private val btnLeft = RectF()
    private val btnRight = RectF()
    private val btnUp = RectF()
    private val btnDown = RectF()
    private val btnJump = RectF()
    private val btnPause = RectF(1190f, 18f, 1260f, 74f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        loadLevel(1, resetCampaign = true)
    }

    private fun updateTouchButtonRects() {
        val s = saveManager.getControlScaleFactor()
        val r = 52f * s
        btnLeft.set(45f, 560f - r, 45f + r * 2, 560f + r)
        btnRight.set(175f + (s - 1f) * 40f, 560f - r, 175f + (s - 1f) * 40f + r * 2, 560f + r)
        btnUp.set(110f + (s - 1f) * 20f, 465f - r, 110f + (s - 1f) * 20f + r * 2, 465f + r)
        btnDown.set(110f + (s - 1f) * 20f, 645f - r, 110f + (s - 1f) * 20f + r * 2, 645f + r)

        val jr = 62f * s
        btnJump.set(1120f - jr, 575f - jr, 1120f + jr, 575f + jr)
    }

    private fun loadLevel(levelNumber: Int, resetCampaign: Boolean = false) {
        if (resetCampaign) {
            lives = 3
            score = 0
            campaignTotalScore = 0
        }
        currentLevelNum = levelNumber.coerceIn(1, 12)
        level = LevelRepository.createLevel(currentLevelNum)
        checkpointReached = false
        lostLifeInCurrentLevel = false
        levelTimer = level.timeLimitSeconds.toFloat()
        elapsedLevelTime = 0f
        barrels.clear()
        particles.clear()

        player = Player(level.startX, level.startY)
        boss = BossGorilla(
            x = level.bossX,
            y = level.bossY,
            spawnInterval = level.barrelSpawnInterval,
            maxSwitches = level.bossSwitches.size.coerceAtLeast(3)
        )
        updateTouchButtonRects()
    }

    private fun respawnPlayerAfterHit() {
        barrels.clear()
        val spawnX = if (checkpointReached) level.checkpointX else level.startX
        val spawnY = if (checkpointReached) level.checkpointY else level.startY
        player = Player(spawnX, spawnY).apply {
            invulnerableTimer = 2.5f
        }
    }

    fun resumeGame() {
        if (!surfaceReady) return
        startThread()
    }

    private fun startThread() {
        if (running) return
        running = true
        renderThread = Thread(this).apply {
            isDaemon = true
            start()
        }
        if (screenState == ScreenState.PLAYING) {
            soundEngine.startMusic(level.worldNumber)
        } else if (screenState == ScreenState.MAIN_MENU) {
            soundEngine.startMusic(0)
        }
    }

    fun pauseGame() {
        running = false
        clearTouchStates()
        soundEngine.stopMusic()
        try {
            renderThread?.join(300)
        } catch (_: Exception) {}
        renderThread = null
    }

    fun handleBackPressed(): Boolean {
        return when (screenState) {
            ScreenState.PLAYING -> {
                screenState = ScreenState.PAUSED
                clearTouchStates()
                true
            }
            ScreenState.PAUSED, ScreenState.LEVEL_SELECT,
            ScreenState.HIGH_SCORES, ScreenState.SETTINGS, ScreenState.CREDITS -> {
                screenState = ScreenState.MAIN_MENU
                soundEngine.startMusic(0)
                true
            }
            else -> false
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            scaleX = width.toFloat() / VIRTUAL_W
            scaleY = height.toFloat() / VIRTUAL_H
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        pauseGame()
    }

    override fun run() {
        var lastTime = System.nanoTime()
        while (running && surfaceReady) {
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
            lastTime = now

            try {
                update(dt)
                drawFrame()
            } catch (_: Exception) {}

            val frameTimeMs = (System.nanoTime() - now) / 1_000_000
            if (frameTimeMs < 16) {
                try {
                    Thread.sleep(16 - frameTimeMs)
                } catch (_: Exception) {}
            }
        }
    }

    private fun update(dt: Float) {
        if (screenState == ScreenState.SPLASH) {
            splashTimer += dt
            if (splashTimer > 2.0f) {
                screenState = ScreenState.MAIN_MENU
                soundEngine.startMusic(0)
            }
            return
        }

        if (screenState != ScreenState.PLAYING) return

        elapsedLevelTime += dt
        levelTimer = (levelTimer - dt).coerceAtLeast(0f)
        if (cameraShakeTimer > 0f) cameraShakeTimer -= dt

        if (levelTimer <= 0f) {
            triggerPlayerDamage()
            return
        }

        // Atualiza plataformas móveis e armadilhas
        level.platforms.forEach { it.update(dt, elapsedLevelTime) }

        // Atualiza jogador
        updatePlayer(dt)

        // Atualiza Rei Korg e geração de obstáculos
        updateBossAndBarrels(dt)

        // Atualiza inimigos secundários
        updateEnemies(dt)

        // Atualiza coletáveis e checkpoints
        updateCollectibles()

        // Atualiza partículas
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 400f * dt
            p.life -= dt
            if (p.life <= 0f) pit.remove()
        }
    }

    private fun updatePlayer(dt: Float) {
        if (player.invulnerableTimer > 0f) player.invulnerableTimer -= dt
        if (player.hammerTimer > 0f) player.hammerTimer -= dt
        player.animTimer += dt

        val moveSpeed = 235f
        val climbSpeed = 165f
        val gravity = 980f
        val jumpVelocity = -445f

        // Verifica proximidade com escadas
        val playerRect = player.getBounds()
        val overlappingLadder = level.ladders.firstOrNull {
            !it.isBroken && RectF.intersects(playerRect, it.getBounds())
        }

        if (overlappingLadder != null && (touchUp || touchDown)) {
            player.onLadder = true
            player.x = overlappingLadder.x
            player.vx = 0f
            player.vy = 0f
        }

        if (player.onLadder) {
            player.state = PlayerAnimState.CLIMB
            when {
                touchUp -> {
                    player.y -= climbSpeed * dt
                    if ((player.animTimer * 10).toInt() % 3 == 0) soundEngine.playClimb()
                }
                touchDown -> {
                    player.y += climbSpeed * dt
                    if ((player.animTimer * 10).toInt() % 3 == 0) soundEngine.playClimb()
                }
            }
            if (overlappingLadder == null || player.y <= overlappingLadder.topY + 4f || player.y >= overlappingLadder.bottomY) {
                player.onLadder = false
            }
            if (touchJump && !jumpConsumed) {
                player.onLadder = false
                player.vy = jumpVelocity * 0.85f
                jumpConsumed = true
                soundEngine.playJump()
            }
            return
        }

        // Movimento Horizontal
        player.vx = when {
            touchLeft -> {
                player.facingRight = false
                -moveSpeed
            }
            touchRight -> {
                player.facingRight = true
                moveSpeed
            }
            else -> 0f
        }

        // Pulo
        if (touchJump && !jumpConsumed && player.onGround) {
            player.vy = jumpVelocity
            player.onGround = false
            jumpConsumed = true
            soundEngine.playJump()
        }

        // Aplica gravidade e deslocamento
        val prevY = player.y
        player.x = (player.x + player.vx * dt).coerceIn(95f, 1185f)
        player.y += player.vy * dt
        player.vy += gravity * dt

        // Colisão com Plataformas
        player.onGround = false
        for (plat in level.platforms) {
            if (player.x >= plat.x1 - 10f && player.x <= plat.x2 + 10f) {
                val platY = plat.getYAt(player.x)
                if (player.vy >= 0f && prevY <= platY + 14f && player.y >= platY - 4f) {
                    if (plat.isTrap && plat.trapActive) {
                        triggerPlayerDamage()
                        return
                    }
                    if (!player.onGround && player.vy > 220f) {
                        spawnDustParticles(player.x, platY)
                    }
                    player.y = platY
                    player.vy = 0f
                    player.onGround = true
                    if (plat.conveyorSpeed != 0f) {
                        player.x = (player.x + plat.conveyorSpeed * dt).coerceIn(95f, 1185f)
                    }
                    break
                }
            }
        }

        // Queda fora da estrutura
        if (player.y > VIRTUAL_H + 40f) {
            triggerPlayerDamage()
            return
        }

        // Define estado de animação
        player.state = when {
            !player.onGround && player.vy < 0f -> PlayerAnimState.JUMP
            !player.onGround -> PlayerAnimState.FALL
            abs(player.vx) > 5f -> PlayerAnimState.RUN
            else -> PlayerAnimState.IDLE
        }

        // Verifica chegada ao objetivo (resgate da Maya no topo)
        val distToGoal = hypot(player.x - level.goalX, player.y - level.goalY)
        val bossDefeatedOrNormal = !level.isBossLevel || boss.switchesActivated >= boss.maxSwitches || distToGoal < 42f
        if (distToGoal < 45f && bossDefeatedOrNormal) {
            completeCurrentLevel()
        }
    }

    private fun updateBossAndBarrels(dt: Float) {
        boss.timer -= dt
        boss.animClock += dt
        if (boss.timer <= 0f) {
            boss.timer = boss.spawnInterval
            boss.animState = 1
            // Nunca spawna diretamente em cima do jogador de forma inevitável
            val safeSpawnX = if (abs(player.x - boss.x) < 90f && player.y < 180f) boss.x + 120f else boss.x + 35f
            val obstacleType = when {
                level.worldNumber == 2 && (barrels.size % 3 == 1) -> ObstacleType.BOUNCING_ROCK
                level.worldNumber >= 3 && (barrels.size % 3 == 2) -> ObstacleType.FIRE_SPIRIT
                else -> ObstacleType.ROLLING_BARREL
            }
            val dir = if (player.x >= boss.x) 1f else -1f
            barrels.add(
                BarrelObstacle(
                    x = safeSpawnX,
                    y = boss.y - 6f,
                    vx = level.barrelSpeed * dir,
                    vy = if (obstacleType == ObstacleType.BOUNCING_ROCK) -180f else 0f,
                    type = obstacleType
                )
            )
        } else if (boss.timer < boss.spawnInterval - 0.45f) {
            boss.animState = 0
        }

        val iterator = barrels.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            val prevY = b.y
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.vy += 880f * dt
            b.rotation += b.vx * dt * 2.5f

            // Colisão do barril com plataformas
            for (plat in level.platforms) {
                if (b.x >= plat.x1 && b.x <= plat.x2) {
                    val py = plat.getYAt(b.x)
                    if (b.vy >= 0f && prevY <= py + 14f && b.y >= py - b.radius) {
                        b.y = py - b.radius
                        if (b.type == ObstacleType.BOUNCING_ROCK) {
                            b.vy = -310f
                            soundEngine.playBarrelBounce()
                        } else {
                            b.vy = 0f
                            val slope = plat.getSlopeDirection()
                            if (slope != 0f) {
                                b.vx = level.barrelSpeed * slope
                            }
                        }
                        break
                    }
                }
            }

            if (b.x < 85f || b.x > 1195f) {
                b.vx = -b.vx
                b.x = b.x.coerceIn(86f, 1194f)
            }

            // Pontuação por pular sobre o barril
            if (!b.scoredJumpOver && !player.onGround && !player.onLadder) {
                if (abs(player.x - b.x) < 26f && player.y < b.y && (b.y - player.y) < 75f) {
                    b.scoredJumpOver = true
                    score += 100
                    soundEngine.playCoin()
                }
            }

            // Colisão com o jogador
            if (RectF.intersects(player.getBounds(), b.getBounds())) {
                if (player.hammerTimer > 0f) {
                    score += 250
                    spawnExplosionParticles(b.x, b.y, Color.rgb(255, 180, 50))
                    soundEngine.playBarrelBounce()
                    iterator.remove()
                    continue
                } else if (player.invulnerableTimer <= 0f) {
                    triggerPlayerDamage()
                    return
                }
            }

            if (b.y > VIRTUAL_H + 50f) {
                iterator.remove()
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        for (enemy in level.enemies) {
            if (!enemy.alive) continue
            enemy.update(dt, player.x, player.y)

            if (RectF.intersects(player.getBounds(), enemy.getBounds())) {
                // Jogador derrota inimigo com o martelo ou caindo por cima dele
                val stomped = player.vy > 80f && (player.y - enemy.y) < -12f
                if (player.hammerTimer > 0f || stomped) {
                    enemy.alive = false
                    player.vy = -290f
                    score += 300
                    spawnExplosionParticles(enemy.x, enemy.y - 16f, Color.YELLOW)
                    soundEngine.playCoin()
                } else if (player.invulnerableTimer <= 0f) {
                    triggerPlayerDamage()
                    return
                }
            }
        }
    }

    private fun updateCollectibles() {
        val pBounds = player.getBounds()
        for (item in level.collectibles) {
            if (!item.collected && RectF.intersects(pBounds, item.getBounds())) {
                item.collected = true
                when (item.type) {
                    ItemType.COIN -> {
                        score += 200
                        soundEngine.playCoin()
                    }
                    ItemType.SECRET_ARTIFACT -> {
                        score += 1000
                        soundEngine.playVictory()
                    }
                    ItemType.HAMMER_POWER -> {
                        player.hammerTimer = 7.5f
                        score += 350
                        soundEngine.playCoin()
                    }
                    ItemType.CHECKPOINT_FLAG -> {
                        checkpointReached = true
                        score += 250
                        soundEngine.playCoin()
                    }
                }
                spawnExplosionParticles(item.x, item.y - 15f, Color.CYAN)
            }
        }

        // Alavancas nas fases de Chefe
        if (level.isBossLevel) {
            for (sw in level.bossSwitches) {
                if (!sw.collected && RectF.intersects(pBounds, sw.getBounds())) {
                    sw.collected = true
                    boss.switchesActivated++
                    boss.animState = 2
                    cameraShakeTimer = 0.35f
                    score += 500
                    soundEngine.playBossRoar()
                    spawnExplosionParticles(sw.x, sw.y - 15f, Color.MAGENTA)
                    if (boss.switchesActivated >= boss.maxSwitches) {
                        completeCurrentLevel()
                        return
                    }
                }
            }
        }
    }

    private fun triggerPlayerDamage() {
        soundEngine.playDamage()
        cameraShakeTimer = 0.3f
        lives--
        lostLifeInCurrentLevel = true
        spawnExplosionParticles(player.x, player.y - 20f, Color.RED)
        if (lives <= 0) {
            soundEngine.playGameOver()
            screenState = ScreenState.GAME_OVER
        } else {
            respawnPlayerAfterHit()
        }
    }

    private fun completeCurrentLevel() {
        soundEngine.playVictory()
        val timeBonus = (levelTimer.toInt() * 15).coerceAtLeast(0)
        val noDeathBonus = if (!lostLifeInCurrentLevel) 1000 else 0
        score += 1000 + timeBonus + noDeathBonus
        campaignTotalScore += score

        lastMedalEarned = when {
            !lostLifeInCurrentLevel && levelTimer >= level.timeLimitSeconds * 0.45f -> 3 // Ouro
            !lostLifeInCurrentLevel || levelTimer >= level.timeLimitSeconds * 0.35f -> 2 // Prata
            else -> 1 // Bronze
        }

        saveManager.recordLevelCompletion(currentLevelNum, score, lastMedalEarned, campaignTotalScore)
        clearTouchStates()

        screenState = if (currentLevelNum >= 12) {
            ScreenState.CAMPAIGN_VICTORY
        } else {
            ScreenState.LEVEL_VICTORY
        }
    }

    private fun spawnDustParticles(x: Float, y: Float) {
        for (i in 0 until 6) {
            particles.add(
                Particle(
                    x = x + (i - 3) * 5f,
                    y = y - 2f,
                    vx = (i - 2.5f) * 35f,
                    vy = -60f - (i * 15f),
                    life = 0.35f,
                    maxLife = 0.35f,
                    color = Color.rgb(210, 200, 180),
                    size = 5f
                )
            )
        }
    }

    private fun spawnExplosionParticles(x: Float, y: Float, color: Int) {
        for (i in 0 until 12) {
            val angle = (i * 30.0) * PI / 180.0
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (cos(angle) * 140f).toFloat(),
                    vy = (sin(angle) * 140f - 60f).toFloat(),
                    life = 0.5f,
                    maxLife = 0.5f,
                    color = color,
                    size = 6f
                )
            )
        }
    }

    // =========================================================================
    // RENDERIZAÇÃO 2D NATIVA (60 FPS) COM TRAVAMENTO SEGURO
    // =========================================================================
    private fun drawFrame() {
        if (!surfaceReady || !holder.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas() ?: return
            canvas.save()
            canvas.scale(scaleX, scaleY)

            if (cameraShakeTimer > 0f && screenState == ScreenState.PLAYING) {
                val shakeX = (sin(elapsedLevelTime * 90f) * 6f)
                val shakeY = (cos(elapsedLevelTime * 90f) * 5f)
                canvas.translate(shakeX, shakeY)
            }

            when (screenState) {
                ScreenState.SPLASH -> drawSplashScreen(canvas)
                ScreenState.MAIN_MENU -> drawMainMenu(canvas)
                ScreenState.LEVEL_SELECT -> drawLevelSelect(canvas)
                ScreenState.HIGH_SCORES -> drawHighScores(canvas)
                ScreenState.SETTINGS -> drawSettings(canvas)
                ScreenState.CREDITS -> drawCredits(canvas)
                ScreenState.PLAYING -> drawGameplay(canvas)
                ScreenState.PAUSED -> {
                    drawGameplay(canvas)
                    drawPauseOverlay(canvas)
                }
                ScreenState.LEVEL_VICTORY -> {
                    drawGameplay(canvas)
                    drawLevelVictoryOverlay(canvas)
                }
                ScreenState.GAME_OVER -> {
                    drawGameplay(canvas)
                    drawGameOverOverlay(canvas)
                }
                ScreenState.CAMPAIGN_VICTORY -> drawCampaignVictoryScreen(canvas)
            }

            canvas.restore()
        } catch (_: Exception) {
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {}
            }
        }
    }

    private fun drawGameplay(c: Canvas) {
        // 1. Fundo temático por Mundo com Paralaxe
        val bgColors = when (level.worldNumber) {
            1 -> intArrayOf(Color.rgb(18, 24, 42), Color.rgb(38, 50, 78))
            2 -> intArrayOf(Color.rgb(12, 38, 24), Color.rgb(25, 68, 42))
            3 -> intArrayOf(Color.rgb(38, 22, 18), Color.rgb(68, 36, 26))
            else -> intArrayOf(Color.rgb(28, 12, 36), Color.rgb(58, 20, 48))
        }
        paint.shader = LinearGradient(0f, 0f, 0f, VIRTUAL_H, bgColors[0], bgColors[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, VIRTUAL_W, VIRTUAL_H, paint)
        paint.shader = null

        // Silhuetas de fundo (paralaxe leve com base no player.x)
        val pxOffset = (player.x - 640f) * -0.05f
        paint.color = Color.argb(45, 255, 255, 255)
        for (i in 0..5) {
            val bx = 90f + i * 210f + pxOffset
            c.drawRect(bx, 180f + (i % 2) * 70f, bx + 95f, VIRTUAL_H, paint)
        }

        // 2. Escadas
        for (lad in level.ladders) {
            paint.color = if (lad.isBroken) Color.rgb(130, 110, 90) else Color.rgb(235, 195, 95)
            paint.strokeWidth = 5f
            if (!lad.isBroken) {
                c.drawLine(lad.x - 14f, lad.topY, lad.x - 14f, lad.bottomY, paint)
                c.drawLine(lad.x + 14f, lad.topY, lad.x + 14f, lad.bottomY, paint)
                var ry = lad.topY + 12f
                while (ry < lad.bottomY) {
                    c.drawLine(lad.x - 14f, ry, lad.x + 14f, ry, paint)
                    ry += 18f
                }
            } else {
                // Escada interrompida no meio (apenas topo e base visíveis)
                c.drawLine(lad.x - 14f, lad.topY, lad.x - 14f, lad.topY + 28f, paint)
                c.drawLine(lad.x + 14f, lad.topY, lad.x + 14f, lad.topY + 28f, paint)
                c.drawLine(lad.x - 14f, lad.bottomY - 28f, lad.x - 14f, lad.bottomY, paint)
                c.drawLine(lad.x + 14f, lad.bottomY - 28f, lad.x + 14f, lad.bottomY, paint)
            }
        }

        // 3. Plataformas (Vigas metálicas, troncos, esteiras ou pedras da fortaleza)
        for (plat in level.platforms) {
            paint.strokeWidth = 14f
            paint.color = when {
                plat.isTrap && plat.trapActive -> Color.rgb(255, 60, 60)
                plat.conveyorSpeed != 0f -> Color.rgb(240, 170, 40)
                level.worldNumber == 1 -> Color.rgb(215, 65, 65)
                level.worldNumber == 2 -> Color.rgb(120, 185, 75)
                level.worldNumber == 3 -> Color.rgb(160, 175, 195)
                else -> Color.rgb(185, 95, 220)
            }
            c.drawLine(plat.x1, plat.y1 + 6f, plat.x2, plat.y2 + 6f, paint)
        }

        // 4. Coletáveis, Checkpoint e Alavancas de Chefe
        for (item in level.collectibles) {
            if (item.collected) continue
            when (item.type) {
                ItemType.COIN -> {
                    paint.color = Color.rgb(255, 215, 0)
                    c.drawCircle(item.x, item.y - 16f, 11f, paint)
                }
                ItemType.SECRET_ARTIFACT -> {
                    paint.color = Color.rgb(0, 240, 255)
                    c.drawRect(item.x - 12f, item.y - 28f, item.x + 12f, item.y - 4f, paint)
                }
                ItemType.HAMMER_POWER -> {
                    paint.color = Color.rgb(200, 130, 60)
                    c.drawRect(item.x - 4f, item.y - 30f, item.x + 4f, item.y - 4f, paint)
                    paint.color = Color.LTGRAY
                    c.drawRect(item.x - 14f, item.y - 34f, item.x + 14f, item.y - 22f, paint)
                }
                ItemType.CHECKPOINT_FLAG -> {
                    paint.color = if (checkpointReached) Color.GREEN else Color.WHITE
                    c.drawLine(item.x, item.y, item.x, item.y - 38f, paint)
                    c.drawRect(item.x, item.y - 38f, item.x + 22f, item.y - 22f, paint)
                }
            }
        }

        for (sw in level.bossSwitches) {
            if (!sw.collected) {
                paint.color = Color.rgb(255, 50, 180)
                c.drawRect(sw.x - 14f, sw.y - 24f, sw.x + 14f, sw.y, paint)
            }
        }

        // 5. Personagem Resgatável (Maya) no topo
        drawCapturedCharacter(c, level.goalX, level.goalY)

        // 6. Antagonista Principal: Gorila Gigante Rei Korg
        drawBossGorilla(c, boss)

        // 7. Inimigos Secundários
        for (e in level.enemies) {
            if (!e.alive) continue
            paint.color = when (e.type) {
                EnemyType.CRAWLER -> Color.rgb(120, 250, 90)
                EnemyType.PATROLLER -> Color.rgb(255, 130, 40)
                EnemyType.FLYER -> Color.rgb(240, 90, 255)
            }
            c.drawOval(e.x - 16f, e.y - 28f, e.x + 16f, e.y, paint)
            paint.color = Color.BLACK
            c.drawCircle(e.x + (e.dir * 6f), e.y - 18f, 3.5f, paint)
        }

        // 8. Obstáculos (Barris, Pedras Quicantes e Espíritos de Fogo)
        for (b in barrels) {
            paint.color = when (b.type) {
                ObstacleType.ROLLING_BARREL -> Color.rgb(195, 115, 45)
                ObstacleType.BOUNCING_ROCK -> Color.rgb(150, 160, 175)
                ObstacleType.FIRE_SPIRIT -> Color.rgb(255, 85, 20)
            }
            c.drawCircle(b.x, b.y, b.radius, paint)
            paint.color = Color.rgb(90, 50, 20)
            paint.strokeWidth = 3f
            val rx = cos(b.rotation * 0.05f) * (b.radius - 4f)
            val ry = sin(b.rotation * 0.05f) * (b.radius - 4f)
            c.drawLine(b.x - rx, b.y - ry, b.x + rx, b.y + ry, paint)
        }

        // 9. Jogador (Aventureiro Leo)
        drawPlayer(c, player)

        // 10. Partículas
        for (p in particles) {
            paint.color = p.color
            c.drawCircle(p.x, p.y, p.size * (p.life / p.maxLife), paint)
        }

        // 11. HUD Superior e Tutorial Integrado na Fase 1
        drawHUD(c)

        // 12. Controles Mobile na Tela
        drawTouchControls(c)
    }

    private fun drawPlayer(c: Canvas, p: Player) {
        if (p.invulnerableTimer > 0f && ((p.invulnerableTimer * 15).toInt() % 2 == 0)) return

        val legSwing = if (p.state == PlayerAnimState.RUN) sin(p.animTimer * 18f) * 8f else 0f
        // Pernas
        paint.color = Color.rgb(50, 110, 220)
        paint.strokeWidth = 7f
        c.drawLine(p.x - 5f, p.y - 16f, p.x - 5f + legSwing, p.y, paint)
        c.drawLine(p.x + 5f, p.y - 16f, p.x + 5f - legSwing, p.y, paint)

        // Tronco
        paint.color = Color.rgb(40, 200, 120)
        c.drawRoundRect(p.x - 11f, p.y - 34f, p.x + 11f, p.y - 14f, 6f, 6f, paint)

        // Cabeça e Chapéu de Explorador
        paint.color = Color.rgb(255, 215, 175)
        c.drawCircle(p.x, p.y - 41f, 9f, paint)
        paint.color = Color.rgb(230, 180, 60)
        c.drawRect(p.x - 13f, p.y - 50f, p.x + 13f, p.y - 45f, paint)

        // Martelo de Poder ativo
        if (p.hammerTimer > 0f) {
            val hx = if (p.facingRight) p.x + 22f else p.x - 22f
            paint.color = Color.YELLOW
            c.drawRect(hx - 10f, p.y - 48f, hx + 10f, p.y - 34f, paint)
        }
    }

    private fun drawBossGorilla(c: Canvas, b: BossGorilla) {
        val bounce = sin(b.animClock * 6f) * 4f
        // Corpo do Gorila Rei Korg
        paint.color = if (b.animState == 2) Color.rgb(230, 70, 70) else Color.rgb(95, 60, 45)
        c.drawRoundRect(b.x - 36f, b.y - 74f + bounce, b.x + 36f, b.y, 16f, 16f, paint)
        // Peito
        paint.color = Color.rgb(145, 100, 80)
        c.drawRoundRect(b.x - 22f, b.y - 56f + bounce, b.x + 22f, b.y - 20f + bounce, 10f, 10f, paint)
        // Coroa do Rei Korg
        paint.color = Color.rgb(255, 215, 0)
        c.drawRect(b.x - 16f, b.y - 86f + bounce, b.x + 16f, b.y - 74f + bounce, paint)
        // Olhos expressivos
        paint.color = Color.YELLOW
        c.drawCircle(b.x - 10f, b.y - 62f + bounce, 4.5f, paint)
        c.drawCircle(b.x + 10f, b.y - 62f + bounce, 4.5f, paint)
    }

    private fun drawCapturedCharacter(c: Canvas, gx: Float, gy: Float) {
        paint.color = Color.rgb(255, 120, 190)
        c.drawRoundRect(gx - 10f, gy - 38f, gx + 10f, gy, 6f, 6f, paint)
        paint.color = Color.rgb(255, 220, 185)
        c.drawCircle(gx, gy - 45f, 8f, paint)
        textPaint.textSize = 16f
        textPaint.color = Color.YELLOW
        c.drawText("SOCORRO!", gx - 38f, gy - 60f, textPaint)
    }

    private fun drawHUD(c: Canvas) {
        paint.color = Color.argb(165, 0, 0, 0)
        c.drawRect(0f, 0f, VIRTUAL_W, 56f, paint)

        textPaint.textSize = 22f
        textPaint.color = Color.WHITE
        c.drawText(level.title, 24f, 36f, textPaint)
        c.drawText("PONTOS: $score", 430f, 36f, textPaint)
        c.drawText("VIDAS: $lives", 690f, 36f, textPaint)
        textPaint.color = if (levelTimer < 20f) Color.RED else Color.rgb(120, 255, 140)
        c.drawText("TEMPO: ${levelTimer.toInt()}s", 860f, 36f, textPaint)

        // Barra de progresso vertical do jogador
        val progress = ((660f - player.y) / (660f - 105f)).coerceIn(0f, 1f)
        textPaint.color = Color.CYAN
        c.drawText("TOPO: ${(progress * 100).toInt()}%", 1035f, 36f, textPaint)

        // Botão de Pausa
        paint.color = Color.argb(180, 60, 75, 100)
        c.drawRoundRect(btnPause, 10f, 10f, paint)
        textPaint.color = Color.WHITE
        c.drawText("II", btnPause.left + 26f, btnPause.top + 36f, textPaint)

        // Tutorial integrado à Fase 1
        if (currentLevelNum == 1 && elapsedLevelTime < 9f) {
            paint.color = Color.argb(190, 15, 25, 45)
            c.drawRoundRect(300f, 65f, 980f, 110f, 10f, 10f, paint)
            textPaint.textSize = 19f
            textPaint.color = Color.YELLOW
            c.drawText("DICA: Use ESQ/DIR para correr, CIMA/BAIXO nas escadas e PULO para saltar barris!", 320f, 94f, textPaint)
        }
    }

    private fun drawTouchControls(c: Canvas) {
        fun drawBtn(rect: RectF, label: String, active: Boolean) {
            paint.color = if (active) Color.argb(165, 90, 220, 255) else Color.argb(85, 255, 255, 255)
            c.drawOval(rect, paint)
            textPaint.textSize = 24f
            textPaint.color = Color.WHITE
            val tw = textPaint.measureText(label)
            c.drawText(label, rect.centerX() - tw / 2, rect.centerY() + 8f, textPaint)
        }

        drawBtn(btnLeft, "◀", touchLeft)
        drawBtn(btnRight, "▶", touchRight)
        drawBtn(btnUp, "▲", touchUp)
        drawBtn(btnDown, "▼", touchDown)
        drawBtn(btnJump, "PULO", touchJump)
    }

    // =========================================================================
    // TELAS DE MENU E INTERFACES (ARCADE UI)
    // =========================================================================
    private fun drawMenuButton(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, color: Int = Color.rgb(45, 130, 220)) {
        paint.color = color
        c.drawRoundRect(x, y, x + w, y + h, 14f, 14f, paint)
        textPaint.textSize = 25f
        textPaint.color = Color.WHITE
        val tw = textPaint.measureText(text)
        c.drawText(text, x + (w - tw) / 2, y + h / 2 + 9f, textPaint)
    }

    private fun drawSplashScreen(c: Canvas) {
        c.drawColor(Color.rgb(14, 18, 34))
        textPaint.textSize = 54f
        textPaint.color = Color.rgb(255, 205, 50)
        c.drawText("JUNGLE CLIMB", 430f, 320f, textPaint)
        textPaint.textSize = 32f
        textPaint.color = Color.WHITE
        c.drawText("A FUGA DO REI", 515f, 375f, textPaint)
        textPaint.textSize = 20f
        textPaint.color = Color.LTGRAY
        c.drawText("Carregando motor arcade 2D...", 500f, 460f, textPaint)
    }

    private fun drawMainMenu(c: Canvas) {
        c.drawColor(Color.rgb(18, 26, 44))
        textPaint.textSize = 48f
        textPaint.color = Color.rgb(255, 210, 60)
        c.drawText("JUNGLE CLIMB: A FUGA DO REI", 275f, 110f, textPaint)

        drawMenuButton(c, 440f, 165f, 400f, 65f, "JOGAR NOVA CAMPANHA", Color.rgb(40, 175, 95))
        drawMenuButton(c, 440f, 245f, 400f, 65f, "CONTINUAR (FASE ${saveManager.unlockedLevel})")
        drawMenuButton(c, 440f, 325f, 400f, 65f, "SELECIONAR FASE")
        drawMenuButton(c, 440f, 405f, 400f, 65f, "RECORDES E MEDALHAS")
        drawMenuButton(c, 440f, 485f, 400f, 65f, "CONFIGURAÇÕES")
        drawMenuButton(c, 440f, 565f, 400f, 65f, "CRÉDITOS", Color.rgb(90, 95, 120))
    }

    private fun drawLevelSelect(c: Canvas) {
        c.drawColor(Color.rgb(20, 30, 48))
        textPaint.textSize = 38f
        textPaint.color = Color.YELLOW
        c.drawText("SELECIONAR FASE (4 MUNDOS / 12 FASES)", 280f, 75f, textPaint)

        for (i in 1..12) {
            val col = (i - 1) % 4
            val row = (i - 1) / 4
            val bx = 150f + col * 255f
            val by = 130f + row * 155f
            val unlocked = i <= saveManager.unlockedLevel
            val medal = saveManager.getLevelMedal(i)
            val medalLabel = when (medal) {
                3 -> " [OURO]"
                2 -> " [PRATA]"
                1 -> " [BRONZE]"
                else -> ""
            }
            val color = if (unlocked) Color.rgb(45, 140, 210) else Color.rgb(70, 75, 85)
            drawMenuButton(c, bx, by, 225f, 110f, if (unlocked) "FASE $i$medalLabel" else "BLOQUEADA", color)
        }
        drawMenuButton(c, 490f, 615f, 300f, 65f, "VOLTAR AO MENU", Color.rgb(180, 60, 60))
    }

    private fun drawHighScores(c: Canvas) {
        c.drawColor(Color.rgb(18, 24, 38))
        textPaint.textSize = 38f
        textPaint.color = Color.YELLOW
        c.drawText("RECORDES GERAIS E POR FASE", 360f, 80f, textPaint)

        textPaint.textSize = 26f
        textPaint.color = Color.CYAN
        c.drawText("RECORDE GERAL DA CAMPANHA: ${saveManager.highScore} PTS", 350f, 135f, textPaint)

        textPaint.textSize = 21f
        textPaint.color = Color.WHITE
        for (i in 1..12) {
            val col = (i - 1) / 6
            val row = (i - 1) % 6
            val best = saveManager.getLevelBestScore(i)
            val medal = when (saveManager.getLevelMedal(i)) {
                3 -> "Ouro"
                2 -> "Prata"
                1 -> "Bronze"
                else -> "---"
            }
            c.drawText("Fase $i: $best pts ($medal)", 220f + col * 460f, 200f + row * 60f, textPaint)
        }
        drawMenuButton(c, 490f, 600f, 300f, 65f, "VOLTAR", Color.rgb(180, 60, 60))
    }

    private fun drawSettings(c: Canvas) {
        c.drawColor(Color.rgb(22, 28, 44))
        textPaint.textSize = 38f
        textPaint.color = Color.YELLOW
        c.drawText("CONFIGURAÇÕES", 480f, 95f, textPaint)

        val sizeLabel = when (saveManager.controlScaleMode) {
            0 -> "PEQUENO"
            2 -> "GRANDE"
            else -> "MÉDIO"
        }

        drawMenuButton(c, 390f, 160f, 500f, 70f, "MÚSICA: ${if (saveManager.musicEnabled) "LIGADA" else "DESLIGADA"}")
        drawMenuButton(c, 390f, 255f, 500f, 70f, "EFEITOS SONOROS: ${if (saveManager.sfxEnabled) "LIGADOS" else "DESLIGADOS"}")
        drawMenuButton(c, 390f, 350f, 500f, 70f, "VIBRAÇÃO: ${if (saveManager.vibrationEnabled) "LIGADA" else "DESLIGADA"}")
        drawMenuButton(c, 390f, 445f, 500f, 70f, "TAMANHO CONTROLES: $sizeLabel")
        drawMenuButton(c, 490f, 575f, 300f, 65f, "SALVAR E VOLTAR", Color.rgb(45, 175, 95))
    }

    private fun drawCredits(c: Canvas) {
        c.drawColor(Color.rgb(16, 22, 36))
        textPaint.textSize = 38f
        textPaint.color = Color.YELLOW
        c.drawText("CRÉDITOS", 540f, 110f, textPaint)
        textPaint.textSize = 24f
        textPaint.color = Color.WHITE
        c.drawText("JUNGLE CLIMB: A FUGA DO REI", 440f, 220f, textPaint)
        c.drawText("Game Design, Engine 2D, Sintetizador de Áudio PCM e Arte Vetorial:", 250f, 290f, textPaint)
        c.drawText("Desenvolvido 100% em Kotlin Nativo para Android", 360f, 340f, textPaint)
        c.drawText("Obra original inspirada nos clássicos arcades de escalada.", 310f, 410f, textPaint)
        drawMenuButton(c, 490f, 560f, 300f, 65f, "VOLTAR", Color.rgb(180, 60, 60))
    }

    private fun drawPauseOverlay(c: Canvas) {
        paint.color = Color.argb(190, 0, 0, 0)
        c.drawRect(0f, 0f, VIRTUAL_W, VIRTUAL_H, paint)
        textPaint.textSize = 44f
        textPaint.color = Color.YELLOW
        c.drawText("JOGO PAUSADO", 480f, 180f, textPaint)
        drawMenuButton(c, 440f, 250f, 400f, 70f, "CONTINUAR", Color.rgb(45, 175, 95))
        drawMenuButton(c, 440f, 345f, 400f, 70f, "REINICIAR FASE")
        drawMenuButton(c, 440f, 440f, 400f, 70f, "SAIR PARA O MENU", Color.rgb(185, 60, 60))
    }

    private fun drawLevelVictoryOverlay(c: Canvas) {
        paint.color = Color.argb(200, 0, 0, 0)
        c.drawRect(0f, 0f, VIRTUAL_W, VIRTUAL_H, paint)
        textPaint.textSize = 44f
        textPaint.color = Color.rgb(80, 255, 120)
        c.drawText("FASE $currentLevelNum CONCLUÍDA!", 415f, 160f, textPaint)

        val medalStr = when (lastMedalEarned) {
            3 -> "MEDALHA DE OURO"
            2 -> "MEDALHA DE PRATA"
            else -> "MEDALHA DE BRONZE"
        }
        textPaint.textSize = 28f
        textPaint.color = Color.YELLOW
        c.drawText("CLASSIFICAÇÃO: $medalStr", 430f, 240f, textPaint)
        textPaint.color = Color.WHITE
        c.drawText("Pontuação da Fase: $score  |  Tempo Restante: ${levelTimer.toInt()}s  |  Vidas: $lives", 260f, 310f, textPaint)
        c.drawText("Recorde da Fase: ${saveManager.getLevelBestScore(currentLevelNum)} pts", 450f, 365f, textPaint)

        drawMenuButton(c, 440f, 440f, 400f, 75f, "PRÓXIMA FASE", Color.rgb(45, 175, 95))
        drawMenuButton(c, 440f, 540f, 400f, 65f, "MENU PRINCIPAL")
    }

    private fun drawGameOverOverlay(c: Canvas) {
        paint.color = Color.argb(210, 20, 0, 0)
        c.drawRect(0f, 0f, VIRTUAL_W, VIRTUAL_H, paint)
        textPaint.textSize = 48f
        textPaint.color = Color.RED
        c.drawText("FIM DE JOGO", 490f, 210f, textPaint)
        textPaint.textSize = 26f
        textPaint.color = Color.WHITE
        c.drawText("Pontuação Final: $score", 505f, 290f, textPaint)

        drawMenuButton(c, 440f, 360f, 400f, 75f, "TENTAR NOVAMENTE", Color.rgb(45, 175, 95))
        drawMenuButton(c, 440f, 460f, 400f, 65f, "MENU PRINCIPAL", Color.rgb(180, 60, 60))
    }

    private fun drawCampaignVictoryScreen(c: Canvas) {
        c.drawColor(Color.rgb(18, 38, 28))
        textPaint.textSize = 48f
        textPaint.color = Color.YELLOW
        c.drawText("PARABÉNS! CAMPANHA CONCLUÍDA!", 230f, 150f, textPaint)
        textPaint.textSize = 28f
        textPaint.color = Color.WHITE
        c.drawText("Você derrotou o Rei Korg nos 4 Mundos e resgatou Maya!", 270f, 240f, textPaint)
        textPaint.color = Color.CYAN
        c.drawText("PONTUAÇÃO TOTAL DA CAMPANHA: $campaignTotalScore PTS", 330f, 320f, textPaint)
        c.drawText("RECORDE GERAL REGISTRADO: ${saveManager.highScore} PTS", 355f, 375f, textPaint)

        drawMenuButton(c, 440f, 480f, 400f, 75f, "VOLTAR AO MENU PRINCIPAL", Color.rgb(45, 175, 95))
    }

    // =========================================================================
    // CONTROLE MULTITOUCH E INTERAÇÃO COM MENUS
    // =========================================================================
    private fun clearTouchStates() {
        touchLeft = false
        touchRight = false
        touchUp = false
        touchDown = false
        touchJump = false
        jumpConsumed = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        if (screenState == ScreenState.PLAYING) {
            var left = false
            var right = false
            var up = false
            var down = false
            var jump = false

            val pointerCount = event.pointerCount
            val upIndex = if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) event.actionIndex else -1

            for (i in 0 until pointerCount) {
                if (i == upIndex) continue
                val vx = event.getX(i) / scaleX
                val vy = event.getY(i) / scaleY

                if (action == MotionEvent.ACTION_DOWN && btnPause.contains(vx, vy)) {
                    screenState = ScreenState.PAUSED
                    clearTouchStates()
                    return true
                }

                if (btnLeft.contains(vx, vy)) left = true
                if (btnRight.contains(vx, vy)) right = true
                if (btnUp.contains(vx, vy)) up = true
                if (btnDown.contains(vx, vy)) down = true
                if (btnJump.contains(vx, vy)) jump = true
            }

            touchLeft = left
            touchRight = right
            touchUp = up
            touchDown = down
            if (!jump) jumpConsumed = false
            touchJump = jump
            return true
        }

        // Cliques de Menu
        if (action == MotionEvent.ACTION_DOWN) {
            val vx = event.x / scaleX
            val vy = event.y / scaleY
            handleMenuTap(vx, vy)
        }
        return true
    }

    private fun inRect(vx: Float, vy: Float, x: Float, y: Float, w: Float, h: Float): Boolean {
        return vx >= x && vx <= x + w && vy >= y && vy <= y + h
    }

    private fun handleMenuTap(vx: Float, vy: Float) {
        when (screenState) {
            ScreenState.MAIN_MENU -> {
                when {
                    inRect(vx, vy, 440f, 165f, 400f, 65f) -> {
                        loadLevel(1, resetCampaign = true)
                        screenState = ScreenState.PLAYING
                        soundEngine.startMusic(level.worldNumber)
                    }
                    inRect(vx, vy, 440f, 245f, 400f, 65f) -> {
                        loadLevel(saveManager.unlockedLevel, resetCampaign = true)
                        screenState = ScreenState.PLAYING
                        soundEngine.startMusic(level.worldNumber)
                    }
                    inRect(vx, vy, 440f, 325f, 400f, 65f) -> screenState = ScreenState.LEVEL_SELECT
                    inRect(vx, vy, 440f, 405f, 400f, 65f) -> screenState = ScreenState.HIGH_SCORES
                    inRect(vx, vy, 440f, 485f, 400f, 65f) -> screenState = ScreenState.SETTINGS
                    inRect(vx, vy, 440f, 565f, 400f, 65f) -> screenState = ScreenState.CREDITS
                }
            }
            ScreenState.LEVEL_SELECT -> {
                for (i in 1..12) {
                    val col = (i - 1) % 4
                    val row = (i - 1) / 4
                    val bx = 150f + col * 255f
                    val by = 130f + row * 155f
                    if (inRect(vx, vy, bx, by, 225f, 110f) && i <= saveManager.unlockedLevel) {
                        loadLevel(i, resetCampaign = true)
                        screenState = ScreenState.PLAYING
                        soundEngine.startMusic(level.worldNumber)
                        return
                    }
                }
                if (inRect(vx, vy, 490f, 615f, 300f, 65f)) screenState = ScreenState.MAIN_MENU
            }
            ScreenState.HIGH_SCORES, ScreenState.CREDITS -> {
                if (inRect(vx, vy, 490f, 560f, 300f, 110f)) screenState = ScreenState.MAIN_MENU
            }
            ScreenState.SETTINGS -> {
                when {
                    inRect(vx, vy, 390f, 160f, 500f, 70f) -> {
                        saveManager.musicEnabled = !saveManager.musicEnabled
                        if (saveManager.musicEnabled) soundEngine.startMusic(0) else soundEngine.stopMusic()
                    }
                    inRect(vx, vy, 390f, 255f, 500f, 70f) -> saveManager.sfxEnabled = !saveManager.sfxEnabled
                    inRect(vx, vy, 390f, 350f, 500f, 70f) -> saveManager.vibrationEnabled = !saveManager.vibrationEnabled
                    inRect(vx, vy, 390f, 445f, 500f, 70f) -> {
                        saveManager.controlScaleMode = (saveManager.controlScaleMode + 1) % 3
                        updateTouchButtonRects()
                    }
                    inRect(vx, vy, 490f, 575f, 300f, 65f) -> screenState = ScreenState.MAIN_MENU
                }
            }
            ScreenState.PAUSED -> {
                when {
                    inRect(vx, vy, 440f, 250f, 400f, 70f) -> {
                        screenState = ScreenState.PLAYING
                        soundEngine.startMusic(level.worldNumber)
                    }
                    inRect(vx, vy, 440f, 345f, 400f, 70f) -> {
                        loadLevel(currentLevelNum, resetCampaign = false)
                        screenState = ScreenState.PLAYING
                        soundEngine.startMusic(level.worldNumber)
                    }
                    inRect(vx, vy, 440f, 440f, 400f, 70f) -> {
                        screenState = ScreenState.MAIN_MENU
                        soundEngine.startMusic(0)
                    }
                }
            }
            ScreenState.LEVEL_VICTORY -> {
                when {
                    inRect(vx, vy, 440f, 440f, 400f, 75f) -> {
                        score = 0
                        loadLevel(currentLevelNum + 1, resetCampaign = false)
                        screenState = ScreenState.PLAYING
                        soundEngine.startMusic(level.worldNumber)
                    }
                    inRect(vx, vy, 440f, 540f, 400f, 65f) -> {
                        screenState = ScreenState.MAIN_MENU
                        soundEngine.startMusic(0)
                    }
                }
            }
            ScreenState.GAME_OVER -> {
                when {
                    inRect(vx, vy, 440f, 360f, 400f, 75f) -> {
                        loadLevel(currentLevelNum, resetCampaign = true)
                        screenState = ScreenState.PLAYING
                        soundEngine.startMusic(level.worldNumber)
                    }
                    inRect(vx, vy, 440f, 460f, 400f, 65f) -> {
                        screenState = ScreenState.MAIN_MENU
                        soundEngine.startMusic(0)
                    }
                }
            }
            ScreenState.CAMPAIGN_VICTORY -> {
                if (inRect(vx, vy, 440f, 480f, 400f, 75f)) {
                    screenState = ScreenState.MAIN_MENU
                    soundEngine.startMusic(0)
                }
            }
            else -> {}
        }
    }
}
