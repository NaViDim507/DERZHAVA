package com.example.derzhava

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.BuildTaskDao
import com.example.derzhava.data.BuildTaskEntity
import com.example.derzhava.data.Buildings
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentBuildBinding
import com.example.derzhava.data.MessageDao
import com.example.derzhava.data.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class BuildFragment : Fragment() {

    private lateinit var messageDao: MessageDao
    private var _binding: FragmentBuildBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private lateinit var db: AppDatabase
    private lateinit var buildTaskDao: BuildTaskDao

    private var country: CountryEntity? = null
    private var currentRuler: String? = null
    private var currentTasks: List<BuildTaskEntity> = emptyList()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        userRepository = UserRepository(context)
        db = AppDatabase.getInstance(context)
        buildTaskDao = db.buildTaskDao()
        messageDao = db.messageDao()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuildBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        currentRuler = user.rulerName

        var c = db.countryDao().getCountryByRuler(user.rulerName)
        if (c == null) {
            c = CountryEntity(rulerName = user.rulerName, countryName = user.countryName)
            db.countryDao().insertCountry(c)
        }
        country = c

        // 1. Завершаем все стройки, которые уже успели закончиться
        completeFinishedTasks()

        // 2. Загружаем актуальный список задач
        currentTasks = loadTasks()

        // 3. Обновляем UI
        bindAll()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupBuildButtons()
    }

    // ---------- Работа с задачами ----------

    private fun loadTasks(): List<BuildTaskEntity> {
        val ruler = currentRuler ?: return emptyList()
        return buildTaskDao.getTasksForRuler(ruler)
    }

    /** Завершает все просроченные задачи: ставит domikX=1 и возвращает рабочих */
    private fun completeFinishedTasks() {
        val ruler = currentRuler ?: return
        val now = System.currentTimeMillis()
        val tasks = buildTaskDao.getTasksForRuler(ruler)
        var c = country ?: return
        var changed = false

        for (task in tasks) {
            if (now >= task.endTimeMillis) {
                // Здание считается построенным
                c = completeBuilding(c, task.buildingType, task.workers)
                // Удаляем задачу из очереди
                buildTaskDao.delete(task)
                // формируем текст сообщения
                val buildingName = Buildings.name(task.buildingType)
                val dateTime = formatDateTime(now)
                val messageText = "System: строительство \"$buildingName\" завершилось. " +
                        "Вам вернулось ${task.workers} рабочих. [$dateTime]"

                messageDao.insert(
                    MessageEntity(
                        rulerName = ruler,
                        text = messageText,
                        timestampMillis = now
                    )
                )
                changed = true
            }
        }

        if (changed) {
            country = c
            db.countryDao().insertCountry(c)
        }
    }
    private fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }
    /** Проставляем domikX = 1 + возвращаем рабочих */
    private fun completeBuilding(
        c: CountryEntity,
        buildingType: Int,
        workersToReturn: Int
    ): CountryEntity {
        val newWorkers = c.workers + workersToReturn
        return when (buildingType) {
            Buildings.KOMBINAT       -> c.copy(domik1 = 1, workers = newWorkers)
            Buildings.TOWN           -> c.copy(domik2 = 1, workers = newWorkers)
            Buildings.COMMAND_CENTER -> c.copy(domik3 = 1, workers = newWorkers)
            Buildings.WAR_BASE       -> c.copy(domik4 = 1, workers = newWorkers)
            Buildings.PERIMETR       -> c.copy(domik5 = 1, workers = newWorkers)
            Buildings.BIRZHA         -> c.copy(domik6 = 1, workers = newWorkers)
            Buildings.WATCH_TOWER    -> c.copy(domik7 = 1, workers = newWorkers)
            else                     -> c.copy(workers = newWorkers)
        }
    }

    private fun bindAll() {
        val c = country ?: return
        currentTasks = loadTasks()
        bindCountry(c)
    }

    private fun bindCountry(c: CountryEntity) = with(binding) {
        tvResources.text =
            "Ресурсы: Деньги ${c.money}, Дерево ${c.wood}, Металл ${c.metal}, Зерно ${c.food}\n" +
                    "Рабочие: ${c.workers}, Боты: ${c.bots}"

        val now = System.currentTimeMillis()
        val tasksByType = currentTasks.associateBy { it.buildingType }

        // Комбинат
        tvKombinatCost.text =
            "Стоимость: Дерево 800, Металл 600, Деньги 2000"
        val kombinatTask = tasksByType[Buildings.KOMBINAT]
        setBuildButtonState(
            buttonTextView = btnBuildKombinat,
            current = c.domik1,
            task = kombinatTask,
            canBuild = canBuildKombinat(c),
            baseText = "Комбинат",
            now = now
        )

        // Городок
        tvTownCost.text =
            "Стоимость: Дерево 600, Зерно 500, Деньги 1500"
        val townTask = tasksByType[Buildings.TOWN]
        setBuildButtonState(
            buttonTextView = btnBuildTown,
            current = c.domik2,
            task = townTask,
            canBuild = canBuildTown(c),
            baseText = "Городок",
            now = now
        )

        // Командный центр
        tvCommandCenterCost.text =
            "Стоимость: Металл 800, Деньги 3000 (требует Комбинат и Городок)"
        val commandTask = tasksByType[Buildings.COMMAND_CENTER]
        val commandPrereqOk = c.domik1 == 1 && c.domik2 == 1
        when {
            c.domik3 == 1 -> {
                btnBuildCommandCenter.text = "Командный центр — построено"
                btnBuildCommandCenter.isEnabled = false
            }

            !commandPrereqOk -> {
                btnBuildCommandCenter.text = "Сначала построй Комбинат и Городок"
                btnBuildCommandCenter.isEnabled = false
            }

            commandTask != null -> {
                val percent = calcPercent(commandTask, now)
                btnBuildCommandCenter.text =
                    "Строится (${percent}%) — раб: ${commandTask.workers}"
                btnBuildCommandCenter.isEnabled = false
            }

            canBuildCommandCenter(c) -> {
                btnBuildCommandCenter.text = "Построить Командный центр"
                btnBuildCommandCenter.isEnabled = true
            }

            else -> {
                btnBuildCommandCenter.text = "Не хватает ресурсов"
                btnBuildCommandCenter.isEnabled = false
            }
        }

        // Военная база
        tvWarBaseCost.text =
            "Стоимость: Металл 800, Дерево 400, Зерно 500, Деньги 2500 (требует Командный центр)"
        val warTask = tasksByType[Buildings.WAR_BASE]
        val warPrereqOk = c.domik3 == 1
        when {
            c.domik4 == 1 -> {
                btnBuildWarBase.text = "Военная база — построено"
                btnBuildWarBase.isEnabled = false
            }

            !warPrereqOk -> {
                btnBuildWarBase.text = "Сначала построй Командный центр"
                btnBuildWarBase.isEnabled = false
            }

            warTask != null -> {
                val percent = calcPercent(warTask, now)
                btnBuildWarBase.text =
                    "Строится (${percent}%) — раб: ${warTask.workers}"
                btnBuildWarBase.isEnabled = false
            }

            canBuildWarBase(c) -> {
                btnBuildWarBase.text = "Построить Военную базу"
                btnBuildWarBase.isEnabled = true
            }

            else -> {
                btnBuildWarBase.text = "Не хватает ресурсов"
                btnBuildWarBase.isEnabled = false
            }
        }

        // Периметр
        tvPerimetrCost.text =
            "Стоимость: Дерево 700, Металл 700, Деньги 3000 (требует Военную базу)"
        val perimTask = tasksByType[Buildings.PERIMETR]
        val perimPrereqOk = c.domik4 == 1
        when {
            c.domik5 == 1 -> {
                btnBuildPerimetr.text = "Периметр — построено"
                btnBuildPerimetr.isEnabled = false
            }

            !perimPrereqOk -> {
                btnBuildPerimetr.text = "Сначала построй Военную базу"
                btnBuildPerimetr.isEnabled = false
            }

            perimTask != null -> {
                val percent = calcPercent(perimTask, now)
                btnBuildPerimetr.text =
                    "Строится (${percent}%) — раб: ${perimTask.workers}"
                btnBuildPerimetr.isEnabled = false
            }

            canBuildPerimetr(c) -> {
                btnBuildPerimetr.text = "Построить Периметр"
                btnBuildPerimetr.isEnabled = true
            }

            else -> {
                btnBuildPerimetr.text = "Не хватает ресурсов"
                btnBuildPerimetr.isEnabled = false
            }
        }

        // Биржа
        tvBirzhaCost.text =
            "Стоимость: Деньги 4000, Дерево 300, Металл 300 (требует Комбинат и Городок)"
        val birzhaTask = tasksByType[Buildings.BIRZHA]
        val birzhaPrereqOk = c.domik1 == 1 && c.domik2 == 1
        when {
            c.domik6 == 1 -> {
                btnBuildBirzha.text = "Биржа — построено"
                btnBuildBirzha.isEnabled = false
            }

            !birzhaPrereqOk -> {
                btnBuildBirzha.text = "Сначала построй Комбинат и Городок"
                btnBuildBirzha.isEnabled = false
            }

            birzhaTask != null -> {
                val percent = calcPercent(birzhaTask, now)
                btnBuildBirzha.text =
                    "Строится (${percent}%) — раб: ${birzhaTask.workers}"
                btnBuildBirzha.isEnabled = false
            }

            canBuildBirzha(c) -> {
                btnBuildBirzha.text = "Построить Биржу"
                btnBuildBirzha.isEnabled = true
            }

            else -> {
                btnBuildBirzha.text = "Не хватает ресурсов"
                btnBuildBirzha.isEnabled = false
            }
        }

        // Сторожевая башня
        tvWatchTowerCost.text =
            "Стоимость: Дерево 400, Металл 400, Деньги 1500 (требует Периметр)"
        val towerTask = tasksByType[Buildings.WATCH_TOWER]
        val towerPrereqOk = c.domik5 == 1
        when {
            c.domik7 == 1 -> {
                btnBuildWatchTower.text = "Сторожевая башня — построено"
                btnBuildWatchTower.isEnabled = false
            }

            !towerPrereqOk -> {
                btnBuildWatchTower.text = "Сначала построй Периметр"
                btnBuildWatchTower.isEnabled = false
            }

            towerTask != null -> {
                val percent = calcPercent(towerTask, now)
                btnBuildWatchTower.text =
                    "Строится (${percent}%) — раб: ${towerTask.workers}"
                btnBuildWatchTower.isEnabled = false
            }

            canBuildWatchTower(c) -> {
                btnBuildWatchTower.text = "Построить Сторожевую башню"
                btnBuildWatchTower.isEnabled = true
            }

            else -> {
                btnBuildWatchTower.text = "Не хватает ресурсов"
                btnBuildWatchTower.isEnabled = false
            }
        }
    }

    private fun setBuildButtonState(
        buttonTextView: View,
        current: Int,
        task: BuildTaskEntity?,
        canBuild: Boolean,
        baseText: String,
        now: Long
    ) {
        val button = buttonTextView as? android.widget.Button ?: return

        when {
            current == 1 -> {
                button.text = "$baseText — построено"
                button.isEnabled = false
            }

            task != null -> {
                val percent = calcPercent(task, now)
                button.text = "Строится (${percent}%) — раб: ${task.workers}"
                button.isEnabled = false
            }

            canBuild -> {
                button.text = "Построить $baseText"
                button.isEnabled = true
            }

            else -> {
                button.text = "Не хватает ресурсов"
                button.isEnabled = false
            }
        }
    }

    private fun calcPercent(task: BuildTaskEntity, now: Long): Int {
        val total = (task.endTimeMillis - task.startTimeMillis).coerceAtLeast(1L)
        val done = (now - task.startTimeMillis).coerceAtLeast(0L)
        val p = (done * 100 / total).toInt()
        return p.coerceIn(0, 99)
    }

    // ---------- проверки ресурсов ----------

    private fun canBuildKombinat(c: CountryEntity) =
        c.domik1 == 0 && c.wood >= 800 && c.metal >= 600 && c.money >= 2000 && c.workers > 0

    private fun canBuildTown(c: CountryEntity) =
        c.domik2 == 0 && c.wood >= 600 && c.food >= 500 && c.money >= 1500 && c.workers > 0

    private fun canBuildCommandCenter(c: CountryEntity) =
        c.domik3 == 0 && c.metal >= 800 && c.money >= 3000 && c.workers > 0

    private fun canBuildWarBase(c: CountryEntity) =
        c.domik4 == 0 && c.metal >= 800 && c.wood >= 400 && c.food >= 500 &&
                c.money >= 2500 && c.workers > 0

    private fun canBuildPerimetr(c: CountryEntity) =
        c.domik5 == 0 && c.wood >= 700 && c.metal >= 700 &&
                c.money >= 3000 && c.workers > 0

    private fun canBuildBirzha(c: CountryEntity) =
        c.domik6 == 0 && c.money >= 4000 && c.wood >= 300 &&
                c.metal >= 300 && c.workers > 0

    private fun canBuildWatchTower(c: CountryEntity) =
        c.domik7 == 0 && c.wood >= 400 && c.metal >= 400 &&
                c.money >= 1500 && c.workers > 0

    // ---------- кнопки ----------

    private fun setupBuildButtons() = with(binding) {
        btnBuildKombinat.setOnClickListener { promptWorkersAndStart(Buildings.KOMBINAT) }
        btnBuildTown.setOnClickListener { promptWorkersAndStart(Buildings.TOWN) }
        btnBuildCommandCenter.setOnClickListener { promptWorkersAndStart(Buildings.COMMAND_CENTER) }
        btnBuildWarBase.setOnClickListener { promptWorkersAndStart(Buildings.WAR_BASE) }
        btnBuildPerimetr.setOnClickListener { promptWorkersAndStart(Buildings.PERIMETR) }
        btnBuildBirzha.setOnClickListener { promptWorkersAndStart(Buildings.BIRZHA) }
        btnBuildWatchTower.setOnClickListener { promptWorkersAndStart(Buildings.WATCH_TOWER) }
    }

    /** Диалог: сколько рабочих отправить */
    private fun promptWorkersAndStart(buildingType: Int) {
        val c = country ?: return

        if (c.workers <= 0) {
            toast("Нет свободных рабочих")
            return
        }

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "От 1 до ${c.workers}"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Сколько рабочих отправить?")
            .setView(input)
            .setPositiveButton("Начать") { _, _ ->
                val text = input.text.toString()
                val workers = text.toIntOrNull()
                if (workers == null || workers <= 0) {
                    toast("Введите число рабочих")
                    return@setPositiveButton
                }
                if (workers > (country?.workers ?: 0)) {
                    toast("Столько рабочих нет")
                    return@setPositiveButton
                }
                startBuild(buildingType, workers)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Старт стройки: списываем ресурсы + рабочих, создаём задачу */
    private fun startBuild(buildingType: Int, workers: Int) {
        val c = country ?: return
        val ruler = currentRuler ?: return

        if (workers <= 0 || workers > c.workers) {
            toast("Неверное количество рабочих")
            return
        }

        val canBuild = when (buildingType) {
            Buildings.KOMBINAT       -> canBuildKombinat(c)
            Buildings.TOWN           -> canBuildTown(c)
            Buildings.COMMAND_CENTER -> canBuildCommandCenter(c) && c.domik1 == 1 && c.domik2 == 1
            Buildings.WAR_BASE       -> canBuildWarBase(c) && c.domik3 == 1
            Buildings.PERIMETR       -> canBuildPerimetr(c) && c.domik4 == 1
            Buildings.BIRZHA         -> canBuildBirzha(c) && c.domik1 == 1 && c.domik2 == 1
            Buildings.WATCH_TOWER    -> canBuildWatchTower(c) && c.domik5 == 1
            else                     -> false
        }

        if (!canBuild) {
            toast("Недостаточно ресурсов или не выполнены условия")
            return
        }

        // Списываем ресурсы И рабочих (как rab -= z5)
        // Списываем ресурсы И рабочих (как rab -= z5)
        val cost = Buildings.cost(buildingType)

        val updated = c.copy(
            money  = c.money  - cost.money,
            food   = c.food   - cost.food,
            wood   = c.wood   - cost.wood,
            metal  = c.metal  - cost.metal,
            workers = c.workers - workers
        )

        // Сохраняем обновлённые локальные ресурсы, но не отправляем на сервер до
        // успешного создания задачи. Если сначала сделать saveCountry(), а потом
        // buildTaskDao.insert(), то ресурсы будут списаны дважды — через
        // сохранение страны и через build_task_add.php. Поэтому сначала
        // отправляем задачу на сервер, который спишет ресурсы и рабочих, затем
        // обновляем локальное состояние и отправляем его на сервер.

        val now = System.currentTimeMillis()
        val duration = Buildings.calcDurationMillis(buildingType, workers)

        val task = BuildTaskEntity(
            rulerName = ruler,
            buildingType = buildingType,
            workers = workers,
            startTimeMillis = now,
            endTimeMillis = now + duration
        )

        // Отправляем задачу на сервер. Сервер сам спишет ресурсы и рабочих.
        buildTaskDao.insert(task)

        // Обновляем локальное состояние: вычитаем ресурсы и рабочих,
        // чтобы отобразить актуальные данные. Это состояние соответствует
        // серверному после успешного добавления задачи.
        country = updated
        db.countryDao().insertCountry(updated)

        toast("Строительство: ${Buildings.name(buildingType)} начато (${workers} раб.)")
        // Отправляем системное сообщение о начале строительства
        run {
            val nowMsg = System.currentTimeMillis()
            val msgText =
                "Строительство ${Buildings.name(buildingType)} начато (${workers} раб.)"
            messageDao.insert(
                MessageEntity(
                    rulerName = c.rulerName,
                    text = msgText,
                    timestampMillis = nowMsg,
                    isRead = false
                )
            )
        }

        // Обновляем UI
        completeFinishedTasks()
        bindAll()
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = BuildFragment()
    }
}
