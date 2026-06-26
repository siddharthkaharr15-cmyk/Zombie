package com.example.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.example.model.ZombieType
import kotlin.math.cos
import kotlin.math.sin

object CharacterRenderer {

    // Helper functions for organic shapes
    private fun DrawScope.drawOrganicBlob(color: Color, center: Offset, radiusX: Float, radiusY: Float, rotation: Float = 0f) {
        rotate(rotation, center) {
            drawOval(color, topLeft = Offset(center.x - radiusX, center.y - radiusY), size = Size(radiusX * 2, radiusY * 2))
        }
    }

    fun DrawScope.drawAdvancedPlayer(
        center: Offset,
        angle: Float,
        movementState: Boolean,
        isShooting: Boolean,
        timeMs: Long
    ) {
        val walkCycle = if (movementState) sin(timeMs / 100f) else 0f
        val recoil = if (isShooting) -4f else 0f
        
        translate(center.x, center.y) {
            rotate(degrees = angle, pivot = Offset.Zero) {
                // Drop shadow
                drawOval(
                    color = Color(0x70000000),
                    topLeft = Offset(-18f, -18f),
                    size = Size(36f, 36f)
                )

                // LEGS (Tactical Pants)
                // Left Boot & leg
                drawOrganicBlob(Color(0xFF262626), Offset(-8f + walkCycle * 10f, -12f), 6f, 5f)
                // Right Boot & leg
                drawOrganicBlob(Color(0xFF262626), Offset(-8f - walkCycle * 10f, 12f), 6f, 5f)

                // BODY - Survivor Gear
                // Backpack
                drawRoundRect(Color(0xFF382C1B), topLeft = Offset(-20f, -12f), size = Size(14f, 24f), cornerRadius = CornerRadius(4f))
                drawRoundRect(Color(0xFF251E13), topLeft = Offset(-22f, -8f), size = Size(6f, 16f), cornerRadius = CornerRadius(2f)) // Bedroll

                // Torso - Flannel/Jacket
                drawOrganicBlob(Color(0xFF3B4031), Offset(-2f, 0f), 12f, 16f)

                // Tactical Vest
                drawRoundRect(Color(0xFF181C16), topLeft = Offset(-10f, -12f), size = Size(14f, 24f), cornerRadius = CornerRadius(3f))
                
                // Gear pouches
                drawRect(Color(0xFF2E332A), topLeft = Offset(-5f, -9f), size = Size(6f, 5f))
                drawRect(Color(0xFF2E332A), topLeft = Offset(-5f, 4f), size = Size(6f, 5f))
                drawRect(Color(0xFF222222), topLeft = Offset(0f, -2f), size = Size(4f, 4f)) // Radio

                // ARMS AND WEAPON
                val gunBaseX = 12f + recoil
                val gunBaseY = 0f
                
                // Muzzle Flash
                if (isShooting) {
                    val flashPath = Path().apply {
                        moveTo(gunBaseX + 28f, gunBaseY)
                        lineTo(gunBaseX + 42f, gunBaseY - 6f)
                        lineTo(gunBaseX + 48f, gunBaseY)
                        lineTo(gunBaseX + 42f, gunBaseY + 6f)
                        close()
                    }
                    drawPath(flashPath, Color(0xBBFFAA00))
                    drawCircle(Color(0xFFFFDD44), radius = 4f, center = Offset(gunBaseX + 30f, gunBaseY))
                }

                // Left Arm
                drawOrganicBlob(Color(0xFF3B4031), Offset(4f, -10f), 6f, 4f, 20f)
                drawOrganicBlob(Color(0xFFD6A07E), Offset(gunBaseX + 16f, gunBaseY - 3f), 3f, 3f) // Left Hand
                
                // Right Arm
                drawOrganicBlob(Color(0xFF3B4031), Offset(0f, 10f), 6f, 4f, -20f)
                drawOrganicBlob(Color(0xFFD6A07E), Offset(gunBaseX + 2f, gunBaseY + 4f), 3f, 3f) // Right Hand

                // WEAPON - Realistic Assault Rifle
                drawRoundRect(Color(0xFF1B1B1B), topLeft = Offset(gunBaseX - 10f, gunBaseY - 2.5f), size = Size(12f, 5f), cornerRadius = CornerRadius(1f)) // Stock
                drawRect(Color(0xFF353535), topLeft = Offset(gunBaseX + 2f, gunBaseY - 3.5f), size = Size(16f, 7f)) // Receiver
                drawRect(Color(0xFF1B1B1B), topLeft = Offset(gunBaseX + 18f, gunBaseY - 1f), size = Size(12f, 2f)) // Barrel
                drawRect(Color(0xFF0F0F0F), topLeft = Offset(gunBaseX + 6f, gunBaseY - 5.5f), size = Size(8f, 2f)) // Sight
                drawRect(Color(0xFF222222), topLeft = Offset(gunBaseX + 4f, gunBaseY + 3.5f), size = Size(4f, 4f)) // Magazine

                // HEAD - Survivor
                drawCircle(color = Color(0xFFD6A07E), radius = 8f, center = Offset(0f, 0f)) // Skin
                
                // Hair / Beanie
                val beanieArc = Path().apply {
                    addArc(Rect(-8.5f, -8.5f, 8.5f, 8.5f), 90f, 180f)
                    lineTo(-8.5f, 0f)
                    close()
                }
                drawPath(beanieArc, color = Color(0xFF2A2A2A)) 
            }
        }
    }

