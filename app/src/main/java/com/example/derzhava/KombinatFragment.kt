package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.ResearchJobDao
import com.example.derzhava.data.ResearchJobEntity
import com.example.derzhava.data.ScientistTrainingJobDao
import com.example.derzhava.data.ScientistTrainingJobEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentKombinatBinding
import kotlin.math.roundToInt

class KombinatFragment : Fragment() {

    private var _binding: FragmentKombinatBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository
    private lateinit var researchJobDao: ResearchJobDao
    private lateinit var scientistTrainingJobDao: ScientistTrainingJobDao

    private var country: CountryEntity? = null
    private var currentResearch: ResearchJobEntity? = null
    private var currentScientistJob: ScientistTrainingJobEntity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)
        researchJobDao = db.researchJobDao()
        scientistTrainingJobDao = db.scientistTrainingJobDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKombinatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        loadState()
    }

    // ------------------------- ЗАГРУЗКА СОСТОЯНИЯ -------------------------

    private fun loadState() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Авторизуйтесь", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        var c = db.countryDao().getCountryByRuler(user.rulerName)
        if (c == null) {
            c = CountryEntity(
                rulerName = user.rulerName,
                countryName = user.countryName
            )
            db.countryDao().insertCountry(c)
        }

        // naukur не должен быть 0, чтобы формула из PHP не делила на 0
        if (c.globalScienceLevel <= 0) {
            c = c.copy(globalScienceLevel = 1)
            db.countryDao().insertCountry(c)
        }

        country = c
        currentResearch = researchJobDao.getJobForRuler(c.rulerName)
        currentScientistJob = scientistTrainingJobDao.getJobForRuler(c.rulerName)

        // Проверяем, не закончились ли уже процессы
        checkResearchCompletion()
        checkScientistTrainingCompletion()

        // Перечитаем страну после возможных апдейтов
        country = db.countryDao().getCountryByRuler(user.rulerName) ?: c

        renderAll()
        showTechTab()
    }

    // ------------------------- TAB UI -------------------------

    private fun setupTabs() {
        binding.tabTech.setOnClickListener { showTechTab() }
        binding.tabProduction.setOnClickListener { showProductionTab() }
        binding.tabScientists.setOnClickListener { showScientistsTab() }
    }

    private fun selectTab(tabIndex: Int) {
        val active = R.drawable.bg_derzhava_button
        val inactive = R.drawable.bg_derzhava_button_base

        binding.tabTech.setBackgroundResource(if (tabIndex == 0) active else inactive)
        binding.tabProduction.setBackgroundResource(if (tabIndex == 1) active else inactive)
        binding.tabScientists.setBackgroundResource(if (tabIndex == 2) active else inactive)
    }

    private fun showTechTab() {
        selectTab(0)
        binding.groupTech.visibility = View.VISIBLE
        binding.groupProduction.visibility = View.GONE
        binding.groupScientists.visibility = View.GONE
        renderTechTab()
    }

    private fun showProductionTab() {
        selectTab(1)
        binding.groupTech.visibility = View.GONE
        binding.groupProduction.visibility = View.VISIBLE
        binding.groupScientists.visibility = View.GONE
        renderProductionTab()
    }

    private fun showScientistsTab() {
        selectTab(2)
        binding.groupTech.visibility = View.GONE
        binding.groupProduction.visibility = View.GONE
        binding.groupScientists.visibility = View.VISIBLE
        renderScientistsTab()
    }

    // ------------------------- ОБЩИЙ РЕНДЕР -------------------------

    private fun renderAll() {
        val c = country ?: return

        binding.tvTitle.text = "КОМБИНАТ"
        binding.tvLevel.text = "Уровень здания: ${c.domik1}"
        binding.tvShortInfo.text =
            if (c.domik1 == 0) {
                "Комбинат ещё не построен. Постройте его, чтобы добывать ресурсы, развивать технологии и обучать учёных."
            } else {
                "Комбинат распределяет рабочих по добыче ресурсов, развивает технологии и обучает учёных."
            }
    }

    // ------------------------- ТЕХНОЛОГИИ (mod1,3,4,5,6,7) -------------------------

    private fun renderTechTab() {
        val c = country ?: return

        // Если Комбинат не построен — никакой науки
        if (c.domik1 == 0) {
            binding.tvCurrentTech.text = "Комбинат не построен. Исследования недоступны."
            binding.btnStartResearch.text = "Нет Комбината"
            binding.btnStartResearch.isEnabled = false

            binding.tvTechList.text = buildString {
                append("Постройте Комбинат, чтобы:\n")
                append("• открывать новые технологии;\n")
                append("• улучшать добычу ресурсов;\n")
                append("• увеличивать прирост населения;\n")
                append("• развивать демонтаж зданий.\n")
            }
            return
        }

        currentResearch = researchJobDao.getJobForRuler(c.rulerName)
        val job = currentResearch

        if (job == null) {
            binding.tvCurrentTech.text = "Исследований сейчас нет."
            binding.btnStartResearch.text = "Начать исследование"
        } else {
            val now = System.currentTimeMillis()
            val end = job.startTimeMillis + job.durationSeconds * 1000L
            val remainingSec = ((end - now) / 1000L).coerceAtLeast(0L).toInt()
            val min = remainingSec / 60
            val sec = remainingSec % 60

            binding.tvCurrentTech.text =
                "Идёт исследование: «${scienceName(job.scienceType)}» (осталось $min мин. $sec с.)"
            binding.btnStartResearch.text = "Отменить исследование"
        }

        val text = buildString {
            val c0 = c
            append("Научный уровень: [${c0.globalScienceLevel}%]\n")
            append("Выплавка железа: [${c0.scienceMetal}%]\n")
            append("Добыча камня: [${c0.scienceStone}%]\n")
            append("Переработка древесины: [${c0.scienceWood}%]\n")
            append("Выращивание зерна: [${c0.scienceFood}%]\n")
            append("Прирост населения: [${c0.scienceGrowthBonus}%]\n")
            append("Демонтаж зданий: [${c0.scienceDemolition}%]\n")
        }
        binding.tvTechList.text = text

        binding.btnStartResearch.isEnabled = true
        binding.btnStartResearch.setOnClickListener {
            val j = currentResearch
            if (j == null) {
                showStartResearchDialog()
            } else {
                showCancelResearchDialog(j)
            }
        }
    }

    private fun showStartResearchDialog() {
        val c = country ?: return

        // Без Комбината — вообще не даём стартовать исследования
        if (c.domik1 == 0) {
            Toast.makeText(
                requireContext(),
                "Комбинат ещё не построен — исследования невозможны.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (currentResearch != null) {
            Toast.makeText(requireContext(), "Исследование уже ведётся", Toast.LENGTH_SHORT).show()
            return
        }

        if (c.bots <= 0) {
            Toast.makeText(requireContext(), "У вас нет учёных", Toast.LENGTH_SHORT).show()
            return
        }

        val ctx = requireContext()
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle("Новое исследование")

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val techTypes = listOf(
            6 to "Научный уровень",
            1 to "Выплавка железа",
            2 to "Добыча камня",
            3 to "Переработка древесины",
            4 to "Выращивание зерна",
            5 to "Прирост населения",
            7 to "Демонтаж зданий"
        )

        val techNames = techTypes.map { (t, name) ->
            val lvl = getScienceLevel(c, t)
            "$name [${lvl}%]"
        }

        val spinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                techNames
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        val tvBots = TextView(ctx).apply {
            text = "Учёные (боты): ${c.bots}"
        }

        val etBots = EditText(ctx).apply {
            hint = "Сколько учёных использовать (0 = все)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val tvMoney = TextView(ctx).apply {
            text = "Деньги: ${c.money}"
        }

        val etMoney = EditText(ctx).apply {
            hint = "Сколько денег вложить (0 = все)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(spinner)
        layout.addView(tvBots)
        layout.addView(etBots)
        layout.addView(tvMoney)
        layout.addView(etMoney)

        builder.setView(layout)

        builder.setPositiveButton("Начать") { _, _ ->
            val idx = spinner.selectedItemPosition
            val (typeId, _) = techTypes[idx]

            val currentLevel = getScienceLevel(c, typeId)
            if (currentLevel >= 100) {
                Toast.makeText(
                    requireContext(),
                    "Достигнуто максимальное значение",
                    Toast.LENGTH_SHORT
                ).show()
                return@setPositiveButton
            }

            val botsInput = etBots.text.toString().trim().toIntOrNull() ?: 0
            val moneyInput = etMoney.text.toString().trim().toIntOrNull() ?: 0

            var bots = botsInput
            if (bots <= 0 || bots > c.bots) {
                bots = c.bots
            }

            var money = moneyInput
            if (money <= 0 || money > c.money) {
                money = c.money
            }

            if (bots <= 0) {
                Toast.makeText(requireContext(), "У вас нет столько учёных", Toast.LENGTH_SHORT)
                    .show()
                return@setPositiveButton
            }

            if (money <= 0) {
                Toast.makeText(
                    requireContext(),
                    "Нужно вложить хоть немного денег",
                    Toast.LENGTH_SHORT
                ).show()
                return@setPositiveButton
            }

            val vr = when (typeId) {
                1 -> 2_140_000
                2 -> 1_700_000
                3 -> 1_200_000
                4 -> 1_250_000
                5 -> 1_800_000
                6 -> 2_170_000
                7 -> 3_150_000
                else -> 2_000_000
            }

            val naukur = c.globalScienceLevel.coerceAtLeast(1)
            val epoch = c.epoch.coerceAtLeast(1)

            // mod6.php: z = round((vr/bot)/naukur * (epoha*epoha))
            val z = (((vr.toDouble() / bots.toDouble()) / naukur.toDouble()) *
                    (epoch * epoch).toDouble())
                .roundToInt()
                .coerceAtLeast(1)

            // pr = d/100
            val progressPoints = (money / 100).coerceAtLeast(1)

            val job = ResearchJobEntity(
                rulerName = c.rulerName,
                scienceType = typeId,
                startTimeMillis = System.currentTimeMillis(),
                durationSeconds = z,
                scientists = bots,
                progressPoints = progressPoints
            )

            val newCountry = c.copy(
                bots = c.bots - bots,
                money = c.money - money
            )

            db.countryDao().insertCountry(newCountry)
            researchJobDao.insert(job)

            country = newCountry
            currentResearch = job

            val minutes = (z / 60).coerceAtLeast(1)
            Toast.makeText(
                requireContext(),
                "Исследование запущено (примерно $minutes мин.)",
                Toast.LENGTH_LONG
            ).show()

            renderTechTab()
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun showCancelResearchDialog(job: ResearchJobEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отменить исследование?")
            .setMessage(
                "Учёные (${job.scientists}) вернутся, но вложенные деньги и прогресс будут потеряны."
            )
            .setPositiveButton("Отменить") { _, _ ->
                cancelResearch(job)
            }
            .setNegativeButton("Назад", null)
            .show()
    }

    private fun cancelResearch(job: ResearchJobEntity) {
        val c = country ?: return
        val updated = c.copy(bots = c.bots + job.scientists)

        db.countryDao().insertCountry(updated)
        researchJobDao.delete(job)

        country = updated
        currentResearch = null

        Toast.makeText(requireContext(), "Исследование отменено", Toast.LENGTH_SHORT).show()
        renderTechTab()
    }

    private fun checkResearchCompletion() {
        val c = country ?: return
        val job = currentResearch ?: return

        val now = System.currentTimeMillis()
        val end = job.startTimeMillis + job.durationSeconds * 1000L
        if (now < end) return

        val current = getScienceLevel(c, job.scienceType)
        val newLevel = (current + job.progressPoints).coerceAtMost(100)
        val delta = newLevel - current

        val withScience = applyScienceIncrease(c, job.scienceType, delta)
        val updated = withScience.copy(
            bots = withScience.bots + job.scientists
        )

        db.countryDao().insertCountry(updated)
        researchJobDao.delete(job)

        country = updated
        currentResearch = null

        Toast.makeText(
            requireContext(),
            "Исследование «${scienceName(job.scienceType)}» завершено",
            Toast.LENGTH_LONG
        ).show()
    }

    // ------------------------- ПРОИЗВОДСТВО (mod1,8–16) -------------------------

    private fun renderProductionTab() {
        val c = country ?: return

        // Если Комбинат не построен — никакой выплавки/добычи
        if (c.domik1 == 0) {
            binding.tvProductionMode.text = "Производство недоступно"
            binding.tvProductionInfo.text =
                "Комбинат ещё не построен.\n" +
                        "Постройте Комбинат, чтобы распределять рабочих и добывать ресурсы."
            binding.btnChangeMode.isEnabled = false
            return
        }

        // mod1.php: komm/komn/komd/komp = (rabX * naukX)/100
        var komm = ((c.metallWorkers * c.scienceMetal) / 100.0).roundToInt()
        var komn = ((c.mineWorkers * c.scienceStone) / 100.0).roundToInt()
        var komd = ((c.woodWorkers * c.scienceWood) / 100.0).roundToInt()
        var komp = ((c.industryWorkers * c.scienceFood) / 100.0).roundToInt()

        // mod8.php: проверка, хватает ли территории (шахты/рудники/леса/поля)
        val x1 = (c.shah - c.metallWorkers / 10.0).roundToInt()
        val x2 = (c.rudn - c.mineWorkers / 10.0).roundToInt()
        val x3 = (c.lesa - c.woodWorkers / 10.0).roundToInt()
        val x4 = (c.pole - c.industryWorkers / 10.0).roundToInt()

        if (x1 <= 0) komm = 0
        if (x2 <= 0) komn = 0
        if (x3 <= 0) komd = 0
        if (x4 <= 0) komp = 0

        binding.tvProductionMode.text = "Производство ресурсов"

        val text = buildString {
            append("Свободные рабочие: ${c.workers}\n\n")
            append("Железо: занято ${c.metallWorkers} (шахты: ${c.shah}), прирост +$komm в час\n")
            append("Камень: занято ${c.mineWorkers} (рудники: ${c.rudn}), прирост +$komn в час\n")
            append("Древесина: занято ${c.woodWorkers} (леса: ${c.lesa}), прирост +$komd в час\n")
            append("Зерно: занято ${c.industryWorkers} (поля: ${c.pole}), прирост +$komp в час\n")
        }

        binding.tvProductionInfo.text = text

        binding.btnChangeMode.isEnabled = true
        binding.btnChangeMode.setOnClickListener {
            showProductionDialog(komm, komn, komd, komp)
        }
    }

    private fun showProductionDialog(komm: Int, komn: Int, komd: Int, komp: Int) {
        val c = country ?: return
        val ctx = requireContext()

        // Без Комбината сюда вообще не должны попадать, но на всякий случай:
        if (c.domik1 == 0) {
            Toast.makeText(
                ctx,
                "Комбинат ещё не построен — распределять рабочих по добыче нельзя.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val totalWorkers =
            c.workers + c.metallWorkers + c.mineWorkers + c.woodWorkers + c.industryWorkers

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle("Распределение рабочих")

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val info = TextView(ctx).apply {
            text = "Всего рабочих (с учётом занятых): $totalWorkers"
        }

        val etMet = EditText(ctx).apply {
            hint = "На железо"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(c.metallWorkers.toString())
        }
        val etMin = EditText(ctx).apply {
            hint = "На камень"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(c.mineWorkers.toString())
        }
        val etWood = EditText(ctx).apply {
            hint = "На древесину"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(c.woodWorkers.toString())
        }
        val etFood = EditText(ctx).apply {
            hint = "На зерно"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(c.industryWorkers.toString())
        }

        layout.addView(info)
        layout.addView(etMet)
        layout.addView(etMin)
        layout.addView(etWood)
        layout.addView(etFood)

        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { _, _ ->
            val m = etMet.text.toString().trim().toIntOrNull() ?: c.metallWorkers
            val n = etMin.text.toString().trim().toIntOrNull() ?: c.mineWorkers
            val w = etWood.text.toString().trim().toIntOrNull() ?: c.woodWorkers
            val f = etFood.text.toString().trim().toIntOrNull() ?: c.industryWorkers

            if (m < 0 || n < 0 || w < 0 || f < 0) {
                Toast.makeText(ctx, "Нельзя указать отрицательное число", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val assigned = m + n + w + f
            if (assigned > totalWorkers) {
                Toast.makeText(ctx, "Не хватает рабочих", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val free = totalWorkers - assigned

            val updated = c.copy(
                workers = free,
                metallWorkers = m,
                mineWorkers = n,
                woodWorkers = w,
                industryWorkers = f
            )

            db.countryDao().insertCountry(updated)
            country = updated

            Toast.makeText(ctx, "Распределение обновлено", Toast.LENGTH_SHORT).show()
            renderProductionTab()
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    // ------------------------- УЧЁНЫЕ (botan: mod17–21) -------------------------

    private fun renderScientistsTab() {
        val c = country ?: return

        // Без Комбината — обучение учёных не даём
        if (c.domik1 == 0) {
            binding.tvScientists.text = "Учёные: ${c.bots}"
            binding.tvScientistsInfo.text =
                "Комбинат не построен.\n" +
                        "Обучение учёных станет доступно после постройки Комбината."
            binding.btnAutoAssignScientists.text = "Нет Комбината"
            binding.btnAutoAssignScientists.isEnabled = false
            return
        }

        currentScientistJob = scientistTrainingJobDao.getJobForRuler(c.rulerName)
        val job = currentScientistJob

        if (job == null) {
            binding.tvScientists.text = "Учёные: ${c.bots}"
            binding.tvScientistsInfo.text =
                "Учёные запускают исследования комбината и могут обучать новых учёных.\n" +
                        "Свободные учёные: ${c.bots}."
            binding.btnAutoAssignScientists.text = "Обучить учёных"
        } else {
            val now = System.currentTimeMillis()
            val end = job.startTimeMillis + job.durationSeconds * 1000L
            val remainingSec = ((end - now) / 1000L).coerceAtLeast(0L).toInt()
            val min = remainingSec / 60
            val sec = remainingSec % 60

            binding.tvScientists.text = "Учёные: ${c.bots}"
            binding.tvScientistsInfo.text =
                "Идёт обучение учёных: рабочих ${job.workers}, тренеров ${job.scientists}.\n" +
                        "До завершения: $min мин. $sec с."
            binding.btnAutoAssignScientists.text = "Отменить обучение"
        }

        binding.btnAutoAssignScientists.isEnabled = true
        binding.btnAutoAssignScientists.setOnClickListener {
            val j = currentScientistJob
            if (j == null) {
                showScientistTrainingDialog()
            } else {
                showCancelScientistTrainingDialog(j)
            }
        }
    }

    private fun showScientistTrainingDialog() {
        val c = country ?: return
        val ctx = requireContext()

        // Страховка: без Комбината не пускаем
        if (c.domik1 == 0) {
            Toast.makeText(
                ctx,
                "Комбинат ещё не построен — обучать учёных нельзя.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (currentScientistJob != null) {
            Toast.makeText(ctx, "Обучение уже ведётся", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle("Обучение учёных")

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val info = TextView(ctx).apply {
            text = buildString {
                append("Рабочие: ${c.workers}\n")
                append("Учёные: ${c.bots}\n")
                append("Камень: ${c.mineral}, деньги: ${c.money}\n")
                append("Стоимость: 5 камня и 5 денег за каждого рабочего.\n")
            }
        }

        val etWorkers = EditText(ctx).apply {
            hint = "Сколько рабочих обучить"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val etScientists = EditText(ctx).apply {
            hint = "Сколько учёных выделить тренерами"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(info)
        layout.addView(etWorkers)
        layout.addView(etScientists)

        builder.setView(layout)

        builder.setPositiveButton("Обучить") { _, _ ->
            val robInput = etWorkers.text.toString().trim().toIntOrNull() ?: 0
            val bobInput = etScientists.text.toString().trim().toIntOrNull() ?: 0

            var rob = robInput
            var bob = bobInput

            if (rob > c.workers) rob = c.workers
            if (bob > c.bots) bob = c.bots

            if (rob <= 0 || bob <= 0) {
                Toast.makeText(ctx, "Нужно указать и рабочих, и учёных", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // Стоимость: x2 = rob*5 камня, x5 = rob*5 денег (mod20.php)
            val needMineral = rob * 5
            val needMoney = rob * 5

            if (needMineral > c.mineral || needMoney > c.money) {
                Toast.makeText(
                    ctx,
                    "Не хватает ресурсов. Нужно камень: $needMineral, деньги: $needMoney.",
                    Toast.LENGTH_LONG
                ).show()
                return@setPositiveButton
            }

            // e4 = ((4000/bob)*rob)/6  (секунды)
            val e4 = (((4000.0 / bob.toDouble()) * rob.toDouble()) / 6.0)
                .roundToInt().coerceAtLeast(1)

            val newWorkers = c.workers - rob
            val newScientists = c.bots - bob
            val newMineral = c.mineral - needMineral
            val newMoney = c.money - needMoney

            val job = ScientistTrainingJobEntity(
                rulerName = c.rulerName,
                workers = rob,
                scientists = bob,
                startTimeMillis = System.currentTimeMillis(),
                durationSeconds = e4
            )

            val updated = c.copy(
                workers = newWorkers,
                bots = newScientists,
                mineral = newMineral,
                money = newMoney
            )

            scientistTrainingJobDao.insert(job)
            db.countryDao().insertCountry(updated)

            country = updated
            currentScientistJob = job

            val minutes = (e4 / 60).coerceAtLeast(1)
            Toast.makeText(
                ctx,
                "Обучение займёт около $minutes мин.",
                Toast.LENGTH_LONG
            ).show()

            renderScientistsTab()
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun showCancelScientistTrainingDialog(job: ScientistTrainingJobEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отменить обучение?")
            .setMessage(
                "Учёные-тренеры вернутся, но рабочие и потраченные ресурсы не вернутся (как в der1)."
            )
            .setPositiveButton("Отменить") { _, _ ->
                cancelScientistTraining(job)
            }
            .setNegativeButton("Назад", null)
            .show()
    }

    private fun cancelScientistTraining(job: ScientistTrainingJobEntity) {
        val c = country ?: return

        // mod18.php: при отмене возвращаются только bot (тренеры), рабочие и ресурсы не возвращаются
        val updated = c.copy(
            bots = c.bots + job.scientists
        )

        scientistTrainingJobDao.delete(job)
        db.countryDao().insertCountry(updated)

        country = updated
        currentScientistJob = null

        Toast.makeText(requireContext(), "Обучение отменено", Toast.LENGTH_SHORT).show()
        renderScientistsTab()
    }

    private fun checkScientistTrainingCompletion() {
        val c = country ?: return
        val job = currentScientistJob ?: return

        val now = System.currentTimeMillis()
        val end = job.startTimeMillis + job.durationSeconds * 1000L
        if (now < end) return

        // mod17.php: up1 = bot + km4 + km5 (старые bot + bot из botan + rab из botan)
        val newBots = c.bots + job.scientists + job.workers
        val updated = c.copy(bots = newBots)

        scientistTrainingJobDao.delete(job)
        db.countryDao().insertCountry(updated)

        country = updated
        currentScientistJob = null

        Toast.makeText(requireContext(), "Обучение учёных завершено", Toast.LENGTH_LONG).show()
    }

    // ------------------------- ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ -------------------------

    private fun scienceName(type: Int): String = when (type) {
        1 -> "Выплавка железа"
        2 -> "Добыча камня"
        3 -> "Переработка древесины"
        4 -> "Выращивание зерна"
        5 -> "Прирост населения"
        6 -> "Научный уровень"
        7 -> "Демонтаж зданий"
        else -> "Неизвестная технология"
    }

    private fun getScienceLevel(c: CountryEntity, type: Int): Int = when (type) {
        1 -> c.scienceMetal
        2 -> c.scienceStone
        3 -> c.scienceWood
        4 -> c.scienceFood
        5 -> c.scienceGrowthBonus
        6 -> c.globalScienceLevel
        7 -> c.scienceDemolition
        else -> 0
    }

    private fun applyScienceIncrease(
        c: CountryEntity,
        type: Int,
        delta: Int
    ): CountryEntity {
        val d = delta.coerceAtLeast(0)
        return when (type) {
            1 -> c.copy(scienceMetal = (c.scienceMetal + d).coerceAtMost(100))
            2 -> c.copy(scienceStone = (c.scienceStone + d).coerceAtMost(100))
            3 -> c.copy(scienceWood = (c.scienceWood + d).coerceAtMost(100))
            4 -> c.copy(scienceFood = (c.scienceFood + d).coerceAtMost(100))
            5 -> c.copy(scienceGrowthBonus = (c.scienceGrowthBonus + d).coerceAtMost(100))
            6 -> c.copy(globalScienceLevel = (c.globalScienceLevel + d).coerceAtMost(100))
            7 -> c.copy(scienceDemolition = (c.scienceDemolition + d).coerceAtMost(100))
            else -> c
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = KombinatFragment()
    }
}
