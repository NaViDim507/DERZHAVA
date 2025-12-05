package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.GeneralDao
import com.example.derzhava.data.GeneralState
import com.example.derzhava.data.UserRepository
import com.example.derzhava.data.WarDao
import com.example.derzhava.data.WarEntity
import com.example.derzhava.data.WarLogDao
import com.example.derzhava.data.WarLogEntity
import com.example.derzhava.data.WarMoveDao
import com.example.derzhava.data.WarMoveEntity
import com.example.derzhava.databinding.FragmentWarDetailsBinding
import com.example.derzhava.net.ApiClient
import com.example.derzhava.net.ReconBuildingDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class WarDetailsFragment : Fragment() {

    private var _binding: FragmentWarDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository

    private lateinit var armyDao: ArmyDao
    private lateinit var warDao: WarDao
    private lateinit var warMoveDao: WarMoveDao
    private lateinit var warLogDao: WarLogDao
    private lateinit var generalDao: GeneralDao
    private lateinit var countryDao: CountryDao

    private var myRuler: String = ""
    private var warId: Long = 0L

    // Текущая война на экране (локальный кэш)
    private var currentWar: WarEntity? = null

    /**
     * Управляет видимостью индикатора загрузки на экране. Когда
     * [show] == true, отображаем прогресс‑бар и прячем действия,
     * чтобы пользователь видел, что идёт обработка запроса. Когда
     * [show] == false, скрываем индикатор. Если прогресс‑бар отсутствует
     * в разметке, метод ничего не делает.
     */
    private fun showLoading(show: Boolean) {
        // progressBar может быть null, если его нет в разметке
        val progress = _binding?.progressBar
        progress?.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)
        warDao = db.warDao()
        warMoveDao = db.warMoveDao()
        warLogDao = db.warLogDao()
        armyDao = db.armyDao()
        generalDao = db.generalDao()
        countryDao = db.countryDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        warId = arguments?.getLong(ARG_WAR_ID) ?: 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWarDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = userRepository.getUser()
        if (user == null) {
            toast("Профиль не найден.")
            parentFragmentManager.popBackStack()
            return
        }
        myRuler = user.rulerName

        // Загружаем локальную войну
        loadWar()

        // Старые отдельные кнопки рейдов больше не используем
        binding.btnRaidTown.visibility = View.GONE
        binding.btnRaidBirzha.visibility = View.GONE
        binding.btnRaidCc.visibility = View.GONE

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // --- Разведка / разрушение (онлайн) ---
        binding.btnRecon.setOnClickListener {
            currentWar?.let { w -> doReconOnline(w) }
        }

        // --- Подкрепление (онлайн + локальный апдейт) ---
        binding.btnReinforce.setOnClickListener {
            currentWar?.let { w -> showReinforceDialogOnline(w) }
        }

        // --- Отзыв войск (онлайн + локальный апдейт) ---
        binding.btnRecall.setOnClickListener {
            currentWar?.let { w -> showRecallDialogOnline(w) }
        }

        // --- Захват (локальная формула der1 + sync на сервер) ---
        binding.btnCapture.setOnClickListener {
            currentWar?.let { w -> doCapture(w) }
        }

        // Защита: отправить войска для отражения вторжения
        binding.btnDefense.setOnClickListener {
            currentWar?.let { w -> showDefenseDialogOnline(w) }
        }
    }

    // =========================================================
    //   ЗАГРУЗКА И ОТРИСОВКА
    // =========================================================

    private fun loadWar() {
        // В онлайн‑режиме warDao.getById() всегда возвращает null. Поэтому
        // сначала пробуем стандартное получение. Если результат null,
        // пытаемся найти войну в списке войн текущего правителя.
        var w: WarEntity? = warDao.getById(warId)
        if (w == null) {
            // Пробуем загрузить все войны для текущего правителя и найти нужную.
            // Это может выбросить исключение при ошибке сети, которую игнорируем.
            try {
                val wars = warDao.getWarsForRuler(myRuler)
                w = wars.firstOrNull { it.id == warId }
            } catch (_: Exception) {
                // ошибка сети — ничего не делаем, w останется null
            }
        }
        if (w == null) {
            toast("Война не найдена.")
            parentFragmentManager.popBackStack()
            return
        }
        currentWar = w
        renderWar(w)
        // В онлайн‑режиме локальный WarMoveDao/WarLogDao возвращают пустые списки.
        // Это не блокирует работу — просто списки ходов и логов будут пустыми.
        renderMoves(w.id)
        renderLogs(w.id)
    }

    private fun renderWar(w: WarEntity) {
        binding.tvTitle.text = "Война #${w.id}"

        val side = when (myRuler) {
            w.attackerRuler -> "Ты — атакующий (${w.attackerCountry}) против ${w.defenderCountry}"
            w.defenderRuler -> "Ты — обороняющийся (${w.defenderCountry}) против ${w.attackerCountry}"
            else -> "Сторона в войне не определена"
        }
        binding.tvSide.text = side

        val armyInfo = """
            Пехота: ${w.peh}
            Казаки: ${w.kaz}
            Гвардия: ${w.gva}
        """.trimIndent()
        binding.tvArmy.text = armyInfo

        val now = System.currentTimeMillis()
        val captureReady = now >= w.canCaptureAt

        binding.tvTimers.text = buildString {
            append("Разведка/рейды: тайминги по серверу\n")
            append("Захват: ")
            append(if (captureReady) "можно пробовать захват" else "пока рано")
        }

        binding.tvReconAcc.text = "Точность разведки: ${w.reconAcc}%"

        val stateText = when {
            w.state == "active" && !w.isResolved -> "Война идёт"
            w.state == "recalled" -> "Войска отозваны"
            w.state == "captured" && w.attackerWon == true -> "Страна захвачена атакующим"
            w.state == "failed" && w.attackerWon == false -> "Атака провалена"
            w.isResolved && w.attackerWon == true -> "Война завершена: победа атакующего"
            w.isResolved && w.attackerWon == false -> "Война завершена: победа обороны"
            else -> w.state
        }
        binding.tvState.text = stateText

        val controlsEnabled = w.state == "active" && !w.isResolved
        binding.btnRecon.isEnabled = controlsEnabled
        // Подкрепление и отзыв доступны только атакующему
        binding.btnReinforce.isEnabled = controlsEnabled && myRuler == w.attackerRuler
        binding.btnRecall.isEnabled = controlsEnabled && myRuler == w.attackerRuler
        binding.btnCapture.isEnabled = controlsEnabled && myRuler == w.attackerRuler && captureReady
        // Кнопка обороны (defense): видна и активна только для защитника
        if (controlsEnabled && myRuler == w.defenderRuler) {
            binding.btnDefense.visibility = View.VISIBLE
            binding.btnDefense.isEnabled = true
        } else {
            binding.btnDefense.visibility = View.GONE
            binding.btnDefense.isEnabled = false
        }
    }

    private fun renderMoves(warId: Long) {
        val moves = warMoveDao.byWar(warId)
        if (moves.isEmpty()) {
            binding.tvMoves.text = "Переброски войск пока не было."
            return
        }
        val sb = StringBuilder()
        moves.forEach { m ->
            val action = when (m.type) {
                "reinforce" -> "подкрепление"
                "recall" -> "отзыв"
                else -> m.type
            }
            sb.append("t=${m.ts}: $action, пех: ${m.peh}, каз: ${m.kaz}, гвард: ${m.gva}\n")
        }
        binding.tvMoves.text = sb.toString()
    }

    private fun renderLogs(warId: Long) {
        val logs = warLogDao.byWar(warId)
        if (logs.isEmpty()) {
            binding.tvLogs.text = "Журнал боевых действий пуст."
            return
        }
        val sb = StringBuilder()
        logs.forEach { l ->
            val line = when (l.type) {
                "recon"        -> "Разведка (${l.payload})"
                "raid_town"    -> "Рейд: городок (${l.payload})"
                "raid_birzha"  -> "Рейд: биржа (${l.payload})"
                "raid_cc"      -> "Рейд: командный центр (${l.payload})"
                "raid_building"-> "Рейд по зданию (${l.payload})"
                "capture_ok"   -> "Захват успешен (${l.payload})"
                "capture_fail" -> "Захват провален (${l.payload})"
                else           -> "${l.type} (${l.payload})"
            }
            sb.append("t=${l.ts}: $line\n")
        }
        binding.tvLogs.text = sb.toString()
    }

    // =========================================================
    //   РАЗВЕДКА + РАЗРУШЕНИЕ (ОНЛАЙН)
    // =========================================================

    private fun doReconOnline(w: WarEntity) {
        if (w.state != "active" || w.isResolved) {
            toast("Война уже завершена.")
            return
        }

        if (w.attackerRuler != myRuler) {
            toast("Разведку и разрушение зданий может проводить только нападающая сторона.")
            return
        }

        // Локально чуть поднимаем точность разведки
        val newAcc = (w.reconAcc + 10).coerceAtMost(100)
        val warWithAcc = if (newAcc != w.reconAcc) {
            val updated = w.copy(reconAcc = newAcc)
            warDao.update(updated)
            currentWar = updated
            renderWar(updated)
            updated
        } else {
            w
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // показываем индикатор загрузки на время выполнения сети
            showLoading(true)
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.warRecon(
                        warId = warWithAcc.id, // на следующем этапе сюда пойдёт remoteWarId
                        attackerRuler = myRuler
                    )
                }

                if (!resp.success) {
                    toast(resp.message ?: "Разведка не удалась")
                    return@launch
                }

                val buildings = resp.buildings
                if (buildings.isNullOrEmpty()) {
                    toast("Ничего интересного не обнаружено.")
                    return@launch
                }

                showBuildingsDialogFromApi(warWithAcc, buildings)
            } catch (e: Exception) {
                toast("Ошибка сети: ${e.localizedMessage ?: "разведка не удалась"}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showBuildingsDialogFromApi(
        w: WarEntity,
        buildings: List<ReconBuildingDto>
    ) {
        val available = buildings.filter { it.exists && it.canDemolish }
        if (available.isEmpty()) {
            toast("У противника нет зданий, которые можно разрушить.")
            return
        }

        val titles = available.map { it.name }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Разведка: здания противника")
            .setItems(titles) { _, which ->
                val b = available[which]
                confirmDestroyBuildingOnline(w.id, b)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDestroyBuildingOnline(
        warId: Long,
        building: ReconBuildingDto
    ) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Разрушить ${building.name}?")
            .setMessage(
                "Здание будет уничтожено. " +
                        "Противник потеряет его и ${building.rewardPercent ?: 0}% " +
                        "ресурсов типа ${building.rewardType ?: "-"}, а ты их получишь."
            )
            .setPositiveButton("Разрушить") { _, _ ->
                doBuildingRaidOnline(warId, building)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun doBuildingRaidOnline(
        warId: Long,
        building: ReconBuildingDto
    ) {
        val w = currentWar ?: return

        if (w.state != "active" || w.isResolved) {
            toast("Война уже завершена.")
            return
        }

        if (w.attackerRuler != myRuler) {
            toast("Разрушать здания может только нападающая сторона.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // показываем индикатор загрузки на время выполнения сети
            showLoading(true)
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.warDemolish(
                        warId = warId, // позже здесь тоже будет remoteWarId
                        attackerRuler = myRuler,
                        buildingKey = building.key
                    )
                }

                toast(resp.message ?: "Запрос выполнен")

                if (resp.success) {
                    val now = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        warLogDao.insert(
                            WarLogEntity(
                                warId = warId,
                                ts = now,
                                type = "raid_building",
                                payload = "{\"key\":\"${building.key}\",\"name\":\"${building.name}\"}"
                            )
                        )
                    }
                    renderLogs(warId)
                }
            } catch (e: Exception) {
                toast("Ошибка сети: ${e.localizedMessage ?: "разрушение не удалось"}")
            } finally {
                showLoading(false)
            }
        }
    }

    // =========================================================
    //   ПОДКРЕПЛЕНИЕ (ОНЛАЙН + ЛОКАЛЬНО)
    // =========================================================

    private fun showReinforceDialogOnline(w: WarEntity) {
        val ctx = requireContext()
        val myArmy = armyDao.getByRuler(myRuler) ?: ArmyState(rulerName = myRuler)

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        fun addRow(label: String, current: Int): EditText {
            val tv = TextView(ctx).apply {
                text = "$label (есть: $current)"
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText("0")
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etPeh = addRow("Пехота", myArmy.infantry)
        val etKaz = addRow("Казаки", myArmy.cossacks)
        val etGva = addRow("Гвардия", myArmy.guards)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Отправить подкрепление")
            .setView(layout)
            .setPositiveButton("Отправить") { _, _ ->
                val pehToSend = etPeh.text.toString().toIntOrNull() ?: 0
                val kazToSend = etKaz.text.toString().toIntOrNull() ?: 0
                val gvaToSend = etGva.text.toString().toIntOrNull() ?: 0

                doReinforceOnline(w, myArmy, pehToSend, kazToSend, gvaToSend)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun doReinforceOnline(
        w: WarEntity,
        myArmy: ArmyState,
        pehToSend: Int,
        kazToSend: Int,
        gvaToSend: Int
    ) {
        if (pehToSend < 0 || kazToSend < 0 || gvaToSend < 0) {
            toast("Нельзя отправить отрицательное количество.")
            return
        }

        if (pehToSend > myArmy.infantry ||
            kazToSend > myArmy.cossacks ||
            gvaToSend > myArmy.guards
        ) {
            toast("Недостаточно войск в резерве.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // показываем индикатор загрузки на время выполнения сети
            showLoading(true)
            try {
                // 1) Онлайн: отправляем запрос на сервер
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.sendReinforcements(
                        warId = w.id, // позже сюда пойдёт remoteWarId
                        attackerRuler = myRuler,
                        peh = pehToSend,
                        kaz = kazToSend,
                        gva = gvaToSend
                    )
                }

                if (!resp.success) {
                    toast(resp.message ?: "Не удалось отправить подкрепление")
                    return@launch
                }

                // 2) Локально зеркалим изменения
                val updatedWar = w.copy(
                    peh = w.peh + pehToSend,
                    kaz = w.kaz + kazToSend,
                    gva = w.gva + gvaToSend
                )

                val newArmy = myArmy.copy(
                    infantry = myArmy.infantry - pehToSend,
                    cossacks = myArmy.cossacks - kazToSend,
                    guards = myArmy.guards - gvaToSend
                )

                val move = WarMoveEntity(
                    warId = w.id,
                    type = "reinforce",
                    ts = System.currentTimeMillis(),
                    peh = pehToSend,
                    kaz = kazToSend,
                    gva = gvaToSend
                )

                withContext(Dispatchers.IO) {
                    warDao.update(updatedWar)
                    armyDao.insert(newArmy)
                    warMoveDao.insert(move)
                }

                currentWar = updatedWar
                renderWar(updatedWar)
                renderMoves(w.id)

                toast(resp.message ?: "Подкрепление отправлено.")
            } catch (e: Exception) {
                toast("Ошибка сети: ${e.localizedMessage ?: "подкрепление не удалось"}")
            } finally {
                showLoading(false)
            }
        }
    }

    // =========================================================
    //   ОТРАЖЕНИЕ АТАКИ (ОБОРОНА)
    // =========================================================

    /**
     * Показывает диалог ввода количества войск для отражения вторжения. Доступно только для обороняющейся стороны.
     */
    private fun showDefenseDialogOnline(w: WarEntity) {
        val ctx = requireContext()
        // Получаем текущее состояние армии защитника
        val myArmy = armyDao.getByRuler(myRuler) ?: ArmyState(rulerName = myRuler)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }
        fun addRow(label: String, current: Int): EditText {
            val tv = TextView(ctx).apply {
                text = "$label (есть: $current)"
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText("0")
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }
        val etPeh = addRow("Пехота", myArmy.infantry)
        val etKaz = addRow("Казаки", myArmy.cossacks)
        val etGva = addRow("Гвардия", myArmy.guards)
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Отразить атаку")
            .setView(layout)
            .setPositiveButton("Отправить") { _, _ ->
                val pehToSend = etPeh.text.toString().toIntOrNull() ?: 0
                val kazToSend = etKaz.text.toString().toIntOrNull() ?: 0
                val gvaToSend = etGva.text.toString().toIntOrNull() ?: 0
                doDefenseOnline(w, myArmy, pehToSend, kazToSend, gvaToSend)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Выполняет обращение к серверу для отражения атаки. Обновляет локальное состояние войны и армии.
     */
    private fun doDefenseOnline(
        w: WarEntity,
        myArmy: ArmyState,
        pehToSend: Int,
        kazToSend: Int,
        gvaToSend: Int
    ) {
        if (pehToSend < 0 || kazToSend < 0 || gvaToSend < 0) {
            toast("Нельзя отправить отрицательное количество.")
            return
        }
        if (pehToSend + kazToSend + gvaToSend <= 0) {
            toast("Нужно отправить хотя бы одного бойца.")
            return
        }
        if (pehToSend > myArmy.infantry || kazToSend > myArmy.cossacks || gvaToSend > myArmy.guards) {
            toast("Недостаточно войск в резерве.")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // показываем индикатор загрузки на время выполнения сети
            showLoading(true)
            try {
                // Отправляем запрос на сервер
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.warDefense(
                        warId = w.id,
                        defenderRuler = myRuler,
                        peh = pehToSend,
                        kaz = kazToSend,
                        gva = gvaToSend
                    )
                }
                if (!resp.success) {
                    toast(resp.message ?: "Не удалось отразить атаку")
                    return@launch
                }
                // 1) Списываем отправленные войска из локальной армии
                val newArmy = myArmy.copy(
                    infantry = max(0, myArmy.infantry - pehToSend),
                    cossacks = max(0, myArmy.cossacks - kazToSend),
                    guards = max(0, myArmy.guards - gvaToSend)
                )
                withContext(Dispatchers.IO) {
                    armyDao.insert(newArmy)
                }
                // 2) Обновляем локальное состояние войны в зависимости от ответа сервера
                // Для обороны сервер возвращает WarDefenseResponse, в котором поля state, attackerPe h,
                // attackerKaz, attackerGva и attackerWon описывают новое состояние войны и остатки
                // войск атакующего. Эти поля могут быть null, если сервер не менял соответствующее
                // значение. Старое обращение через resp.data? использовало Map, что больше не
                // применяется для нового типа ответа.
                val newState = resp.state
                val attackerWonFlag = resp.attackerWon
                val newPeh = resp.attackerPeh
                val newKaz = resp.attackerKaz
                val newGva = resp.attackerGva
                var updatedWar = w
                if (newState != null) {
                    updatedWar = updatedWar.copy(state = newState)
                }
                if (attackerWonFlag != null) {
                    updatedWar = updatedWar.copy(attackerWon = (attackerWonFlag == 1), isResolved = true)
                }
                if (newPeh != null) {
                    updatedWar = updatedWar.copy(peh = newPeh)
                }
                if (newKaz != null) {
                    updatedWar = updatedWar.copy(kaz = newKaz)
                }
                if (newGva != null) {
                    updatedWar = updatedWar.copy(gva = newGva)
                }
                withContext(Dispatchers.IO) {
                    warDao.update(updatedWar)
                }
                currentWar = updatedWar
                renderWar(updatedWar)
                // Логи и ходы можно перезагрузить
                renderMoves(updatedWar.id)
                renderLogs(updatedWar.id)
            } catch (e: Exception) {
                toast("Не удалось отправить войска на оборону: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    // =========================================================
    //   ОТЗЫВ ВОЙСК (ОНЛАЙН + ЛОКАЛЬНО)
    // =========================================================

    private fun showRecallDialogOnline(w: WarEntity) {
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        fun addRow(label: String, current: Int): EditText {
            val tv = TextView(ctx).apply {
                text = "$label (в войне: $current)"
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText("0")
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etPeh = addRow("Пехота", w.peh)
        val etKaz = addRow("Казаки", w.kaz)
        val etGva = addRow("Гвардия", w.gva)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Отозвать войска")
            .setView(layout)
            .setPositiveButton("Отозвать") { _, _ ->
                val pehBack = etPeh.text.toString().toIntOrNull() ?: 0
                val kazBack = etKaz.text.toString().toIntOrNull() ?: 0
                val gvaBack = etGva.text.toString().toIntOrNull() ?: 0

                doRecallOnline(w, pehBack, kazBack, gvaBack)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun doRecallOnline(
        w: WarEntity,
        pehBack: Int,
        kazBack: Int,
        gvaBack: Int
    ) {
        if (pehBack < 0 || kazBack < 0 || gvaBack < 0) {
            toast("Нельзя отозвать отрицательное количество.")
            return
        }
        if (pehBack > w.peh || kazBack > w.kaz || gvaBack > w.gva) {
            toast("Столько войск на территории нет.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // показываем индикатор загрузки на время выполнения сети
            showLoading(true)
            try {
                // 1) Онлайн-отзыв на сервере
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.recallTroops(
                        warId = w.id, // потом здесь будет remoteWarId
                        attackerRuler = myRuler,
                        peh = pehBack,
                        kaz = kazBack,
                        gva = gvaBack
                    )
                }

                if (!resp.success) {
                    toast(resp.message ?: "Не удалось отозвать войска")
                    return@launch
                }

                // 2) Локально зеркалим
                val myArmy = armyDao.getByRuler(myRuler) ?: ArmyState(rulerName = myRuler)

                val newPeh = w.peh - pehBack
                val newKaz = w.kaz - kazBack
                val newGva = w.gva - gvaBack

                val updatedWar = w.copy(
                    peh = newPeh,
                    kaz = newKaz,
                    gva = newGva,
                    state = if (newPeh <= 0 && newKaz <= 0 && newGva <= 0) "recalled" else w.state,
                    isResolved = if (newPeh <= 0 && newKaz <= 0 && newGva <= 0) true else w.isResolved
                )

                val updatedArmy = myArmy.copy(
                    infantry = myArmy.infantry + pehBack,
                    cossacks = myArmy.cossacks + kazBack,
                    guards = myArmy.guards + gvaBack
                )

                val move = WarMoveEntity(
                    warId = w.id,
                    type = "recall",
                    ts = System.currentTimeMillis(),
                    peh = pehBack,
                    kaz = kazBack,
                    gva = gvaBack
                )

                withContext(Dispatchers.IO) {
                    warDao.update(updatedWar)
                    armyDao.insert(updatedArmy)
                    warMoveDao.insert(move)
                }

                currentWar = updatedWar
                renderWar(updatedWar)
                renderMoves(w.id)

                toast(resp.message ?: "Войска отозваны.")
            } catch (e: Exception) {
                toast("Ошибка сети: ${e.localizedMessage ?: "отзыв не удался"}")
            } finally {
                showLoading(false)
            }
        }
    }

    // =========================================================
    //   ЗАХВАТ (боёвка der1 + sync на сервер)
    // =========================================================

    private fun doCapture(w: WarEntity) {
        val ctx = requireContext()
        val now = System.currentTimeMillis()

        if (w.state != "active" || w.isResolved) {
            Toast.makeText(ctx, "Война уже завершена.", Toast.LENGTH_SHORT).show()
            return
        }

        if (w.attackerRuler != myRuler) {
            Toast.makeText(ctx, "Захват может инициировать только нападающая сторона.", Toast.LENGTH_SHORT).show()
            return
        }

        if (now < w.canCaptureAt) {
            Toast.makeText(ctx, "До захвата ещё рано.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Армии и генералы обеих сторон
            val attackerArmy =
                armyDao.getByRuler(w.attackerRuler) ?: ArmyState(rulerName = w.attackerRuler)

            val atkGen = generalDao.getByRuler(w.attackerRuler)
                ?: GeneralState(rulerName = w.attackerRuler).also {
                    generalDao.insert(it)
                }

            val defArmy = armyDao.getByRuler(w.defenderRuler)
                ?: ArmyState(rulerName = w.defenderRuler).also {
                    armyDao.insert(it)
                }

            val defGen = generalDao.getByRuler(w.defenderRuler)
                ?: GeneralState(rulerName = w.defenderRuler).also {
                    generalDao.insert(it)
                }

            val atkTotal = w.peh + w.kaz + w.gva
            val defTotal = defArmy.infantry + defArmy.cossacks + defArmy.guards

            // 1) у атакующего должны быть войска на фронте
            if (atkTotal <= 0) {
                Toast.makeText(
                    ctx,
                    "Для захвата у тебя должны быть войска на территории противника.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // 2) если у защиты НОЛЬ войск – захватываем без боя
            if (defTotal <= 0) {
                val resolvedWar = w.copy(
                    isResolved = true,
                    attackerWon = true,
                    state = "captured",
                    endedAt = now
                )
                warDao.update(resolvedWar)

                // Все войска с фронта возвращаются домой без потерь
                val newArmy = attackerArmy.copy(
                    infantry = attackerArmy.infantry + w.peh,
                    cossacks = attackerArmy.cossacks + w.kaz,
                    guards = attackerArmy.guards + w.gva
                )
                armyDao.insert(newArmy)

                // Чуть прокачаем генерала за «победу без боя»
                val newAtkGen = atkGen.copy(
                    battles = atkGen.battles + 1,
                    wins = atkGen.wins + 1,
                    experience = atkGen.experience + 5
                )
                generalDao.insert(newAtkGen)

                // Награда: 30% денег и 20% земли + zah++
                val attCountry = countryDao.getCountryByRuler(w.attackerRuler)
                val defCountry = countryDao.getCountryByRuler(w.defenderRuler)
                if (attCountry != null && defCountry != null) {
                    val rewardMoney = (defCountry.money * 30) / 100
                    val rewardLand = (defCountry.land * 20) / 100

                    val updatedAtt = attCountry.copy(
                        money = attCountry.money + rewardMoney,
                        land = attCountry.land + rewardLand,
                        zah = attCountry.zah + 1 // +1 захваченная страна
                    )
                    val updatedDef = defCountry.copy(
                        money = max(0, defCountry.money - rewardMoney),
                        land = max(0, defCountry.land - rewardLand)
                    )

                    countryDao.insertCountry(updatedAtt)
                    countryDao.insertCountry(updatedDef)
                }

                // Лог: захват без боя
                warLogDao.insert(
                    WarLogEntity(
                        warId = w.id,
                        ts = now,
                        type = "capture_ok",
                        payload = "{\"auto\":\"no_def_army\"}"
                    )
                )

                currentWar = resolvedWar
                renderWar(resolvedWar)
                renderMoves(resolvedWar.id)
                renderLogs(resolvedWar.id)

                Toast.makeText(
                    ctx,
                    "Страна захвачена без боя: у противника не осталось войск.",
                    Toast.LENGTH_LONG
                ).show()

                // Синхронизируем захват с сервером (успех)
                syncCaptureToServer(resolvedWar)

                return
            }

            // 3) обычная логика боя, если у защиты всё-таки есть войска
            val battle = resolveBattlePhpStyle(
                atkPeh = w.peh,
                atkKaz = w.kaz,
                atkGva = w.gva,
                defPeh = defArmy.infantry,
                defKaz = defArmy.cossacks,
                defGva = defArmy.guards,
                atkArmy = attackerArmy,
                defArmy = defArmy,
                atkGen = atkGen,
                defGen = defGen
            )

            val attackerWon = battle.attackerWon

            val resolvedWar = w.copy(
                isResolved = true,
                attackerWon = attackerWon,
                state = if (attackerWon) "captured" else "failed",
                endedAt = now
            )
            warDao.update(resolvedWar)

            // армия атакующего: возвращаем выживших с фронта
            val newArmy = attackerArmy.copy(
                infantry = max(0, attackerArmy.infantry + battle.attackerPeh - w.peh),
                cossacks = max(0, attackerArmy.cossacks + battle.attackerKaz - w.kaz),
                guards = max(0, attackerArmy.guards + battle.attackerGva - w.gva)
            )
            armyDao.insert(newArmy)

            // прокачка генерала атакующего
            val newAtkGen = atkGen.copy(
                level = atkGen.level + if (attackerWon) 1 else 0,
                experience = atkGen.experience + battle.attackerExp,
                battles = atkGen.battles + 1,
                wins = atkGen.wins + if (attackerWon) 1 else 0
            )
            generalDao.insert(newAtkGen)

            // прокачка генерала обороны
            val newDefGen = defGen.copy(
                level = defGen.level + if (!attackerWon) 1 else 0,
                experience = defGen.experience + battle.defenderExp,
                battles = defGen.battles + 1,
                wins = defGen.wins + if (!attackerWon) 1 else 0
            )
            generalDao.insert(newDefGen)

            // потери защитника
            val newDefArmy = defArmy.copy(
                infantry = max(0, defArmy.infantry - battle.defenderPeh),
                cossacks = max(0, defArmy.cossacks - battle.defenderKaz),
                guards = max(0, defArmy.guards - battle.defenderGva)
            )
            armyDao.insert(newDefArmy)

            // Награда за успешный захват: ресурсы + zah++
            val attCountry = countryDao.getCountryByRuler(w.attackerRuler)
            val defCountry = countryDao.getCountryByRuler(w.defenderRuler)
            if (attackerWon && attCountry != null && defCountry != null) {
                val rewardMoney = (defCountry.money * 30) / 100
                val rewardLand = (defCountry.land * 20) / 100

                val updatedAtt = attCountry.copy(
                    money = attCountry.money + rewardMoney,
                    land = attCountry.land + rewardLand,
                    zah = attCountry.zah + 1 // +1 захваченная страна
                )
                val updatedDef = defCountry.copy(
                    money = max(0, defCountry.money - rewardMoney),
                    land = max(0, defCountry.land - rewardLand)
                )

                countryDao.insertCountry(updatedAtt)
                countryDao.insertCountry(updatedDef)
            }

            // Лог боя
            warLogDao.insert(
                WarLogEntity(
                    warId = w.id,
                    ts = now,
                    type = if (attackerWon) "capture_ok" else "capture_fail",
                    payload = "{}"
                )
            )

            currentWar = resolvedWar
            renderWar(resolvedWar)
            renderMoves(resolvedWar.id)
            renderLogs(resolvedWar.id)

            Toast.makeText(
                ctx,
                if (attackerWon) "Захват успешен!" else "Захват провален.",
                Toast.LENGTH_LONG
            ).show()

            // Синхронизируем исход с сервером
            syncCaptureToServer(resolvedWar)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                ctx,
                "Ошибка при расчёте боя: ${e.message ?: "неизвестная"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * НОВОЕ: синк результата захвата с сервером через war_capture.php.
     * Отправляем только факт победы/поражения.
     */
    private fun syncCaptureToServer(resolvedWar: WarEntity) {
        val attackerWon = resolvedWar.attackerWon ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.warCapture(
                        warId = resolvedWar.id,
                        attackerRuler = myRuler,
                        attackerWon = if (attackerWon) 1 else 0
                    )
                }

                resp.message?.let { msg ->
                    if (msg.isNotBlank()) {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }

                resp.data?.let { data ->
                    val newState = data.state
                    if (newState != null && newState != resolvedWar.state) {
                        val synced = resolvedWar.copy(
                            state = newState,
                            attackerWon = (data.attacker_won == 1),
                            endedAt = data.ended_at ?: resolvedWar.endedAt
                        )
                        withContext(Dispatchers.IO) {
                            warDao.update(synced)
                        }
                        currentWar = synced
                        renderWar(synced)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Не удалось синхронизировать захват с сервером: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // --------- Формула боя der1 (адаптация PHP) ----------

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

        // Параметры атакующего
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

        // Параметры защитника
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

        val gena1 = a1 * 2 + a2 * 1.5 + a3 / 10.0 + 1
        val gena2 = b1 * 2 + b2 * 1.5 + b3 / 10.0 + 1

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

        // --- 1. Гвардия атакующего против всех ---
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

        gva1 = ((g12 * (1 + ((a11 * a12) * 0.625)) * k1) * 1.25) * 2.0
        var peh2 = (b4 * (1 + ((b5 * b6) * 0.625))) * k2
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
            op5 = (b4 - p23) * 1.0
            op6 = g12 * 3.0
        }

        // --- 2. Казаки атакующего против всех ---
        var kaz1 = (a7 * (1 + ((a8 * a9) * 0.625)) * k1) * 1.25
        gva2 = (b10 * (1 + ((b11 * b12) * 0.625))) * k2
        if (kaz1 > gva2) {
            val w1 = kaz1 - gva2
            val w2 = (100.0 * w1) / kaz1
            k11 = rnd((a7 * w2) / 100.0)
            g24 = 0.0
            op7 = b10 * 3.0
            op8 = (a7 - k11) * 2.0
        } else {
            val w1 = gva2 - kaz1
            val w2 = (100.0 * w1) / gva2
            g24 = rnd((b10 * w2) / 100.0)
            k11 = 0.0
            op7 = (b10 - g24) * 3.0
            op8 = a7 * 2.0
        }

        kaz1 = ((k11 * (1 + ((a8 * a9) * 0.625)) * k1) * 1.25) * 1.5
        kaz2 = (b7 * (1 + ((b8 * b9) * 0.625))) * k2
        if (kaz1 > kaz2) {
            val w1 = kaz1 - kaz2
            val w2 = (100.0 * w1) / kaz1
            k12 = rnd((k11 * w2) / 100.0)
            k25 = 0.0
            op9 = b7 * 2.0
            op10 = (k11 - k12) * 2.0
        } else {
            val w1 = kaz2 - kaz1
            val w2 = (100.0 * w1) / kaz2
            k25 = rnd((b7 * w2) / 100.0)
            k12 = 0.0
            op9 = (b7 - k25) * 2.0
            op10 = k11 * 2.0
        }

        kaz1 = ((k12 * (1 + ((a8 * a9) * 0.625)) * k1) * 1.25) * 2.0
        peh2 = (b4 * (1 + ((b5 * b6) * 0.625))) * k2
        if (kaz1 > peh2) {
            val w1 = kaz1 - peh2
            val w2 = (100.0 * w1) / kaz1
            k13 = rnd((k12 * w2) / 100.0)
            p26 = 0.0
            op11 = b4 * 1.0
            op12 = (k12 - k13) * 2.0
        } else {
            val w1 = peh2 - kaz1
            val w2 = (100.0 * w1) / peh2
            p26 = rnd((b4 * w2) / 100.0)
            k13 = 0.0
            op11 = (b4 - p26) * 1.0
            op12 = k12 * 2.0
        }

        // --- 3. Пехота атакующего против всех ---
        var peh1 = (a4 * (1 + ((a5 * a6) * 0.625)) * k1) * 1.25
        gva2 = (b10 * (1 + ((b11 * b12) * 0.625))) * k2
        if (peh1 > gva2) {
            val w1 = peh1 - gva2
            val w2 = (100.0 * w1) / peh1
            p11 = rnd((a4 * w2) / 100.0)
            g27 = 0.0
            op13 = b10 * 3.0
            op14 = (a4 - p11) * 1.0
        } else {
            val w1 = gva2 - peh1
            val w2 = (100.0 * w1) / gva2
            g27 = rnd((b10 * w2) / 100.0)
            p11 = 0.0
            op13 = (b10 - g27) * 3.0
            op14 = a4 * 1.0
        }

        peh1 = ((p11 * (1 + ((a5 * a6) * 0.625)) * k1) * 1.25) * 1.5
        kaz2 = (b7 * (1 + ((b8 * b9) * 0.625))) * k2
        if (peh1 > kaz2) {
            val w1 = peh1 - kaz2
            val w2 = (100.0 * w1) / peh1
            p12 = rnd((p11 * w2) / 100.0)
            k28 = 0.0
            op15 = b7 * 2.0
            op16 = (p11 - p12) * 1.0
        } else {
            val w1 = kaz2 - peh1
            val w2 = (100.0 * w1) / kaz2
            k28 = rnd((b7 * w2) / 100.0)
            p12 = 0.0
            op15 = (b7 - k28) * 2.0
            op16 = p11 * 1.0
        }

        peh1 = ((p12 * (1 + ((a5 * a6) * 0.625)) * k1) * 1.25) * 2.0
        peh2 = (b4 * (1 + ((b5 * b6) * 0.625))) * k2
        if (peh1 > peh2) {
            val w1 = peh1 - peh2
            val w2 = (100.0 * w1) / peh1
            p13 = rnd((p12 * w2) / 100.0)
            p29 = 0.0
            op17 = b4 * 1.0
            op18 = (p12 - p13) * 1.0
        } else {
            val w1 = peh2 - peh1
            val w2 = (100.0 * w1) / peh2
            p29 = rnd((b4 * w2) / 100.0)
            p13 = 0.0
            op17 = (b4 - p29) * 1.0
            op18 = p12 * 1.0
        }

        // --- Потери и выжившие ---
        val attGvaLost = (g11 + g12 + g13)
        val attKazLost = (k11 + k12 + k13)
        val attPehLost = (p11 + p12 + p13)

        val defGvaLost = (g21 + g24 + g27)
        val defKazLost = (k22 + k25 + k28)
        val defPehLost = (p23 + p26 + p29)

        val attackerPehLeft = max(0, atkPeh - attPehLost.toInt())
        val attackerKazLeft = max(0, atkKaz - attKazLost.toInt())
        val attackerGvaLeft = max(0, atkGva - attGvaLost.toInt())

        val defenderPehLeft = max(0, defPeh - defPehLost.toInt())
        val defenderKazLeft = max(0, defKaz - defKazLost.toInt())
        val defenderGvaLeft = max(0, defGva - defGvaLost.toInt())

        val attackerPower =
            attackerPehLeft * 1.0 + attackerKazLeft * 2.0 + attackerGvaLeft * 3.0
        val defenderPower =
            defenderPehLeft * 1.0 + defenderKazLeft * 2.0 + defenderGvaLeft * 3.0

        val attackerWon = attackerPower >= defenderPower

        val attackerExpGain = (attPehLost + attKazLost * 2 + attGvaLost * 3).toInt()
        val defenderExpGain = (defPehLost + defKazLost * 2 + defGvaLost * 3).toInt()

        return PhpBattleResult(
            attackerPeh = attackerPehLeft,
            attackerKaz = attackerKazLeft,
            attackerGva = attackerGvaLeft,
            defenderPeh = defenderPehLeft,
            defenderKaz = defenderKazLeft,
            defenderGva = defenderGvaLeft,
            attackerExp = attackerExpGain,
            defenderExp = defenderExpGain,
            attackerWon = attackerWon
        )
    }

    // =========================================================
    //   ВСПОМОГАТЕЛЬНОЕ
    // =========================================================

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_WAR_ID = "war_id"

        fun newInstance(warId: Long): WarDetailsFragment {
            val f = WarDetailsFragment()
            f.arguments = Bundle().apply { putLong(ARG_WAR_ID, warId) }
            return f
        }
    }
}
