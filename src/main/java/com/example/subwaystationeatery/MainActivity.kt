package com.example.subwaystationeatery

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ======================= COLORS =======================
object Pal {
    val Wall = Color(0xFF1C2833)
    val Grout = Color(0xFF2C3E50)
    val Panel = Color(0xFF0E1621)
    val Amber = Color(0xFFFFB300)
    val Cream = Color(0xFFFDF6E3)
    val Red = Color(0xFFE53935)
    val Green = Color(0xFF43A047)
    val Blue = Color(0xFF1E88E5)
    val Yellow = Color(0xFFFDD835)
    val Purple = Color(0xFF8E24AA)
    val Counter = Color(0xFF4E342E)
}

// ======================= GAME DATA =======================
data class Ingredient(val id: String, val name: String, val emoji: String)

data class Recipe(
    val name: String,
    val emoji: String,
    val ingredients: List<String>,
    val points: Int,
    val description: String
)

data class Customer(
    val id: Int,
    val emoji: String,
    val name: String,
    val recipe: Recipe,
    val maxPatience: Float,
    val patience: Float
)

val INGREDIENTS = listOf(
    Ingredient("bread", "Bread", "🍞"),
    Ingredient("beef", "Beef Patty", "🥩"),
    Ingredient("bacon", "Bacon", "🥓"),
    Ingredient("cheese", "Cheese", "🧀"),
    Ingredient("lettuce", "Lettuce", "🥬"),
    Ingredient("tomato", "Tomato", "🍅"),
    Ingredient("onion", "Onion", "🧅"),
    Ingredient("avocado", "Avocado", "🥑"),
    Ingredient("egg", "Egg", "🥚"),
    Ingredient("chili", "Chili", "🌶️"),
    Ingredient("noodles", "Noodles", "🍝"),
    Ingredient("coffee", "Coffee", "☕"),
    Ingredient("milk", "Milk", "🥛"),
    Ingredient("chocolate", "Chocolate", "🍫"),
    Ingredient("ice", "Ice", "🧊")
)

fun ing(id: String): Ingredient = INGREDIENTS.first { it.id == id }

val RECIPES = listOf(
    Recipe("Rush Hour Sandwich", "🥪", listOf("bread", "bacon", "cheese", "lettuce"), 50,
        "A classic grab-and-go sandwich for the morning crowd."),
    Recipe("Commuter Egg Toast", "🍳", listOf("bread", "egg", "avocado"), 40,
        "Quick breakfast eaten between two metro stops."),
    Recipe("Express Latte", "🥤", listOf("coffee", "milk"), 25,
        "Ready before the doors close. The station's best seller."),
    Recipe("Iced Metro Mocha", "🧋", listOf("coffee", "milk", "chocolate", "ice"), 45,
        "Cold, sweet and strong for hot summer platforms."),
    Recipe("Late Train Ramen", "🍜", listOf("noodles", "egg", "chili", "onion"), 55,
        "Spicy noodles for night shift workers waiting for the last train."),
    Recipe("Station Burger", "🍔", listOf("bread", "beef", "cheese", "tomato"), 60,
        "The juiciest burger on Line 4. Worth missing a train for."),
    Recipe("Line 4 Green Salad", "🥗", listOf("lettuce", "tomato", "avocado", "onion"), 45,
        "Fresh and light for health-conscious travelers."),
    Recipe("Midnight Chili Bowl", "🍲", listOf("beef", "chili", "tomato", "onion"), 50,
        "A warm bowl that keeps the cold tunnel wind away.")
)

val COMMUTERS = listOf(
    "🧑‍💼" to "Office Worker",
    "👩‍🎓" to "Student",
    "👷" to "Builder",
    "👵" to "Grandma",
    "🧔" to "Traveler",
    "👩‍💻" to "Coder",
    "🧑‍🎤" to "Musician",
    "👮" to "Station Guard"
)

const val MAX_QUEUE = 3
const val MAX_LIVES = 3
const val RUSH_EVERY_MS = 30000L
const val RUSH_LENGTH_MS = 10000L
const val WRONG_PENALTY = 15

