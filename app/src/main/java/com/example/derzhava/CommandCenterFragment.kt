package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CommandCenterDao
import com.example.derzhava.data.CommandCenterState
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.GeneralDao
import com.example.derzhava.data.GeneralState
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentCommandCenterBinding
import com.example.derzhava.net.OnlineCountrySync
import com.example.derzhava.net.OnlineCommandCenterSync
import com.example.derzhava.net.OnlineGeneralSync
import kotlin.math.max
import kotlin.math.min

class CommandCenterFragment : Fragment() {

    private var _binding: FragmentCommandCenterBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository
    private lateinit var commandCenterDao: CommandCenterDao
    private lateinit var generalDao: GeneralDao

    private var country: CountryEntity? = null
    private var ccState: CommandCenterState? = null
    private var general: GeneralState? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)
        commandCenterDao = db.commandCenterDao()
        generalDao = db.generalDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommandCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        // При возвращении в командный центр обновляем данные, чтобы учесть
        // возможные изменения в соседях, спецслужбах и генерале.
        loadData()
    }

    private fun loadData() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден, вход...", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        // Запрашиваем актуальные данные с сервера и сохраняем в локальной БД.
        // Используем coroutine для выполнения сетевых запросов вне UI-потока.
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // синхрон страны, командного центра и генерала
                OnlineCountrySync.syncDownOrCreate(db, user.rulerName, user.countryName)
                OnlineCommandCenterSync.syncDown(db, user.rulerName)
                OnlineGeneralSync.syncDown(db, user.rulerName)
            } catch (e: Exception) {
                // В случае ошибки сети показываем сообщение, но продолжаем работать с локальными данными
                Toast.makeText(requireContext(), "Ошибка обновления данных: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }

            // Загружаем локальные данные в фоновом потоке
            val triple = withContext(Dispatchers.IO) {
                val c = db.countryDao().getCountryByRuler(user.rulerName)
                val state = commandCenterDao.getStateByRuler(user.rulerName)
                val g = generalDao.getByRuler(user.rulerName)
                Triple(c, state, g)
            }
            val (c, state, g) = triple
            // Обязательно должна быть страна, иначе игра не может работать
            if (c == null) {
                Toast.makeText(requireContext(), "Не удалось загрузить данные", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Если нет состояния командного центра или генерала — создаём дефолтные записи
            var newState = state
            var newGeneral = g
            if (newState == null) {
                val defaultState = CommandCenterState(rulerName = user.rulerName)
                withContext(Dispatchers.IO) {
                    commandCenterDao.insertState(defaultState)
                }
                newState = defaultState
                // Пытаемся отправить на сервер, но не блокируем пользователя при ошибке
                try {
                    OnlineCommandCenterSync.syncUp(db, user.rulerName)
                } catch (_: Exception) {
                    // Игнорируем
                }
            }
            if (newGeneral == null) {
                val defaultGeneral = GeneralState(rulerName = user.rulerName)
                withContext(Dispatchers.IO) {
                    generalDao.insert(defaultGeneral)
                }
                newGeneral = defaultGeneral
                try {
                    OnlineGeneralSync.syncUp(db, user.rulerName)
                } catch (_: Exception) {
                    // Игнорируем
                }
            }
            // Сохраняем и отображаем данные
            country = c
            ccState = newState
            general = newGeneral
            bindUi()
        }
    }

    private fun bindUi() {
        val c = country ?: return
        val s = ccState ?: return
        val g = general ?: return

        binding.tvLevel.text = "Уровень: ${max(1, c.domik3)}"
        binding.tvMoney.text = "Деньги: ${c.money}"

        // спецслужбы
        binding.tvIntelValue.text = "${s.intel} %"
        binding.tvSabotageValue.text = "${s.sabotage} %"
        binding.tvTheftValue.text = "${s.theft} %"
        binding.tvAgitationValue.text = "${s.agitation} %"

        binding.tvIntelCost.text = costText(s.intel)
        binding.tvSabotageCost.text = costText(s.sabotage)
        binding.tvTheftCost.text = costText(s.theft)
        binding.tvAgitationCost.text = costText(s.agitation)

        // генерал
        bindGeneralUi(g)
    }

    private fun bindGeneralUi(g: GeneralState) {
        binding.tvGeneralLevel.text = "Уровень: ${g.level}"
        binding.tvGeneralStats.text =
            "Атака: ${g.attack}, Защита: ${g.defense}, Лидерство: ${g.leadership}"

        val coeff = calcBattleCoeff(g)
        val coeffRounded = ((coeff * 100).toInt()) / 100.0
        binding.tvGeneralCoeff.text = "Боевой коэффициент генерала: x$coeffRounded"
    }

    private fun costText(percent: Int): String {
        return if (percent >= 100) {
            "Максимум"
        } else {
            val cost = calcUpgradeCost(percent)
            "Стоимость повышения: $cost"
        }
    }

    // стоимость повышения спецслужб на +5%
    private fun calcUpgradeCost(currentPercent: Int): Int {
        val p = currentPercent.coerceIn(0, 99)
        return 25 + (p * p * 5)
    }

    private fun setupButtons() = with(binding) {
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        btnIntelPlus.setOnClickListener { onUpgradeParam(ParamType.INTEL) }
        btnSabotagePlus.setOnClickListener { onUpgradeParam(ParamType.SABOTAGE) }
        btnTheftPlus.setOnClickListener { onUpgradeParam(ParamType.THEFT) }
        btnAgitationPlus.setOnClickListener { onUpgradeParam(ParamType.AGITATION) }

        btnTrainAttack.setOnClickListener { trainGeneralStat(StatType.ATTACK) }
        btnTrainDefense.setOnClickListener { trainGeneralStat(StatType.DEFENSE) }
        btnTrainLeadership.setOnClickListener { trainGeneralStat(StatType.LEADERSHIP) }
        btnOpenSpecialOps.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SpecialOpsFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun onUpgradeParam(type: ParamType) {
        val c = country ?: return
        val s = ccState ?: return

        val current = when (type) {
            ParamType.INTEL     -> s.intel
            ParamType.SABOTAGE  -> s.sabotage
            ParamType.THEFT     -> s.theft
            ParamType.AGITATION -> s.agitation
        }

        if (current >= 100) {
            Toast.makeText(requireContext(), "Уже максимум (100%)", Toast.LENGTH_SHORT).show()
            return
        }

        val cost = calcUpgradeCost(current)
        if (c.money < cost) {
            Toast.makeText(
                requireContext(),
                "Недостаточно денег. Нужно $cost, у тебя ${c.money}.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val newPercent = min(current + 5, 100)

        val newCountry = c.copy(money = c.money - cost)
        val newState = when (type) {
            ParamType.INTEL     -> s.copy(intel = newPercent)
            ParamType.SABOTAGE  -> s.copy(sabotage = newPercent)
            ParamType.THEFT     -> s.copy(theft = newPercent)
            ParamType.AGITATION -> s.copy(agitation = newPercent)
        }
        val name = when (type) {
            ParamType.INTEL     -> "Разведка"
            ParamType.SABOTAGE  -> "Диверсия"
            ParamType.THEFT     -> "Воровство"
            ParamType.AGITATION -> "Вербовка"
        }
        val ruler = c.rulerName
        // Выполняем запись в БД и синхронизацию на сервере в корутине, чтобы не блокировать UI
        viewLifecycleOwner.lifecycleScope.launch {
            // Сначала записываем изменения в локальную БД на фоне
            withContext(Dispatchers.IO) {
                db.countryDao().insertCountry(newCountry)
                commandCenterDao.insertState(newState)
            }
            // Обновляем локальные поля и UI на главном потоке
            country = newCountry
            ccState = newState
            bindUi()
            Toast.makeText(
                requireContext(),
                "$name повышена до $newPercent% (списано $cost денег).",
                Toast.LENGTH_SHORT
            ).show()
            // Затем отправляем обновления на сервер
            try {
                OnlineCountrySync.syncUp(db, ruler)
                OnlineCommandCenterSync.syncUp(db, ruler)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка отправки на сервер: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun trainGeneralStat(type: StatType) {
        val c = country ?: return
        val g = general ?: return

        val currentValue = when (type) {
            StatType.ATTACK     -> g.attack
            StatType.DEFENSE    -> g.defense
            StatType.LEADERSHIP -> g.leadership
        }

        val cost = calcTrainCost(currentValue)
        if (c.money < cost) {
            Toast.makeText(
                requireContext(),
                "Недостаточно денег для тренировки. Нужно $cost, у тебя ${c.money}.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val newCountry = c.copy(money = c.money - cost)
        // Вычисляем новые параметры генерала
        val updated = when (type) {
            StatType.ATTACK     -> g.copy(attack = g.attack + 1)
            StatType.DEFENSE    -> g.copy(defense = g.defense + 1)
            StatType.LEADERSHIP -> g.copy(leadership = g.leadership + 1)
        }
        val sumStats = updated.attack + updated.defense + updated.leadership
        val newLevel = 1 + sumStats / 5
        val newG = updated.copy(level = newLevel)
        val statName = when (type) {
            StatType.ATTACK     -> "атака"
            StatType.DEFENSE    -> "защита"
            StatType.LEADERSHIP -> "лидерство"
        }
        val ruler = c.rulerName
        // Записываем изменения и синхронизируем с сервером в корутине
        viewLifecycleOwner.lifecycleScope.launch {
            // Сначала записываем изменения в БД в фоновом потоке
            withContext(Dispatchers.IO) {
                db.countryDao().insertCountry(newCountry)
                generalDao.insert(newG)
            }
            // Обновляем локальные поля и UI на главном потоке
            country = newCountry
            general = newG
            bindUi()
            Toast.makeText(
                requireContext(),
                "Генерал: $statName повышена до ${currentValue + 1} (списано $cost денег).",
                Toast.LENGTH_SHORT
            ).show()
            // Затем отправляем на сервер
            try {
                OnlineCountrySync.syncUp(db, ruler)
                OnlineGeneralSync.syncUp(db, ruler)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка отправки на сервер: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // стоимость +1 очка стата генерала
    private fun calcTrainCost(currentValue: Int): Int {
        val v = currentValue.coerceAtLeast(0)
        return 100 + v * v * 20
    }

    // боевой коэффициент генерала (будем использовать в военной базе)
    private fun calcBattleCoeff(g: GeneralState): Double {
        val attackBonus = g.attack * 0.05
        val defenseBonus = g.defense * 0.03
        val leadershipBonus = g.leadership * 0.02
        val coeff = 1.0 + attackBonus + defenseBonus + leadershipBonus
        return coeff.coerceAtMost(3.0)
    }

    // пригодится позже, когда пойдём в WarBaseFragment
    fun calcArmyPower(baseAttack: Int, baseDefense: Int, g: GeneralState): Double {
        val base = (baseAttack + baseDefense).coerceAtLeast(0)
        val coeff = calcBattleCoeff(g)
        return base * coeff
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class ParamType {
        INTEL, SABOTAGE, THEFT, AGITATION
    }

    private enum class StatType {
        ATTACK, DEFENSE, LEADERSHIP
    }

    companion object {
        fun newInstance() = CommandCenterFragment()
    }
}