    fun DrawScope.drawAdvancedZombie(
        center: Offset,
        angle: Float,
        zombie: com.example.model.Zombie,
        timeMs: Long,
        isAttacking: Boolean
    ) {
        val walkTime = timeMs / 120f
        val attackOffset = if (isAttacking) 8f else 0f
        
        translate(center.x, center.y) {
            rotate(degrees = angle, pivot = Offset.Zero) {
                // Shadow
                drawOval(
                    color = Color(0x70000000),
                    topLeft = Offset(-18f, -18f),
                    size = Size(36f, 36f)
                )
                
                when (zombie.type) {
                    com.example.model.ZombieType.WALKER -> drawNormalZombie(walkTime, attackOffset)
                    com.example.model.ZombieType.RUNNER -> drawFastZombie(walkTime, attackOffset)
                    com.example.model.ZombieType.TANK -> drawTankZombie(walkTime, attackOffset)
                    com.example.model.ZombieType.BOMBER -> drawBomberZombie(walkTime, attackOffset, timeMs)
                    com.example.model.ZombieType.BOSS -> drawBossZombie(walkTime, attackOffset, timeMs, zombie.bossName ?: "")
                }
            }
        }
    }

    private fun DrawScope.drawNormalZombie(walkTime: Float, attackOffset: Float) {
        val walk1 = sin(walkTime) * 6f
        val walk2 = -sin(walkTime) * 6f
        val flesh = Color(0xFF9BAB91) // Lighter pale flesh
        val clothes = Color(0xFF6B7887) // Lighter clothes
        val darkBlood = Color(0xFF8B1A1A) // Brighter blood

        // LEGS (Dragging)
        drawOrganicBlob(Color(0xFF2C2F33), Offset(-8f + walk1, -8f), 5f, 4f)
        drawOrganicBlob(Color(0xFF2C2F33), Offset(-8f + walk2, 8f), 5f, 4f)

        // BODY - Ragged clothes
        drawOrganicBlob(clothes, Offset(-2f, 0f), 10f, 14f)
        // Infection details - exposed flesh / blood
        drawOrganicBlob(flesh, Offset(0f, -6f), 3f, 4f)
        drawOrganicBlob(darkBlood, Offset(2f, 5f), 4f, 5f)
        drawCircle(Color(0xFF1E1E1E), radius = 2f, center = Offset(4f, -4f)) // Wound hole

        // ARMS - Reaching
        val armSwing = sin(walkTime * 0.5f) * 2f
        drawOrganicBlob(flesh, Offset(6f + attackOffset, -12f - armSwing), 7f, 3f)
        drawOrganicBlob(flesh, Offset(6f + attackOffset, 12f + armSwing), 7f, 3f)
        
        // Blooded hands
        drawOrganicBlob(darkBlood, Offset(13f + attackOffset, -12f - armSwing), 2f, 2f)
        drawOrganicBlob(darkBlood, Offset(13f + attackOffset, 12f + armSwing), 2f, 2f)

        // HEAD
        drawCircle(flesh, radius = 7f, center = Offset(2f, 0f))
        
        // Face features
        drawOrganicBlob(darkBlood, Offset(6f, -3f), 2f, 1.5f) // Gored eye
        drawCircle(Color(0xFF111111), radius = 1f, center = Offset(6f, 3f)) // Other eye hollow
        drawOrganicBlob(Color(0xFF441111), Offset(8f, 0f), 2f, 3f) // Jaw blood
    }

