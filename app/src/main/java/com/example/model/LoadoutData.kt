package com.example.model

enum class WeaponCategory {
    PISTOLS, SHOTGUNS, RIFLES, MINIGUNS
}

data class WeaponItem(
    val id: String,
    val name: String,
    val category: WeaponCategory,
    val priceDollars: Long,
    val damageBase: Float,
    val fireRateBase: Float,
    val range: Float,
    val isFree: Boolean = false,
    val icon: String = "🔫"
)

data class TurretItem(
    val id: String,
    val name: String,
    val priceDollars: Long,
    val damageBase: Float,
    val fireRateBase: Float,
    val range: Float,
    val turretType: TurretType = TurretType.NONE,
    val icon: String = "📡"
)

object LoadoutData {
    val PISTOLS = listOf(
        WeaponItem("Ranger P1", "Ranger P1", WeaponCategory.PISTOLS, 0, 15f, 2.5f, 300f, true, "🔫"),
        WeaponItem("Desert Hawk", "Desert Hawk", WeaponCategory.PISTOLS, 500, 25f, 2.0f, 350f, false, "🦅"),
        WeaponItem("Shadow Pistol", "Shadow Pistol", WeaponCategory.PISTOLS, 1000, 35f, 2.8f, 320f, false, "🥷"),
        WeaponItem("Venom Sidearm", "Venom Sidearm", WeaponCategory.PISTOLS, 2500, 45f, 3.0f, 330f, false, "🐍"),
        WeaponItem("Thunder P9", "Thunder P9", WeaponCategory.PISTOLS, 5000, 60f, 3.5f, 380f, false, "⚡"),
        WeaponItem("Crimson Eagle", "Crimson Eagle", WeaponCategory.PISTOLS, 10000, 80f, 3.0f, 400f, false, "🦅"),
        WeaponItem("Falcon X", "Falcon X", WeaponCategory.PISTOLS, 20000, 110f, 4.0f, 450f, false, "🦅")
    )

    val SHOTGUNS = listOf(
        WeaponItem("Crusher 12G", "Crusher 12G", WeaponCategory.SHOTGUNS, 0, 12f, 1.0f, 200f, true, "💥"),
        WeaponItem("Bone Breaker", "Bone Breaker", WeaponCategory.SHOTGUNS, 2500, 20f, 1.1f, 220f, false, "🦴"),
        WeaponItem("Doom Shotgun", "Doom Shotgun", WeaponCategory.SHOTGUNS, 5000, 32f, 1.2f, 240f, false, "💀"),
        WeaponItem("Titan Pump", "Titan Pump", WeaponCategory.SHOTGUNS, 10000, 50f, 1.0f, 260f, false, "🗿"),
        WeaponItem("Reaper SG", "Reaper SG", WeaponCategory.SHOTGUNS, 20000, 75f, 1.4f, 280f, false, "☠️"),
        WeaponItem("Hellfire Shotgun", "Hellfire Shotgun", WeaponCategory.SHOTGUNS, 35000, 105f, 1.6f, 300f, false, "🔥"),
        WeaponItem("Storm Blaster", "Storm Blaster", WeaponCategory.SHOTGUNS, 50000, 150f, 1.8f, 330f, false, "🌩️")
    )

    val RIFLES = listOf(
        WeaponItem("AR-X Defender", "AR-X Defender", WeaponCategory.RIFLES, 0, 18f, 5.0f, 400f, true, "🎯"),
        WeaponItem("Phantom Rifle", "Phantom Rifle", WeaponCategory.RIFLES, 5000, 26f, 5.5f, 420f, false, "👻"),
        WeaponItem("Viper AR", "Viper AR", WeaponCategory.RIFLES, 10000, 38f, 6.0f, 450f, false, "🐍"),
        WeaponItem("Ranger MK2", "Ranger MK2", WeaponCategory.RIFLES, 20000, 55f, 6.5f, 480f, false, "🪖"),
        WeaponItem("Inferno Rifle", "Inferno Rifle", WeaponCategory.RIFLES, 40000, 80f, 7.0f, 520f, false, "🔥"),
        WeaponItem("Eclipse AR", "Eclipse AR", WeaponCategory.RIFLES, 75000, 115f, 8.0f, 550f, false, "🌑"),
        WeaponItem("Predator Rifle", "Predator Rifle", WeaponCategory.RIFLES, 125000, 160f, 9.0f, 600f, false, "👁️")
    )

