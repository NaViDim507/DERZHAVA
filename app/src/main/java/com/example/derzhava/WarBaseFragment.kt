package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.EconomyService
import com.example.derzhava.data.GeneralDao
import com.example.derzhava.data.GeneralState
import com.example.derzhava.data.TrainingJobDao
import com.example.derzhava.data.TrainingJobEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.data.WarDao
import com.example.derzhava.data.WarEntity
import com.example.derzhava.data.WarLogDao
import com.example.derzhava.data.WarLogEntity
import com.example.derzhava.data.WarMoveDao
import com.example.derzhava.data.WarMoveEntity
import com.example.derzhava.data.MarketDao
import com.example.derzhava.databinding.FragmentWarBaseBinding
import com.example.derzhava.net.OnlineArmySync
import com.example.derzhava.net.OnlineCountrySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.withContext

class WarBaseFragment : Fragment() {

    private var _binding: FragmentWarBaseBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository

    private lateinit var armyDao: ArmyDao
    private lateinit var warDao: WarDao
    private lateinit var countryDao: CountryDao
    private lateinit var generalDao: GeneralDao
    private lateinit var warMoveDao: WarMoveDao
    private lateinit var warLogDao: WarLogDao
    private lateinit var trainingJobDao: TrainingJobDao
    private lateinit var economyService: EconomyService

    // Биржа (для учёных на продаже)
    private lateinit var marketDao: MarketDao