    private fun DrawScope.drawFastZombie(walkTime: Float, attackOffset: Float) {
        val walk1 = sin(walkTime * 1.5f) * 10f
        val walk2 = -sin(walkTime * 1.5f) * 10f
        val flesh = Color(0xFFB5B3A1) // Lighter pale flesh
        val clothes = Color(0xFF9E5151) // Brighter Bloody red shirt
        val darkBlood = Color(0xFF7A1E1E)

        // LEGS (lean running)
        drawOrganicBlob(Color(0xFF1F1F1F), Offset(-8f + walk1, -6f), 5f, 3.5f)
        drawOrganicBlob(Color(0xFF1F1F1F), Offset(-8f + walk2, 6f), 5f, 3.5f)

        // BODY - Skinny & hunched
        drawOrganicBlob(clothes, Offset(-1f, 0f), 9f, 11f)
        drawOrganicBlob(darkBlood, Offset(3f, 0f), 5f, 8f) // Heavily bloodied front

        // ARMS
        drawOrganicBlob(flesh, Offset(8f + attackOffset, -9f), 8f, 2.5f)
        drawOrganicBlob(flesh, Offset(8f + attackOffset, 9f), 8f, 2.5f)

        // HEAD
        drawCircle(flesh, radius = 6f, center = Offset(4f, 0f))
        drawCircle(Color(0xFF000000), radius = 1.2f, center = Offset(8f, -2f))
        drawCircle(Color(0xFF000000), radius = 1.2f, center = Offset(8f, 2f))
    }

    private fun DrawScope.drawTankZombie(walkTime: Float, attackOffset: Float) {
        val walkShove = sin(walkTime * 0.7f) * 6f
        val flesh = Color(0xFF8B9480) // Lighter flesh
        val mutations = Color(0xFF806868) // Lighter mutations
        val darkBlood = Color(0xFF5A1616)

        // LEGS
        drawOrganicBlob(Color(0xFF282C24), Offset(-10f + walkShove, -14f), 8f, 7f)
        drawOrganicBlob(Color(0xFF282C24), Offset(-10f - walkShove, 14f), 8f, 7f)

        // MASSIVE TORSO
        drawOrganicBlob(flesh, Offset(-4f, 0f), 16f, 24f)
        
        // Tumors/Mutations
        drawOrganicBlob(mutations, Offset(0f, -12f), 8f, 10f)
        drawOrganicBlob(mutations, Offset(-6f, 10f), 7f, 9f)

        // SCARS
        drawLine(darkBlood, Offset(-4f, -16f), Offset(6f, -8f), 2f)
        drawLine(darkBlood, Offset(-2f, 12f), Offset(4f, 16f), 2f)

        // ARMS
        drawOrganicBlob(flesh, Offset(6f, -20f), 12f, 12f)
        drawOrganicBlob(flesh, Offset(6f, 20f), 12f, 12f)
        
        // Fists
        drawCircle(mutations, radius = 9f, center = Offset(20f + attackOffset, -22f))
        drawCircle(mutations, radius = 9f, center = Offset(20f + attackOffset, 22f))

        // HEAD
        drawCircle(flesh, radius = 7f, center = Offset(6f, 0f))
        drawOrganicBlob(darkBlood, Offset(11f, 0f), 2f, 4f) // Gritted teeth
    }

