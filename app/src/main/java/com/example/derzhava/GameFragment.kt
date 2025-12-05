package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.BuildTaskDao
import com.example.derzhava.data.Buildings
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.MessageDao
import com.example.derzhava.data.MessageEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentGameBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color
import android.app.AlertDialog
import com.example.derzhava.data.BuildTaskEntity
import com.example.derzhava.ResourcesFragment
import com.example.derzhava.data.WarDao
import com.example.derzhava.data.WarEntity

// Импортируем новые экраны для профиля, статистики, генерала и лидеров
import com.example.derzhava.ProfileFragment
import com.example.derzhava.StatsFragment
// import com.example.derzhava.GeneralFragment // генерала открываем через командный центр
import com.example.derzhava.LeadersFragment
import com.example.derzhava.data.TrainingJobDao
import com.example.derzhava.data.TrainingJobEntity
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.CountryDao
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.derzhava.data.MarketDao
import com.example.derzhava.data.ProductionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.derzhava.net.OnlineCountrySync
import com.example.derzhava.net.OnlineArmySync
import com.example.derzhava.net.OnlineCommandCenterSync
import com.example.derzhava.net.OnlineGeneralSync
import android.widget.Toast
import android.view.MotionEvent



class GameFragment : Fragment() {

    private lateinit var marketDao: MarketDao      // ← ДОБАВЬ ЭТУ СТРОКУ
    private var autoRefreshJob: Job? = null   // ← вот ЭТО
    private lateinit var countryDao: CountryDao
    private lateinit var armyDao: ArmyDao
    private lateinit var trainingJobDao: TrainingJobDao
    private var currentWar: WarEntity? = null
    private lateinit var warDao: WarDao
    private var activeWars: List<WarEntity> = emptyList()
    private var currentTasks: List<BuildTaskEntity> = emptyList()
    private var defaultMessagesTextColor: Int? = null
    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!
    private lateinit var userRepository: UserRepository
    private lateinit var db: AppDatabase
    private lateinit var buildTaskDao: BuildTaskDao
    private lateinit var messageDao: MessageDao

    // Для обработки жеста «потянуть вниз для обновления» на игровом экране
    private var startYForRefresh: Float = 0f

