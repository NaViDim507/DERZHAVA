package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AllianceDao
import com.example.derzhava.data.AllianceEntity
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.Buildings
import com.example.derzhava.data.CommandCenterDao
import com.example.derzhava.data.CommandCenterState
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.MarketDao
import com.example.derzhava.data.MessageDao
import com.example.derzhava.data.MessageEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.data.WarDao
import com.example.derzhava.data.WarEntity
import com.example.derzhava.data.WarMoveDao
import com.example.derzhava.data.WarMoveEntity
import com.example.derzhava.databinding.FragmentSpecialOpsBinding
import com.example.derzhava.databinding.ItemSpecialTargetBinding
import kotlin.collections.buildList
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.net.ApiClient
import com.example.derzhava.net.OnlineArmySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.derzhava.net.OnlineCountrySync



/**
 * Командный центр: соседи / война / разведка / диверсии / союзы.
 *
 * Цели берём из таблицы countries:
 *  - NPC создаются в AdminNpcFragment (isNpc = true);
 *  - позже тут же будут реальные игроки.
 */
class SpecialOpsFragment : Fragment() {

    // DAO
    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository
    private lateinit var commandCenterDao: CommandCenterDao
    private lateinit var countryDao: CountryDao
    private lateinit var warDao: WarDao
    private lateinit var armyDao: ArmyDao
    private lateinit var warMoveDao: WarMoveDao
    private lateinit var marketDao: MarketDao
    private lateinit var messageDao: MessageDao
    private lateinit var allianceDao: AllianceDao

    // view-binding
    private var _binding: FragmentSpecialOpsBinding? = null
    private val binding get() = _binding!!

    // наша страна
    private var myCountry: CountryEntity? = null

    // состояние командного центра
    private var ccState: CommandCenterState? = null

    // соседи (все страны, кроме нас)
    private var neighbors: List<CountryEntity> = emptyList()

    // активные враги по таблице войн
    private var enemyRulers: Set<String> = emptySet()

    // состояния союзов
    private var allies: Set<String> = emptySet()
    private var pendingAlliesSent: Set<String> = emptySet()
    private var pendingAlliesIncoming: Set<String> = emptySet()