    private fun DrawScope.drawBomberZombie(walkTime: Float, attackOffset: Float, timeMs: Long) {
        val walk1 = sin(walkTime * 0.8f) * 6f
        val walk2 = -sin(walkTime * 0.8f) * 6f
        val flesh = Color(0xFFA6AD94) // Lighter flesh
        val infected = Color(0xFF6E8756) // Brighter green
        
        // LEGS
        drawOrganicBlob(Color(0xFF3B2E25), Offset(-8f + walk1, -8f), 5f, 4f)
        drawOrganicBlob(Color(0xFF3B2E25), Offset(-8f + walk2, 8f), 5f, 4f)

        // TORSO - Swollen and decayed
        drawOrganicBlob(flesh, Offset(-2f, 0f), 14f, 16f)
        drawOrganicBlob(infected, Offset(4f, 0f), 11f, 14f) // Bloated belly
        
        // Bursting pustules
        drawCircle(Color(0xFF7A8B44), radius = 3f, center = Offset(8f, -6f))
        drawCircle(Color(0xFF7A8B44), radius = 4f, center = Offset(10f, 4f))
        drawCircle(Color(0xFF8E9E55), radius = 2.5f, center = Offset(6f, 10f))

        // ARMS
        drawOrganicBlob(flesh, Offset(4f + attackOffset, -14f), 7f, 4f)
        drawOrganicBlob(flesh, Offset(4f + attackOffset, 14f), 7f, 4f)

        // HEAD
        drawCircle(flesh, radius = 7f, center = Offset(5f, 0f))
        drawOrganicBlob(Color(0xFF1E1E1E), Offset(7f, -4f), 4f, 7f) // Gas Mask / Respirator
        drawCircle(Color(0xFF111111), radius = 2f, center = Offset(9f, -3f)) // Filter
    }