// ======================= GAME LOGIC =======================
class GameState {
    var score by mutableStateOf(0)
    var lives by mutableStateOf(MAX_LIVES)
    var combo by mutableStateOf(0)
    var bestCombo by mutableStateOf(0)
    var served by mutableStateOf(0)
    var level by mutableStateOf(1)
    var gameOver by mutableStateOf(false)
    var selectedId by mutableStateOf(-1)
    var message by mutableStateOf("Welcome to your shift! Tap a commuter, build the order, then SERVE.")
    var messageColor by mutableStateOf(Color.White)
    var nextTrainMs by mutableStateOf(RUSH_EVERY_MS)
    var rushMs by mutableStateOf(0L)
    val customers = mutableStateListOf<Customer>()
    val tray = mutableStateListOf<String>()

    private var spawnTimer = 800L
    private var nextId = 1

    val rushActive: Boolean get() = rushMs > 0

    private fun spawnInterval(): Long {
        val base = (6500L - (level - 1) * 600L).coerceAtLeast(2500L)
        return if (rushActive) base / 2 else base
    }

    private fun patienceSeconds(): Float = (22f - (level - 1) * 2f).coerceAtLeast(9f)

    private fun say(text: String, color: Color) {
        message = text
        messageColor = color
    }

    private fun spawn() {
        val (emoji, name) = COMMUTERS.random()
        val p = patienceSeconds()
        customers.add(Customer(nextId++, emoji, name, RECIPES.random(), p, p))
        if (selectedId == -1) selectedId = customers.first().id
    }

    fun update(dt: Long) {
        if (gameOver) return

        // Trains and rush hour
        if (rushMs > 0) rushMs = (rushMs - dt).coerceAtLeast(0L)
        nextTrainMs -= dt
        if (nextTrainMs <= 0) {
            nextTrainMs = RUSH_EVERY_MS
            rushMs = RUSH_LENGTH_MS
            say("🚇 Train arrived! RUSH HOUR: double points for 10 seconds!", Pal.Amber)
            if (customers.size < MAX_QUEUE) spawn()
        }

        // New commuters
        spawnTimer -= dt
        if (spawnTimer <= 0) {
            if (customers.size < MAX_QUEUE) spawn()
            spawnTimer = spawnInterval()
        }

        // Patience
        val leaving = mutableListOf<Customer>()
        for (i in customers.indices) {
            val c = customers[i]
            val np = c.patience - dt / 1000f
            if (np <= 0f) leaving.add(c) else customers[i] = c.copy(patience = np)
        }
        if (leaving.isNotEmpty()) {
            val ids = leaving.map { it.id }.toSet()
            customers.removeAll { it.id in ids }
            lives = (lives - leaving.size).coerceAtLeast(0)
            combo = 0
            say("😠 ${leaving.first().name} left hungry to catch the train! -1 ❤️", Pal.Red)
            if (lives <= 0) {
                gameOver = true
                say("🚉 Shift over! The last train has departed.", Pal.Amber)
            }
        }

        if (customers.none { it.id == selectedId }) {
            selectedId = customers.firstOrNull()?.id ?: -1
        }
    }

    fun addIngredient(id: String) {
        if (gameOver) return
        if (tray.size >= 6) {
            say("Tray is full! Serve or clear it.", Pal.Yellow)
            return
        }
        tray.add(id)
    }

    fun serve() {
        if (gameOver) return
        val c = customers.firstOrNull { it.id == selectedId }
        if (c == null) {
            say("No commuter selected. Wait for the next train!", Pal.Yellow)
            return
        }
        if (tray.isEmpty()) {
            say("The tray is empty! Add ingredients first.", Pal.Yellow)
            return
        }
        if (tray.sorted() == c.recipe.ingredients.sorted()) {
            combo += 1
            if (combo > bestCombo) bestCombo = combo
            val base = c.recipe.points
            val speed = (c.patience / c.maxPatience * 30f).toInt()
            val comboBonus = ((combo - 1) * 10).coerceAtMost(50)
            var total = base + speed + comboBonus
            if (rushActive) total *= 2
            score += total
            served += 1
            customers.removeAll { it.id == c.id }
            val oldLevel = level
            level = 1 + score / 200
            val rushText = if (rushActive) " x2 RUSH" else ""
            var text = "✅ +$total  (Meal $base + Speed $speed + Combo $comboBonus$rushText)"
            if (level > oldLevel) text += "\n⬆️ LEVEL $level! Commuters are in a bigger hurry."
            say(text, Pal.Green)
        } else {
            score = (score - WRONG_PENALTY).coerceAtLeast(0)
            combo = 0
            say("❌ Wrong recipe for ${c.recipe.name}! -$WRONG_PENALTY points", Pal.Red)
        }
        tray.clear()
    }
}

