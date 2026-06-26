package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.example.AdManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.ZombieType
import com.example.model.Particle
import com.example.model.Turret
import com.example.model.TurretType
import com.example.viewmodel.GameState
import com.example.viewmodel.GameViewModel
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

// Track joystick value globally in composable scope
private var joystickOffset by mutableStateOf(Offset.Zero)

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    
    // Track local physics ticker in Compose to feed joystick movement
    LaunchedEffect(viewModel.gameState) {
        if (viewModel.gameState == GameState.PLAYING) {
            var lastTick = System.currentTimeMillis()
            while (isActive && viewModel.gameState == GameState.PLAYING) {
                val now = System.currentTimeMillis()
                val delta = (now - lastTick) / 1000f
                lastTick = now
                
                // Joystick input polling/updating
                if (joystickOffset != Offset.Zero) {
                    viewModel.handleJoystickInput(joystickOffset.x, joystickOffset.y, delta)
                }
                delay(16)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F131A))
    ) {
        val isWidescreen = maxWidth >= 720.dp

        if (isWidescreen) {
            // Adaptive Dual-Pane layout matching the prompt's layout perfectly!
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Directives & Prompt Checklist Pane (Scrollable)
                GuidancePromptPanel(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF0C0E14))
                        .border(1.dp, Color(0xFF1E293B))
                )

                // Right Panel: Gorgeous Mobile Screen Simulator View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF05070B))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Mobile Screen frame bounds simulating high-end handheld phone
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(9f / 16f)
                            .shadow(24.dp, RoundedCornerShape(24.dp))
                            .testTag("mobile_viewport_frame"),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(3.dp, Color(0xFF2E3B4E)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F131A))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Custom Core 2D gameplay canvas
                            GameplayCanvas(
                                viewModel = viewModel,
                                textMeasurer = textMeasurer,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Unified UI overlay top bar HUD + HUD Side panels
                            UIOverlay(viewModel = viewModel)

                            // Menu overlays based on viewmodel states
                            RenderStateScreens(viewModel = viewModel)
                        }
                    }
                }
            }
        } else {
            // Compact Mobile Viewport
            var showGuidanceOverlay by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize()) {
                // Game Area
                GameplayCanvas(
                    viewModel = viewModel,
                    textMeasurer = textMeasurer,
                    modifier = Modifier.fillMaxSize()
                )

                UIOverlay(viewModel = viewModel)

                RenderStateScreens(viewModel = viewModel)

                // Small floating pill to toggle checklist guidelines
                Button(
                    onClick = { 
                        com.example.audio.GameAudioSynth.playButtonClick()
                        showGuidanceOverlay = true 
                    },
                    modifier = Modifier
                        .padding(top = 96.dp, start = 14.dp)
                        .size(42.dp)
                        .align(Alignment.TopStart)
                        .testTag("floating_directives_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xDD0F131A)),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF9800))
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Show Guidelines",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Guidelines sheet overlay inside AlertDialog
                if (showGuidanceOverlay) {
                    AlertDialog(
                        onDismissRequest = { showGuidanceOverlay = false },
                        confirmButton = {
                            TextButton(onClick = { 
                                com.example.audio.GameAudioSynth.playButtonClick()
                                showGuidanceOverlay = false 
                            }) {
                                Text("CLOSE", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            GuidancePromptPanel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(480.dp)
                            )
                        },
                        containerColor = Color(0xFF0F131A),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("directives_modal")
                    )
                }
            }
        }
    }
}

@Composable
fun RenderStateScreens(viewModel: GameViewModel) {
    when (viewModel.gameState) {
        GameState.MENU -> {
            MenuOverlay(viewModel = viewModel)
        }
        GameState.GAME_OVER -> {
            GameOverOverlay(viewModel = viewModel)
        }
        GameState.PLAYING -> {
            if (viewModel.isPaused) {
                PauseMenuOverlay(viewModel = viewModel)
            }
        }
        else -> {}
    }
}