    val MINIGUNS = listOf(
        WeaponItem("Vulcan M1", "Vulcan M1", WeaponCategory.MINIGUNS, 0, 10f, 10.0f, 350f, true, "⚙️"),
        WeaponItem("Titan Minigun", "Titan Minigun", WeaponCategory.MINIGUNS, 50000, 16f, 12.0f, 380f, false, "🤖"),
        WeaponItem("Cyclone MG", "Cyclone MG", WeaponCategory.MINIGUNS, 100000, 24f, 14.0f, 400f, false, "🌪️"),
        WeaponItem("Destroyer X", "Destroyer X", WeaponCategory.MINIGUNS, 200000, 35f, 16.0f, 450f, false, "☄️"),
        WeaponItem("Thunder Minigun", "Thunder Minigun", WeaponCategory.MINIGUNS, 350000, 50f, 18.0f, 480f, false, "⛈️"),
        WeaponItem("Inferno Spinner", "Inferno Spinner", WeaponCategory.MINIGUNS, 500000, 75f, 20.0f, 500f, false, "🔥"),
        WeaponItem("Apocalypse MG", "Apocalypse MG", WeaponCategory.MINIGUNS, 500000, 110f, 25.0f, 550f, false, "🌋")
    )
    
    val WEAPONS = PISTOLS + SHOTGUNS + RIFLES + MINIGUNS

    val TURRETS = listOf(
        TurretItem("Basic Turret", "Basic Turret", 0, 15f, 1.5f, 250f, TurretType.GATLING, "🔰"),
        TurretItem("Auto Turret Alpha", "Auto Turret Alpha", 5000, 20f, 2.0f, 260f, TurretType.GATLING, "𝗔"),
        TurretItem("Plasma Sentry", "Plasma Sentry", 15000, 35f, 1.0f, 300f, TurretType.PLASMA, "🟣"),
        TurretItem("Tesla Coil Mk1", "Tesla Coil Mk1", 25000, 18f, 3.0f, 200f, TurretType.TESLA, "⚡"),
        TurretItem("Gatling Defender", "Gatling Defender", 40000, 25f, 4.0f, 300f, TurretType.GATLING, "🛡️"),
        TurretItem("Heavy Plasma", "Heavy Plasma", 60000, 60f, 1.2f, 350f, TurretType.PLASMA, "🟣"),
        TurretItem("Lightning Rod", "Lightning Rod", 85000, 30f, 3.5f, 220f, TurretType.TESLA, "🌩️"),
        TurretItem("Sentry Vanguard", "Sentry Vanguard", 110000, 45f, 2.5f, 320f, TurretType.GATLING, "🗡️"),
        TurretItem("Nova Turret", "Nova Turret", 140000, 85f, 1.5f, 400f, TurretType.PLASMA, "🌟"),
        TurretItem("Arc Emitter", "Arc Emitter", 180000, 45f, 4.5f, 240f, TurretType.TESLA, "⛓️"),
        TurretItem("Shredder Cannon", "Shredder Cannon", 220000, 60f, 6.0f, 330f, TurretType.GATLING, "🌪️"),
        TurretItem("Fusion Sentry", "Fusion Sentry", 280000, 120f, 1.8f, 420f, TurretType.PLASMA, "⚛️"),
        TurretItem("Volt Storm", "Volt Storm", 350000, 65f, 5.0f, 260f, TurretType.TESLA, "⛈️"),
        TurretItem("Obliterator", "Obliterator", 450000, 90f, 8.0f, 350f, TurretType.GATLING, "💥"),
        TurretItem("Solar Beam", "Solar Beam", 600000, 180f, 2.0f, 450f, TurretType.PLASMA, "☀️"),
        TurretItem("Thunder God", "Thunder God", 800000, 100f, 6.0f, 300f, TurretType.TESLA, "🔱"),
        TurretItem("Doomsday Turret", "Doomsday Turret", 1000000, 140f, 10.0f, 380f, TurretType.GATLING, "💀"),
        TurretItem("Singularity", "Singularity", 1300000, 250f, 2.5f, 500f, TurretType.PLASMA, "🕳️"),
        TurretItem("Zeus Engine", "Zeus Engine", 1600000, 160f, 8.0f, 350f, TurretType.TESLA, "🌩️"),
        TurretItem("Genesis Matrix", "Genesis Matrix", 2000000, 350f, 5.0f, 600f, TurretType.PLASMA, "🧬")
    )
}