// ======================= ACTIVITY =======================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SubwayEateryApp()
            }
        }
    }
}

enum class Screen { MENU, RECIPES, GAME, OVER }

@Composable
fun SubwayEateryApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("subway_eatery", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.MENU) }
    var highScore by remember { mutableStateOf(prefs.getInt("high_score", 0)) }
    var lastGame by remember { mutableStateOf<GameState?>(null) }
    var newRecord by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Wall)
            .systemBarsPadding()
    ) {
        when (screen) {
            Screen.MENU -> MenuScreen(
                highScore = highScore,
                onStart = { screen = Screen.GAME },
                onRecipes = { screen = Screen.RECIPES }
            )
            Screen.RECIPES -> RecipeBookScreen(onBack = { screen = Screen.MENU })
            Screen.GAME -> GameScreen(onFinish = { g ->
                lastGame = g
                newRecord = g.score > highScore
                if (newRecord) {
                    highScore = g.score
                    prefs.edit().putInt("high_score", g.score).apply()
                }
                screen = Screen.OVER
            })
            Screen.OVER -> GameOverScreen(
                game = lastGame,
                highScore = highScore,
                newRecord = newRecord,
                onAgain = { screen = Screen.GAME },
                onMenu = { screen = Screen.MENU }
            )
        }
    }
}

// ======================= STATION VISUALS =======================
fun Modifier.stationTiles(): Modifier = this.drawBehind {
    drawRect(Pal.Wall)
    val tileW = 48.dp.toPx()
    val tileH = 24.dp.toPx()
    var y = 0f
    var row = 0
    while (y < size.height) {
        drawLine(Pal.Grout, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        var x = if (row % 2 == 0) 0f else tileW / 2
        while (x < size.width) {
            drawLine(Pal.Grout, Offset(x, y), Offset(x, y + tileH), strokeWidth = 2f)
            x += tileW
        }
        y += tileH
        row++
    }
}

@Composable
fun LineStripes() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        listOf(Pal.Red, Pal.Yellow, Pal.Green, Pal.Blue, Pal.Purple).forEach {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(it)
            )
        }
    }
}

@Composable
fun TrainTrack(height: Dp = 64.dp) {
    val transition = rememberInfiniteTransition(label = "train")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "trainX"
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(0xFF0A0F14))
    ) {
        val trainW = 260.dp
        val x = (maxWidth + trainW) * progress - trainW
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(6) {
                Box(
                    Modifier
                        .size(width = 18.dp, height = 4.dp)
                        .background(Pal.Amber.copy(alpha = 0.6f))
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-6).dp)
                .background(Color(0xFF90A4AE))
        )
        Text(
            text = "🚃🚃🚃🚃🚇",
            fontSize = 34.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = x, y = (-6).dp)
                .width(trainW)
        )
    }
}

@Composable
fun StationSign(text: String, fontSize: Int = 18) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D47A1))
            .border(2.dp, Color.White, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Pal.Red),
            contentAlignment = Alignment.Center
        ) {
            Text("M", color = Color.White, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
    }
}

@Composable
fun DepartureBoard(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black)
            .border(1.dp, Color(0xFF424242), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = Pal.Amber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Pal.Panel)
            .padding(10.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Pal.Amber,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun BigButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(56.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun HudItem(label: String, value: String) {
    Column(Modifier.padding(end = 16.dp)) {
        Text(label, color = Pal.Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun PatienceBar(fraction: Float) {
    val f = fraction.coerceIn(0f, 1f)
    val col = when {
        f > 0.6f -> Pal.Green
        f > 0.3f -> Pal.Yellow
        else -> Pal.Red
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .fillMaxHeight()
                .background(col)
        )
    }
}

@Composable
fun CustomerCard(c: Customer, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Color(0xFF2E4057) else Pal.Panel)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) Pal.Amber else Color(0x55FFFFFF),
                shape
            )
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Pal.Cream)
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Text(c.recipe.emoji, fontSize = 24.sp)
        }
        Text(c.emoji, fontSize = 40.sp)
        Text(c.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
        PatienceBar(c.patience / c.maxPatience)
    }
}