    override fun onAttach(context: Context) {
        super.onAttach(context)
        userRepository = UserRepository(context)
        db = AppDatabase.getInstance(context)
        buildTaskDao = db.buildTaskDao()
        messageDao = db.messageDao()
        warDao = db.warDao()
        trainingJobDao = db.trainingJobDao()   // ← НОВОЕ
        countryDao = db.countryDao()      // ← добавили
        armyDao = db.armyDao()            // ← добавили
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Обработчик жеста «потянуть вниз для обновления». Если пользователь
        // тянет экран вниз на значительное расстояние, выполняем полное
        // обновление данных, аналогично нажатию на кнопку «Обновить».
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startYForRefresh = event.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - startYForRefresh
                    if (dy > 250) {
                        refreshGame()
                        // сбрасываем начальную точку, чтобы последующие смещения
                        // вызывали новое обновление только после следующего жеста
                        startYForRefresh = event.y
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден, вход...", Toast.LENGTH_SHORT)
                .show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        // Кнопку "Администрация" отображаем для всех игроков. Если игрок
        // является администратором, он получит доступ к функциям
        // администрирования, но даже обычные игроки смогут открыть
        // информационный раздел. Кнопка объявлена с android:visibility="gone"
        // в разметке, поэтому явно делаем её видимой и назначаем обработчик.
        binding.btnAdminPanel.visibility = View.VISIBLE
        binding.btnAdminPanel.setOnClickListener {
            openAdminPanel()
        }

        // Выполняем потенциально долгие операции (загрузка страны, обновление ресурсов)
        // в корутине, чтобы не блокировать главный поток. Все операции требуют связи с сервером.
        lifecycleScope.launch {
            try {
                val loadedCountry = withContext(Dispatchers.IO) {
                    var c = OnlineCountrySync.syncDownOrCreate(db, user.rulerName, user.countryName)
                    // ---- оффлайн‑производство ресурсов и прирост населения ----
                    val nowMillis = System.currentTimeMillis()
                    val processed = ProductionManager.applyHourlyProductionAndGrowth(c, nowMillis)
                    if (processed != c) {
                        db.countryDao().insertCountry(processed)
                        c = processed
                    }
                    return@withContext c
                }

                // Обновляем интерфейс уже в главном потоке
                bindCountry(loadedCountry)
                defaultMessagesTextColor = binding.btnRefresh.currentTextColor
                updateMessagesButton()          // сразу покажем актуальный счётчик
                updateConstructionInfo()
                setupClicks(loadedCountry)
                renderWarBlock()
            } catch (e: Exception) {
                // Если не удалось получить данные с сервера, возвращаем пользователя на экран входа
                Toast.makeText(requireContext(), "Сервер недоступен. Попробуйте позже", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.openLoginScreen()
                return@launch
            }
        }
    }

    /**
     * Обновляем основные блоки при возвращении на главный экран. Метод onViewCreated
     * вызывается только при первой загрузке фрагмента, поэтому после возврата
     * со screens строительства или войны данные могли устареть. В onResume
     * повторно вызываем методы, обновляющие состояние сообщений, стройки и войн.
     */
    override fun onResume() {
        super.onResume()
        // Не обновляем defaultMessagesTextColor здесь. Цвет кнопки сообщений
        // запоминается один раз в onViewCreated после первоначальной загрузки.
        // Если обновлять его при каждом возврате на экран, можно случайно
        // сохранить красный цвет как "по умолчанию" после непрочитанных
        // сообщений. Поэтому оставляем первоначальное значение.
        // Обновляем счётчик непрочитанных сообщений
        updateMessagesButton()
        // Перерисовываем блок стройки и таймеры
        updateConstructionInfo()
        // Перерисовываем блок войн
        renderWarBlock()
    }
    private fun renderWarBlock() {
        val user = userRepository.getUser() ?: run {
            binding.layoutWarBlock.visibility = View.GONE
            currentWar = null
            return
        }

        lifecycleScope.launch {
            // Все незавершённые войны правителя в фоновом потоке
            val wars = withContext(Dispatchers.IO) {
                warDao.getWarsForRuler(user.rulerName)
                    .filter { !it.isResolved }
            }

            if (wars.isEmpty()) {
                binding.layoutWarBlock.visibility = View.GONE
                currentWar = null
                return@launch
            }

            // Запомним "текущую" войну (если где-то ещё нужна)
            currentWar = wars.firstOrNull { it.state == "active" } ?: wars.first()

            binding.layoutWarBlock.visibility = View.VISIBLE

            // Список противников по всем войнам
            val enemyNames = wars.map { w ->
                if (w.attackerRuler == user.rulerName) {
                    w.defenderCountry
                } else {
                    w.attackerCountry
                }
            }.distinct()

            binding.tvWarTitle.text = if (enemyNames.size == 1) {
                "Война: ${enemyNames[0]}"
            } else {
                "Войны: " + enemyNames.joinToString(", ")
            }

            binding.tvWarSubtitle.text = if (wars.any { it.state == "active" }) {
                "Идут активные войны"
            } else {
                "Есть незавершённые войны"
            }

            // Клик по блоку войн
            binding.layoutWarBlock.setOnClickListener {
                if (wars.size == 1) {
                    // Одна война – сразу открываем её
                    openWarDetails(wars[0].id)
                } else {
                    // Несколько войн – даём выбрать
                    val items = wars.map { w ->
                        if (w.attackerRuler == user.rulerName) {
                            w.defenderCountry
                        } else {
                            w.attackerCountry
                        }
                    }.toTypedArray()

                    AlertDialog.Builder(requireContext())
                        .setTitle("Выберите войну")
                        .setItems(items) { _, which ->
                            val selectedWar = wars[which]
                            openWarDetails(selectedWar.id)
                        }
                        .show()
                }
            }
        }
    }

    /**
     * Переходит на экран администрирования игроков. Доступно только
     * администраторам. Новая транзакция добавляется в backstack.
     */
    private fun openAdminPanel() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AdminUsersFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    private fun updateMessagesButton() {
        val user = userRepository.getUser() ?: return
        val ruler = user.rulerName
        // Загружаем количество непрочитанных сообщений в фоне, чтобы не блокировать UI
        lifecycleScope.launch {
            // Считаем непрочитанные сообщения сами, т.к. сервер не поддерживает
            // пометку сообщений как прочитанных. Берём все сообщения и
            // фильтруем их по временной метке. В фоне, чтобы не блокировать UI.
            val (unreadCount, lastReadTs) = withContext(Dispatchers.IO) {
                val lastRead = userRepository.getLastMessagesReadTimestamp()
                val msgs = messageDao.getMessagesForRuler(ruler)
                Pair(msgs.count { it.timestampMillis > lastRead }, lastRead)
            }

            val btn = binding.btnRefresh
            if (unreadCount > 0) {
                btn.text = "Сообщения ($unreadCount)"
                btn.setTextColor(Color.RED)
            } else {
                btn.text = "Сообщения (0)"
                val defColor = defaultMessagesTextColor ?: btn.currentTextColor
                btn.setTextColor(defColor)
            }
        }
    }

    // ------------------ Привязка страны к экрану ------------------

    private fun bindCountry(country: CountryEntity) = with(binding) {
        tvCountryName.text = country.countryName
        tvRulerName.text = "Правитель (логин): ${country.rulerName}"

        tvGold.text = "Деньги [${country.money}]"
        tvFood.text = "Зерно: ${country.food}"
        tvWood.text = "Дерево: ${country.wood}"
        tvMetal.text = "Металл: ${country.metal}"

        // все ли здания построены?
        val allBuildingsBuilt =
            country.domik1 == 1 &&
                    country.domik2 == 1 &&
                    country.domik3 == 1 &&
                    country.domik4 == 1 &&
                    country.domik5 == 1 &&
                    country.domik6 == 1 &&
                    country.domik7 == 1

        // если всё построено — стройплощадку прячем
        btnBuildSite.visibility = if (allBuildingsBuilt) View.GONE else View.VISIBLE

        btnCommandCenter.visibility = if (country.domik3 == 1) View.VISIBLE else View.GONE
        btnBirzha.visibility       = if (country.domik6 == 1) View.VISIBLE else View.GONE
        btnWarBase.visibility      = if (country.domik4 == 1) View.VISIBLE else View.GONE
        btnKombinat.visibility     = if (country.domik1 == 1) View.VISIBLE else View.GONE
        btnTown.visibility         = if (country.domik2 == 1) View.VISIBLE else View.GONE
        btnPerimetr.visibility     = if (country.domik5 == 1) View.VISIBLE else View.GONE
        btnWatchTower.visibility   = if (country.domik7 == 1) View.VISIBLE else View.GONE

        // ---------- ТЕРРИТОРИЯ ----------
        val totalTerritory = country.land + country.lesa + country.shah + country.rudn + country.pole
        btnTerritory.text = "Территория [${country.land}/$totalTerritory]"
        // Показываем общее население: свободные рабочие + работающие + учёные + армия
        val productionWorkers = country.metallWorkers + country.mineWorkers +
                country.woodWorkers + country.industryWorkers
        val army = db.armyDao().getByRuler(country.rulerName)
        val infantry = army?.infantry ?: country.peh
        val cossacks = army?.cossacks ?: country.kaz
        val guards = army?.guards ?: country.gva
        val totalPopulation = country.workers + productionWorkers + country.bots + infantry + cossacks + guards
        btnPopulation.text = "Население [$totalPopulation]"
        btnResources.text =
            "Ресурсы [${country.metal + country.mineral + country.wood + country.food}]"
    }


    // ------------------ Стройка / проценты / завершение ------------------

    private fun updateConstructionInfo() {
        val user = userRepository.getUser() ?: return
        val ruler = user.rulerName

        // Выполняем работу с базой данных в фоновом потоке
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                var changed = false
                val now = System.currentTimeMillis()
                val tasks = buildTaskDao.getTasksForRuler(ruler)
                var country = db.countryDao().getCountryByRuler(ruler)
                if (country == null) {
                    // если вдруг страна отсутствует, вернуть пустые данные
                    return@withContext Quadruple(false, null, emptyList<BuildTaskEntity>(), emptyList<String>())
                }

                // Разделяем стройки: завершённые и активные. Завершённые сразу
                // убираем из списка для отображения и обновляем страну. Это
                // позволяет мгновенно скрывать завершённую стройку, не дожидаясь
                // повторной загрузки списка задач с сервера.
                val activeList = mutableListOf<BuildTaskEntity>()
                for (task in tasks) {
                    if (now >= task.endTimeMillis) {
                        // Стройка завершена: выставляем соответствующее здание и
                        // возвращаем рабочих. Id здания совпадает с domikX.
                        country = when (task.buildingType) {
                            Buildings.KOMBINAT       -> country!!.copy(domik1 = 1, workers = country!!.workers + task.workers)
                            Buildings.TOWN           -> country!!.copy(domik2 = 1, workers = country!!.workers + task.workers)
                            Buildings.COMMAND_CENTER -> country!!.copy(domik3 = 1, workers = country!!.workers + task.workers)
                            Buildings.WAR_BASE       -> country!!.copy(domik4 = 1, workers = country!!.workers + task.workers)
                            Buildings.PERIMETR       -> country!!.copy(domik5 = 1, workers = country!!.workers + task.workers)
                            Buildings.BIRZHA         -> country!!.copy(domik6 = 1, workers = country!!.workers + task.workers)
                            Buildings.WATCH_TOWER    -> country!!.copy(domik7 = 1, workers = country!!.workers + task.workers)
                            else -> country!!
                        }
                        // Удаляем задачу на сервере. Это происходит асинхронно, но UI
                        // уже не учитывает завершённую задачу.
                        buildTaskDao.deleteById(task.id)
                        changed = true
                    } else {
                        // Стройка ещё в процессе — оставляем её для отображения
                        activeList.add(task)
                    }
                }
                if (changed) {
                    db.countryDao().insertCountry(country!!)
                }
                // Подготовим строки только для активных строек
                val lines = activeList.map { task ->
                    val total = (task.endTimeMillis - task.startTimeMillis).coerceAtLeast(1L)
                    val done = (now - task.startTimeMillis).coerceAtLeast(0L)
                    val p = (done * 100 / total).toInt().coerceIn(0, 99)
                    val remaining = (task.endTimeMillis - now).coerceAtLeast(0L)
                    val remainingText = formatDurationShort(remaining)
                    "${Buildings.name(task.buildingType)} — ${p}% (осталось $remainingText)"
                }
                Quadruple(changed, country, activeList, lines)
            }

            val (changed, country, activeTasks, lines) = result
            if (changed && country != null) {
                bindCountry(country)
            }
            currentTasks = activeTasks
            if (activeTasks.isEmpty()) {
                binding.tvConstructionTitle.visibility = View.GONE
                binding.tvConstructionList.visibility = View.GONE
            } else {
                binding.tvConstructionTitle.visibility = View.VISIBLE
                binding.tvConstructionList.visibility = View.VISIBLE
                binding.tvConstructionList.text = lines.joinToString("\n")
                binding.tvConstructionList.setOnClickListener {
                    openWorkScreenChooser()
                }
            }
        }
    }