    // направление соседей
    private enum class Direction { WEST, EAST }
    private var currentDirection: Direction = Direction.WEST

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)
        commandCenterDao = db.commandCenterDao()
        countryDao = db.countryDao()
        warDao = db.warDao()
        armyDao = db.armyDao()
        warMoveDao = db.warMoveDao()
        marketDao = db.marketDao()
        messageDao = db.messageDao()
        allianceDao = db.allianceDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpecialOpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDirectionButtons()
        loadData()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        // При возвращении на экран спецопераций заново загружаем данные.
        // Это позволит увидеть вновь созданные страны и актуальные соседские списки
        // без перезахода в игру.
        loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // Загрузка данных
    // -------------------------------------------------------------------------

    private fun loadData() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден", Toast.LENGTH_SHORT).show()
            return
        }

        // наша страна
        var c = countryDao.getCountryByRuler(user.rulerName)
        if (c == null) {
            c = CountryEntity(
                rulerName = user.rulerName,
                countryName = user.countryName
            )
            countryDao.insertCountry(c)
        }
        myCountry = c

        // состояние командного центра
        var state = commandCenterDao.getStateByRuler(user.rulerName)
        if (state == null) {
            state = CommandCenterState(rulerName = user.rulerName)
            commandCenterDao.insertState(state)
        }
        ccState = state

        // список врагов по активным войнам
        enemyRulers = warDao.getActiveEnemiesForRuler(user.rulerName).toSet()

        // соседи = все страны, кроме нас самих
        neighbors = countryDao.getAllExcept(user.rulerName)

        // сначала загружаем союзы (чтобы кнопки рисовались правильно)
        loadAllianceState(user.rulerName)

        updateDirectionButtons()
        bindTargets()
    }

    // -------------------------------------------------------------------------
    // Помощники по ресурсам / бирже
    // -------------------------------------------------------------------------

    // сколько ресурса у страны
    private fun getResourceAmount(c: CountryEntity, type: Int): Int = when (type) {
        1 -> c.metal
        2 -> c.mineral
        3 -> c.wood
        4 -> c.food
        5 -> c.workers
        6 -> c.bots
        else -> 0
    }

    // обновить ресурс у страны
    private fun setResourceAmount(c: CountryEntity, type: Int, newValue: Int): CountryEntity =
        when (type) {
            1 -> c.copy(metal = newValue)
            2 -> c.copy(mineral = newValue)
            3 -> c.copy(wood = newValue)
            4 -> c.copy(food = newValue)
            5 -> c.copy(workers = newValue)
            6 -> c.copy(bots = newValue)
            else -> c
        }

    /**
     * При старте вторжения:
     *  - снимаем все лоты защитника с биржи
     *  - ресурсы возвращаем в его запасы
     *  - шлём сообщение, что на него напали и всё вернули на склады
     */
    private fun onInvasionStarted(attacker: CountryEntity, defender: CountryEntity) {
        val offers = marketDao.getOffersForRuler(defender.rulerName)
        if (offers.isEmpty()) {
            sendInvasionMessage(attacker, defender, resourcesReturned = false)
            return
        }

        var updatedDefender = defender
        for (offer in offers) {
            val current = getResourceAmount(updatedDefender, offer.resourceType)
            updatedDefender =
                setResourceAmount(updatedDefender, offer.resourceType, current + offer.amount)
            marketDao.delete(offer)
        }

        countryDao.insertCountry(updatedDefender)
        sendInvasionMessage(attacker, updatedDefender, resourcesReturned = true)
    }

    private fun sendInvasionMessage(
        attacker: CountryEntity,
        defender: CountryEntity,
        resourcesReturned: Boolean
    ) {
        val now = System.currentTimeMillis()
        val text = buildString {
            append("На ваше государство напало государство ")
            append(attacker.countryName)
            append(" (правитель ")
            append(attacker.rulerName)
            append("). ")
            if (resourcesReturned) {
                append("Все ресурсы, находящиеся на продаже на бирже, перенесены на ваши склады.")
            } else {
                append("В данный момент у вас не было ресурсов на продаже на бирже.")
            }
        }

        messageDao.insert(
            MessageEntity(
                rulerName = defender.rulerName,
                text = text,
                timestampMillis = now,
                isRead = false
            )
        )
    }

    // -------------------------------------------------------------------------
    // Запад / Восток
    // -------------------------------------------------------------------------

    private fun setupDirectionButtons() {
        currentDirection = Direction.WEST
        updateDirectionButtons()

        binding.btnWest.setOnClickListener {
            if (currentDirection != Direction.WEST) {
                currentDirection = Direction.WEST
                updateDirectionButtons()
                bindTargets()
            }
        }

        binding.btnEast.setOnClickListener {
            if (currentDirection != Direction.EAST) {
                currentDirection = Direction.EAST
                updateDirectionButtons()
                bindTargets()
            }
        }
    }

    private fun getTargetsForCurrentDirection(): List<CountryEntity> {
        if (neighbors.isEmpty()) return emptyList()

        val sorted = neighbors.sortedBy { it.countryName }
        val maxPerSide = 4

        val mid = (sorted.size + 1) / 2
        val westAll = sorted.take(mid)
        val eastAll = sorted.drop(mid)

        return if (currentDirection == Direction.WEST) {
            westAll.take(maxPerSide)
        } else {
            eastAll.take(maxPerSide)
        }
    }

    private fun updateDirectionButtons() {
        val sorted = neighbors.sortedBy { it.countryName }
        val maxPerSide = 4

        val mid = (sorted.size + 1) / 2
        val westCount = sorted.take(mid).take(maxPerSide).size
        val eastCount = sorted.drop(mid).take(maxPerSide).size

        val westSelected = currentDirection == Direction.WEST
        val eastSelected = currentDirection == Direction.EAST

        binding.btnWest.alpha = if (westSelected) 1.0f else 0.6f
        binding.btnEast.alpha = if (eastSelected) 1.0f else 0.6f

        binding.btnWest.text = if (westCount > 0) "Запад ($westCount)" else "Запад"
        binding.btnEast.text = if (eastCount > 0) "Восток ($eastCount)" else "Восток"

        binding.tvOpsInfo.text = if (westSelected) {
            "Западные соседи — государства, появившиеся в мире раньше тебя. Чаще всего более развитые и опасные."
        } else {
            "Восточные соседи — государства, пришедшие позже. Часто слабее, но могут быстро развиваться."
        }
    }

    // -------------------------------------------------------------------------
    // Отрисовка списка целей
    // -------------------------------------------------------------------------

    private fun bindTargets() {
        val container = binding.targetsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        val my = myCountry
        if (my == null) {
            val tv = TextView(requireContext()).apply {
                text = "Твоя страна не найдена."
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
                textSize = 14f
            }
            container.addView(tv)
            return
        }

        val visibleTargets = getTargetsForCurrentDirection()
        if (visibleTargets.isEmpty()) {
            val infoView = TextView(requireContext()).apply {
                text = "Подходящих соседей пока нет. Создай NPC-страны в админке."
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
                textSize = 14f
            }
            container.addView(infoView)
            return
        }

        for (target in visibleTargets) {
            val itemBinding = ItemSpecialTargetBinding.inflate(inflater, container, false)

            itemBinding.tvName.text = target.countryName

            val isEnemy = enemyRulers.contains(target.rulerName)
            val isAlly = allies.contains(target.rulerName)

            when {
                isEnemy -> {
                    itemBinding.tvRelation.visibility = View.VISIBLE
                    itemBinding.tvRelation.text = "враг"
                }

                isAlly -> {
                    itemBinding.tvRelation.visibility = View.VISIBLE
                    itemBinding.tvRelation.text = "союзник"
                }

                else -> {
                    itemBinding.tvRelation.visibility = View.GONE
                }
            }

            val defense = defenseValue(target)
            val per = target.defenseLevel
            itemBinding.tvStats.text =
                "Население: ${target.workers}, Казна: ${target.money}, Периметр: $per, Оборона: $defense"

            // Разведка доступна всегда
            itemBinding.btnRecon.setOnClickListener {
                performRecon(target)
            }

            if (isAlly) {
                // с союзником нельзя диверсии/воровство/войну — вместо этого Бартер
                itemBinding.btnSabotage.visibility = View.GONE

                itemBinding.btnTheft.visibility = View.VISIBLE
                itemBinding.btnTheft.text = "Бартер"
                itemBinding.btnTheft.setOnClickListener {
                    showBarterDialog(target)
                }

                itemBinding.btnDeclareWar.apply {
                    text = "Союзник"
                    isEnabled = false
                    setOnClickListener(null)
                }
            } else {
                // обычный режим: диверсия / воровство / война
                itemBinding.btnSabotage.visibility = View.VISIBLE
                itemBinding.btnSabotage.text = "Диверсия"
                itemBinding.btnSabotage.setOnClickListener {
                    performSabotage(target)
                }

                itemBinding.btnTheft.visibility = View.VISIBLE
                itemBinding.btnTheft.text = "Воровство"
                itemBinding.btnTheft.setOnClickListener {
                    performTheft(target)
                }

                itemBinding.btnDeclareWar.apply {
                    if (isEnemy) {
                        text = "Война идёт"
                        isEnabled = false
                        setOnClickListener(null)
                    } else {
                        text = "Объявить войну"
                        isEnabled = true
                        setOnClickListener {
                            showDeclareWarDialog(target)
                        }
                    }
                }
            }

            // --- Союз / разрыв / ожидание ответа ---
            val isPendingSent = pendingAlliesSent.contains(target.rulerName)
            val isPendingIncoming = pendingAlliesIncoming.contains(target.rulerName)

            itemBinding.btnAlliance.visibility = View.VISIBLE
            itemBinding.btnAlliance.apply {
                when {
                    isAlly -> {
                        text = "Разорвать союз"
                        isEnabled = true
                        setOnClickListener { confirmBreakAlliance(target) }
                    }

                    isPendingSent -> {
                        text = "Союз предложен"
                        isEnabled = false
                        setOnClickListener(null)
                    }

                    isPendingIncoming -> {
                        // Это дублирует кнопки в сообщениях, но можно и отсюда ответить
                        text = "Ответить на союз"
                        isEnabled = true
                        setOnClickListener { showAnswerAllianceDialog(target) }
                    }

                    else -> {
                        text = "Предложить союз"
                        isEnabled = true
                        setOnClickListener { showProposeAllianceDialog(target) }
                    }
                }
            }

            // ВАЖНО: добавляем карточку в контейнер
            container.addView(itemBinding.root)
        }
    }

    // -------------------------------------------------------------------------
    // СОЮЗЫ
    // -------------------------------------------------------------------------

    private fun normalizePair(r1: String, r2: String): Pair<String, String> =
        if (r1 <= r2) r1 to r2 else r2 to r1

    private fun otherRuler(all: AllianceEntity, me: String): String =
        if (all.rulerA == me) all.rulerB else all.rulerA

    private fun loadAllianceState(myRuler: String) {
        val now = System.currentTimeMillis()

        // 1) истёкшие предложения — авто-отмена + сообщение отправителю
        val expired = allianceDao.getExpiredPendingForRuler(myRuler, now)
        for (al in expired) {
            if (al.initiator == myRuler) {
                val other = otherRuler(al, myRuler)
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

        // 2) активные союзы
        val active = allianceDao.getActiveAlliancesForRuler(myRuler)
        allies = active.map { otherRuler(it, myRuler) }.toSet()

        // 3) всё ещё ожидающие
        val pending = allianceDao.getPendingForRuler(myRuler)
        pendingAlliesSent = pending
            .filter { it.initiator == myRuler }
            .map { otherRuler(it, myRuler) }
            .toSet()
        pendingAlliesIncoming = pending
            .filter { it.initiator != myRuler }
            .map { otherRuler(it, myRuler) }
            .toSet()
    }

    private fun showProposeAllianceDialog(target: CountryEntity) {
        val user = userRepository.getUser() ?: return
        val myRuler = user.rulerName
        val myCountryName = myCountry?.countryName ?: "Твоё государство"

        val ctx = requireContext()

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Предложить союз ${target.countryName}?")
            .setMessage("Государства не смогут воевать и вредить друг другу, вместо этого доступен бартер.")
            .setPositiveButton("Предложить") { _, _ ->
                val (a, b) = normalizePair(myRuler, target.rulerName)
                val existing = allianceDao.getAlliance(a, b)
                if (existing != null && existing.status == ALLIANCE_STATUS_ACTIVE) {
                    Toast.makeText(ctx, "Союз уже заключён.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing != null && existing.status == ALLIANCE_STATUS_PENDING) {
                    Toast.makeText(ctx, "Предложение союза уже существует.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val now = System.currentTimeMillis()
                val alliance = AllianceEntity(
                    rulerA = a,
                    rulerB = b,
                    initiator = myRuler,
                    status = ALLIANCE_STATUS_PENDING,
                    createdAt = now,
                    expiresAt = now + ALLIANCE_EXPIRY_MS
                )
                allianceDao.insert(alliance)

                // сообщение себе
                messageDao.insert(
                    MessageEntity(
                        rulerName = myRuler,
                        text = "Вы предложили государству ${target.countryName} союз. Ждите пока оно ответит!",
                        timestampMillis = now,
                        isRead = false,
                        type = "generic"
                    )
                )
                // сообщение адресату — ВАЖНО: тип и payloadRuler
                messageDao.insert(
                    MessageEntity(
                        rulerName = target.rulerName,
                        text = "$myCountryName предлагает вам заключить союз. У вас есть 10 минут, чтобы согласовать или отклонить предложение.",
                        timestampMillis = now,
                        isRead = false,
                        type = "alliance_invite",
                        payloadRuler = myRuler
                    )
                )

                loadAllianceState(myRuler)
                bindTargets()

                Toast.makeText(ctx, "Предложение союза отправлено.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAnswerAllianceDialog(target: CountryEntity) {
        val user = userRepository.getUser() ?: return
        val myRuler = user.rulerName
        val myCountryName = myCountry?.countryName ?: "Твоё государство"

        val (a, b) = normalizePair(myRuler, target.rulerName)
        val alliance = allianceDao.getAlliance(a, b) ?: return
        if (alliance.status != ALLIANCE_STATUS_PENDING) return

        val ctx = requireContext()
        val initiator = alliance.initiator
        val otherName = target.countryName

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Союз с $otherName")
            .setMessage("$otherName предлагает вам союз. Принять?")
            .setPositiveButton("Согласовать") { _, _ ->
                val now = System.currentTimeMillis()
                allianceDao.update(
                    alliance.copy(
                        status = ALLIANCE_STATUS_ACTIVE,
                        respondedAt = now
                    )
                )

                // себе
                messageDao.insert(
                    MessageEntity(
                        rulerName = myRuler,
                        text = "Вы согласились на союз с государством $otherName.",
                        timestampMillis = now,
                        isRead = false
                    )
                )
                // инициатору
                messageDao.insert(
                    MessageEntity(
                        rulerName = initiator,
                        text = "$myCountryName приняло ваше предложение союза.",
                        timestampMillis = now,
                        isRead = false
                    )
                )

                loadAllianceState(myRuler)
                bindTargets()
            }
            .setNegativeButton("Отклонить") { _, _ ->
                val now = System.currentTimeMillis()
                allianceDao.update(
                    alliance.copy(
                        status = ALLIANCE_STATUS_REJECTED,
                        respondedAt = now
                    )
                )

                // себе
                messageDao.insert(
                    MessageEntity(
                        rulerName = myRuler,
                        text = "Вы отклонили предложение союза от государства $otherName.",
                        timestampMillis = now,
                        isRead = false
                    )
                )
                // инициатору
                messageDao.insert(
                    MessageEntity(
                        rulerName = initiator,
                        text = "$myCountryName отклонило ваше предложение союза.",
                        timestampMillis = now,
                        isRead = false
                    )
                )

                loadAllianceState(myRuler)
                bindTargets()
            }
            .show()
    }

    private fun confirmBreakAlliance(target: CountryEntity) {
        val user = userRepository.getUser() ?: return
        val myRuler = user.rulerName
        val myCountryName = myCountry?.countryName ?: "Твоё государство"

        val ctx = requireContext()
        val (a, b) = normalizePair(myRuler, target.rulerName)
        val alliance = allianceDao.getAlliance(a, b) ?: return
        if (alliance.status != ALLIANCE_STATUS_ACTIVE) return

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Разорвать союз с ${target.countryName}?")
            .setMessage("После разрыва вы снова сможете объявить войну и проводить диверсии.")
            .setPositiveButton("Разорвать") { _, _ ->
                val now = System.currentTimeMillis()
                allianceDao.update(
                    alliance.copy(
                        status = ALLIANCE_STATUS_BROKEN,
                        respondedAt = now
                    )
                )

                // себе
                messageDao.insert(
                    MessageEntity(
                        rulerName = myRuler,
                        text = "Вы разорвали союз с государством ${target.countryName}.",
                        timestampMillis = now,
                        isRead = false
                    )
                )
                // союзнику
                messageDao.insert(
                    MessageEntity(
                        rulerName = target.rulerName,
                        text = "$myCountryName разорвало с вами союз.",
                        timestampMillis = now,
                        isRead = false
                    )
                )

                loadAllianceState(myRuler)
                bindTargets()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showBarterDialog(target: CountryEntity) {
        Toast.makeText(
            requireContext(),
            "Бартер с союзниками пока в разработке.",
            Toast.LENGTH_LONG
        ).show()
    }

    // -------------------------------------------------------------------------
    // Общие помощники: защита, кулдауны
    // -------------------------------------------------------------------------

    private fun defenseValue(c: CountryEntity): Int {
        val perimeter = c.defenseLevel      // urz
        val towers = c.domik7               // сторожевые башни
        val workersFactor = c.workers / 100 // небольшой бонус от населения
        return max(0, perimeter + towers * 2 + workersFactor)
    }

    private fun checkCooldown(
        lastTime: Long,
        cooldownMinutes: Int,
        actionName: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val cooldownMs = cooldownMinutes * 60 * 1000L
        val passed = now - lastTime
        if (passed < cooldownMs) {
            val left = cooldownMs - passed
            val minutes = left / (60 * 1000)
            val seconds = (left / 1000) % 60
            Toast.makeText(
                requireContext(),
                "$actionName будет доступно через ${minutes}м ${seconds}с.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Разведка
    // -------------------------------------------------------------------------

    private fun performRecon(target: CountryEntity) {
        val s = ccState ?: return

        // Уровень разведки 0–100
        val intel = s.intel.coerceIn(0, 100)

        // Реальные ресурсы соседней страны
        val realGold = target.money
        val realFood = target.food
        val realMetal = target.metal
        val realWood = target.wood

        // Погрешность зависит от уровня разведки:
        // 100% — почти точно, 0% — ошибка до ±50%.
        fun approximate(real: Int): Int {
            if (real <= 0) return 0
            val accuracy = intel / 100.0
            val maxErrorFraction = 0.5 * (1.0 - accuracy)
            val minFactor = 1.0 - maxErrorFraction
            val maxFactor = 1.0 + maxErrorFraction
            val factor = minFactor + Random.nextDouble() * (maxFactor - minFactor)
            return (real * factor).toInt().coerceAtLeast(0)
        }

        val shownGold = if (intel >= 100) realGold else approximate(realGold)
        val shownFood = if (intel >= 100) realFood else approximate(realFood)
        val shownMetal = if (intel >= 100) realMetal else approximate(realMetal)
        val shownWood = if (intel >= 100) realWood else approximate(realWood)

        // Определяем список построек
        val buildings = buildList {
            if (target.domik1 > 0) add(Buildings.KOMBINAT to target.domik1)
            if (target.domik2 > 0) add(Buildings.TOWN to target.domik2)
            if (target.domik3 > 0) add(Buildings.COMMAND_CENTER to target.domik3)
            if (target.domik4 > 0) add(Buildings.WAR_BASE to target.domik4)
            if (target.domik5 > 0) add(Buildings.PERIMETR to target.domik5)
            if (target.domik6 > 0) add(Buildings.BIRZHA to target.domik6)
            if (target.domik7 > 0) add(Buildings.WATCH_TOWER to target.domik7)
        }
        // Подготовим строки вида "• название xкол-во"
        val buildingLines = ArrayList<String>()
        if (buildings.isNotEmpty()) {
            for ((id, count) in buildings) {
                buildingLines.add("• ${Buildings.name(id)} x$count")
            }
        }

        // Открываем отдельный экран для отображения результатов разведки
        val frag = ReconResultFragment.newInstance(
            countryName = target.countryName,
            gold = shownGold,
            food = shownFood,
            metal = shownMetal,
            wood = shownWood,
            buildings = ArrayList(buildingLines)
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag)
            .addToBackStack(null)
            .commit()
    }

    // -------------------------------------------------------------------------
    // Диверсия
    // -------------------------------------------------------------------------

    private fun performSabotage(target: CountryEntity) {
        val s = ccState ?: return

        if (!checkCooldown(s.lastSabotageTime, 60, "Диверсия")) return

        var sab = s.sabotage
        val enemyDefense = defenseValue(target)

        val diff = sab - enemyDefense
        val baseChance = 45 + diff
        val chance = baseChance.coerceIn(10, 90)
        val roll = Random.nextInt(100)

        val updatedTarget: CountryEntity
        val logMessage: String

        if (roll < chance) {
            val currentDef = target.defenseLevel

            var newDefense = currentDef
            var defenseLost = 0
            if (currentDef > 0) {
                val percent = (15 + sab / 4).coerceIn(5, 60)
                defenseLost = max(1, currentDef * percent / 100)
                newDefense = (currentDef - defenseLost).coerceAtLeast(0)
            }

            val lostWorkers = max(1, (target.workers * 0.05).toInt())
            val newWorkers = max(0, target.workers - lostWorkers)

            if (sab < 80) sab += 1

            updatedTarget = target.copy(
                defenseLevel = newDefense,
                workers = newWorkers
            )

            logMessage =
                if (currentDef > 0)
                    "Диверсия удалась! Периметр ${target.countryName} ослаблен (-$defenseLost к уровню защиты), рабочие потеряны ($lostWorkers чел.)."
                else
                    "Диверсия удалась! У ${target.countryName} и так нет периметра, но рабочие потеряны ($lostWorkers чел.)."
        } else {
            sab = max(sab - 1, 0)
            updatedTarget = target.copy(
                domik7 = target.domik7 + 1
            )
            logMessage =
                "Диверсия провалилась! Оборона ${target.countryName} усилилась (добавлена сторожевая башня)."
        }

        val newState = s.copy(
            sabotage = sab,
            lastSabotageTime = System.currentTimeMillis()
        )
        commandCenterDao.insertState(newState)
        ccState = newState

        countryDao.insertCountry(updatedTarget)

        myCountry?.let { my ->
            neighbors = countryDao.getAllExcept(my.rulerName)
        }
        updateDirectionButtons()
        bindTargets()

        Toast.makeText(requireContext(), logMessage, Toast.LENGTH_LONG).show()
    }

    // -------------------------------------------------------------------------
    // Воровство
    // -------------------------------------------------------------------------

    private fun performTheft(target: CountryEntity) {
        val c = myCountry ?: return
        val s = ccState ?: return

        if (!checkCooldown(s.lastTheftTime, 60, "Воровство")) return
        if (s.theft <= 0) {
            Toast.makeText(requireContext(), "Навык воровства 0%.", Toast.LENGTH_SHORT).show()
            return
        }
        if (target.money <= 0) {
            Toast.makeText(requireContext(), "У цели пустая казна.", Toast.LENGTH_SHORT).show()
            return
        }

        var theftSkill = s.theft
        val enemyDefense = defenseValue(target)

        val diff = theftSkill - enemyDefense
        val baseChance = 50 + diff
        val chance = baseChance.coerceIn(10, 90)
        val roll = Random.nextInt(100)

        val success = roll < chance

        var enemyMoney = target.money
        var ourMoney = c.money
        val message: String

        val updatedTarget: CountryEntity

        if (success) {
            val percent = (10 + diff.coerceAtLeast(0)).coerceAtMost(40)
            val stolen = min(
                enemyMoney,
                (enemyMoney * percent) / 100
            ).coerceAtLeast(1)

            enemyMoney -= stolen
            ourMoney += stolen

            if (theftSkill < 80) theftSkill += 1

            updatedTarget = target.copy(money = enemyMoney)

            message =
                "Воровство удалось! Украдено $stolen монет у ${target.countryName}.\nТвоя казна: $ourMoney."
        } else {
            theftSkill = max(theftSkill - 1, 0)
            updatedTarget = target.copy(domik7 = target.domik7 + 1)
            message = "Воровство провалилось! Оборона ${target.countryName} усилилась."
        }

        val newCountry = c.copy(money = ourMoney)
        countryDao.insertCountry(newCountry)
        myCountry = newCountry

        val newState = s.copy(
            theft = theftSkill,
            lastTheftTime = System.currentTimeMillis()
        )
        commandCenterDao.insertState(newState)
        ccState = newState

        countryDao.insertCountry(updatedTarget)
        myCountry?.let { my ->
            neighbors = countryDao.getAllExcept(my.rulerName)
        }
        updateDirectionButtons()
        bindTargets()

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    // -------------------------------------------------------------------------
    // Объявление войны
    // -------------------------------------------------------------------------

    private fun showDeclareWarDialog(target: CountryEntity) {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден.", Toast.LENGTH_SHORT).show()
            return
        }
        val myRuler = user.rulerName

        val myC = myCountry ?: run {
            Toast.makeText(requireContext(), "Твоя страна не найдена.", Toast.LENGTH_SHORT).show()
            return
        }

        if (allies.contains(target.rulerName)) {
            Toast.makeText(
                requireContext(),
                "Нельзя объявить войну союзнику. Сначала разорви союз.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val activeWars = warDao.countActiveForAttacker(myRuler)
        if (activeWars >= 3) {
            Toast.makeText(
                requireContext(),
                "Можно вести не более трёх войн одновременно.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        var army = armyDao.getByRuler(myRuler) ?: ArmyState(rulerName = myRuler)
        val totalTroops = army.infantry + army.cossacks + army.guards + army.catapults
        if (totalTroops <= 0) {
            Toast.makeText(
                requireContext(),
                "У тебя нет войск для похода.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val enemyCountry = target
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        fun addRow(label: String, maxValue: Int): android.widget.EditText {
            val tv = TextView(ctx).apply {
                text = "$label (до $maxValue):"
                textSize = 14f
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
            }
            val et = android.widget.EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText("0")
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etInf = addRow("Пехота", army.infantry)
        val etKaz = addRow("Казаки", army.cossacks)
        val etGva = addRow("Гвардия", army.guards)
        val etKat = addRow("Катапульты", army.catapults)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Объявить войну ${enemyCountry.countryName}")
            .setView(layout)
            .setPositiveButton("Вывести войска") { _, _ ->
                val peh = (etInf.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val kaz = (etKaz.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                val gva = (etGva.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
                var kat = (etKat.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)

                if (peh + kaz + gva + kat <= 0) {
                    Toast.makeText(
                        ctx,
                        "Нужно отправить хотя бы одного бойца.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                if (peh > army.infantry ||
                    kaz > army.cossacks ||
                    gva > army.guards ||
                    kat > army.catapults
                ) {
                    Toast.makeText(
                        ctx,
                        "Нельзя отправить войск больше, чем есть в армии.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val defenderCountry =
                    countryDao.getCountryByRuler(enemyCountry.rulerName) ?: enemyCountry
                var effectiveKat = kat

                if (defenderCountry.domik5 > 0 && defenderCountry.defenseLevel > 0) {
                    if (kat <= 0) {
                        Toast.makeText(
                            ctx,
                            "У врага построен Периметр. Нужны катапульты, чтобы прорвать оборону.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setPositiveButton
                    }

                    val defense = defenderCountry.defenseLevel

                    if (defense > kat) {
                        val newDefense = defense - kat
                        val updatedDef = defenderCountry.copy(defenseLevel = newDefense)
                        countryDao.insertCountry(updatedDef)

                        val newArmy = army.copy(
                            catapults = army.catapults - kat
                        )
                        armyDao.insert(newArmy)

                        Toast.makeText(
                            ctx,
                            "Периметр ${defenderCountry.countryName} выдержал атаку катапульт.\nУровень защиты снижен до $newDefense.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setPositiveButton
                    } else {
                        // Периметр пробит, здание уничтожено
                        effectiveKat = kat - defense
                        val updatedDef = defenderCountry.copy(
                            defenseLevel = 0,
                            domik5 = 0
                        )
                        countryDao.insertCountry(updatedDef)

                        Toast.makeText(
                            ctx,
                            "Периметр ${defenderCountry.countryName} прорван! Здание «Периметр» разрушено.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Запуск войны теперь происходит на сервере. После обработки
                // периметра/катапульт отправляем запрос war_declare.php. Сервер
                // спишет войска из gos_app и создаст запись войны. Мы
                // асинхронно обновляем армию и список войн.
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Перед объявлением войны убедимся, что наша страна и армия синхронизированы
                        withContext(Dispatchers.IO) {
                            try {
                                // Сохраняем актуальные данные страны
                                OnlineCountrySync.syncUp(db, myRuler)
                            } catch (_: Exception) {
                                // если не получилось — всё равно попробуем объявить войну
                            }
                            try {
                                // Сохраняем актуальное состояние армии. Это важно,
                                // чтобы сервер видел правильное количество войск для проверки.
                                OnlineArmySync.syncUp(db, myRuler)
                            } catch (_: Exception) {
                                // пропускаем ошибку
                            }
                        }
                        // 1. Объявляем войну на сервере. catapults на сервере не
                        // поддерживаются, поэтому отправляем только пехоту, казаков
                        // и гвардию. Катапульты учитываются локально для
                        // разрушения Периметра.
                        val resp = withContext(Dispatchers.IO) {
                            ApiClient.apiService.declareWar(
                                attackerRuler = myRuler,
                                defenderRuler = enemyCountry.rulerName,
                                peh = peh,
                                kaz = kaz,
                                gva = gva
                            )
                        }

                        if (!resp.success) {
                            Toast.makeText(ctx, resp.message ?: "Не удалось объявить войну.", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        // 2. Списываем войска и катапульты локально. Сервер уже
                        // списал peh/kaz/gva, но catapults остаются офлайн.
                        val newArmy = army.copy(
                            infantry = army.infantry - peh,
                            cossacks = army.cossacks - kaz,
                            guards = army.guards - gva,
                            catapults = army.catapults - kat
                        )
                        armyDao.insert(newArmy)

                        // 3. Зеркалим движение войск в локальный лог
                        val now = System.currentTimeMillis()
                        warMoveDao.insert(
                            WarMoveEntity(
                                warId = resp.war_id ?: 0L,
                                type = "reinforce",
                                ts = now,
                                peh = peh,
                                kaz = kaz,
                                gva = gva,
                                cat = kat
                            )
                        )

                        // 4. Синхронизируем армию с VPS и перезагружаем список войн
                        withContext(Dispatchers.IO) {
                            try { OnlineArmySync.syncDown(db, myRuler) } catch (_: Exception) {}
                        }
                        army = newArmy

                        // 5. Обновляем список врагов через серверный war_list.php
                        enemyRulers = warDao.getActiveEnemiesForRuler(myRuler).toSet()
                        bindTargets()

                        // Сообщаем об объявлении войны. Информация появится на главном
                        // экране при возврате. Не открываем WarDetailsFragment
                        // автоматически, чтобы избежать ситуации, когда сервер
                        // ещё не добавил войну в список и фрагмент не может
                        // её загрузить.
                        Toast.makeText(
                            ctx,
                            resp.message ?: "Война с ${enemyCountry.countryName} начата.",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Ошибка сети: ${e.localizedMessage ?: "объявить войну не удалось"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // Константы
    // -------------------------------------------------------------------------

    companion object {
        private const val FIRST_RAID_DELAY = 3 * 60 * 60 * 1000L  // 3 часа
        private const val CAPTURE_DELAY = 12 * 60 * 60 * 1000L   // 12 часов

        private const val ALLIANCE_STATUS_PENDING = 0
        private const val ALLIANCE_STATUS_ACTIVE = 1
        private const val ALLIANCE_STATUS_REJECTED = 2
        private const val ALLIANCE_STATUS_BROKEN = 3
        private const val ALLIANCE_STATUS_EXPIRED = 4

        private const val ALLIANCE_EXPIRY_MS = 10 * 60 * 1000L   // 10 минут

        fun newInstance() = SpecialOpsFragment()
    }
}