@Composable
fun EmptySlot(modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, Color(0x33FFFFFF), shape),
        contentAlignment = Alignment.Center
    ) {
        Text("🚶\nwaiting...", color = Color.Gray, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

@Composable
fun IngredientButton(item: Ingredient, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color(0xFF6D4C41))
            .border(1.dp, Color(0xFFA1887F), shape)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(item.emoji, fontSize = 26.sp)
        Text(item.name, color = Pal.Cream, fontSize = 10.sp, maxLines = 1)
    }
}

// ======================= SCREENS =======================
@Composable
fun MenuScreen(highScore: Int, onStart: () -> Unit, onRecipes: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .stationTiles()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LineStripes()
        TrainTrack()
        Spacer(Modifier.height(20.dp))
        StationSign("SUBWAY STATION EATERY", fontSize = 20)
        Spacer(Modifier.height(10.dp))
        Text(
            "Feed hungry commuters before their train leaves!",
            color = Pal.Cream,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("🧑‍💼 👩‍🎓 👷 👵 🧔 👩‍💻", fontSize = 34.sp)
        Text("🥪 🥤 🍜 🍔 🥗 🧋", fontSize = 30.sp)
        Spacer(Modifier.height(12.dp))
        DepartureBoard("HIGH SCORE  $highScore")
        Spacer(Modifier.height(20.dp))
        BigButton("▶  START SHIFT", Pal.Green, onStart)
        Spacer(Modifier.height(12.dp))
        BigButton("📖  RECIPE BOOK", Pal.Blue, onRecipes)
        Spacer(Modifier.height(16.dp))
        Panel {
            Text("HOW TO PLAY", color = Pal.Amber, fontWeight = FontWeight.Bold)
            Text("1. Tap a commuter to see their order.", color = Pal.Cream, fontSize = 13.sp)
            Text("2. Tap ingredients on the counter to build the recipe.", color = Pal.Cream, fontSize = 13.sp)
            Text("3. Press SERVE. Ingredient order does not matter.", color = Pal.Cream, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("SCORING", color = Pal.Amber, fontWeight = FontWeight.Bold)
            Text("✅ Correct meal: recipe points (25 to 60)", color = Pal.Cream, fontSize = 13.sp)
            Text("⚡ Speed bonus: up to +30 for fast service", color = Pal.Cream, fontSize = 13.sp)
            Text("🔥 Combo bonus: +10 per streak (max +50)", color = Pal.Cream, fontSize = 13.sp)
            Text("🚇 Rush hour after every train: double points", color = Pal.Cream, fontSize = 13.sp)
            Text("❌ Wrong meal: -$WRONG_PENALTY points, combo resets", color = Pal.Cream, fontSize = 13.sp)
            Text("😠 Commuter leaves hungry: lose a heart. No hearts = shift over", color = Pal.Cream, fontSize = 13.sp)
            Text("⬆️ Every 200 points: level up, faster commuters", color = Pal.Cream, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun RecipeBookScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .stationTiles()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pal.Panel)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Pal.Amber, fontSize = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("📖 RECIPE BOOK", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        LineStripes()
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(RECIPES) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Pal.Panel)
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(r.emoji, fontSize = 44.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = Pal.Cream, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(r.description, color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            r.ingredients.joinToString("  +  ") { ing(it).emoji + " " + ing(it).name },
                            color = Pal.Amber,
                            fontSize = 13.sp
                        )
                    }
                    Text("${r.points}\npts", color = Pal.Green, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun GameScreen(onFinish: (GameState) -> Unit) {
    val game = remember { GameState() }

    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        while (!game.gameOver) {
            delay(100)
            val now = System.currentTimeMillis()
            game.update(now - last)
            last = now
        }
        delay(1200)
        onFinish(game)
    }

    val selected = game.customers.firstOrNull { it.id == game.selectedId }

    Column(
        Modifier
            .fillMaxSize()
            .stationTiles()
            .verticalScroll(rememberScrollState())
    ) {
        // HUD
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pal.Panel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudItem("SCORE", game.score.toString())
            HudItem("LEVEL", game.level.toString())
            HudItem("COMBO", "x${game.combo}")
            Spacer(Modifier.weight(1f))
            Text("❤️".repeat(game.lives) + "🖤".repeat(MAX_LIVES - game.lives), fontSize = 18.sp)
        }
        LineStripes()
        TrainTrack()

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationSign("SUBWAY EATERY", fontSize = 15)
            DepartureBoard(
                if (game.rushActive) "RUSH x2  ${game.rushMs / 1000 + 1}s"
                else "NEXT TRAIN ${game.nextTrainMs / 1000 + 1}s"
            )
        }

        if (game.rushActive) {
            Text(
                "🚇 RUSH HOUR! DOUBLE POINTS 🚇",
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pal.Red)
                    .padding(6.dp)
            )
        }

        SectionTitle("WAITING COMMUTERS (tap to select)")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            for (slot in 0 until MAX_QUEUE) {
                val c = game.customers.getOrNull(slot)
                val m = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .height(150.dp)
                if (c != null) {
                    CustomerCard(c, c.id == game.selectedId, m) { game.selectedId = c.id }
                } else {
                    EmptySlot(m)
                }
            }
        }

        // Current order
        Panel {
            if (selected != null) {
                Text(
                    "ORDER  ${selected.recipe.emoji}  ${selected.recipe.name}",
                    color = Pal.Amber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recipe: " + selected.recipe.ingredients.joinToString("  +  ") { ing(it).emoji + " " + ing(it).name },
                    color = Pal.Cream,
                    fontSize = 13.sp
                )
                Text(
                    "Worth ${selected.recipe.points} pts + speed bonus",
                    color = Pal.Green,
                    fontSize = 12.sp
                )
            } else {
                Text("No commuters yet... the next train is on its way 🚇", color = Pal.Cream)
            }
        }

        // Tray
        Panel {
            Text(
                "YOUR TRAY (tap an item to remove it)",
                color = Pal.Amber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (game.tray.isEmpty()) {
                    Text("Empty plate 🍽️", color = Color.Gray)
                }
                game.tray.forEachIndexed { i, id ->
                    Text(
                        ing(id).emoji,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { if (i < game.tray.size) game.tray.removeAt(i) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { game.tray.clear() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🗑 CLEAR", color = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { game.serve() },
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Green),
                    modifier = Modifier.weight(2f)
                ) {
                    Text("🍽 SERVE ORDER", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Feedback message
        Text(
            game.message,
            color = game.messageColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .heightIn(min = 40.dp)
        )

        // Kitchen counter
        SectionTitle("KITCHEN COUNTER")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Pal.Counter)
                .padding(6.dp)
        ) {
            INGREDIENTS.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { item ->
                        IngredientButton(
                            item,
                            Modifier
                                .weight(1f)
                                .padding(3.dp)
                        ) { game.addIngredient(item.id) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label, color = Pal.Cream, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GameOverScreen(
    game: GameState?,
    highScore: Int,
    newRecord: Boolean,
    onAgain: () -> Unit,
    onMenu: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .stationTiles()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LineStripes()
        TrainTrack()
        Spacer(Modifier.height(16.dp))
        Text("🚉", fontSize = 72.sp)
        Text("LAST TRAIN DEPARTED", color = Pal.Amber, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("Your shift at the station is over", color = Pal.Cream)
        Spacer(Modifier.height(16.dp))
        Panel {
            StatRow("🏅 Final score", "${game?.score ?: 0}")
            StatRow("🍽 Meals served", "${game?.served ?: 0}")
            StatRow("🔥 Best combo", "x${game?.bestCombo ?: 0}")
            StatRow("⬆️ Level reached", "${game?.level ?: 1}")
            StatRow("🏆 High score", "$highScore")
        }
        if (newRecord) {
            Spacer(Modifier.height(8.dp))
            Text("🏆 NEW HIGH SCORE! 🏆", color = Pal.Yellow, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(20.dp))
        BigButton("🔁  PLAY AGAIN", Pal.Green, onAgain)
        Spacer(Modifier.height(12.dp))
        BigButton("🏠  MAIN MENU", Pal.Blue, onMenu)
        Spacer(Modifier.height(20.dp))
    }
}