    private fun DrawScope.drawBossZombie(walkTime: Float, attackOffset: Float, timeMs: Long, bossName: String) {
        val throb = sin(timeMs / 200f) * 1.5f
        
        val isBehemoth = bossName.contains("TOXIC")
        val isReaper = bossName.contains("REAPER")
        val isNightmare = bossName.contains("NIGHTMARE")
        
        if (isBehemoth) {
            val walkShove = sin(walkTime * 0.4f) * 8f
            val flesh = Color(0xFF7B8569) // Lighter Behemoth flesh
            val toxic = Color(0xFF8ECD2E) // Brighter toxic green
            val crust = Color(0xFF566644)

            // Legs
            drawOrganicBlob(crust, Offset(-14f + walkShove, -24f), 12f, 12f)
            drawOrganicBlob(crust, Offset(-14f - walkShove, 24f), 12f, 12f)

            // Body
            drawOrganicBlob(flesh, Offset(-10f, 0f), 24f + throb, 32f + throb)
            
            // Toxic pustules
            drawCircle(toxic, radius = 6f + throb, center = Offset(4f, -14f))
            drawCircle(toxic, radius = 5f + throb, center = Offset(6f, 16f))
            drawCircle(toxic, radius = 8f + throb, center = Offset(-2f, 0f))
            
            // Arms
            drawOrganicBlob(flesh, Offset(16f + attackOffset, -30f), 16f, 12f)
            drawOrganicBlob(flesh, Offset(16f + attackOffset, 30f), 16f, 12f)
            
            // Head
            drawCircle(flesh, radius = 10f, center = Offset(12f, 0f))
            drawCircle(toxic, radius = 3f, center = Offset(18f, -4f))
            drawCircle(toxic, radius = 3f, center = Offset(18f, 4f))
            
        } else if (isReaper) {
            val walk1 = sin(walkTime * 2.0f) * 12f
            val flesh = Color(0xFF6B6262) // Lighter flesh
            val bone = Color(0xFFFFFFFF) // Brighter bone
            val blood = Color(0xFF8B151C) // Brighter blood

            // Legs
            drawOrganicBlob(Color(0xFF1E1C1A), Offset(-12f + walk1, -10f), 8f, 6f)
            drawOrganicBlob(Color(0xFF1E1C1A), Offset(-12f - walk1, 10f), 8f, 6f)

            // Body 
            drawOrganicBlob(flesh, Offset(-6f, 0f), 14f, 18f)
            
            // Exposed Ribs
            for(i in -1..1) {
                drawRect(bone, topLeft = Offset(0f, i * 6f - 1f), size = Size(6f, 2f))
            }

            // Scythe Arms
            drawOrganicBlob(flesh, Offset(4f + attackOffset, -18f), 10f, 4f)
            drawOrganicBlob(flesh, Offset(4f + attackOffset, 18f), 10f, 4f)
            
            // Bone Blades
            drawRoundRect(bone, topLeft = Offset(14f + attackOffset, -26f), size = Size(3f, 24f), cornerRadius = CornerRadius(1f))
            drawRoundRect(bone, topLeft = Offset(14f + attackOffset, 2f), size = Size(3f, 24f), cornerRadius = CornerRadius(1f))
            
            // Blood on blades
            drawOrganicBlob(blood, Offset(16f + attackOffset, -22f), 2f, 5f)
            drawOrganicBlob(blood, Offset(16f + attackOffset, 22f), 2f, 5f)

            // Head
            drawCircle(flesh, radius = 7f, center = Offset(8f, 0f))
            drawCircle(Color(0xFF881111), radius = 2f, center = Offset(12f, -3f)) 
            drawCircle(Color(0xFF881111), radius = 2f, center = Offset(12f, 3f)) 
            
        } else {
            // NIGHTMARE & BRUTE - Realistic Armored/Hulking Zombie
            val walkShove = sin(walkTime * 0.3f) * 10f
            val armor = Color(0xFF4D5361) // Brighter armor
            val flesh = Color(0xFF6E635C) // Lighter flesh
            val detail = Color(0xFF3A3E47)

            // Massive Boots
            drawRoundRect(armor, topLeft = Offset(-18f + walkShove, -30f), size = Size(20f, 18f), cornerRadius = CornerRadius(4f))
            drawRoundRect(armor, topLeft = Offset(-18f - walkShove, 12f), size = Size(20f, 18f), cornerRadius = CornerRadius(4f))

            // Body
            drawOrganicBlob(flesh, Offset(-12f, 0f), 26f, 38f)
            
            // Rusty Riot Armor Overlay
            drawRoundRect(armor, topLeft = Offset(-16f, -32f), size = Size(28f, 64f), cornerRadius = CornerRadius(6f))
            
            // Armor damage/scratches
            drawLine(Color(0xFF424754), Offset(-8f, -20f), Offset(4f, -12f), 2f)
            drawLine(Color(0xFF424754), Offset(-12f, 16f), Offset(0f, 24f), 2f)
            
            // Armored gauntlets
            drawRoundRect(armor, topLeft = Offset(8f + attackOffset, -42f), size = Size(24f, 20f), cornerRadius = CornerRadius(4f))
            drawRoundRect(armor, topLeft = Offset(8f + attackOffset, 22f), size = Size(24f, 20f), cornerRadius = CornerRadius(4f))
            
            // Blood & Rust on gauntlets
            drawOrganicBlob(Color(0xFF4A1A1A), Offset(22f + attackOffset, -32f), 8f, 6f)
            drawOrganicBlob(Color(0xFF4A1A1A), Offset(22f + attackOffset, 32f), 8f, 6f)

            // Head (Riot Helmet)
            drawCircle(armor, radius = 14f, center = Offset(10f, 0f))
            drawRect(detail, topLeft = Offset(18f, -6f), size = Size(6f, 12f)) // Visor slit
            drawCircle(Color(0xFF991111), radius = 2f, center = Offset(22f, 0f)) // Piercing eye
        }
    }
}