    private var country: CountryEntity? = null
    private var army: ArmyState? = null
    private var wars: List<WarEntity> = emptyList()
    private var trainingJob: TrainingJobEntity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)

        countryDao = db.countryDao()
        armyDao = db.armyDao()
        warDao = db.warDao()
        generalDao = db.generalDao()
        warMoveDao = db.warMoveDao()
        warLogDao = db.warLogDao()
        trainingJobDao = db.trainingJobDao()
        economyService = EconomyService(countryDao)

        // Инициализируем DAO для биржи
        marketDao = db.marketDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWarBaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден", Toast.LENGTH_SHORT).show()
            return
        }

        // Страна игрока (gos)
        var c = countryDao.getCountryByRuler(user.rulerName)
        if (c == null) {
            c = CountryEntity(
                rulerName = user.rulerName,
                countryName = user.countryName
            )
            countryDao.insertCountry(c)
        }
        country = c

        // Армия игрока (army_state)
        var a = armyDao.getByRuler(user.rulerName)
        if (a == null) {
            a = ArmyState(rulerName = user.rulerName)
            armyDao.insert(a)
        }
        army = a

        binding.tvTitle.text = "ВОЕННАЯ БАЗА"

        // На военной базе войны пока не показываем списком
        wars = emptyList()

        renderArmy()
        renderPohozStub()
        setupButtons()

        // Текущая задача обучения (если есть)
        trainingJob = trainingJobDao.getJobForRuler(user.rulerName)

        // Онлайн-синхрон армии с VPS (peh/kaz/gva — источник истины)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Синхронизируем и армию, и страну. Страна нужна для
                // обновления учёных/рабочих после автоматического
                // завершения обучения на сервере. Используем
                // syncDownOrCreate(), т.к. syncDown() отсутствует для Country.
                withContext(Dispatchers.IO) {
                    try {
                        OnlineCountrySync.syncDownOrCreate(db, user.rulerName, user.countryName)
                    } catch (_: Exception) {
                        // игнорируем
                    }
                    try {
                        OnlineArmySync.syncDown(db, user.rulerName)
                    } catch (_: Exception) {
                        // игнорируем
                    }
                }
                // Перечитаем страну и армию и обновим локальные переменные
                val updatedCountry = countryDao.getCountryByRuler(user.rulerName)
                val updatedArmy = armyDao.getByRuler(user.rulerName)
                if (updatedCountry != null && isAdded) {
                    country = updatedCountry
                }
                if (updatedArmy != null && isAdded) {
                    army = updatedArmy
                }
            } catch (_: Exception) {
                // тихо игнорируем ошибки сети
            }

            // После синка с VPS — обработать обучение и обновить UI
            if (isAdded) {
                processTrainingJob()
                renderArmy()
            }
        }

        // Завершаем onViewCreated. Все последующие методы должны быть объявлены на уровне класса
    }


    private fun renderPohozStub() {
        val container = binding.warListContainer
        container.removeAllViews()

        val tv = TextView(requireContext()).apply {
            text = "Походы пока в разработке."
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
            textSize = 14f
        }
        container.addView(tv)
    }

    // ---------- РЕНДЕР ----------

    private fun renderArmy() = with(binding) {
        val a = army ?: return@with

        tvArmySummary.text =
            "Пехота: ${a.infantry}\n" +
                    "Казаки: ${a.cossacks}\n" +
                    "Гвардия: ${a.guards}\n" +
                    "Катапульты: ${a.catapults}"

        tvArmyParams.text =
            "Пехота: атака ${a.infantryAttack} / защита ${a.infantryDefense}\n" +
                    "Казаки: атака ${a.cossackAttack} / защита ${a.cossackDefense}\n" +
                    "Гвардия: атака ${a.guardAttack} / защита ${a.guardDefense}"
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return "%02d:%02d".format(h, m)
    }

    private fun renderWars(myRuler: String) {
        val container = binding.warListContainer
        container.removeAllViews()

        val tv = TextView(requireContext()).apply {
            text = "Походы пока в разработке."
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
            textSize = 14f
        }
        container.addView(tv)
    }

    // ---------- КНОПКИ НА ЭКРАНЕ ----------

    private fun setupButtons() = with(binding) {
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnManageArmy.setOnClickListener {
            showManageArmyDialog()
        }

        btnTrainParams.setOnClickListener {
            showTrainParamsDialog()
        }

        btnTrainTroops.setOnClickListener {
            showTrainTroopsDialog()
        }

        btnCatapults.setOnClickListener {
            showCatapultsDialog()
        }

        btnFireArmy.setOnClickListener {
            showFireArmyDialog()
        }
    }

    // ---------- Катапульты (как в der1 /domm/warbase/?vb=kat1/kat2) ----------

    private fun showCatapultsDialog() {
        val c = country ?: return
        val a = army ?: return
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        fun label(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }

        // инфа как в mod7.php
        val tvInfo = TextView(ctx).apply {
            text = buildString {
                append("Катапульты: ${a.catapults}\n\n")
                append("Стоимость 1 катапульты:\n")
                append("Камень: 300\n")
                append("Дерево: 400\n\n")
                append("Твои ресурсы:\n")
                append("Камень (минерал): ${c.mineral}\n")
                append("Дерево: ${c.wood}")
            }
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }
        layout.addView(tvInfo)

        val etCount = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Сколько построить"
        }
        layout.addView(label("\nСколько построить катапульт:"))
        layout.addView(etCount)

        AlertDialog.Builder(ctx)
            .setTitle("Катапульты")
            .setView(layout)
            .setPositiveButton("Построить") { _, _ ->
                val text = etCount.text.toString().trim()
                val count = text.toIntOrNull()

                if (count == null || count <= 0) {
                    Toast.makeText(ctx, "Неверное число", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val needMineral = 300 * count
                val needWood = 400 * count

                val newMineral = c.mineral - needMineral
                val newWood = c.wood - needWood

                if (newMineral < 0 || newWood < 0) {
                    Toast.makeText(
                        ctx,
                        "Недостаточно ресурсов!!!",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                val newCountry = c.copy(
                    mineral = newMineral,
                    wood = newWood
                )
                val newArmy = a.copy(
                    catapults = a.catapults + count
                )

                countryDao.insertCountry(newCountry)
                armyDao.insert(newArmy)

                country = newCountry
                army = newArmy

                // Онлайн-синхрон армии с VPS
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        OnlineArmySync.syncUp(db, newArmy.rulerName)
                    } catch (_: Exception) {
                    }
                }

                renderArmy()

                Toast.makeText(
                    ctx,
                    "Изготовлено катапульт: $count",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- УПРАВЛЕНИЕ АРМИЕЙ ----------

    private fun showManageArmyDialog() {
        val a = army ?: return
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        fun addRow(label: String, value: Int): EditText {
            val tv = TextView(ctx).apply {
                text = label
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etInf = addRow("Пехота:", a.infantry)
        val etKaz = addRow("Казаки:", a.cossacks)
        val etGva = addRow("Гвардия:", a.guards)
        val etKat = addRow("Катапульты:", a.catapults)

        AlertDialog.Builder(ctx)
            .setTitle("Управление армией")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val inf = etInf.text.toString().toIntOrNull() ?: 0
                val kaz = etKaz.text.toString().toIntOrNull() ?: 0
                val gva = etGva.text.toString().toIntOrNull() ?: 0
                val kat = etKat.text.toString().toIntOrNull() ?: 0

                val newArmy = a.copy(
                    infantry = max(0, inf),
                    cossacks = max(0, kaz),
                    guards = max(0, gva),
                    catapults = max(0, kat)
                )
                armyDao.insert(newArmy)
                army = newArmy

                // Онлайн-синхрон армии с VPS
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        OnlineArmySync.syncUp(db, newArmy.rulerName)
                    } catch (_: Exception) {
                    }
                }

                renderArmy()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- ПРОКАЧКА ПАРАМЕТРОВ ВОЙСК ----------

    private fun showTrainParamsDialog() {
        val a = army ?: return
        val c = country ?: return
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        val tvCurrent = TextView(ctx).apply {
            text = buildString {
                append("Пехота [атака(${a.infantryAttack})][защита(${a.infantryDefense})]\n")
                append("Казаки [атака(${a.cossackAttack})][защита(${a.cossackDefense})]\n")
                append("Гвардия [атака(${a.guardAttack})][защита(${a.guardDefense})]\n")
                append("\nЖелезо: ${c.metal}")
            }
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }
        layout.addView(tvCurrent)

        fun label(text: String): TextView = TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }

        val spUnit = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                listOf("Пехота", "Казаки", "Гвардия")
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        layout.addView(label("Тип войск:"))
        layout.addView(spUnit)

        val spParam = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                listOf("Атака", "Защита")
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        layout.addView(label("Параметр:"))
        layout.addView(spParam)

        val etPoints = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        layout.addView(label("На сколько пунктов увеличить:"))
        layout.addView(etPoints)

        AlertDialog.Builder(ctx)
            .setTitle("Параметры войск")
            .setView(layout)
            .setPositiveButton("Увеличить") { _, _ ->
                val delta = etPoints.text.toString().toIntOrNull() ?: 0
                if (delta <= 0) {
                    Toast.makeText(
                        ctx,
                        "Нужно указать положительное число пунктов.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val unitIndex = spUnit.selectedItemPosition
                val paramIndex = spParam.selectedItemPosition

                val currentValue = when (unitIndex to paramIndex) {
                    0 to 0 -> a.infantryAttack
                    0 to 1 -> a.infantryDefense
                    1 to 0 -> a.cossackAttack
                    1 to 1 -> a.cossackDefense
                    2 to 0 -> a.guardAttack
                    2 to 1 -> a.guardDefense
                    else -> 0
                }

                val coef = when (unitIndex to paramIndex) {
                    0 to 0 -> 10
                    0 to 1 -> 15
                    1 to 0 -> 20
                    1 to 1 -> 25
                    2 to 0 -> 30
                    2 to 1 -> 35
                    else -> 10
                }

                val cost = ((currentValue + delta) * delta) * coef

                if (cost > c.metal) {
                    Toast.makeText(
                        ctx,
                        "Не хватает железа. Нужно $cost, у вас ${c.metal}.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                val newArmy = when (unitIndex to paramIndex) {
                    0 to 0 -> a.copy(infantryAttack = a.infantryAttack + delta)
                    0 to 1 -> a.copy(infantryDefense = a.infantryDefense + delta)
                    1 to 0 -> a.copy(cossackAttack = a.cossackAttack + delta)
                    1 to 1 -> a.copy(cossackDefense = a.cossackDefense + delta)
                    2 to 0 -> a.copy(guardAttack = a.guardAttack + delta)
                    2 to 1 -> a.copy(guardDefense = a.guardDefense + delta)
                    else -> a
                }

                val newCountry = c.copy(metal = c.metal - cost)

                armyDao.insert(newArmy)
                countryDao.insertCountry(newCountry)

                army = newArmy
                country = newCountry

                // На VPS параметры не хранятся, но на всякий случай синкаем состав войск
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        OnlineArmySync.syncUp(db, newArmy.rulerName)
                    } catch (_: Exception) {
                    }
                }

                renderArmy()

                Toast.makeText(
                    ctx,
                    "Параметр улучшен на $delta, потрачено $cost железа.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- ОБЪЯВЛЕНИЕ ВОЙНЫ (вывод войск) ----------

    private fun showDeclareWarDialog(myRuler: String) {
        val a = army ?: return
        val ctx = requireContext()

        val active = warDao.countActiveForAttacker(myRuler)
        if (active >= 3) {
            Toast.makeText(ctx, "Можно вести не более трёх войн одновременно.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (a.infantry + a.cossacks + a.guards + a.catapults <= 0) {
            Toast.makeText(ctx, "У тебя нет войск для похода.", Toast.LENGTH_SHORT).show()
            return
        }

        val targets = countryDao.getAllNpcCountries().filter { it.rulerName != myRuler }
        if (targets.isEmpty()) {
            Toast.makeText(ctx, "NPC-страны для войны не найдены.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetNames = targets.map { it.countryName }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        val tvTarget = TextView(ctx).apply {
            text = "Цель:"
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }
        val spinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_dropdown_item,
                targetNames
            )
        }

        fun addRow(label: String, maxValue: Int): EditText {
            val tv = TextView(ctx).apply {
                text = "$label (доступно: $maxValue)"
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("0")
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        layout.addView(tvTarget)
        layout.addView(spinner)

        val etInf = addRow("Пехота:", a.infantry)
        val etKaz = addRow("Казаки:", a.cossacks)
        val etGva = addRow("Гвардия:", a.guards)
        val etKat = addRow("Катапульты:", a.catapults)

        AlertDialog.Builder(ctx)
            .setTitle("Объявить войну")
            .setView(layout)
            .setPositiveButton("Вывести войска") { _, _ ->
                val target = targets[spinner.selectedItemPosition]

                val peh = (etInf.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val kaz = (etKaz.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val gva = (etGva.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val kat = (etKat.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)

                if (peh + kaz + gva + kat <= 0) {
                    Toast.makeText(
                        ctx,
                        "Нужно отправить хотя бы одного бойца.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                if (peh > a.infantry || kaz > a.cossacks || gva > a.guards || kat > a.catapults) {
                    Toast.makeText(
                        ctx,
                        "Нельзя отправить войск больше, чем есть в армии.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val myCountry = country ?: return@setPositiveButton
                val now = System.currentTimeMillis()

                val war = WarEntity(
                    attackerRuler = myRuler,
                    defenderRuler = target.rulerName,
                    attackerCountry = myCountry.countryName,
                    defenderCountry = target.countryName,
                    peh = peh,
                    kaz = kaz,
                    gva = gva,
                    catapults = kat,
                    startAt = now,
                    canRaidAt = now + FIRST_RAID_DELAY,
                    canCaptureAt = now + CAPTURE_DELAY
                )

                val warId = warDao.insert(war)

                val newArmy = a.copy(
                    infantry = a.infantry - peh,
                    cossacks = a.cossacks - kaz,
                    guards = a.guards - gva,
                    catapults = a.catapults - kat
                )
                armyDao.insert(newArmy)
                army = newArmy

                // лог движения
                warMoveDao.insert(
                    WarMoveEntity(
                        warId = warId,
                        type = "reinforce",
                        ts = now,
                        peh = peh,
                        kaz = kaz,
                        gva = gva,
                        cat = kat
                    )
                )

                wars = warDao.getWarsForRuler(myRuler)
                renderArmy()
                renderWars(myRuler)

                // syncUp армии после вывода
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        OnlineArmySync.syncUp(db, newArmy.rulerName)
                    } catch (_: Exception) {
                    }
                }

                Toast.makeText(
                    ctx,
                    "Война начата, войска выведены на территорию врага.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- ДЕЙСТВИЯ ПО ВОЙНЕ ----------

    private fun showWarActionsDialog(war: WarEntity, myRuler: String) {
        if (war.state != "active" || war.isResolved) {
            Toast.makeText(requireContext(), "Война уже завершена.", Toast.LENGTH_SHORT).show()
            return
        }

        val ctx = requireContext()
        val now = System.currentTimeMillis()

        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Разведка
        options += "Разведка"
        actions += {
            val newAcc = (war.reconAcc + 10).coerceAtMost(100)
            val updated = war.copy(reconAcc = newAcc)
            warDao.update(updated)
            wars = warDao.getWarsForRuler(myRuler)
            renderWars(myRuler)
            Toast.makeText(ctx, "Разведка проведена (+10% точности).", Toast.LENGTH_SHORT).show()
        }

        // Подкрепление / отзыв
        options += "Подкрепление"
        actions += { showReinforceDialog(war, myRuler) }

        options += "Отозвать войска"
        actions += { showRecallDialog(war, myRuler) }

        // Рейды
        if (now >= war.canRaidAt) {
            options += "Рейд: Городок (рабочие)"
            actions += { doRaid(war, myRuler, "town") }

            options += "Рейд: Биржа (ресурсы)"
            actions += { doRaid(war, myRuler, "birzha") }

            options += "Рейд: Ком.центр (деньги)"
            actions += { doRaid(war, myRuler, "cc") }
        }

        // Захват
        if (now >= war.canCaptureAt) {
            options += "Захватить страну"
            actions += { doCapture(war, myRuler) }
        }

        AlertDialog.Builder(ctx)
            .setTitle(
                "Война с ${
                    if (war.attackerRuler == myRuler) war.defenderCountry else war.attackerCountry
                }"
            )
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showReinforceDialog(war: WarEntity, myRuler: String) {
        val a = army ?: return
        val ctx = requireContext()

        if (a.infantry + a.cossacks + a.guards + a.catapults <= 0) {
            Toast.makeText(ctx, "Нет свободных войск для подкрепления.", Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        fun addRow(label: String, maxValue: Int): EditText {
            val tv = TextView(ctx).apply {
                text = "$label (до $maxValue):"
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("0")
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etInf = addRow("Пехота", a.infantry)
        val etKaz = addRow("Казаки", a.cossacks)
        val etGva = addRow("Гвардия", a.guards)
        val etKat = addRow("Катапульты", a.catapults)

        AlertDialog.Builder(ctx)
            .setTitle("Отправить подкрепление")
            .setView(layout)
            .setPositiveButton("Отправить") { _, _ ->
                val peh = (etInf.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val kaz = (etKaz.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val gva = (etGva.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val kat = (etKat.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)

                if (peh + kaz + gva + kat <= 0) return@setPositiveButton
                if (peh > a.infantry || kaz > a.cossacks || gva > a.guards || kat > a.catapults) {
                    Toast.makeText(ctx, "Столько войск нет в гарнизоне.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val now = System.currentTimeMillis()

                val newWar = war.copy(
                    peh = war.peh + peh,
                    kaz = war.kaz + kaz,
                    gva = war.gva + gva,
                    catapults = war.catapults + kat
                )
                warDao.update(newWar)

                val newArmy = a.copy(
                    infantry = a.infantry - peh,
                    cossacks = a.cossacks - kaz,
                    guards = a.guards - gva,
                    catapults = a.catapults - kat
                )
                armyDao.insert(newArmy)
                army = newArmy

                warMoveDao.insert(
                    WarMoveEntity(
                        warId = war.id,
                        type = "reinforce",
                        ts = now,
                        peh = peh,
                        kaz = kaz,
                        gva = gva,
                        cat = kat
                    )
                )

                wars = warDao.getWarsForRuler(myRuler)
                renderArmy()
                renderWars(myRuler)

                // syncUp армии
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        OnlineArmySync.syncUp(db, newArmy.rulerName)
                    } catch (_: Exception) {
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRecallDialog(war: WarEntity, myRuler: String) {
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        fun addRow(label: String, maxValue: Int): EditText {
            val tv = TextView(ctx).apply {
                text = "$label (до $maxValue):"
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("0")
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etInf = addRow("Пехота", war.peh)
        val etKaz = addRow("Казаки", war.kaz)
        val etGva = addRow("Гвардия", war.gva)
        val etKat = addRow("Катапульты", war.catapults)

        AlertDialog.Builder(ctx)
            .setTitle("Отозвать войска")
            .setView(layout)
            .setPositiveButton("Отозвать") { _, _ ->
                val peh = (etInf.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val kaz = (etKaz.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val gva = (etGva.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val kat = (etKat.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)

                if (peh + kaz + gva + kat <= 0) return@setPositiveButton
                if (peh > war.peh || kaz > war.kaz || gva > war.gva || kat > war.catapults) {
                    Toast.makeText(
                        ctx,
                        "Нельзя отозвать больше, чем есть на территории.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val now = System.currentTimeMillis()
                val a = army ?: return@setPositiveButton

                val newArmy = a.copy(
                    infantry = a.infantry + peh,
                    cossacks = a.cossacks + kaz,
                    guards = a.guards + gva,
                    catapults = a.catapults + kat
                )
                armyDao.insert(newArmy)
                army = newArmy

                val remainPeh = war.peh - peh
                val remainKaz = war.kaz - kaz
                val remainGva = war.gva - gva
                val remainKat = war.catapults - kat

                val newWar = if (remainPeh + remainKaz + remainGva + remainKat <= 0) {
                    war.copy(
                        peh = 0,
                        kaz = 0,
                        gva = 0,
                        catapults = 0,
                        state = "recalled",
                        isResolved = true,
                        attackerWon = false,
                        endedAt = now
                    )
                } else {
                    war.copy(
                        peh = remainPeh,
                        kaz = remainKaz,
                        gva = remainGva,
                        catapults = remainKat
                    )
                }
                warDao.update(newWar)

                warMoveDao.insert(
                    WarMoveEntity(
                        warId = war.id,
                        type = "recall",
                        ts = now,
                        peh = peh,
                        kaz = kaz,
                        gva = gva,
                        cat = kat
                    )
                )

                wars = warDao.getWarsForRuler(myRuler)
                renderArmy()
                renderWars(myRuler)

                // syncUp армии
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        OnlineArmySync.syncUp(db, newArmy.rulerName)
                    } catch (_: Exception) {
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- РЕЙДЫ И ЗАХВАТ ----------

    private fun doRaid(war: WarEntity, myRuler: String, raidType: String) {
        val ctx = requireContext()
        val now = System.currentTimeMillis()
        if (now < war.canRaidAt) {
            Toast.makeText(ctx, "Ещё рано для следующего рейда.", Toast.LENGTH_SHORT).show()
            return
        }

        when (raidType) {
            "town" -> economyService.stealWorkers40(war.attackerRuler, war.defenderRuler)
            "birzha" -> economyService.stealResources40(war.attackerRuler, war.defenderRuler)
            "cc" -> economyService.stealMoney40(war.attackerRuler, war.defenderRuler)
        }

        val updatedWar = war.copy(canRaidAt = now + RAID_COOLDOWN)
        warDao.update(updatedWar)

        warLogDao.insert(
            WarLogEntity(
                warId = war.id,
                ts = now,
                type = when (raidType) {
                    "town" -> "raid_town"
                    "birzha" -> "raid_birzha"
                    else -> "raid_cc"
                },
                payload = "{}"
            )
        )

        wars = warDao.getWarsForRuler(myRuler)
        renderWars(myRuler)
        Toast.makeText(ctx, "Рейд выполнен.", Toast.LENGTH_SHORT).show()
    }

    private fun doCapture(war: WarEntity, myRuler: String) {
        val ctx = requireContext()
        val now = System.currentTimeMillis()

        if (now < war.canCaptureAt) {
            Toast.makeText(ctx, "До захвата ещё рано.", Toast.LENGTH_SHORT).show()
            return
        }

        val attackerArmy = army ?: ArmyState(rulerName = war.attackerRuler)

        var atkGen = generalDao.getByRuler(war.attackerRuler)
        if (atkGen == null) {
            atkGen = GeneralState(rulerName = war.attackerRuler)
            generalDao.insert(atkGen)
        }

        var defArmy = armyDao.getByRuler(war.defenderRuler)
        if (defArmy == null) {
            defArmy = ArmyState(rulerName = war.defenderRuler)
            armyDao.insert(defArmy)
        }

        var defGen = generalDao.getByRuler(war.defenderRuler)
        if (defGen == null) {
            defGen = GeneralState(rulerName = war.defenderRuler)
            generalDao.insert(defGen)
        }

        val battle = resolveBattlePhpStyle(
            atkPeh = war.peh,
            atkKaz = war.kaz,
            atkGva = war.gva,
            defPeh = defArmy.infantry,
            defKaz = defArmy.cossacks,
            defGva = defArmy.guards,
            atkArmy = attackerArmy,
            defArmy = defArmy,
            atkGen = atkGen,
            defGen = defGen
        )

        val attackerWon = battle.attackerWon

        val resolvedWar = war.copy(
            isResolved = true,
            attackerWon = attackerWon,
            state = if (attackerWon) "captured" else "failed",
            endedAt = now
        )
        warDao.update(resolvedWar)

        val newArmy = attackerArmy.copy(
            infantry = max(0, attackerArmy.infantry + battle.attackerPeh - war.peh),
            cossacks = max(0, attackerArmy.cossacks + battle.attackerKaz - war.kaz),
            guards = max(0, attackerArmy.guards + battle.attackerGva - war.gva)
        )
        armyDao.insert(newArmy)
        if (war.attackerRuler == myRuler) {
            army = newArmy
        }

        val newAtkGen = atkGen.copy(
            experience = atkGen.experience + battle.attackerExp,
            battles = atkGen.battles + 1,
            wins = atkGen.wins + if (attackerWon) 1 else 0
        )
        generalDao.insert(newAtkGen)

        val newDefGen = defGen.copy(
            experience = defGen.experience + battle.defenderExp,
            battles = defGen.battles + 1,
            wins = defGen.wins + if (!attackerWon) 1 else 0
        )
        generalDao.insert(newDefGen)

        val attCountry = countryDao.getCountryByRuler(war.attackerRuler)
        val defCountry = countryDao.getCountryByRuler(war.defenderRuler)
        if (attackerWon && attCountry != null && defCountry != null) {
            val rewardMoney = (defCountry.money * 30) / 100
            val rewardLand = (defCountry.land * 20) / 100

            val updatedAtt = attCountry.copy(
                money = attCountry.money + rewardMoney,
                land = attCountry.land + rewardLand
            )
            val updatedDef = defCountry.copy(
                money = max(0, defCountry.money - rewardMoney),
                land = max(0, defCountry.land - rewardLand)
            )

            countryDao.insertCountry(updatedAtt)
            countryDao.insertCountry(updatedDef)

            if (war.attackerRuler == myRuler) {
                country = updatedAtt
            }
        }

        warLogDao.insert(
            WarLogEntity(
                warId = war.id,
                ts = now,
                type = if (attackerWon) "capture_ok" else "capture_fail",
                payload = "{}"
            )
        )

        wars = warDao.getWarsForRuler(myRuler)
        renderArmy()
        renderWars(myRuler)

        // syncUp армии после боя (для атакующего)
        if (war.attackerRuler == myRuler) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    OnlineArmySync.syncUp(db, newArmy.rulerName)
                } catch (_: Exception) {
                }
            }
        }

        Toast.makeText(
            ctx,
            if (attackerWon) "Государство захвачено!" else "Провал: наш отряд уничтожен.",
            Toast.LENGTH_LONG
        ).show()
    }

    // ---------- ФОРМУЛА БОЯ (из der1) ----------

    private data class PhpBattleResult(
        val attackerPeh: Int,
        val attackerKaz: Int,
        val attackerGva: Int,
        val defenderPeh: Int,
        val defenderKaz: Int,
        val defenderGva: Int,
        val attackerExp: Int,
        val defenderExp: Int,
        val attackerWon: Boolean
    )

    private fun resolveBattlePhpStyle(
        atkPeh: Int,
        atkKaz: Int,
        atkGva: Int,
        defPeh: Int,
        defKaz: Int,
        defGva: Int,
        atkArmy: ArmyState,
        defArmy: ArmyState,
        atkGen: GeneralState,
        defGen: GeneralState
    ): PhpBattleResult {

        fun lvlAttack(base: Int, stat: Int): Double =
            max(0, stat - base).toDouble()

        val a1 = atkGen.attack.toDouble()
        val a2 = max(1, atkGen.leadership).toDouble()
        val a3 = atkGen.experience.toDouble()

        val a4 = atkPeh.toDouble()
        val a5 = lvlAttack(10, atkArmy.infantryAttack)
        val a6 = lvlAttack(10, atkArmy.infantryDefense)

        val a7 = atkKaz.toDouble()
        val a8 = lvlAttack(10, atkArmy.cossackAttack)
        val a9 = lvlAttack(10, atkArmy.cossackDefense)

        val a10 = atkGva.toDouble()
        val a11 = lvlAttack(10, atkArmy.guardAttack)
        val a12 = lvlAttack(10, atkArmy.guardDefense)

        val b1 = defGen.attack.toDouble()
        val b2 = max(1, defGen.leadership).toDouble()
        val b3 = defGen.experience.toDouble()

        val b4 = defPeh.toDouble()
        val b5 = lvlAttack(10, defArmy.infantryAttack)
        val b6 = lvlAttack(10, defArmy.infantryDefense)

        val b7 = defKaz.toDouble()
        val b8 = lvlAttack(10, defArmy.cossackAttack)
        val b9 = lvlAttack(10, defArmy.cossackDefense)

        val b10 = defGva.toDouble()
        val b11 = lvlAttack(10, defArmy.guardAttack)
        val b12 = lvlAttack(10, defArmy.guardDefense)

        val gena1 = (a2 * (a1 + (a3 / 500.0))).coerceAtLeast(1.0)
        val gena2 = (b2 * (b1 + (b3 / 500.0))).coerceAtLeast(1.0)

        val k1 = sqrt(gena1 / gena2)
        val k2 = sqrt(gena2 / gena1)

        fun rnd(x: Double): Double = x.roundToInt().toDouble()
        var g11 = 0.0; var g12 = 0.0; var g13 = 0.0
        var g21 = 0.0; var g24 = 0.0; var g27 = 0.0
        var k11 = 0.0; var k12 = 0.0; var k13 = 0.0
        var k22 = 0.0; var k25 = 0.0; var k28 = 0.0
        var p11 = 0.0; var p12 = 0.0; var p13 = 0.0
        var p23 = 0.0; var p26 = 0.0; var p29 = 0.0
        var op1 = 0.0; var op2 = 0.0; var op3 = 0.0; var op4 = 0.0
        var op5 = 0.0; var op6 = 0.0; var op7 = 0.0; var op8 = 0.0
        var op9 = 0.0; var op10 = 0.0; var op11 = 0.0; var op12 = 0.0
        var op13 = 0.0; var op14 = 0.0; var op15 = 0.0; var op16 = 0.0
        var op17 = 0.0; var op18 = 0.0

        // --- блок 1: гвардия атакующего против всех ---
        var gva1 = (a10 * (1 + ((a11 * a12) * 0.625)) * k1) * 1.25
        var gva2 = (b10 * (1 + ((b11 * b12) * 0.625))) * k2
        if (gva1 > gva2) {
            val w1 = gva1 - gva2
            val w2 = (100.0 * w1) / gva1
            g11 = rnd((a10 * w2) / 100.0)
            g21 = 0.0
            op1 = b10 * 3.0
            op2 = (a10 - g11) * 3.0
        } else {
            val w1 = gva2 - gva1
            val w2 = (100.0 * w1) / gva2
            g21 = rnd((b10 * w2) / 100.0)
            g11 = 0.0
            op1 = (b10 - g21) * 3.0
            op2 = a10 * 3.0
        }

        gva1 = ((g11 * (1 + ((a11 * a12) * 0.625)) * k1) * 1.25) * 1.5
        var kaz2 = (b7 * (1 + ((b8 * b9) * 0.625))) * k2
        if (gva1 > kaz2) {
            val w1 = gva1 - kaz2
            val w2 = (100.0 * w1) / gva1
            g12 = rnd((g11 * w2) / 100.0)
            k22 = 0.0
            op3 = b7 * 2.0
            op4 = (g11 - g12) * 3.0
        } else {
            val w1 = kaz2 - gva1
            val w2 = (100.0 * w1) / kaz2
            k22 = rnd((b7 * w2) / 100.0)
            g12 = 0.0
            op3 = (b7 - k22) * 2.0
            op4 = g11 * 3.0
        }

        gva1 = ((g12 * (1 + ((a11 * a12) * 0.625)) * k1) * 1.25)
        var peh2 = ((b4 * (1 + ((b5 * b6) * 0.625))) * k2) * 1.5
        if (gva1 > peh2) {
            val w1 = gva1 - peh2
            val w2 = (100.0 * w1) / gva1
            g13 = rnd((g12 * w2) / 100.0)
            p23 = 0.0
            op5 = b4 * 1.0
            op6 = (g12 - g13) * 3.0
        } else {
            val w1 = peh2 - gva1
            val w2 = (100.0 * w1) / peh2
            p23 = rnd((b4 * w2) / 100.0)
            g13 = 0.0
            op5 = b4 - p23
            op6 = g12 * 3.0
        }

        // --- блок 2: казаки атакующего ---
        var kaz1 = ((a7 * (1 + ((a8 * a9) * 0.625)) * k1) * 1.25)
        gva2 = (g21 * (1 + ((b11 * b12) * 0.625)) * k2) * 1.5
        if (kaz1 > gva2) {
            val w1 = kaz1 - gva2
            val w2 = (100.0 * w1) / kaz1
            k11 = rnd((a7 * w2) / 100.0)
            g24 = 0.0
            op7 = g21 * 3.0
            op8 = (a7 - k11) * 2.0
        } else {
            val w1 = gva2 - kaz1
            val w2 = (100.0 * w1) / gva2
            g24 = rnd((g21 * w2) / 100.0)
            k11 = 0.0
            op7 = (g21 - g24) * 3.0
            op8 = a7 * 2.0
        }

        kaz1 = ((k11 * (1 + ((a8 * a9) * 0.625)) * k1) * 1.25)
        kaz2 = (k22 * (1 + ((b8 * b9) * 0.625))) * k2
        if (kaz1 > kaz2) {
            val w1 = kaz1 - kaz2
            val w2 = (100.0 * w1) / kaz1
            k12 = rnd((k11 * w2) / 100.0)
            k25 = 0.0
            op9 = k22 * 2.0
            op10 = (k11 - k12) * 2.0
        } else {
            val w1 = kaz2 - kaz1
            val w2 = (100.0 * w1) / kaz2
            k25 = rnd((k22 * w2) / 100.0)
            k12 = 0.0
            op9 = (k22 - k25) * 2.0
            op10 = k11 * 2.0
        }

        kaz1 = ((k12 * (1 + ((a8 * a9) * 0.625)) * k1) * 1.25) * 1.5
        peh2 = (p23 * (1 + ((b5 * b6) * 0.625))) * k2
        if (kaz1 > peh2) {
            val w1 = kaz1 - peh2
            val w2 = (100.0 * w1) / kaz1
            k13 = rnd((k12 * w2) / 100.0)
            p26 = 0.0
            op11 = p23 * 1.0
            op12 = (k12 - k13) * 2.0
        } else {
            val w1 = peh2 - kaz1
            val w2 = (100.0 * w1) / peh2
            p26 = rnd((p23 * w2) / 100.0)
            k13 = 0.0
            op11 = p23 - p26
            op12 = k12 * 2.0
        }

        // --- блок 3: пехота атакующего ---
        var peh1 = ((a4 * (1 + ((a5 * a6) * 0.625)) * k1) * 1.25) * 1.5
        gva2 = (g24 * (1 + ((b11 * b12) * 0.625)) * k2)
        if (peh1 > gva2) {
            val w1 = peh1 - gva2
            val w2 = (100.0 * w1) / peh1
            p11 = rnd((a4 * w2) / 100.0)
            g27 = 0.0
            op13 = g24 * 3.0
            op14 = a4 - p11
        } else {
            val w1 = gva2 - peh1
            val w2 = (100.0 * w1) / gva2
            g27 = rnd((g24 * w2) / 100.0)
            p11 = 0.0
            op13 = (g24 - g27) * 3.0
            op14 = a4 * 1.0
        }

        peh1 = ((p11 * (1 + ((a5 * a6) * 0.625)) * k1) * 1.25)
        kaz2 = ((k25 * (1 + ((b8 * b9) * 0.625))) * k2) * 1.5
        if (peh1 > kaz2) {
            val w1 = peh1 - kaz2
            val w2 = (100.0 * w1) / peh1
            p12 = rnd((p11 * w2) / 100.0)
            k28 = 0.0
            op15 = k25 * 2.0
            op16 = p11 - p12
        } else {
            val w1 = kaz2 - peh1
            val w2 = (100.0 * w1) / kaz2
            k28 = rnd((k25 * w2) / 100.0)
            p12 = 0.0
            op15 = (k25 - k28) * 2.0
            op16 = p11 * 1.0
        }

        peh1 = ((p12 * (1 + ((a5 * a6) * 0.625)) * k1) * 1.25)
        peh2 = (p26 * (1 + ((b5 * b6) * 0.625))) * k2
        if (peh1 > peh2) {
            val w1 = peh1 - peh2
            val w2 = (100.0 * w1) / peh1
            p13 = rnd((p12 * w2) / 100.0)
            p29 = 0.0
            op17 = p26 * 1.0
            op18 = p12 - p13
        } else {
            val w1 = peh2 - peh1
            val w2 = (100.0 * w1) / peh2
            p29 = rnd((p26 * w2) / 100.0)
            p13 = 0.0
            op17 = p26 - p29
            op18 = p12 * 1.0
        }

        val opt1 = op1 + op3 + op5 + op7 + op9 + op11 + op13 + op15 + op17
        val opt2 = op2 + op4 + op6 + op8 + op10 + op12 + op14 + op16 + op18

        val attackerWon = (p13 > 0.0 || k13 > 0.0 || g13 > 0.0)

        return PhpBattleResult(
            attackerPeh = max(0, p13.roundToInt()),
            attackerKaz = max(0, k13.roundToInt()),
            attackerGva = max(0, g13.roundToInt()),
            defenderPeh = max(0, p29.roundToInt()),
            defenderKaz = max(0, k28.roundToInt()),
            defenderGva = max(0, g27.roundToInt()),
            attackerExp = max(0, opt1.roundToInt()),
            defenderExp = max(0, opt2.roundToInt()),
            attackerWon = attackerWon
        )
    }

    // ---------- ОБУЧЕНИЕ ВОЙСК ПО УЧЁНЫМ ----------

    private fun processTrainingJob() {
        val c = country ?: return
        val a = army ?: return

        val job = trainingJobDao.getJobForRuler(c.rulerName)
        if (job == null) {
            trainingJob = null
            renderTrainingStatus(null)
            return
        }

        val endTimeMillis = job.startTimeMillis + job.durationSeconds * 1000L
        val now = System.currentTimeMillis()

        if (now >= endTimeMillis) {
            // Обучение завершилось. Сервер уже добавил войска и вернул
            // учёных в training_jobs_get.php при запросе списка. Нам
            // необходимо обновить локальную армию и страну, но не вызывать
            // training_job_delete.php, так как он предназначен для отмены
            // задания и вернёт учёных и рабочих. Вместо этого просто
            // обновляем локальные сущности и отправляем их на сервер.
            val newBots = c.bots + job.scientists
            val newArmy = when (job.unitType) {
                1 -> a.copy(infantry = a.infantry + job.workers)
                2 -> a.copy(cossacks = a.cossacks + job.workers)
                3 -> a.copy(guards = a.guards + job.workers)
                else -> a
            }
            val newCountry = c.copy(bots = newBots)

            armyDao.insert(newArmy)
            countryDao.insertCountry(newCountry)

            army = newArmy
            country = newCountry
            trainingJob = null

            // Синхронизуем армию на сервере. Страна синхронизируется в
            // countryDao.insertCountry(). Не вызываем delete(job), иначе
            // рабочие и учёные будут возвращены ошибочно.
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    OnlineArmySync.syncUp(db, newArmy.rulerName)
                } catch (_: Exception) {
                }
            }

            renderArmy()
            renderTrainingStatus(null)

            Toast.makeText(
                requireContext(),
                "Обучение завершено: новые войска готовы.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            trainingJob = job
            val remainingSec = ((endTimeMillis - now) / 1000L).coerceAtLeast(0L).toInt()
            renderTrainingStatus(remainingSec)
        }
    }

    private fun renderTrainingStatus(remainingSeconds: Int?) {
        val c = country
        val tv = binding.tvTrainingStatus

        if (remainingSeconds == null) {
            if (c == null) {
                tv.text = "Обучение не ведётся."
            } else {
                tv.text = "Обучение не ведётся. Учёные: ${c.bots}, рабочие: ${c.workers}"
            }
            return
        }

        val min = remainingSeconds / 60
        val sec = remainingSeconds % 60
        tv.text = "Идёт обучение (${min} мин. ${sec} с.)"
    }

    private fun showTrainTroopsDialog() {
        val c = country ?: return
        val ctx = requireContext()

        val existing = trainingJobDao.getJobForRuler(c.rulerName)
        if (existing != null) {
            trainingJob = existing
            val endTime = existing.startTimeMillis + existing.durationSeconds * 1000L
            val now = System.currentTimeMillis()
            val remainingSec = ((endTime - now) / 1000L).coerceAtLeast(0L).toInt()
            val min = remainingSec / 60
            val sec = remainingSec % 60
            Toast.makeText(
                ctx,
                "Обучение уже ведётся (${min} мин. ${sec} с.)",
                Toast.LENGTH_LONG
            ).show()
            renderTrainingStatus(remainingSec)
            return
        }

        // Определяем, сколько учёных сейчас выставлено на продажу на бирже,
        // чтобы запретить использовать их в обучении. Используем DAO напрямую —
        // в онлайн‑версии это делается через сеть, но код приложения и так
        // читает данные синхронно на главном потоке.
        val offerBotsOnSale = marketDao.getOfferForRulerAndResource(c.rulerName, 6)?.amount ?: 0
        val availableBots = (c.bots - offerBotsOnSale).coerceAtLeast(0)

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        val tvInfo = TextView(ctx).apply {
            text = buildString {
                append("Рабочие: ${c.workers}\n")
                // Показываем сколько учёных всего и сколько свободно (без учёных на бирже)
                if (offerBotsOnSale > 0) {
                    append("Учёные: всего ${c.bots}, свободно $availableBots (на бирже $offerBotsOnSale)\n\n")
                } else {
                    append("Учёные: ${c.bots}\n\n")
                }
                append("Железо: ${c.metal}, Камень: ${c.mineral}\n")
                append("Дерево: ${c.wood}, Зерно: ${c.food}\n")
                append("Деньги: ${c.money}\n")
            }
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }
        layout.addView(tvInfo)

        fun label(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }

        val etWorkers = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Сколько рабочих обучить"
        }
        val etBots = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Сколько учёных задействовать"
        }

        val spUnit = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                listOf("Пехота", "Казаки", "Гвардия")
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        layout.addView(label("Рабочие для обучения:"))
        layout.addView(etWorkers)
        layout.addView(label("Учёные на обучение:"))
        layout.addView(etBots)
        layout.addView(label("Тип войск:"))
        layout.addView(spUnit)

        AlertDialog.Builder(ctx)
            .setTitle("Обучение войск")
            .setView(layout)
            .setPositiveButton("Начать") { _, _ ->
                val rawRob = etWorkers.text.toString().trim().toIntOrNull() ?: 0
                val rawBot = etBots.text.toString().trim().toIntOrNull() ?: 0
                // Корректируем ввод, чтобы не превышать доступных ресурсов
                var rob = rawRob.coerceAtLeast(0)
                var bob = rawBot.coerceAtLeast(0)

                if (rob > c.workers) rob = c.workers
                // Используем только свободных учёных (без тех, что на бирже)
                if (bob > availableBots) bob = availableBots

                if (rob <= 0 || bob <= 0) {
                    Toast.makeText(
                        ctx,
                        "Нужно указать положительное число рабочих и учёных.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val unitType = spUnit.selectedItemPosition + 1

                val (x1, x2, x3, x4, x5) = when (unitType) {
                    1 -> listOf(rob * 2, rob * 0, rob * 4, rob * 15, rob * 7)
                    2 -> listOf(rob * 4, rob * 20, rob * 20, rob * 30, rob * 12)
                    3 -> listOf(rob * 6, rob * 30, rob * 15, rob * 45, rob * 20)
                    else -> listOf(0, 0, 0, 0, 0)
                }

                if (x1 > c.metal || x2 > c.mineral || x3 > c.wood ||
                    x4 > c.food || x5 > c.money
                ) {
                    Toast.makeText(
                        ctx,
                        "Не хватает ресурсов.\n" +
                                "Нужно: железо $x1, камень $x2, дерево $x3, зерно $x4, деньги $x5.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                val e4 = (((4000.0 / bob) * rob) / 6.0).roundToInt().coerceAtLeast(60)
                val startTime = System.currentTimeMillis()

                val newCountry = c.copy(
                    metal = c.metal - x1,
                    mineral = c.mineral - x2,
                    wood = c.wood - x3,
                    food = c.food - x4,
                    money = c.money - x5,
                    workers = c.workers - rob,
                    bots = c.bots - bob
                )
                countryDao.insertCountry(newCountry)
                country = newCountry

                val job = TrainingJobEntity(
                    rulerName = c.rulerName,
                    unitType = unitType,
                    workers = rob,
                    scientists = bob,
                    startTimeMillis = startTime,
                    durationSeconds = e4
                )
                trainingJobDao.insert(job)
                trainingJob = job

                renderTrainingStatus(e4)
                Toast.makeText(
                    ctx,
                    "Обучение началось. Около ${e4 / 60} мин.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- УВОЛЬНЕНИЕ ВОЕННЫХ ----------

    private fun showFireArmyDialog() {
        val a = army ?: return
        val c = country ?: return
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        val tvInfo = TextView(ctx).apply {
            text = buildString {
                append("Пехота: ${a.infantry}\n")
                append("Казаки: ${a.cossacks}\n")
                append("Гвардия: ${a.guards}\n\n")
                append("Железо: ${c.metal}, Камень: ${c.mineral}\n")
                append("Дерево: ${c.wood}, Зерно: ${c.food}, Деньги: ${c.money}\n")
                append("Рабочие: ${c.workers}\n")
            }
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }
        layout.addView(tvInfo)

        fun label(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(resources.getColor(R.color.derzhava_text_dark))
        }

        val etCount = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Сколько военных уволить"
        }
        layout.addView(label("Сколько военных уволить:"))
        layout.addView(etCount)

        val spUnit = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                listOf("Пехота", "Казаки", "Гвардия")
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        layout.addView(label("Тип войск:"))
        layout.addView(spUnit)

        AlertDialog.Builder(ctx)
            .setTitle("Уволить военных")
            .setView(layout)
            .setPositiveButton("Уволить") { _, _ ->
                val kolvo = etCount.text.toString().trim().toIntOrNull() ?: 0
                if (kolvo <= 0) {
                    Toast.makeText(
                        ctx,
                        "Нужно указать положительное число военных.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val unitIndex = spUnit.selectedItemPosition
                val available = when (unitIndex) {
                    0 -> a.infantry
                    1 -> a.cossacks
                    2 -> a.guards
                    else -> 0
                }

                if (kolvo > available) {
                    Toast.makeText(
                        ctx,
                        "У тебя нет столько военных этого типа.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                val newArmy = when (unitIndex) {
                    0 -> a.copy(infantry = a.infantry - kolvo)
                    1 -> a.copy(cossacks = a.cossacks - kolvo)
                    2 -> a.copy(guards = a.guards - kolvo)
                    else -> a
                }

                val (d1, d2, d3, d4, d5) = when (unitIndex) {
                    0 -> listOf(2, 0, 4, 15, 7)
                    1 -> listOf(4, 20, 20, 30, 12)
                    2 -> listOf(6, 30, 15, 45, 20)
                    else -> listOf(0, 0, 0, 0, 0)
                }

                fun refundPerRes(d: Int): Int =
                    ((d * kolvo) / 2.0).roundToInt()

                val r1 = refundPerRes(d1)
                val r2 = refundPerRes(d2)
                val r3 = refundPerRes(d3)
                val r4 = refundPerRes(d4)
                val r5 = refundPerRes(d5)

                val newCountry = c.copy(
                    metal = c.metal + r1,
                    mineral = c.mineral + r2,
                    wood = c.wood + r3,
                    food = c.food + r4,
                    money = c.money + r5,
                    workers = c.workers + kolvo
                )

                armyDao.insert(newArmy)
                countryDao.insertCountry(newCountry)

                army = newArmy
                country = newCountry

                renderArmy()

                // syncUp армии
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        OnlineArmySync.syncUp(db, newArmy.rulerName)
                    } catch (_: Exception) {
                    }
                }

                Toast.makeText(
                    ctx,
                    "Военные уволены. Возвращено 50% ресурсов:\n" +
                            "железо $r1, камень $r2, дерево $r3, зерно $r4, деньги $r5.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // При возвращении на экран военной базы подтягиваем актуальную армию с сервера.
        // Это позволяет отобразить изменения, сделанные напрямую в базе данных, без перезахода.
        val user = userRepository.getUser() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                OnlineArmySync.syncDown(db, user.rulerName)
                val updated = armyDao.getByRuler(user.rulerName)
                if (updated != null && isAdded) {
                    army = updated
                    renderArmy()
                }
            } catch (_: Exception) {
                // если сеть недоступна — ничего не делаем
            }
        }
    }

    companion object {
        private const val FIRST_RAID_DELAY = 3 * 60 * 60 * 1000L  // 3 часа
        private const val RAID_COOLDOWN = 1 * 60 * 60 * 1000L     // 1 час
        private const val CAPTURE_DELAY = 12 * 60 * 60 * 1000L    // 12 часов

        fun newInstance() = WarBaseFragment()
    }
}