@Composable
fun PauseMenuOverlay(viewModel: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}
            .testTag("pause_menu_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E293B))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Text(
                text = "PAUSED",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.togglePause() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
            ) {
                Text("RESUME", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Button(
                onClick = {
                    viewModel.togglePause()
                    viewModel.abandonRun()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
            ) {
                Text("EXIT RUN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun GuidancePromptPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Header matching User's change request layout
        Column(
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                text = "CHANGE PROMPT –",
                style = TextStyle(
                    color = Color(0xFFFFC107),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
            )
            Text(
                text = "IMPROVE VISUALS OF CHARACTER AND ZOMBIES",
                style = TextStyle(
                    color = Color(0xFFFFE082),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Please make the following visual and design changes to the current game:",
                style = TextStyle(
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        // Section 1: Player character specs
        PromptChecklistCard(
            sectionTitle = "1. CHARACTER (PLAYER)",
            colorAccent = Color(0xFFFF1744),
            items = listOf(
                "Change player character to detailed, high-quality top-down survivor.",
                "Realistic/stylized 2D cartoony look holding rifle.",
                "Detailed survivor jacket, armor and tan backpack.",
                "Circular shadow beneath player coordinate.",
                "Smooth moving & shooting animations.",
                "Small floating health bar directly above player as indicator."
            )
        )

        // Section 2: Zombie specs
        PromptChecklistCard(
            sectionTitle = "2. ZOMBIES",
            colorAccent = Color(0xFF66BB6A),
            items = listOf(
                "Completely redesigned classic undead zombies with blood stains and pale-greenish skin.",
                "Visually distinct types: Walker, Runner, Tank, Bomber, Boss Behemoth.",
                "Walkers have ragged green skins & scars.",
                "Runners are hot-red deformed agile zombies with speed smoke trails.",
                "Tanks are colossus giants wrapped in dark armor back plating.",
                "Bombers carry hazard warning explosive kegs with flashing red timers.",
                "Boss Behemoths are massive neon-cyan cyber creatures with glowing multiple bio-eyes.",
                "Detailed splatter and blood pool decals when defeated."
            )
        )

        // Section 3: Environment design
        PromptChecklistCard(
            sectionTitle = "3. VISUAL STYLE",
            colorAccent = Color(0xFF29B6F6),
            items = listOf(
                "Modern top-down cyberpunk post-apocalyptic dark battle-field.",
                "Crack patterns on ground tiles, mud patches, and organic grass details.",
                "Scattered debris cars, hazard oil barrels, fences, and concrete barricades.",
                "Fully rendered Outpost Citadel core fortress with heavy steel doors and fluttering flag pole on top!"
            )
        )

        // Section 4: UI upgrade
        PromptChecklistCard(
            sectionTitle = "4. UI IMPROVEMENTS",
            colorAccent = Color(0xFFFFCA28),
            items = listOf(
                "Futuristic HUD bars: Skull wave tracker, Shield base bar with green slider, Gold earning badge.",
                "Premium glassy upgrade cards matching mobile shooter games with accent colored borders.",
                "Dual rings circular virtual joystick with clear arrow direction pointers.",
                "Interactive buy-upgrade pricing pills with stark forest-green layouts."
            )
        )

        // Section 5: Overall
        PromptChecklistCard(
            sectionTitle = "5. OVERALL",
            colorAccent = Color(0xFFAB47BC),
            items = listOf(
                "Visually premium screen aesthetics.",
                "Adheres strictly to core game loops with polished rendering.",
                "High performance drawing frames."
            )
        )

        // Warning Footer Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x33C62828))
                .border(1.dp, Color(0xFFEF5350), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "★ IMPORTANT: Do not change the core gameplay. Only improve the visuals of character, zombies, environment and UI as described.",
                style = TextStyle(
                    color = Color(0xFFFFCDD2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                )
            )
        }
    }
}

@Composable
fun PromptChecklistCard(
    sectionTitle: String,
    colorAccent: Color,
    items: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26)),
        border = BorderStroke(0.5.dp, colorAccent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = sectionTitle,
                style = TextStyle(
                    color = colorAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            )
            Divider(color = Color.White.copy(alpha = 0.05f))
            items.forEach { bullet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        color = colorAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = bullet,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GameplayCanvas(
    viewModel: GameViewModel,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier
) {
    val strokeDash = remember { floatArrayOf(20f, 15f) }

    Canvas(
        modifier = modifier
            .testTag("gameplay_canvas")
            .pointerInput(viewModel.gameState) {
                if (viewModel.gameState == GameState.PLAYING) {
                    detectTapGestures { tapOffset ->
                        val gameW = 800f
                        val gameH = 1200f
                        val scale = min(size.width / gameW, size.height / gameH)
                        val offsetX = (size.width - gameW * scale) / 2f
                        val offsetY = (size.height - gameH * scale) / 2f
                        
                        val gameX = (tapOffset.x - offsetX) / scale
                        val gameY = (tapOffset.y - offsetY) / scale
                        
                        var hitSlot: Int? = null
                        for (turret in viewModel.turrets) {
                            val dist = sqrt((gameX - turret.x) * (gameX - turret.x) + (gameY - turret.y) * (gameY - turret.y))
                            if (dist <= 45f) {
                                hitSlot = turret.slotId
                                break
                            }
                        }
                        
                        if (hitSlot != null) {
                            viewModel.selectedTurretSlotId = hitSlot
                        } else {
                            viewModel.selectedTurretSlotId = null
                        }
                    }
                }
            }
    ) {
        // Mapping standard independent game screen space: 800w x 1200h
        val gameW = 800f
        val gameH = 1200f
        val scale = min(size.width / gameW, size.height / gameH)
        val offsetX = (size.width - gameW * scale) / 2f
        val offsetY = (size.height - gameH * scale) / 2f

        // Screen shake matrices transformations
        val sX = viewModel.shakeX * scale
        val sY = viewModel.shakeY * scale

        drawContext.canvas.save()
        drawContext.canvas.translate(offsetX + sX, offsetY + sY)
        drawContext.canvas.scale(scale, scale)

        // 1. Draw Field Background (Realistic dark asphalt/dirt ground)
        drawRect(Color(0xFF141416), topLeft = Offset.Zero, size = Size(gameW, gameH)) // Very dark dirt/asphalt
        
        // 2. Draw Post-Apocalyptic Ground Textures & Environmental Details
        // A. Subtle cracks and road markings
        val roadMarkingColor = Color(0x1F9E9E9E)
        for (y in 100..1100 step 150) {
            drawRect(roadMarkingColor, topLeft = Offset(gameW/2f - 4f, y.toFloat()), size = Size(8f, 60f)) // Dashed center line
        }
        
        val crackColor = Color(0x15000000)
        listOf(Offset(140f, 220f), Offset(640f, 980f), Offset(150f, 850f), Offset(680f, 320f)).forEach { crackCentroid ->
            drawCircle(Color(0x0C000000), radius = 110f, center = crackCentroid)
            for (i in 0 until 6) {
                val angle = i * 60f * PI.toFloat() / 180f + (crackCentroid.x % 1f)
                val breakLength = 80f + (crackCentroid.y % 20f)
                drawLine(
                    color = crackColor,
                    start = crackCentroid,
                    end = Offset(crackCentroid.x + cos(angle) * breakLength, crackCentroid.y + sin(angle) * breakLength),
                    strokeWidth = 3f
                )
            }
        }

        // B. Realistic mud puddles / dried blood / debris
        drawCircle(Color(0x11211b15), radius = 120f, center = Offset(180f, 880f)) // Dirt patch
        drawCircle(Color(0x1A141210), radius = 135f, center = Offset(620f, 280f)) // Charred Ground
        drawCircle(Color(0x0E1A0F0D), radius = 115f, center = Offset(650f, 750f)) // Very old dried blood

        // C. Dead vegetation
        val grassPoints = listOf(
            Offset(90f, 120f), Offset(110f, 130f),
            Offset(670f, 200f), Offset(690f, 210f),
            Offset(160f, 920f), Offset(140f, 930f),
            Offset(590f, 1080f), Offset(610f, 1090f),
            Offset(300f, 140f), Offset(510f, 970f)
        )
        grassPoints.forEach { pt ->
            drawLine(Color(0x3B3D3B2B), start = pt, end = Offset(pt.x - 3f, pt.y - 10f), strokeWidth = 2f)
            drawLine(Color(0x3B3D3B2B), start = pt, end = Offset(pt.x + 4f, pt.y - 8f), strokeWidth = 2f)
            drawLine(Color(0x28474229), start = pt, end = Offset(pt.x + 1f, pt.y - 12f), strokeWidth = 1.5f)
        }

        // D. Persistent Blood Pool Splatter Decals (Drawn directly on ground)
        viewModel.decals.forEach { decal ->
            val poolAlpha = 0.55f
            val baseSplatterColor = Color(decal.colorHex).copy(alpha = poolAlpha)
            val darkCenterColor = Color(0xFF1B0306).copy(alpha = 0.7f)

            drawCircle(baseSplatterColor, radius = decal.size * 0.45f, center = Offset(decal.x, decal.y))
            drawCircle(darkCenterColor, radius = decal.size * 0.25f, center = Offset(decal.x, decal.y))

            for (i in 0 until 4) {
                val rot = (decal.angle + i * 90f) * PI.toFloat() / 180f
                val dist = decal.size * 0.6f
                drawCircle(
                    color = baseSplatterColor,
                    radius = decal.size * 0.15f,
                    center = Offset(decal.x + cos(rot) * dist, decal.y + sin(rot) * dist)
                )
            }
        }

        // E. Scattered Concrete Jersey Barriers & Crates
        val barricades = listOf(
            Offset(220f, 140f) to 15f,
            Offset(110f, 380f) to -20f,
            Offset(730f, 480f) to 10f,
            Offset(590f, 1020f) to -15f
        )
        barricades.forEach { (loc, rot) ->
            drawContext.canvas.save()
            drawContext.canvas.translate(loc.x, loc.y)
            drawContext.canvas.rotate(rot)
            
            // Heavy shadow
            drawRect(Color(0x66000000), topLeft = Offset(-18f, -6f), size = Size(36f, 16f))
            // Concrete Barrier
            drawRoundRect(Color(0xFF5E6061), topLeft = Offset(-20f, -10f), size = Size(40f, 12f), cornerRadius = CornerRadius(2f))
            drawRoundRect(Color(0xFF86888A), topLeft = Offset(-18f, -8f), size = Size(36f, 8f), cornerRadius = CornerRadius(1f))
            
            drawContext.canvas.restore()
        }
        
        val crates = listOf(Offset(130f, 360f), Offset(200f, 160f))
        crates.forEach { loc ->
            drawRect(Color(0x55000000), topLeft = Offset(loc.x - 13f, loc.y - 13f), size = Size(26f, 26f))
            drawRect(Color(0xFF4A3828), topLeft = Offset(loc.x - 14f, loc.y - 14f), size = Size(28f, 28f))
            drawRect(Color(0xFF38291A), topLeft = Offset(loc.x - 12f, loc.y - 12f), size = Size(24f, 24f))
            drawLine(Color(0xFF261D12), Offset(loc.x - 12f, loc.y - 12f), Offset(loc.x + 12f, loc.y + 12f), strokeWidth = 2f)
            drawLine(Color(0xFF261D12), Offset(loc.x - 12f, loc.y + 12f), Offset(loc.x + 12f, loc.y - 12f), strokeWidth = 2f)
        }

        // F. Wrecked Rusted Military Cars (realistic)
        val carPositions = listOf(Offset(105f, 1050f), Offset(695f, 160f))
        carPositions.forEach { car ->
            drawContext.canvas.save()
            drawContext.canvas.translate(car.x, car.y)
            drawContext.canvas.rotate(if (car.y > 600f) 15f else -25f)
            
            // Shadow
            drawRoundRect(Color(0x77000000), topLeft = Offset(-28f, -42f), size = Size(56f, 84f), cornerRadius = CornerRadius(8f))
            // Rusty body chassis
            drawRoundRect(Color(0xFF323432), topLeft = Offset(-22f, -38f), size = Size(44f, 76f), cornerRadius = CornerRadius(6f))
            drawRoundRect(Color(0xFF4D3325), topLeft = Offset(-20f, -35f), size = Size(40f, 70f), cornerRadius = CornerRadius(4f)) // Rust

            // Windshield and side windows
            drawRect(Color(0xFF131518), topLeft = Offset(-16f, -14f), size = Size(32f, 26f))
            drawRect(Color(0xFF1D2024), topLeft = Offset(-14f, -11f), size = Size(28f, 20f))
            
            // Debris details
            drawLine(Color(0xFF888888), Offset(-12f, -10f), Offset(10f, 5f), strokeWidth = 1f) // Shattered glass crack
            
            drawContext.canvas.restore()
        }

        val baseCentroid = Offset(400f, 600f)

        // 3. Draw Radar Core Outpost Guard Boundary (Subdued)
        drawCircle(
            color = Color(0x0CFFFFFF), 
            radius = 120f, 
            center = baseCentroid,
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(strokeDash))
        )

        // 4. Draw Outpost Citadel Core (Fully rendered steel concrete fortress with defense arcs)
        val baseHpPct = (viewModel.baseHp / viewModel.baseMaxHp).coerceIn(0f, 1f)
        val securityPulseColor = Color(0xFFE65100).copy(alpha = 0.15f + 0.25f * baseHpPct)

        // Defensive Sandbag Barricades defending the central base perimeter
        val sandbagStations = listOf(
            Offset(baseCentroid.x - 90f, baseCentroid.y),
            Offset(baseCentroid.x + 90f, baseCentroid.y),
            Offset(baseCentroid.x, baseCentroid.y - 92f)
        )
        sandbagStations.forEach { bag ->
            // Stacked sandbag 1
            drawRoundRect(
                color = Color(0xFF78350F).copy(alpha = 0.85f),
                topLeft = Offset(bag.x - 16f, bag.y - 8f),
                size = Size(32f, 16f),
                cornerRadius = CornerRadius(5f)
            )
            drawRoundRect(
                color = Color(0xFFD97706),
                topLeft = Offset(bag.x - 14f, bag.y - 6f),
                size = Size(28f, 12f),
                cornerRadius = CornerRadius(4f)
            )
            // Sandbag 2 layered slightly higher
            drawRoundRect(
                color = Color(0xFFB45309),
                topLeft = Offset(bag.x - 11f, bag.y - 12f),
                size = Size(22f, 10f),
                cornerRadius = CornerRadius(3f)
            )
        }

        // Octagonal concrete compound wall
        drawRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(baseCentroid.x - 70f, baseCentroid.y - 70f),
            size = Size(140f, 140f),
            style = Stroke(width = 6f)
        )
        drawRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(baseCentroid.x - 67f, baseCentroid.y - 67f),
            size = Size(134f, 134f)
        )
        // Draw concrete bunker divisions with metal rivets
        drawLine(Color(0xFF334155), start = Offset(baseCentroid.x - 67f, baseCentroid.y - 25f), end = Offset(baseCentroid.x + 67f, baseCentroid.y - 25f), strokeWidth = 2f)
        drawLine(Color(0xFF334155), start = Offset(baseCentroid.x - 67f, baseCentroid.y + 25f), end = Offset(baseCentroid.x + 67f, baseCentroid.y + 25f), strokeWidth = 2f)

        // Centered defensive outpost turret visual evolution
        viewModel.turrets.forEach { turret ->
            val center = Offset(turret.x, turret.y)
            val isSelected = viewModel.selectedTurretSlotId == turret.slotId

            // Significantly brighten the ground texture around turret slots for map visibility
            drawCircle(color = Color(0x33FFFFFF), radius = 32f, center = center)

            if (turret.type == TurretType.NONE) {
                // Empty Turret Slot (Military Mount)
                val radiusNormal = 22f
                
                val slotGlowColor = if (isSelected) Color(0xFFFF9800) else Color(0xFFFFF176) 
                
                // Add a thicker glowing outline
                drawCircle(
                    color = slotGlowColor.copy(alpha = if(isSelected) 0.5f else 0.25f),
                    radius = radiusNormal + 6f,
                    center = center
                )

                // 1. Draw solid steel base
                drawCircle(
                    color = Color(0xFF454A4A),
                    radius = radiusNormal,
                    center = center
                )
                
                // 2. Draw outer slot border ring with brighter dashed steel effect
                drawCircle(
                    color = slotGlowColor.copy(alpha = 0.9f),
                    radius = radiusNormal,
                    center = center,
                    style = Stroke(
                        width = 5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )

                // 3. Draw inner mechanism hole (keep slightly dark)
                drawCircle(
                    color = Color(0xFF141615),
                    radius = radiusNormal * 0.6f,
                    center = center
                )

                // 4. Draw bold '+' symbol in the center (Construction mark)
                val barHalfLength = 8f
                val barThickness = 3f
                // Horizontal bar
                drawLine(
                    color = slotGlowColor.copy(alpha = 0.8f),
                    start = Offset(center.x - barHalfLength, center.y),
                    end = Offset(center.x + barHalfLength, center.y),
                    strokeWidth = barThickness
                )
                // Vertical bar
                drawLine(
                    color = slotGlowColor.copy(alpha = 0.8f),
                    start = Offset(center.x, center.y - barHalfLength),
                    end = Offset(center.x, center.y + barHalfLength),
                    strokeWidth = barThickness
                )
                return@forEach
            }

            // --- ACTIVE TURRET RENDERING ---
            // High visibility realistic colors
            val armorColor = Color(0xFF8BA38B) // Very bright military green
            val detailColor = Color(0xFFE2E8E8) // Bright metallic silver/white
            
            // Strong shadow to pop it up from ground
            drawCircle(
                color = Color(0xCC000000),
                radius = 28f,
                center = center.copy(y = center.y + 6f)
            )

            // Bright glow under turret
            val activeGlowColor = if (isSelected) Color(0xFFFFB300) else Color(0xFF00FF88)
            drawCircle(
                color = activeGlowColor.copy(alpha = if(isSelected) 0.6f else 0.3f),
                radius = if(isSelected) 34f else 30f,
                center = center
            )

            // Heavy static mounting base plate (Brighter base)
            drawCircle(color = Color(0xFF4A4E4D), radius = 24f, center = center)
            // Thick bright outer ring
            drawCircle(color = Color(0xFFD0D5D5), radius = 24f, center = center, style = Stroke(width = 3f))
            // Inner armor plate
            drawCircle(color = armorColor, radius = 20f, center = center, style = Stroke(width = 4f))
            drawCircle(color = detailColor, radius = 12f, center = center)
            
            // Hexagon bolts
            for(i in 0 until 6) {
                val bA = i * 60f * PI.toFloat() / 180f
                drawCircle(color = Color(0xFF111111), radius = 2f, center = Offset(center.x + cos(bA) * 16f, center.y + sin(bA) * 16f))
            }

            // Render rotated weapon barrels
            val rad = turret.angle * PI.toFloat() / 180f
            val cosA = cos(rad)
            val sinA = sin(rad)

            val level = turret.level

            // Architectural evolution based on level
            if (level >= 8) {
                // TRIPLE HEAVY ARTILLERY
                val orthX = -sinA * 8f
                val orthY = cosA * 8f
                val lenCenter = (36f + level * 1.0f).coerceAtMost(55f)
                val lenSides = (30f + level * 0.8f).coerceAtMost(48f)

                // Central heavy barrel
                drawLine(detailColor, start = center, end = Offset(turret.x + cosA * lenCenter, turret.y + sinA * lenCenter), strokeWidth = 9f)
                // Left support cannon
                drawLine(detailColor, start = Offset(turret.x + orthX, turret.y + orthY), end = Offset(turret.x + cosA * lenSides + orthX, turret.y + sinA * lenSides + orthY), strokeWidth = 5f)
                // Right support cannon
                drawLine(detailColor, start = Offset(turret.x - orthX, turret.y - orthY), end = Offset(turret.x + cosA * lenSides - orthX, turret.y + sinA * lenSides - orthY), strokeWidth = 5f)

                // Armored housing
                drawRect(armorColor, topLeft = Offset(center.x - 14f, center.y - 14f), size = Size(28f, 28f))
                drawLine(Color(0xFF5A666A), start = center, end = Offset(turret.x + cosA * 16f, turret.y + sinA * 16f), strokeWidth = 14f) // Shield
                
                // Muzzle breaks
                drawLine(Color(0xFF000000), start = Offset(turret.x + cosA * (lenCenter-4f), turret.y + sinA * (lenCenter-4f)), end = Offset(turret.x + cosA * lenCenter, turret.y + sinA * lenCenter), strokeWidth = 12f)
            } else if (level >= 4) {
                // DUAL AUTO CANNON
                val orthX = -sinA * 6f
                val orthY = cosA * 6f
                val len = (30f + level * 1.2f).coerceAtMost(48f)

                // Left gun barrel
                drawLine(detailColor, start = Offset(turret.x + orthX, turret.y + orthY), end = Offset(turret.x + cosA * len + orthX, turret.y + sinA * len + orthY), strokeWidth = 6f)
                // Right gun barrel
                drawLine(detailColor, start = Offset(turret.x - orthX, turret.y - orthY), end = Offset(turret.x + cosA * len - orthX, turret.y + sinA * len - orthY), strokeWidth = 6f)
                
                // Ammunition box / housing
                drawRect(armorColor, topLeft = Offset(center.x - 12f, center.y - 12f), size = Size(24f, 24f))
                
                // Vents
                drawLine(Color(0xFF111111), start = Offset(turret.x + orthX + cosA * 15f, turret.y + orthY + sinA * 15f), end = Offset(turret.x - orthX + cosA * 15f, turret.y - orthY + sinA * 15f), strokeWidth = 2.5f)
                drawLine(Color(0xFF111111), start = Offset(turret.x + orthX + cosA * 20f, turret.y + orthY + sinA * 20f), end = Offset(turret.x - orthX + cosA * 20f, turret.y - orthY + sinA * 20f), strokeWidth = 2.5f)
            } else {
                // SINGLE HEAVY MACHINE GUN
                val len = (26f + level * 1.5f).coerceAtMost(42f)
                drawLine(detailColor, start = center, end = Offset(turret.x + cosA * len, turret.y + sinA * len), strokeWidth = 6.5f)
                
                // Weapon housing
                drawRect(armorColor, topLeft = Offset(center.x - 10f, center.y - 10f), size = Size(20f, 20f))
                
                drawCircle(color = Color(0xFF333333), radius = 6f, center = center)
            }

            // Level text indicator next to the turret (LVL 1-10+)
            val textLayoutResult = textMeasurer.measure(
                text = "LVL $level",
                style = TextStyle(
                    color = Color(0xFFB0BEC5),

                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(turret.x - 14f, turret.y - 34f)
            )
        }

        // Heavy steel bunker compound gate
        drawRect(
            color = Color(0xFF334155),
            topLeft = Offset(baseCentroid.x - 22f, baseCentroid.y + 58f),
            size = Size(44f, 12f)
        )
        // Hazard striping on gate
        for (i in 0 until 4) {
            drawLine(
                color = Color(0xFFFFA000),
                start = Offset(baseCentroid.x - 20f + i * 11f, baseCentroid.y + 58f),
                end = Offset(baseCentroid.x - 12f + i * 11f, baseCentroid.y + 70f),
                strokeWidth = 2f
            )
        }

        // Inner core dome
        drawCircle(color = Color(0xFF1E293B), radius = 48f, center = baseCentroid)
        drawCircle(color = Color(0xFF334155), radius = 48f, center = baseCentroid, style = Stroke(width = 3.5f))

        // Nuclear core pulse light
        drawCircle(color = Color(0xFF020617), radius = 24f, center = baseCentroid)
        drawCircle(securityPulseColor, radius = 16f, center = baseCentroid)

        // Clean decorative border loop around Core Outpost dome
        drawCircle(
            color = Color(0x33475569),
            radius = 54f,
            center = baseCentroid,
            style = Stroke(width = 2.5f)
        )

        // Animated Tactical Fluttering Flag on Outpost core flag pole
        val polePos = Offset(baseCentroid.x, baseCentroid.y - 12f)
        drawLine(
            color = Color(0xFF94A3B8),
            start = polePos,
            end = Offset(polePos.x, polePos.y - 38f),
            strokeWidth = 4f
        )
        val flagWave = (System.currentTimeMillis() % 1200) / 1200f
        val flapAmt = sin(flagWave * 2f * PI.toFloat()) * 4f
        val triFlag = Path().apply {
            moveTo(polePos.x, polePos.y - 38f)
            lineTo(polePos.x + 30f, polePos.y - 30f + flapAmt)
            lineTo(polePos.x, polePos.y - 22f)
            close()
        }
        drawPath(path = triFlag, color = Color(0xFF0284C7)) // Cyber blue outpost flag
        drawCircle(color = Color(0xFFFFD700), radius = 3f, center = Offset(polePos.x + 10f, polePos.y - 30f + flapAmt / 2f)) // Star insignia

        // Security rotating light cone (Lighting and Shadows)
        val securityLightAngle = (System.currentTimeMillis() % 4500) / 4500f * 360f
        val searchLightCone = Path().apply {
            moveTo(baseCentroid.x, baseCentroid.y)
            val angleLeft = (securityLightAngle - 15f) * PI.toFloat() / 180f
            val angleRight = (securityLightAngle + 15f) * PI.toFloat() / 180f
            val maxLightRange = 400f
            lineTo(baseCentroid.x + cos(angleLeft) * maxLightRange, baseCentroid.y + sin(angleLeft) * maxLightRange)
            lineTo(baseCentroid.x + cos(angleRight) * maxLightRange, baseCentroid.y + sin(angleRight) * maxLightRange)
            close()
        }
        drawPath(
            path = searchLightCone,
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1FFFFFED), Color.Transparent),
                center = baseCentroid,
                radius = 400f
            )
        )

        // 5. Draw Active Splatter Particles
        viewModel.particles.forEach { part ->
            val partColor = Color(part.colorHex).copy(alpha = part.life.coerceIn(0f, 1f))
            drawCircle(
                color = partColor,
                radius = part.size,
                center = Offset(part.x, part.y)
            )
        }

        // 6. Draw Redesigned Undead Horde Zombies
        // Common animation cycle frame
        val zombieTimeCycle = (System.currentTimeMillis() % 1000) / 1000f
        val legSwing = sin(zombieTimeCycle * 2f * PI.toFloat()) * 5f
        val armSwing = cos(zombieTimeCycle * 2f * PI.toFloat()) * 4f

        viewModel.zombies.forEach { zombie ->
            val isHit = zombie.hitTimer > 0f
            val hitRatio = if (isHit) (zombie.hitTimer / 0.18f).coerceIn(0f, 1f) else 0f
            
            // Wobble/shake offset on impact
            val wobbleX = if (isHit) (sin(zombie.hitTimer * 100f) * 5f * hitRatio) else 0f
            val wobbleY = if (isHit) (cos(zombie.hitTimer * 100f) * 5f * hitRatio) else 0f
            
            // Scale factor (swells on impact)
            val zScale = 1f + hitRatio * 0.22f
            
            val zombieCenter = Offset(zombie.x, zombie.y)
            val baseCol = Color(zombie.colorHex)

            // Bottom Drop Shadow
            drawCircle(Color(0x66000000), radius = zombie.size * zScale + 4f, center = Offset(zombie.x + wobbleX, zombie.y + wobbleY + 4f))

            // Rotated direction toward player
            val angleToPlayerRad = atan2(viewModel.playerPos.y - zombie.y, viewModel.playerPos.x - zombie.x)
            val angleToPlayerDeg = angleToPlayerRad * 180f / PI.toFloat()

            // Translate and scale the entire drawing
            translate(left = wobbleX, top = wobbleY) {
                scale(scale = zScale, pivot = zombieCenter) {
                    val isNearPlayer = kotlin.math.hypot(
                        (viewModel.playerPos.x - zombie.x).toDouble(),
                        (viewModel.playerPos.y - zombie.y).toDouble()
                    ) < 45.0
                    with(com.example.ui.CharacterRenderer) {
                        drawAdvancedZombie(
                            center = zombieCenter,
                            angle = angleToPlayerDeg,
                            zombie = zombie,
                            timeMs = System.currentTimeMillis(),
                            isAttacking = isNearPlayer
                        )
                    }
                    
                    // Keep particle emission for Runner
                    if (zombie.type == com.example.model.ZombieType.RUNNER && kotlin.random.Random.nextFloat() < 0.2f) {
                        viewModel.particles.add(
                            com.example.model.Particle(
                                x = zombie.x - zombie.size,
                                y = zombie.y + (kotlin.random.Random.nextFloat() * 10f - 5f),
                                vx = -cos(angleToPlayerRad) * 40f,
                                vy = -sin(angleToPlayerRad) * 40f,
                                colorHex = zombie.colorHex,
                                size = kotlin.random.Random.nextFloat() * 4f + 2f,
                                life = 0.5f,
                                decay = 1.8f
                            )
                        )
                    }
                }
            }

            // Draw high-visibility hit flash overlay
            if (isHit) {
                val flashColor = Color(0xFFFFFFFF).copy(alpha = hitRatio * 0.75f)
                drawCircle(color = flashColor, radius = zombie.size * zScale, center = Offset(zombie.x + wobbleX, zombie.y + wobbleY))
            }

            // Small red hovering HP indicator bar if zombie took hit damage
            if (zombie.hp < zombie.maxHp) {
                val zPct = (zombie.hp / zombie.maxHp).coerceIn(0f, 1f)
                val barW = zombie.size * 1.55f
                val barH = 5f
                drawRoundRect(
                    color = Color(0xB0000000),
                    topLeft = Offset(zombieCenter.x - barW / 2f, zombieCenter.y - zombie.size - 10f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2f)
                )
                drawRoundRect(
                    color = Color(0xFFEF4444),
                    topLeft = Offset(zombieCenter.x - barW / 2f, zombieCenter.y - zombie.size - 10f),
                    size = Size(barW * zPct, barH),
                    cornerRadius = CornerRadius(2f)
                )
            }
        }

        // 7. Draw Player character (Detailed, high-quality survivor with steps walk animations)
        val playerCenter = Offset(viewModel.playerPos.x, viewModel.playerPos.y)
        
        // Circular drop shadow under survivor handled in CharacterRenderer
        
        // Survivor tactical flashlight cone extending in front (Lighting and Shadows)
        val flashCone = Path().apply {
            moveTo(playerCenter.x, playerCenter.y)
            val leftF = (viewModel.playerAngle - 20f) * PI.toFloat() / 180f
            val rightF = (viewModel.playerAngle + 20f) * PI.toFloat() / 180f
            val len = 320f
            lineTo(playerCenter.x + cos(leftF) * len, playerCenter.y + sin(leftF) * len)
            lineTo(playerCenter.x + cos(rightF) * len, playerCenter.y + sin(rightF) * len)
            close()
        }
        drawPath(
            path = flashCone,
            brush = Brush.radialGradient(
                colors = listOf(Color(0x11FFFFFF), Color.Transparent),
                center = playerCenter,
                radius = 320f
            )
        )

        with(com.example.ui.CharacterRenderer) {
            val isShooting = viewModel.bullets.isNotEmpty()
            drawAdvancedPlayer(
                center = playerCenter,
                angle = viewModel.playerAngle,
                movementState = (joystickOffset != Offset.Zero),
                isShooting = isShooting,
                timeMs = System.currentTimeMillis()
            )
        }

        // Realistic floating HUD health bar directly above player head
        val pBarW = 38f
        val pBarH = 4f
        drawRoundRect(
            color = Color(0x99000000),
            topLeft = Offset(playerCenter.x - pBarW / 2f, playerCenter.y - 28f),
            size = Size(pBarW, pBarH),
            cornerRadius = CornerRadius(2f)
        )
        drawRoundRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(playerCenter.x - pBarW / 2f, playerCenter.y - 28f),
            size = Size(pBarW * 1.0f, pBarH),
            cornerRadius = CornerRadius(2f)
        )

        // 8. Draw Projectile Bullets (Tracer rounds)
        viewModel.bullets.forEach { bullet ->
            val bCenter = Offset(bullet.x, bullet.y)
            
            // Draw short tracer tail (Physical bullet illusion)
            val tailLenX = bullet.vx / bullet.speed * 12f
            val tailLenY = bullet.vy / bullet.speed * 12f
            
            drawLine(
                color = Color(0x66FF9800), // Orange burnt tracer tail
                start = Offset(bullet.x - tailLenX, bullet.y - tailLenY),
                end = bCenter,
                strokeWidth = bullet.size * 1.2f
            )
            
            // Solid bullet tip
            drawCircle(Color(0xFFFFEE58), radius = bullet.size * 0.8f, center = bCenter) // Yellow-hot tip
            drawCircle(Color.White, radius = bullet.size * 0.4f, center = bCenter) // Bright core
        }

        // 9. Draw Floating elements (Floating damage indicator texts and coin scores)
        viewModel.damageNumbers.forEach { dNum ->
            val textValue = dNum.text
            val isReward = textValue.contains("+$") || textValue.contains("BONUS")
            val fontSize = if (isReward) 16.sp else 12.sp
            val fontWeight = if (isReward) FontWeight.Black else FontWeight.Bold
            val alpha = dNum.life.coerceIn(0f, 1f)

            if (isReward) {
                // Draw drop shadow/outline for extra readability and punch
                val outlineColor = Color.Black.copy(alpha = alpha * 0.85f)
                val styleOutline = TextStyle(
                    color = outlineColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = FontFamily.Monospace
                )
                val offset = 1.5f
                drawText(textMeasurer, textValue, Offset(dNum.x - 12f - offset, dNum.y - offset), styleOutline)
                drawText(textMeasurer, textValue, Offset(dNum.x - 12f + offset, dNum.y - offset), styleOutline)
                drawText(textMeasurer, textValue, Offset(dNum.x - 12f - offset, dNum.y + offset), styleOutline)
                drawText(textMeasurer, textValue, Offset(dNum.x - 12f + offset, dNum.y + offset), styleOutline)
            }

            drawText(
                textMeasurer = textMeasurer,
                text = textValue,
                topLeft = Offset(dNum.x - 12f, dNum.y),
                style = TextStyle(
                    color = Color(dNum.colorHex).copy(alpha = alpha),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        viewModel.coins.forEach { coin ->
            // Shiny rotating golden coins gliding towards top-right
            drawCircle(Color(0x88FFD700), radius = 8.5f, center = Offset(coin.x, coin.y))
            drawCircle(Color(0xFFFFD700), radius = 7f, center = Offset(coin.x, coin.y))
            drawCircle(Color(0xFFF59E0B), radius = 4f, center = Offset(coin.x, coin.y))
        }

        drawContext.canvas.restore()
    }
}

@Composable
fun UIOverlay(viewModel: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // --- BOSS INCOMING WARNING BANNER ---
        androidx.compose.animation.AnimatedVisibility(
            visible = viewModel.isBossWarningActive && viewModel.gameState == GameState.PLAYING,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(32.dp, RoundedCornerShape(16.dp))
                    .border(3.dp, Color(0xFFFF1744), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xF01D0408))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("⚠ BOSS INCOMING ⚠", style = TextStyle(color = Color(0xFFFF1744), fontSize = 28.sp, fontWeight = FontWeight.Black))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PREPARE FOR BATTLE", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
                }
            }
        }
        
        // --- BOSS HP BAR ---
        androidx.compose.animation.AnimatedVisibility(
            visible = viewModel.isBossActive && viewModel.gameState == GameState.PLAYING,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp)
                .padding(horizontal = 24.dp)
        ) {
            val bossHpPct = (viewModel.activeBossHp / viewModel.activeBossMaxHp).coerceIn(0f, 1f)
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = viewModel.activeBossName,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF260E0E))
                        .border(1.dp, Color(0xFF5D1212), RoundedCornerShape(10.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bossHpPct)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFFF1744), Color(0xFFFF8A80))
                                )
                            )
                    )
                    Text(
                        text = "${viewModel.activeBossHp.toInt()} / ${viewModel.activeBossMaxHp.toInt()}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        
        // Non-blocking Wave Completed banner at the top of the screen
        androidx.compose.animation.AnimatedVisibility(
            visible = viewModel.isBetweenWaves && viewModel.gameState == GameState.PLAYING,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp)
                .padding(horizontal = 16.dp)
        ) {
            val waveCompletedNum = viewModel.currentWave - 1
            val nextWaveNum = viewModel.currentWave
            val isNextBossSpike = (nextWaveNum == 25 || nextWaveNum == 50 || nextWaveNum == 75 || nextWaveNum == 100)
            val isNextHorde = (nextWaveNum % 5 == 0 && !isNextBossSpike)
            
            val bonusRewardBonus = 50L + (waveCompletedNum * 10L)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isNextBossSpike -> Color(0xE6A11F1D) // Deep Crimson
                        isNextHorde -> Color(0xE6E65100) // Deep Orange
                        else -> Color(0xE60D131F) // Dark theme
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = when {
                        isNextBossSpike -> Color(0xFFFF1744)
                        isNextHorde -> Color(0xFFFB8C00)
                        else -> Color(0xFF00FFCC)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎉 WAVE $waveCompletedNum COMPLETED",
                                style = TextStyle(
                                    color = Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+$$bonusRewardBonus",
                                style = TextStyle(
                                    color = Color(0xFFFFD700),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isNextBossSpike -> "☠️ WAVE $nextWaveNum: BOSS APEX WARNING"
                                isNextHorde -> "🔥 WAVE $nextWaveNum: SWARM WAVE PROXIMATE"
                                else -> "🛰️ Wave $nextWaveNum secure scanning..."
                            },
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        )
                    }
                    if (!viewModel.isAutoSkipEnabled) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x33000000)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "Next: ${maxOf(1, viewModel.betweenWaveTimer.toInt())}s",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        // Instant spawn, so nothing to show here and wait time is 0.
                    }
                }
            }
        }

        // TOP HEAD HUD ROW containing WAVE, BASE HP capsule, and CURRENT RUN EARNINGS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left HUD element: Skull Wave counter styled with glowing gradient border + Auto Skip Toggle
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE60A0D15))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFC107), Color(0x33FFC107))
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💀",
                        fontSize = 15.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Column {
                        Text(
                            text = "WAVE",
                            style = TextStyle(
                                color = Color(0xFFB0BEC5),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        )
                        Text(
                            text = viewModel.currentWave.toString(),
                            style = TextStyle(
                                color = Color(0xFFFFD54F),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                // Small elegant Auto Skip toggle with custom colors under the Wave Box
                val isSkip = viewModel.isAutoSkipEnabled
                Card(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .clickable { 
                            com.example.audio.GameAudioSynth.playButtonClick()
                            viewModel.isAutoSkipEnabled = !viewModel.isAutoSkipEnabled 
                        }
                        .testTag("auto_skip_toggle"),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSkip) Color(0xE62E7D32) else Color(0xE637474F)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSkip) Color(0xFF81C784) else Color(0xFF90A4AE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (isSkip) Color(0xFF69F0AE) else Color(0xFFB0BEC5),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSkip) "AUTO SKIP: ON" else "AUTO SKIP: OFF",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // Center HUD element: Player HP Progress display capsule holding heart
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xBA07090E))
                    .border(0.5.dp, Color(0x33FF5252), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val hpPct = (viewModel.baseHp / viewModel.baseMaxHp).coerceIn(0f, 1f)
                val healthColor = Color(0xFFFF5252)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("❤️", fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = "PLAYER HP",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.SansSerif
                            )
                        )
                    }
                    Text(
                        text = "${(viewModel.baseHp * 10).toInt()} / ${(viewModel.baseMaxHp * 10).toInt()}",
                        style = TextStyle(
                            color = healthColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                // Glass-morphic slider slide track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = hpPct)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFE53935), healthColor)
                                )
                            )
                    )
                }
            }

            // Right HUD element: Stack money earnings
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Dollars component
                    Row(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE60A0D15))
                            .border(1.dp, Color(0x8000FFCC), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💵", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$${viewModel.bankDollars}",
                            style = TextStyle(
                                color = Color(0xFF00FFCC),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    // Coins component
                    Row(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE60A0D15))
                            .border(1.dp, Color(0x80FFD700), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🪙", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${viewModel.lifetimeCoins}",
                            style = TextStyle(
                                color = Color(0xFFFFD700),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(6.dp))

                // Pause toggle button next to earnings
                IconButton(
                    onClick = { viewModel.togglePause() },
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xDD0D0F14), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .testTag("pause_button")
            ) {
                Text(
                    text = "║", // Pause lines
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            }
        }

        // BOSS SPECS STATUS BOARD DISPLAY
        val bossZombie = viewModel.zombies.firstOrNull { it.type == ZombieType.BOSS }
        if (bossZombie != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset(y = 80.dp)
                    .shadow(10.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xEE1E0407))
                    .border(1.5.dp, Color(0xFFFF1744), RoundedCornerShape(10.dp))
                    .padding(8.dp)
                    .testTag("boss_health_panel"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val bossPct = (bossZombie.hp / bossZombie.maxHp).coerceIn(0f, 1f)
                Text(
                    text = bossZombie.bossName ?: "BOSS ENEMY SPAWNED",
                    color = Color(0xFFFF1744),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = bossPct)
                            .background(Color(0xFFFF1744))
                    )
                }
            }
        }

        // LOWER JOYSTICK & SIDEBAR PERMANENT CONTROLLER
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Bottom-Left Virtual joystick matching drawing requirements
            if (viewModel.gameState == GameState.PLAYING) {
                // Comfortable center-left area (~25-30% inward from the left edge)
                val scaleStartPadding = (maxWidth * 0.28f).coerceAtLeast(80.dp).coerceAtMost(160.dp)
                VirtualJoystick(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = scaleStartPadding, bottom = 8.dp)
                        .testTag("game_joystick")
                )
            }

            // Bottom-Right Upgrades Panel columns overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp)
                    .width(82.dp)
                    .testTag("upgrades_sidebar_column"),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Persistent Bank Balance badge display on top of side panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC070B11))
                        .border(0.5.dp, Color(0x77FFD700), RoundedCornerShape(6.dp))
                        .padding(vertical = 2.dp, horizontal = 3.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Bank",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${viewModel.lifetimeCoins} 🪙",
                        color = Color(0xFFFFD700),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // 1. HP UPGRADE BUTTON
                OutpostUpgradeButton(
                    title = "HP",
                    level = viewModel.hpLevel,
                    cost = viewModel.hpCost,
                    iconLabel = "❤️",
                    themeColor = Color(0xFFFF1744),
                    canAfford = viewModel.lifetimeCoins >= viewModel.hpCost,
                    onClick = { viewModel.upgradeHp() }
                )

                // 2. GUN UPGRADE BUTTON
                val weapon = viewModel.getGunStats(viewModel.gunLevel)
                OutpostUpgradeButton(
                    title = "GUN",
                    level = viewModel.gunLevel,
                    cost = viewModel.gunCost,
                    iconLabel = "🔫",
                    themeColor = Color(0xFF29B6F6),
                    canAfford = viewModel.lifetimeCoins >= viewModel.gunCost,
                    isMaxed = viewModel.gunLevel >= 4,
                    onClick = { viewModel.upgradeGun() }
                )

                // 3. REGEN UPGRADE BUTTON
                OutpostUpgradeButton(
                    title = "REGEN",
                    level = viewModel.regenLevel,
                    cost = viewModel.regenCost,
                    iconLabel = "➕",
                    themeColor = Color(0xFF66BB6A),
                    canAfford = viewModel.lifetimeCoins >= viewModel.regenCost,
                    isMaxed = viewModel.regenLevel >= 5,
                    onClick = { viewModel.upgradeRegen() }
                )
            }
        }

        // 5. FLOATING TACTICAL TURRET SELECTION & ACTIONS DIALOG PANEL
        val slotId = viewModel.selectedTurretSlotId
        if (slotId != null) {
            val turret = viewModel.turrets.firstOrNull { it.slotId == slotId }
            if (turret != null || slotId == -1) {
                // Background overlay scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { 
                            com.example.audio.GameAudioSynth.playButtonClick()
                            viewModel.selectedTurretSlotId = null 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Quick panel container
                    val equippedId = viewModel.equippedTurretsCSV.split(",").firstOrNull() ?: "Basic Turret"
                    val turretItem = com.example.model.LoadoutData.TURRETS.find { it.id == equippedId } ?: com.example.model.LoadoutData.TURRETS.first()
                    Card(
                        modifier = Modifier
                            .width(320.dp)
                            .padding(16.dp)
                            .border(
                                2.dp,
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF00FFCC), Color(0xFFE040FB))
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable(enabled = false) {} // Prevent click-throughs
                            .testTag("turret_tactical_panel"),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF2070A13)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header segment
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (slotId == -1) "📡 NEW FORTRESS LOCATION" else "🛰️ FORTRESS SLOT #${slotId + 1}",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                // Close button
                                IconButton(
                                    onClick = { viewModel.selectedTurretSlotId = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("✕", color = Color.White, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (turret == null || turret.type == TurretType.NONE) {
                                // DEPLOY TURRET
                                val glowColor = Color(0xFFF39C12) // Amber warning status
                                val buildCost = viewModel.getTurretBuildCost(turretItem.turretType)
                                val canAfford = viewModel.lifetimeCoins >= buildCost

                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                        .border(2.dp, glowColor, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(turretItem.icon, fontSize = 40.sp)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "DEPLOY ${turretItem.name.uppercase()}",
                                    color = glowColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Automated high-caliber defensive guard. Attacks nearby walking undead and scales dynamically with upgrades.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Stats Preview Card for turret purchase
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0x1300FFCC)),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0x2200FFCC))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "🛰️ INITIAL BASE LEVEL STATS",
                                            color = glowColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        val statsList = listOf(
                                            "Damage" to "${turretItem.damageBase} DMG",
                                            "Range" to "${turretItem.range.toInt()}m",
                                            "Fire Rate" to "${turretItem.fireRateBase}/s",
                                            "Type" to turretItem.turretType.name
                                        )
                                        statsList.forEach { (label, value) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = "• $label", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                                Text(text = value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Player Current Money Indicators
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "YOUR BALANCE:",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${viewModel.lifetimeCoins} 🪙",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Deploy Button
                                Button(
                                    onClick = {
                                        viewModel.deployTurret(slotId, turretItem.turretType)
                                        viewModel.selectedTurretSlotId = null
                                    },
                                    enabled = canAfford,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("turret_deploy_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = glowColor,
                                        disabledContainerColor = Color(0x33FFFFFF)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🔨 BUILD TURRET",
                                            color = if (canAfford) Color.Black else Color.White.copy(alpha = 0.4f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "🪙$buildCost",
                                            color = if (canAfford) Color.Black else Color.White.copy(alpha = 0.4f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (!canAfford) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "⚠️ Not Enough Money",
                                        color = Color(0xFFEF5350),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                // Redesigned OUTPOST CHRONO-TURRET UPGRADE SYSTEM
                                val glowColor = Color(0xFFF39C12) // Amber warning glow
                                val turretName = turretItem.name.uppercase()

                                Text(
                                    text = "$turretName (LVL ${turret.level})",
                                    color = glowColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            Text(
                                text = "ACTIVE GUARD",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Current Stats Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0x1300FFCC)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0x2200FFCC))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "⚡ ACTIVE WEAPON SPECIFICATIONS",
                                        color = glowColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    val projVal = 1
                                    val splashVal = if (turretItem.turretType == com.example.model.TurretType.PLASMA) "Thermal Blast" else "None"
                                    val pierceVal = if (turretItem.turretType == com.example.model.TurretType.PLASMA || turretItem.turretType == com.example.model.TurretType.TESLA) "3 Targets" else "1 Target"

                                    val statsList = listOf(
                                        "Bullet Kinetic Damage" to (turretItem.damageBase * (1f + (turret.level - 1) * 0.25f)).toInt().toString(),
                                        "Automated Firing Range" to "${turretItem.range.toInt()}m",
                                        "Weapon Fire Rate" to "${turretItem.fireRateBase}/s",
                                        "Active Projectile Streams" to "$projVal streams",
                                        "Target Pierces Allowed" to pierceVal,
                                        "Radial Explosive Splash" to splashVal
                                    )
                                    statsList.forEach { (label, value) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "• $label", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                            Text(text = value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Player Current Money Indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "YOUR BALANCE:",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${viewModel.lifetimeCoins} 🪙",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 1. DISMANTLE BUTTON
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.dismantleTurret(slotId)
                                        viewModel.selectedTurretSlotId = null
                                    }
                                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0x33FF1744)), // Dark red base
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFFFF1744).copy(alpha = 0.2f), CircleShape)
                                            .border(1.dp, Color(0xFFFF1744).copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("♻️", fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "DISMANTLE (50% REFUND)",
                                            color = Color(0xFFFF1744),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "You will receive 50% of the upgrade cost back.",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. UPGRADE BUTTON
                            val upgradeCost = viewModel.getTurretUpgradeCost(turret.level)
                            val canAffordUpgrade = viewModel.lifetimeCoins >= upgradeCost
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canAffordUpgrade) { viewModel.upgradeTurret(slotId) }
                                    .shadow(4.dp, RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (canAffordUpgrade) Color(0xED131824) else Color(0xD80E1119)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (canAffordUpgrade) {
                                        Brush.linearGradient(colors = listOf(glowColor, glowColor.copy(alpha = 0.35f)))
                                    } else {
                                        SolidColor(Color.White.copy(alpha = 0.08f))
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(glowColor.copy(alpha = 0.15f))
                                                .border(0.5.dp, glowColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("▲", color = glowColor, fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("UPGRADE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                            Text("Increase turret fire rate", color = Color.White.copy(alpha=0.7f), fontSize = 10.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("LVL ${turret.level} → LVL ${turret.level + 1} (Speed +25%)", color = glowColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (canAffordUpgrade) Brush.verticalGradient(listOf(Color(0xFF0F3A22), Color(0xFF144D2E))) else SolidColor(Color(0x15FFFFFF))
                                            )
                                            .border(0.5.dp, if (canAffordUpgrade) Color(0xFF2DCE89) else Color.Transparent, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (canAffordUpgrade) Text("⚡", color = Color(0xFF2DCE89), fontSize = 10.sp)
                                            Text("${upgradeCost} Coins", color = if (canAffordUpgrade) Color(0xFF2DCE89) else Color.White.copy(alpha=0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OutpostUpgradeButton(
    title: String,
    level: Int,
    cost: Long,
    iconLabel: String,
    themeColor: Color,
    canAfford: Boolean,
    isMaxed: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canAfford && !isMaxed) { onClick() }
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isMaxed) Color(0xD80E1119) else if (canAfford) Color(0xED131824) else Color(0xD80E1119)
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (isMaxed) {
                SolidColor(Color(0x3300E676))
            } else if (canAfford) {
                Brush.linearGradient(
                    colors = listOf(themeColor, themeColor.copy(alpha = 0.35f))
                )
            } else {
                SolidColor(Color.White.copy(alpha = 0.08f))
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Left aligned icon badge + level text details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Icon label protective box badge
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(themeColor.copy(alpha = 0.15f))
                        .border(0.5.dp, themeColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconLabel,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title.uppercase(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1
                    )
                    Text(
                        text = if (isMaxed) "MAX LEVEL!" else "LVL $level",
                        color = if (isMaxed) Color(0xFF00FFCC) else themeColor.copy(alpha = 0.85f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Interactive buy-upgrade pricing pill indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isMaxed) {
                            SolidColor(Color(0xFF0F3A22))
                        } else if (canAfford) {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF0F3A22), Color(0xFF144D2E))
                            )
                        } else {
                            SolidColor(Color(0x15FFFFFF))
                        }
                    )
                    .border(
                        width = 0.5.dp,
                        color = if (isMaxed) Color(0xFF00FFCC) else if (canAfford) Color(0xFF2DCE89) else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isMaxed) {
                    Text(
                        text = "MAXED OUT",
                        color = Color(0xFF00FFCC),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (canAfford) {
                            Text(
                                text = "⚡",
                                color = Color(0xFF2DCE89),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$cost 🪙",
                            color = if (canAfford) Color(0xFF2DCE89) else Color.White.copy(alpha = 0.4f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VirtualJoystick(modifier: Modifier = Modifier) {
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusRadius = 55.dp
    val maxRadiusPx = with(LocalDensity.current) { maxRadiusRadius.toPx() }

    Box(
        modifier = modifier
            .size(110.dp)
            .shadow(8.dp, CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x400C111A), Color(0xEE080C14))
                ),
                shape = CircleShape
            )
            .border(
                BorderStroke(
                    1.75.dp,
                    Brush.sweepGradient(
                        colors = listOf(Color(0xFF00FFCC), Color(0xFF0284C7), Color(0xFF00FFCC))
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = dragPosition + dragAmount
                        val distance = newOffset.getDistance()
                        dragPosition = if (distance <= maxRadiusPx) {
                            newOffset
                        } else {
                            newOffset * (maxRadiusPx / distance)
                        }
                        joystickOffset = Offset(dragPosition.x / maxRadiusPx, dragPosition.y / maxRadiusPx)
                    },
                    onDragEnd = {
                        dragPosition = Offset.Zero
                        joystickOffset = Offset.Zero
                    },
                    onDragCancel = {
                        dragPosition = Offset.Zero
                        joystickOffset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Dual rings: Decorative concentric dotted inner guidance ring
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(
                    BorderStroke(
                        0.75.dp,
                        Color(0x1F00FFCC)
                    ),
                    shape = CircleShape
                )
        )

        // Cosmic directional neon triangulation indicators
        Box(modifier = Modifier.fillMaxSize()) {
            Text("▲", color = Color(0xBB00FFCC), fontSize = 10.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp))
            Text("▼", color = Color(0xBB00FFCC), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp))
            Text("◀", color = Color(0xBB00FFCC), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
            Text("▶", color = Color(0xBB00FFCC), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
        }

        // Draggable glowing core knob
        val knobOffsetDpX = with(LocalDensity.current) { dragPosition.x.toDp() }
        val knobOffsetDpY = with(LocalDensity.current) { dragPosition.y.toDp() }
        
        Box(
            modifier = Modifier
                .offset(x = knobOffsetDpX, y = knobOffsetDpY)
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    ),
                    shape = CircleShape
                )
                .border(
                    BorderStroke(1.5.dp, Color(0xFF00FFCC)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Neon cyan micro-orb core representing cursor pivot point
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFF00FFCC), shape = CircleShape)
                    .shadow(4.dp, CircleShape)
            )
        }
    }
}

enum class MenuTab {
    HOME, LOADOUT, UPGRADES, STATS, SETTINGS
}

@Composable
fun MenuOverlay(viewModel: GameViewModel) {
    var selectedTab by remember { mutableStateOf(MenuTab.HOME) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Progress"
    )

    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A11))
            .testTag("menu_overlay")
    ) {
        // High quality tactical animated canvas background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Post-apocalyptic dark vignette gradient
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0F1B2A), Color(0xFF05080E)),
                    center = Offset(w / 2f, h / 2f),
                    radius = w * 1.2f
                )
            )

            // Draw post-apocalyptic radar grid
            val gridRadiusSpacing = 70f
            val gridAlpha = 0.08f
            var cx = 0f
            while (cx < w) {
                drawLine(
                    color = Color(0xFF00FFCC).copy(alpha = gridAlpha),
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 1f
                )
                cx += gridRadiusSpacing
            }
            var cy = 0f
            while (cy < h) {
                drawLine(
                    color = Color(0xFF00FFCC).copy(alpha = gridAlpha),
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = 1f
                )
                cy += gridRadiusSpacing
            }

            // Tech circular radar rings
            drawCircle(
                color = Color(0xFF00FFCC).copy(alpha = 0.04f),
                radius = w * 0.25f,
                center = Offset(w / 2f, h * 0.35f),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = Color(0xFF00FFCC).copy(alpha = 0.02f),
                radius = w * 0.5f,
                center = Offset(w / 2f, h * 0.35f),
                style = Stroke(width = 1f)
            )

            // Dynamic rising spores/particles
            val numParticles = 25
            for (i in 0 until numParticles) {
                val seedX = (i * 277) % w
                val seedY = (i * 487) % h
                val speed = 0.3f + (i % 4) * 0.15f
                val sizeParticle = 3f + (i % 3) * 3f

                var py = seedY - (animProgress * h * speed)
                if (py < 0) py += h // wrap

                val px = (seedX + sin(animProgress * 2f * PI.toFloat() + (i * 10)) * 20f) % w
                val alpha = 0.1f + 0.35f * sin((py / h) * PI.toFloat())

                drawCircle(
                    color = if (i % 3 == 0) Color(0xFFCC3300) else if (i % 2 == 0) Color(0xFF00FFCC) else Color(0xFFFFCC00),
                    radius = sizeParticle,
                    center = Offset(px, py),
                    alpha = alpha
                )
            }

            // Creepy zombie shadow walkers at bottom of menu screen
            val zombieX1 = (animProgress * (w + 140f) - 70f)
            val zombieY1 = h - 140f + sin(animProgress * 14f * PI.toFloat()) * 6f
            drawZombieSilhouetteCustom(this, zombieX1, zombieY1, scale = 1.1f, alpha = 0.25f, isGlowing = true)

            val zombieX2 = w - ((animProgress * 0.65f) * (w + 160f) - 80f)
            val zombieY2 = h - 110f + cos(animProgress * 11f * PI.toFloat()) * 5f
            drawZombieSilhouetteCustom(this, zombieX2, zombieY2, scale = 1.0f, alpha = 0.3f, isGlowing = false)

            drawZombieSilhouetteCustom(this, w * 0.18f, h - 85f, scale = 0.85f, alpha = 0.15f, isGlowing = false)
            drawZombieSilhouetteCustom(this, w * 0.82f, h - 100f, scale = 0.95f, alpha = 0.2f, isGlowing = false)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 64.dp) // Leave exact space for bottom navigation bar
        ) {
            // Header: Display the game title "Zombie Outpost" at the top center.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DEADWAVE SURVIVAL",
                    color = Color(0xFF00FFCC),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color(0xFFFF1744).copy(alpha = 0.7f),
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier
                        .scale(if (selectedTab == MenuTab.HOME) scalePulse else 1.0f)
                )

                Text(
                    text = "SECTOR DEFENDER COMMAND CENTRE",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
            }

            // Primary Content Switcher (using modern crossfade animation!)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(targetState = selectedTab, label = "TabChange") { tab ->
                    when (tab) {
                        MenuTab.HOME -> HomeTabLayout(viewModel)
                        MenuTab.LOADOUT -> LoadoutTabLayout(viewModel)
                        MenuTab.UPGRADES -> UpgradesTabLayout(viewModel)
                        MenuTab.STATS -> StatsTabLayout(viewModel)
                        MenuTab.SETTINGS -> SettingsTabLayout(
                            viewModel = viewModel,
                            onTriggerReset = { showResetConfirmation = true }
                        )
                    }
                }
            }
        }

        // Pinned Bottom Navigation Tab Strip (Notched 16:9 responsive deck!)
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B12)),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple(MenuTab.HOME, "🏰", "HOME"),
                    Triple(MenuTab.LOADOUT, "🎒", "LOADOUT"),
                    Triple(MenuTab.UPGRADES, "🚀", "UPGRADES"),
                    Triple(MenuTab.STATS, "📊", "STATS"),
                    Triple(MenuTab.SETTINGS, "⚙️", "SETTINGS")
                )
                for ((tab, icon, label) in tabs) {
                    val isSelected = selectedTab == tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { 
                                com.example.audio.GameAudioSynth.playButtonClick()
                                selectedTab = tab 
                            }
                            .testTag("tab_${label.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = icon,
                            fontSize = 18.sp,
                            color = if (isSelected) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Beautiful reset confirmation popup overlay
        if (showResetConfirmation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { /* Block clicks */ },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1420)),
                    border = BorderStroke(2.dp, Color(0xFFFF1744)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "⚠️ WIPEOUT ARMORY RECORDS?",
                            color = Color(0xFFFF1744),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Are you absolutely sure you want to reset your outpost armory? All record wave progress, persistent weapon/regen/outpost level upgrades and accumulated coin balances will be permanently deleted.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { 
                                    com.example.audio.GameAudioSynth.playButtonClick()
                                    showResetConfirmation = false 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3A4F)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CANCEL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.resetGameStats()
                                    showResetConfirmation = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CONFIRM WIPE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom Draw Helper for Silhouettes
private fun drawZombieSilhouetteCustom(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    x: Float,
    y: Float,
    scale: Float,
    alpha: Float,
    isGlowing: Boolean
) {
    with(drawScope) {
        val headRadius = 14f * scale
        val torsoWidth = 22f * scale
        val torsoHeight = 42f * scale
        
        val headCenter = Offset(x, y - torsoHeight / 2f - headRadius)
        
        // Head
        drawCircle(
            color = Color(0xFF03060C),
            radius = headRadius,
            center = headCenter,
            alpha = alpha
        )
        
        // Torso
        drawRoundRect(
            color = Color(0xFF03060C),
            topLeft = Offset(x - torsoWidth / 2f, y - torsoHeight / 2f),
            size = Size(torsoWidth, torsoHeight),
            cornerRadius = CornerRadius(6f * scale),
            alpha = alpha
        )
        
        // Outstretched arm L
        drawLine(
            color = Color(0xFF03060C),
            start = Offset(x - torsoWidth / 2f, y - torsoHeight * 0.2f),
            end = Offset(x - torsoWidth * 1.4f, y - torsoHeight * 0.3f),
            strokeWidth = 5f * scale,
            cap = StrokeCap.Round,
            alpha = alpha
        )
        
        // Outstretched arm R
        drawLine(
            color = Color(0xFF03060C),
            start = Offset(x + torsoWidth / 2f, y - torsoHeight * 0.2f),
            end = Offset(x + torsoWidth * 1.4f, y - torsoHeight * 0.25f),
            strokeWidth = 5f * scale,
            cap = StrokeCap.Round,
            alpha = alpha
        )

        if (isGlowing) {
            // Glowing radioactive red eyes
            drawCircle(
                color = Color(0xFF00FFCC),
                radius = 2.5f * scale,
                center = Offset(headCenter.x - 3.5f * scale, headCenter.y),
                alpha = alpha * 1.5f
            )
            drawCircle(
                color = Color(0xFF00FFCC),
                radius = 2.5f * scale,
                center = Offset(headCenter.x + 3.5f * scale, headCenter.y),
                alpha = alpha * 1.5f
            )
        }
    }
}

// Formatter for total playtime
fun formatPlayTime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        "%02d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}

@Composable
fun HomeTabLayout(viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Game Modes Section
        Text(
            text = "SELECT GAME MODE",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        val modes = com.example.viewmodel.GameMode.entries.toTypedArray()
        for (mode in modes) {
            val isSelected = viewModel.currentGameMode == mode
            
            val isLocked = when(mode) {
                com.example.viewmodel.GameMode.NORMAL -> false
                com.example.viewmodel.GameMode.HARDCORE -> viewModel.highestWaveNormal < 50
                com.example.viewmodel.GameMode.NIGHTMARE -> viewModel.highestWaveHardcore < 100
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (!isLocked) {
                            it.clickable {
                                com.example.audio.GameAudioSynth.playButtonClick()
                                viewModel.currentGameMode = mode
                            }
                        } else it
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF00FFCC).copy(alpha = 0.2f) else if (isLocked) Color.Red.copy(alpha = 0.1f) else Color(0x400C121C)
                ),
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) Color(0xFF00FFCC) else if (isLocked) Color.Red.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.04f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.displayName.uppercase(),
                            color = if (isLocked) Color.Red.copy(alpha = 0.8f) else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mode.desc,
                            color = if (isLocked) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        if (isLocked) {
                            Text(
                                text = if (mode == com.example.viewmodel.GameMode.HARDCORE) "Requirement: Wave 50+ in Normal Mode" else "Requirement: Wave 100+ in Hardcore Mode",
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(
                                text = "Reward: 15 Coins + $${mode.reward} per zombie",
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    if (isLocked) {
                        Text("🔒", fontSize = 24.sp)
                    } else if (isSelected) {
                        Text("✅", fontSize = 24.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // CONTINUE BUTTON (Conditional)
        if (viewModel.canContinueRun) {
            Button(
                onClick = { viewModel.continueGame() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .testTag("continue_run_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Color.White)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🎮", fontSize = 20.sp)
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "RESUME RUN",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "WAVE ${viewModel.savedWave}",
                            color = Color.Black.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // START RUN BUTTON
        Button(
            onClick = { viewModel.startGame() },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .testTag("start_run_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFCC3300)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💀", fontSize = 18.sp)
                Text(
                    text = "START DEPLOYMENT",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun LoadoutTabLayout(viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    com.example.audio.GameAudioSynth.playButtonClick()
                    viewModel.selectedLoadoutTab = "WEAPONS"
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.selectedLoadoutTab == "WEAPONS") Color(0xFF00FFCC) else Color(0xFF2B3A4F)
                )
            ) {
                Text("WEAPONS", color = if (viewModel.selectedLoadoutTab == "WEAPONS") Color.Black else Color.White)
            }
            Button(
                onClick = {
                    com.example.audio.GameAudioSynth.playButtonClick()
                    viewModel.selectedLoadoutTab = "TURRETS"
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.selectedLoadoutTab == "TURRETS") Color(0xFF00FFCC) else Color(0xFF2B3A4F)
                )
            ) {
                Text("TURRETS", color = if (viewModel.selectedLoadoutTab == "TURRETS") Color.Black else Color.White)
            }
        }

        if (viewModel.selectedLoadoutTab == "WEAPONS") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val subTabs = listOf("PISTOLS", "SHOTGUNS", "RIFLES", "MINIGUNS")
                subTabs.forEach { tab ->
                    val color = if (viewModel.selectedLoadoutSubTab == tab) Color(0xFFFF9800) else Color(0x400C121C)
                    Button(
                        onClick = {
                            com.example.audio.GameAudioSynth.playButtonClick()
                            viewModel.selectedLoadoutSubTab = tab
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) {
                        Text(tab, fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            val items = when (viewModel.selectedLoadoutSubTab) {
                "PISTOLS" -> com.example.model.LoadoutData.PISTOLS
                "SHOTGUNS" -> com.example.model.LoadoutData.SHOTGUNS
                "RIFLES" -> com.example.model.LoadoutData.RIFLES
                "MINIGUNS" -> com.example.model.LoadoutData.MINIGUNS
                else -> emptyList()
            }

            items.forEach { item ->
                val isOwned = viewModel.ownedWeapons.contains(item.id) || item.isFree
                val isEquipped = when (item.category) {
                    com.example.model.WeaponCategory.PISTOLS -> viewModel.equippedPistol == item.id
                    com.example.model.WeaponCategory.SHOTGUNS -> viewModel.equippedShotgun == item.id
                    com.example.model.WeaponCategory.RIFLES -> viewModel.equippedRifle == item.id
                    com.example.model.WeaponCategory.MINIGUNS -> viewModel.equippedMinigun == item.id
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x400C121C)),
                    border = BorderStroke(1.dp, if (isEquipped) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.icon, fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("DMG: ${item.damageBase} | Fire Rate: ${item.fireRateBase}/s | Range: ${item.range}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isEquipped) {
                            Button(
                                onClick = { },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                            ) {
                                Text("EQUIPPED", color = Color.Black)
                            }
                        } else if (isOwned) {
                            Button(
                                onClick = {
                                    com.example.audio.GameAudioSynth.playButtonClick()
                                    when (item.category) {
                                        com.example.model.WeaponCategory.PISTOLS -> viewModel.equippedPistol = item.id
                                        com.example.model.WeaponCategory.SHOTGUNS -> viewModel.equippedShotgun = item.id
                                        com.example.model.WeaponCategory.RIFLES -> viewModel.equippedRifle = item.id
                                        com.example.model.WeaponCategory.MINIGUNS -> viewModel.equippedMinigun = item.id
                                    }
                                    viewModel.savePersistentStatsPublic()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3A4F))
                            ) {
                                Text("EQUIP", color = Color.White)
                            }
                        } else {
                            Button(
                                    onClick = {
                                        if (viewModel.bankDollars >= item.priceDollars) {
                                            com.example.audio.GameAudioSynth.playUpgrade()
                                            viewModel.bankDollars -= item.priceDollars
                                            viewModel.ownedWeapons.add(item.id)
                                            com.example.audio.GameAudioSynth.playPurchase()
                                            viewModel.savePersistentStatsPublic()
                                        } else {
                                            com.example.audio.GameAudioSynth.playButtonClick() // error sound
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.bankDollars >= item.priceDollars) Color(0xFFFF9800) else Color.Red.copy(alpha = 0.5f))
                                ) {
                                    Text("BUY $${item.priceDollars}", color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                // TURRETS
            com.example.model.LoadoutData.TURRETS.forEach { item ->
                val isOwned = viewModel.ownedTurrets.contains(item.id) || item.priceDollars == 0L
                val isEquipped = viewModel.equippedTurretsCSV.split(",").contains(item.id)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x400C121C)),
                    border = BorderStroke(1.dp, if (isEquipped) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.icon, fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Type: ${item.turretType} | DMG: ${item.damageBase} | Fire Rate: ${item.fireRateBase}/s", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isEquipped) {
                            Button(
                                onClick = { },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                            ) {
                                Text("EQUIPPED", color = Color.Black)
                            }
                        } else if (isOwned) {
                            Button(
                                onClick = {
                                    com.example.audio.GameAudioSynth.playButtonClick()
                                    viewModel.equippedTurretsCSV = item.id
                                    viewModel.savePersistentStatsPublic()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3A4F))
                            ) {
                                Text("EQUIP", color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (viewModel.bankDollars >= item.priceDollars) {
                                        com.example.audio.GameAudioSynth.playUpgrade()
                                        viewModel.bankDollars -= item.priceDollars
                                        viewModel.ownedTurrets.add(item.id)
                                        com.example.audio.GameAudioSynth.playPurchase()
                                        viewModel.savePersistentStatsPublic()
                                    } else {
                                        com.example.audio.GameAudioSynth.playButtonClick()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.bankDollars >= item.priceDollars) Color(0xFFFF9800) else Color.Red.copy(alpha = 0.5f))
                            ) {
                                Text("BUY $${item.priceDollars}", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun UpgradesTabLayout(viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Roguelike Run Reset Explanation Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x22FB8C00)),
            border = BorderStroke(1.2.dp, Color(0xFFFB8C00)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🛡️ RUN-BASED ROGUELIKE ENGINE ACTIVE",
                    color = Color(0xFFFFA726),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "All visual & stat upgrades reset to default on starting a run. Spend your persistent bank balance on upgrades on-the-fly dynamically inside active combat runs!",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }
        }

        // Balance HUD Card with high-quality custom shape and subtle glow
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
            border = BorderStroke(1.5.dp, Color(0x4DFFD700)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "PERSISTENT BANK BALANCE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFBACADB)
                    )
                    Text(
                        "Accumulated permanent funds ready for use",
                        fontSize = 8.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Text(
                    "$${viewModel.bankDollars}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00FFCC),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(
            text = "OUTPOST ARMORY ARCHITECT WORKSHOP",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Upgrade HP (Disabled in main menu)
        PersistentUpgradeRowBetter(
            title = "SURVIVOR HITPOINTS (HP)",
            desc = "Boost maximum health point pool (+25 HP/lvl)",
            level = viewModel.hpLevel,
            cost = viewModel.hpCost,
            iconLabel = "❤️",
            themeColor = Color(0xFFFF1744),
            canAfford = false, // Must buy in-game
            onClick = { }
        )

        // Upgrade Gun (Disabled in main menu)
        PersistentUpgradeRowBetter(
            title = "WEAPON TIER (GUN)",
            desc = if (viewModel.gunLevel >= 4) "Maximum level reached." else "Increase core weapon fire rate & bullet damage",
            level = viewModel.gunLevel,
            cost = viewModel.gunCost,
            iconLabel = "🔫",
            themeColor = Color(0xFF29B6F6),
            canAfford = false, // Must buy in-game
            isMaxed = viewModel.gunLevel >= 4,
            onClick = { }
        )

        // Upgrade Regeneration (Disabled in main menu)
        PersistentUpgradeRowBetter(
            title = "BIOLOGICAL REGENERATION",
            desc = if (viewModel.regenLevel >= 5) "Maximum level reached." else "Passively regenerate player health over time (+0.5 HP/sec)",
            level = viewModel.regenLevel,
            cost = viewModel.regenCost,
            iconLabel = "➕",
            themeColor = Color(0xFF66BB6A),
            canAfford = false, // Must buy in-game
            isMaxed = viewModel.regenLevel >= 5,
            onClick = { }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// A beautiful, improved PersistentUpgradeRow showing current and next level!
@Composable
fun PersistentUpgradeRowBetter(
    title: String,
    desc: String,
    level: Int,
    cost: Long,
    iconLabel: String,
    themeColor: Color,
    canAfford: Boolean,
    isMaxed: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canAfford && !isMaxed) { onClick() }
            .testTag("persistent_upgrade_row_${title.take(5).replace(" ", "_").lowercase()}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isMaxed) Color(0xD80E1119) else if (canAfford) Color(0xFF131D31) else Color(0x660B1220)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (canAfford && !isMaxed) 1.5.dp else 1.dp,
            if (isMaxed) Color(0x3300FFCC) else if (canAfford) themeColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(themeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconLabel, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (canAfford || isMaxed) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                // Level transition display requirements
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isMaxed) "MAX LEVEL ($level)" else "Level $level",
                        color = if (isMaxed) Color(0xFF00FFCC) else themeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isMaxed) {
                        Text(
                            text = " ➜ Lvl ${level + 1}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = Color(0xFF90A4AE),
                    fontSize = 8.sp,
                    lineHeight = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "MODIFY",
                    color = if (canAfford) themeColor else Color.White.copy(alpha = 0.3f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${cost} 🪙",
                    color = if (canAfford) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.3f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatsTabLayout(viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "OUTPOST SURVIVAL LOGS & TACTICAL DATA",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Stat 1: Highest Wave Reached
        StatBlockItem(
            title = "Highest Wave Reached",
            value = "WAVE ${viewModel.highestWaveReached}",
            desc = "Maximum wave survived in Sector Outpost",
            themeColor = Color(0xFFFF9800),
            icon = "🏆"
        )

        // Stat 2: Total Zombies Killed
        StatBlockItem(
            title = "Total Zombies Killed",
            value = "${viewModel.totalZombiesKilled}",
            desc = "Enemies defeated across all historical deployments",
            themeColor = Color(0xFFFF5252),
            icon = "🧟"
        )

        // Stat 3: Total Money Earned
        StatBlockItem(
            title = "Total Money Earned",
            value = "${viewModel.totalMoneyEarned}",
            desc = "Cumulative tactical coin generation",
            themeColor = Color(0xFFFFD700),
            icon = "🪙"
        )

        // Stat 4: Total Runs Played
        StatBlockItem(
            title = "Total Runs Played",
            value = "${viewModel.totalRunsPlayed}",
            desc = "Deployment missions initiated",
            themeColor = Color(0xFF29B6F6),
            icon = "🛡️"
        )

        // Stat 5: Bosses Defeated
        StatBlockItem(
            title = "Bosses Defeated",
            value = "${viewModel.bossesDefeated}",
            desc = "Gigantic mutant beasts successfully slain",
            themeColor = Color(0xFFE040FB),
            icon = "💀"
        )

        // Stat 6: Play Time
        StatBlockItem(
            title = "Deployment Play Time",
            value = formatPlayTime(viewModel.playTimeSeconds),
            desc = "Total time spent actively protecting sectors",
            themeColor = Color(0xFF00FFCC),
            icon = "⏱️"
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SettingsTabLayout(
    viewModel: GameViewModel,
    onTriggerReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "TACTICAL OUTPOST SYSTEM CONFIG",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Custom config list rows card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sound Effects Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sound Effects", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Combat acoustic responses", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    }
                    Switch(
                        checked = viewModel.soundOn,
                        onCheckedChange = { viewModel.toggleSound(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("sound_toggle")
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                // Music Soundtrack Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Music Soundtrack", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Ambient background synth loops", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    }
                    Switch(
                        checked = viewModel.musicOn,
                        onCheckedChange = { viewModel.toggleMusic(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("music_toggle")
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                // Vibration Haptic Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Vibration Haptics", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Outpost impact and firing feedbacks", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    }
                    Switch(
                        checked = viewModel.vibrationOn,
                        onCheckedChange = { viewModel.vibrationOn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("vibration_toggle")
                    )
                }
            }
        }

        // Beautiful Volume Controls Card
        Text(
            text = "AUDIO VOLUME SLIDERS",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Master Volume Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Master Volume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(viewModel.masterVolume * 100).toInt()}%", color = Color(0xFF00FFCC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = viewModel.masterVolume,
                        onValueChange = { viewModel.updateMasterVolume(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00FFCC),
                            activeTrackColor = Color(0xFF00FFCC),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("master_volume_slider")
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                // Music Volume Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Music Volume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(viewModel.musicVolume * 100).toInt()}%", color = Color(0xFF00FFCC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = viewModel.musicVolume,
                        onValueChange = { viewModel.updateMusicVolume(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00FFCC),
                            activeTrackColor = Color(0xFF00FFCC),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("music_volume_slider")
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                // SFX Volume Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sound Effects Volume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(viewModel.sfxVolume * 100).toInt()}%", color = Color(0xFF00FFCC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = viewModel.sfxVolume,
                        onValueChange = { viewModel.updateSfxVolume(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00FFCC),
                            activeTrackColor = Color(0xFF00FFCC),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("sfx_volume_slider")
                    )
                }
            }
        }

        // Quality Select Segmented buttons Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("VECTORS RENDERING QUALITY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(
                    text = "Balance particle systems performance ratios",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1220), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val settings = listOf("Low", "Medium", "High")
                    for (setting in settings) {
                        val selected = viewModel.qualitySetting == setting
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(
                                    if (selected) Color(0xFF00FFCC) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { 
                                    com.example.audio.GameAudioSynth.playButtonClick()
                                    viewModel.qualitySetting = setting 
                                }
                                .testTag("quality_tab_${setting.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = setting,
                                color = if (selected) Color.Black else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Cheat panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x3300FFCC)),
            border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "⚙️ DEV SANDBOX CHEAT",
                    color = Color(0xFF00FFCC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Provide the tactical commander with emergency credits to buy or test advanced defensive turrets and high-level base repairs instantly.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
                Button(
                    onClick = { 
                        com.example.audio.GameAudioSynth.playButtonClick()
                        viewModel.claimTestCoins() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ADD +$1,000 COINS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }

        // Wipe records warning card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x33D50000)),
            border = BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "🛡️ RESET ARMORY METRICS",
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Wipe all records, level progress, coins and revert the command outpost status to level zero.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
                Button(
                    onClick = { 
                        com.example.audio.GameAudioSynth.playButtonClick()
                        onTriggerReset() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RESTORE PROGRESS TO ABSOLUTE ZERO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }

        Text(
            text = "v2.0.0 • Jetpack Compose • Room Persistent Database",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun PersistentUpgradeRow(
    title: String,
    desc: String,
    level: Int,
    cost: Long,
    iconLabel: String,
    themeColor: Color,
    canAfford: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canAfford) { onClick() }
            .testTag("persistent_upgrade_row_${title.take(5).replace(" ", "_")}"),
        colors = CardDefaults.cardColors(
            containerColor = if (canAfford) Color(0xFF1E293B) else Color(0x770F172A)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (canAfford) 1.5.dp else 1.dp,
            if (canAfford) themeColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(themeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconLabel, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$title (LVL $level)",
                    color = if (canAfford) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    color = Color(0xFF90A4AE),
                    fontSize = 8.sp,
                    lineHeight = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "UPGRADE",
                    color = if (canAfford) themeColor else Color.White.copy(alpha = 0.3f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "$$cost",
                    color = if (canAfford) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatBlockItem(
    title: String,
    value: String,
    desc: String,
    themeColor: Color,
    icon: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x221E293B)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(themeColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = Color(0xFF90A4AE),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    color = themeColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = desc,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
fun InstructionRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF00FFCC),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GameOverOverlay(viewModel: GameViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEA0B0305))
            .padding(24.dp)
            .testTag("game_over_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF230D11))
                .border(2.dp, Color(0xFFFF1744), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF1744),
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = "SURVIVOR PERISHED",
                color = Color(0xFFFF1744),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = "You were overwhelmed by the zombie horde at Wave ${viewModel.currentWave}.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33000000))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccomplishmentRow(label = "WAVE REACHED", valStr = "${viewModel.currentWave}", valCol = Color.White)
                AccomplishmentRow(label = "ZOMBIES KILLED", valStr = "${viewModel.runZombiesKilled}", valCol = Color(0xFFEF5350))
                AccomplishmentRow(label = "COINS EARNED", valStr = "${viewModel.runEarnings} 🪙", valCol = Color(0xFFFFD700))
                AccomplishmentRow(label = "DOLLARS EARNED", valStr = "$${viewModel.runDollarsEarned}", valCol = Color(0xFF00FFCC))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!viewModel.reviveUsedThisRun) {
                    Button(
                        onClick = {
                            if (activity != null) {
                                AdManager.showRewardedAd(
                                    activity,
                                    onRewarded = { viewModel.revive() },
                                    onAdDismissed = {}
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("revive_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("REVIVE (Watch Ad)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }

                if (!viewModel.doubleRewardsClaimedThisRun) {
                    Button(
                        onClick = {
                            if (activity != null) {
                                AdManager.showRewardedAd(
                                    activity,
                                    onRewarded = { viewModel.claimDoubleRewardsAndExit() },
                                    onAdDismissed = {}
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("double_rewards_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("2X REWARDS (Watch Ad)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.exitToMenu() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("no_thanks_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text("NO THANKS", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun AccomplishmentRow(label: String, valStr: String, valCol: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = valStr,
            color = valCol,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun BetweenWavesAnnouncement(
    wave: Int,
    countdown: Float
) {
    val nextWave = wave + 1
    val isNextBossSpike = (nextWave == 25 || nextWave == 50 || nextWave == 75 || nextWave == 100)
    val isNextHorde = (nextWave % 5 == 0 && !isNextBossSpike)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .testTag("between_waves_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "WAVE $wave COMPLETED",
                color = Color(0xFF81C784),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            when {
                isNextBossSpike -> {
                    // Boss Spike Wave Design
                    Card(
                        modifier = Modifier
                            .width(360.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF5350)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, Color(0xFFEF5350))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "💀 CRITICAL THREAT LEVEL 💀",
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "WAVE $nextWave: BOSS ALERT",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Elite bio-goliath mutation with heavy special cyber-zombie swarms closing in on the Outpost. Upgrade base defenses and weapons immediately!",
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
                isNextHorde -> {
                    // Horde Wave Design
                    Card(
                        modifier = Modifier
                            .width(360.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FF9800)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, Color(0xFFFF9800))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚠️ MASSIVE BIOMASS DETECTED ⚠️",
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "WAVE $nextWave: SWARM HORDE",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Extreme surge of walker mutants and rapid-response runner packs detected. Automatic turrets and fast weapons recommended.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
                else -> {
                    // Normal Wave transition
                    Card(
                        modifier = Modifier
                            .width(340.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xE60D131F)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF00FFCC))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🛰️ SCANNING SECTOR SECURE",
                                color = Color(0xFF00FFCC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "WAVE $nextWave APPROACHING",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Prepare defenses. Upgrading movement speed and bullet damage scales your survival survivability rate.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val accentColor = when {
                    isNextBossSpike -> Color(0xFFEF5350)
                    isNextHorde -> Color(0xFFFF9800)
                    else -> Color(0xFF00FFCC)
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "NEXT ATTACK IN ${maxOf(1, countdown.toInt())}s",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(
        scaleX = scale,
        scaleY = scale
    )
)

@Composable
fun TurretBuildRow(
    title: String,
    desc: String,
    cost: Long,
    iconLabel: String,
    tagColor: Color,
    canAfford: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canAfford) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (canAfford) Color(0xFF131A2D) else Color(0xFF0F121C)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (canAfford) tagColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(tagColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconLabel, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (canAfford) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = desc,
                    color = Color(0xFF78909C),
                    fontSize = 9.sp
                )
            }

            Text(
                text = "$$cost",
                color = if (canAfford) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