    /**
     * Вспомогательный тип для возврата нескольких значений из withContext
     */
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private fun formatDurationShort(millis: Long): String {
        var seconds = millis / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        val sec = seconds % 60

        return when {
            hours > 0  -> String.format("%dч %02dм", hours, minutes)
            minutes > 0 -> String.format("%dм %02dс", minutes, sec)
            else        -> String.format("%dс", sec)
        }
    }

    private fun openWorkScreenChooser() {
        if (currentTasks.isEmpty()) return

        if (currentTasks.size == 1) {
            // одна стройка – сразу в неё
            openWorkScreen(currentTasks[0].id)
            return
        }

        val names = currentTasks.map { Buildings.name(it.buildingType) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Выбери стройку")
            .setItems(names) { _, which ->
                val task = currentTasks.getOrNull(which) ?: return@setItems
                openWorkScreen(task.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openWorkScreen(taskId: Long) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BuildWorkFragment.newInstance(taskId))
            .addToBackStack(null)
            .commit()
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        val minutesOnly = minutes % 60

        return when {
            hours > 0 -> String.format("%d ч %02d мин", hours, minutesOnly)
            minutes > 0 -> String.format("%d мин %02d с", minutes, seconds)
            else -> String.format("%d с", seconds)
        }
    }

    private fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    // ------------------ Кнопки ------------------

    private fun setupClicks(country: CountryEntity) = with(binding) {

        // Кнопка в шапке — теперь "Сообщения"
        btnRefresh.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MessagesFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }

        btnAssembly.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AssemblyFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnProfileTop.setOnClickListener {
            // Открываем экран профиля вместо перезагрузки главной
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }
        btnStats.setOnClickListener {
            // Открываем экран общей статистики
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, StatsFragment())
                .addToBackStack(null)
                .commit()
        }

        btnBuildSite.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BuildFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnCommandCenter.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CommandCenterFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnBirzha.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BirzhaFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnWarBase.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WarBaseFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnKombinat.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, KombinatFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnTown.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TownFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnPerimetr.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PerimetrFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnWatchTower.setOnClickListener {
            toast("Сторожевая башня: разведка")
        }
        btnTerritory.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TerritoryFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnPopulation.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PopulationFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnResources.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ResourcesFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }

        btnClan.setOnClickListener {
            toast("Клановый центр в разработке")
        }
        btnAllClans.setOnClickListener {
            toast("Список всех кланов Державы")
        }
        btnLeaders.setOnClickListener {
            // Открываем экран лидеров игры
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LeadersFragment())
                .addToBackStack(null)
                .commit()
        }
        btnRules.setOnClickListener {
            toast("Правила игры в разработке")
        }
        btnDevArticle.setOnClickListener {
            toast("Статья по развитию в разработке")
        }
        btnRadio.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdminNpcFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        btnSatelliteMap.setOnClickListener {
            toast("Земля со спутника в разработке")
        }
        btnHallOfFame.setOnClickListener {
            toast("Зал славы турниров — позже")
        }
        btnWml.setOnClickListener {
            // При нажатии на кнопку "Профиль / Выход" сохраняем текущее состояние
            // страны на сервере, очищаем локальный профиль и возвращаемся на
            // экран авторизации. Это предотвращает переход в игру без
            // повторной авторизации и убирает оффлайн‑режим.
            lifecycleScope.launch {
                val user = userRepository.getUser()
                if (user != null) {
                    // Попытаться отправить состояние страны на сервер перед выходом.
                    // Если возникнет ошибка сети, её игнорируем — главное выйти
                    try {
                        // Сохраняем в фоновом потоке, чтобы не блокировать UI
                        withContext(Dispatchers.IO) {
                            OnlineCountrySync.syncUp(db, user.rulerName)
                        }
                    } catch (_: Exception) {
                        // Игнорируем
                    }
                }
                // Очищаем локальные данные пользователя
                userRepository.clearUser()
                // Возвращаемся на экран входа
                (activity as? MainActivity)?.openLoginScreen()
            }
        }
        btnVk.setOnClickListener {
            toast("Откроем группу ВК, когда добавим браузер/интент")
        }
        btnPortal.setOnClickListener {
            toast("Портал w-gp.ru — позже через браузер")
        }

        // Кнопка генерала больше не отображается в главном меню.
        // Тренировка генерала доступна через командный центр.
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun openWarDetails(warId: Long) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, WarDetailsFragment.newInstance(warId))
            .addToBackStack(null)
            .commit()
    }

    /**
     * Автодокатка обучения войск:
     * если есть training_job и его время уже вышло, то:
     *  - добавляем обученных солдат в армию;
     *  - возвращаем учёных;
     *  - удаляем job.
     * Вызывается при каждом заходе на главный экран.
     */
    private fun autoFinishTrainingIfNeeded() {
        val user = userRepository.getUser() ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Смотрим, есть ли незавершённое обучение у этого правителя
                val job = trainingJobDao.getJobForRuler(user.rulerName) ?: return@withContext null
                val endTimeMillis = job.startTimeMillis + job.durationSeconds * 1000L
                val now = System.currentTimeMillis()
                if (now < endTimeMillis) return@withContext null
                // Берём страну и армию
                val c = countryDao.getCountryByRuler(user.rulerName) ?: return@withContext null
                var a = armyDao.getByRuler(user.rulerName)
                if (a == null) {
                    a = ArmyState(rulerName = user.rulerName)
                    armyDao.insert(a)
                }
                // Учёные возвращаются
                val newBots = c.bots + job.scientists
                // Рабочие уже были списаны в момент старта обучения,
                // поэтому здесь мы просто прибавляем обученных бойцов,
                // рабочих НЕ трогаем.
                val newArmy = when (job.unitType) {
                    1 -> a!!.copy(infantry = a.infantry + job.workers)
                    2 -> a!!.copy(cossacks = a.cossacks + job.workers)
                    3 -> a!!.copy(guards = a.guards + job.workers)
                    else -> a!!
                }
                val newCountry = c.copy(bots = newBots)
                armyDao.insert(newArmy)
                countryDao.insertCountry(newCountry)
                trainingJobDao.delete(job)
                // подготовим текст сообщения и вернём его
                val trainedCount = job.workers
                val unitName = when (job.unitType) {
                    1 -> "пехоты"
                    2 -> "казаков"
                    3 -> "гвардии"
                    else -> "войск"
                }
                val now2 = System.currentTimeMillis()
                val msgText = "Обучение $trainedCount $unitName завершено. " +
                        "Новые войска уже добавлены в армию."
                val msgEntity = MessageEntity(
                    rulerName = user.rulerName,
                    text = msgText,
                    timestampMillis = now2
                )
                messageDao.insert(msgEntity)
                return@withContext msgEntity
            }
            // Если обучение завершилось, обновляем счётчик сообщений
            if (result != null) {
                updateMessagesButton()
            }
        }
    }

    private fun refreshGame() {
        val user = userRepository.getUser() ?: return
        lifecycleScope.launch {
            // Сначала завершаем обучение, если истекло время
            autoFinishTrainingIfNeeded()
            // Загружаем актуальные данные с сервера в фоне. Это позволит
            // подтянуть изменения, сделанные напрямую в базе данных,
            // и отразить их в игре без перезахода.
            val updated = withContext(Dispatchers.IO) {
                try {
                    // синхронизация страны, армии, командного центра и генерала
                    // Для страны используем syncDownOrCreate(), т.к. syncDown() в OnlineCountrySync
                    // отсутствует. Мы передаём имя страны из User, чтобы в случае отсутствия
                    // страны на сервере она была создана.
                    OnlineCountrySync.syncDownOrCreate(db, user.rulerName, user.countryName)
                    OnlineArmySync.syncDown(db, user.rulerName)
                    OnlineCommandCenterSync.syncDown(db, user.rulerName)
                    OnlineGeneralSync.syncDown(db, user.rulerName)
                } catch (_: Exception) {
                    // ignore network errors; we'll work with local data
                }
                // после синка возвращаем свежие данные
                countryDao.getCountryByRuler(user.rulerName)
            }
            updated?.let { c ->
                bindCountry(c)
                updateConstructionInfo()
                updateMessagesButton()
                renderWarBlock()
                processAllianceTimeouts(user.rulerName)
            }
        }
    }
    private fun processAllianceTimeouts(myRuler: String) {
        val now = System.currentTimeMillis()
        val allianceDao = AppDatabase.getInstance(requireContext()).allianceDao()
        val messageDao = AppDatabase.getInstance(requireContext()).messageDao()

        // все просроченные pending-союзы, где участвует текущий правитель
        val expired = allianceDao.getExpiredPendingForRuler(myRuler, now)

        for (al in expired) {
            // сообщение отправителю предложения
            if (al.initiator == myRuler) {
                val other = if (al.rulerA == myRuler) al.rulerB else al.rulerA
                messageDao.insert(
                    MessageEntity(
                        rulerName = myRuler,
                        text = "Время ожидания предложения союза государству $other вышло. Автоматически отменено предложение!",
                        timestampMillis = now,
                        isRead = false
                    )
                )
            }

            allianceDao.update(
                al.copy(
                    status = ALLIANCE_STATUS_EXPIRED,
                    respondedAt = now
                )
            )
        }
    }


   /* override fun onResume() {
        super.onResume()

        // Разовое обновление при входе
        refreshGame()

        // Запускаем авто-обновление. Интервал сокращён до 10 секунд,
        // чтобы изменения, внесённые напрямую в базу данных, быстрее
        // отражались в игре. Более частые обновления позволяют почти
        // моментально синхронизировать состояние игры с сервером.
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(10_000L)   // 10 секунд = 10 000 мс
                refreshGame()
            }
        }
    }*/
    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ALLIANCE_STATUS_EXPIRED = 4
        fun newInstance() = GameFragment()
    }
}
