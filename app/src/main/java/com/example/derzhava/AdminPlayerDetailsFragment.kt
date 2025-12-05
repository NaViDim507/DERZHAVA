package com.example.derzhava

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.CommandCenterDao
import com.example.derzhava.data.CommandCenterState
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.GeneralDao
import com.example.derzhava.data.GeneralState
import com.example.derzhava.databinding.FragmentAdminPlayerDetailsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Экран редактирования параметров игрока (не NPC).
 * Показывает информацию о дате регистрации, последнем визите, статусе (админ/игрок)
 * и позволяет менять ресурсы, распределение рабочих, учёных, здания, армию,
 * параметры командного центра и генерала. Данные сохраняются на сервере
 * через соответствующие DAO.
 */
class AdminPlayerDetailsFragment : Fragment() {

    private var _binding: FragmentAdminPlayerDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var countryDao: CountryDao
    private lateinit var armyDao: ArmyDao
    private lateinit var commandCenterDao: CommandCenterDao
    private lateinit var generalDao: GeneralDao

    private lateinit var rulerName: String
    private lateinit var country: CountryEntity
    private var army: ArmyState? = null
    private var ccState: CommandCenterState? = null
    private var general: GeneralState? = null

    // Информация о пользователе (регистрация / последний вход / админ)
    private var registrationTime: Long = 0L
    private var lastLoginTime: Long = 0L
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rulerName = requireArguments().getString(ARG_RULER_NAME)
            ?: throw IllegalArgumentException("rulerName is required")
        // Время регистрации и последнего входа и флаг админа передаются аргументами.
        registrationTime = requireArguments().getLong(ARG_REG_TIME, 0L)
        lastLoginTime = requireArguments().getLong(ARG_LAST_LOGIN, 0L)
        isAdmin = requireArguments().getBoolean(ARG_IS_ADMIN, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminPlayerDetailsBinding.inflate(inflater, container, false)
        val db = AppDatabase.getInstance(requireContext())
        countryDao = db.countryDao()
        armyDao = db.armyDao()
        commandCenterDao = db.commandCenterDao()
        generalDao = db.generalDao()

        // Загружаем текущее состояние страны
        country = countryDao.getCountryByRuler(rulerName)
            ?: throw IllegalStateException("Страна для игрока $rulerName не найдена")
        army = armyDao.getByRuler(rulerName)
        ccState = commandCenterDao.getStateByRuler(rulerName)
        general = generalDao.getByRuler(rulerName)

        bindData()

        binding.btnSave.setOnClickListener { savePlayer() }
        binding.btnMarket.setOnClickListener {
            // Показываем товары игрока на бирже через уже готовый фрагмент AdminNpcMarketFragment,
            // поскольку он отображает лоты и позволяет их удалять. Используем его и для игроков.
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdminNpcMarketFragment.newInstance(rulerName))
                .addToBackStack(null)
                .commit()
        }
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        return binding.root
    }

    private fun bindData() = with(binding) {
        // Заголовок
        tvTitle.text = "Игрок: ${country.countryName}"
        tvRulerName.text = country.rulerName
        // Даты
        val dateFormatReg = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateFormatLogin = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val regStr = dateFormatReg.format(Date(registrationTime))
        val loginStr = dateFormatLogin.format(Date(lastLoginTime))
        tvRegDate.text = "Регистрация: $regStr"
        tvLastLogin.text = "Последний вход: $loginStr"
        tvStatus.text = if (isAdmin) "Администратор" else "Игрок"

        // Основные ресурсы
        etCountryName.setText(country.countryName)
        etMoney.setText(country.money.toString())
        etWorkers.setText(country.workers.toString())
        etBots.setText(country.bots.toString())
        etMetallWorkers.setText(country.metallWorkers.toString())
        etMineWorkers.setText(country.mineWorkers.toString())
        etWoodWorkers.setText(country.woodWorkers.toString())
        etIndustryWorkers.setText(country.industryWorkers.toString())
        etLand.setText(country.land.toString())
        etFood.setText(country.food.toString())
        etWood.setText(country.wood.toString())
        etMetal.setText(country.metal.toString())
        etMineral.setText(country.mineral.toString())

        // Здания
        etDom1.setText(country.domik1.toString())
        etDom2.setText(country.domik2.toString())
        etDom3.setText(country.domik3.toString())
        etDom4.setText(country.domik4.toString())
        etDom5.setText(country.domik5.toString())
        etDom6.setText(country.domik6.toString())
        etDom7.setText(country.domik7.toString())

        // Армия
        val a = army ?: ArmyState(rulerName = country.rulerName)
        etInfantry.setText(a.infantry.toString())
        etCossacks.setText(a.cossacks.toString())
        etGuards.setText(a.guards.toString())
        etCatapults.setText(a.catapults.toString())

        // Командный центр
        val cc = ccState ?: CommandCenterState(rulerName = country.rulerName)
        etIntel.setText(cc.intel.toString())
        etSabotage.setText(cc.sabotage.toString())
        etTheft.setText(cc.theft.toString())
        etAgitation.setText(cc.agitation.toString())

        // Генерал
        val g = general ?: GeneralState(rulerName = country.rulerName)
        etGenLevel.setText(g.level.toString())
        etGenAttack.setText(g.attack.toString())
        etGenDefense.setText(g.defense.toString())
        etGenLeadership.setText(g.leadership.toString())
        etGenExperience.setText(g.experience.toString())
        etGenBattles.setText(g.battles.toString())
        etGenWins.setText(g.wins.toString())
    }

    private fun parseInt(et: EditText, def: Int): Int {
        val txt = et.text.toString().trim()
        return txt.toIntOrNull() ?: def
    }

    private fun parseLong(et: EditText, def: Long): Long {
        val txt = et.text.toString().trim()
        return txt.toLongOrNull() ?: def
    }

    private fun savePlayer() {
        // Для игрока логин менять не будем, поэтому rulerName остаётся прежним.
        val newCountryName = binding.etCountryName.text.toString().trim()
            .ifEmpty { country.countryName }
        // Обновляем CountryEntity
        val updatedCountry = country.copy(
            countryName = newCountryName,
            money   = max(0, parseInt(binding.etMoney, country.money)),
            workers = max(0, parseInt(binding.etWorkers, country.workers)),
            bots    = max(0, parseInt(binding.etBots, country.bots)),
            metallWorkers = max(0, parseInt(binding.etMetallWorkers, country.metallWorkers)),
            mineWorkers   = max(0, parseInt(binding.etMineWorkers, country.mineWorkers)),
            woodWorkers   = max(0, parseInt(binding.etWoodWorkers, country.woodWorkers)),
            industryWorkers = max(0, parseInt(binding.etIndustryWorkers, country.industryWorkers)),
            land    = max(0, parseInt(binding.etLand, country.land)),
            food    = max(0, parseInt(binding.etFood, country.food)),
            wood    = max(0, parseInt(binding.etWood, country.wood)),
            metal   = max(0, parseInt(binding.etMetal, country.metal)),
            mineral = max(0, parseInt(binding.etMineral, country.mineral)),
            domik1 = max(0, parseInt(binding.etDom1, country.domik1)),
            domik2 = max(0, parseInt(binding.etDom2, country.domik2)),
            domik3 = max(0, parseInt(binding.etDom3, country.domik3)),
            domik4 = max(0, parseInt(binding.etDom4, country.domik4)),
            domik5 = max(0, parseInt(binding.etDom5, country.domik5)),
            domik6 = max(0, parseInt(binding.etDom6, country.domik6)),
            domik7 = max(0, parseInt(binding.etDom7, country.domik7)),
            isNpc = false  // игрок всегда не НПС
        )
        countryDao.insertCountry(updatedCountry)

        // Обновляем армию
        val curArmy = army ?: ArmyState(rulerName = rulerName)
        val newArmy = curArmy.copy(
            infantry = max(0, parseInt(binding.etInfantry, curArmy.infantry)),
            cossacks = max(0, parseInt(binding.etCossacks, curArmy.cossacks)),
            guards   = max(0, parseInt(binding.etGuards, curArmy.guards)),
            catapults = max(0, parseInt(binding.etCatapults, curArmy.catapults))
        )
        armyDao.insert(newArmy)

        // Обновляем состояние командного центра
        val curCc = ccState ?: CommandCenterState(rulerName = rulerName)
        val newCc = curCc.copy(
            intel     = max(0, parseInt(binding.etIntel, curCc.intel)),
            sabotage  = max(0, parseInt(binding.etSabotage, curCc.sabotage)),
            theft     = max(0, parseInt(binding.etTheft, curCc.theft)),
            agitation = max(0, parseInt(binding.etAgitation, curCc.agitation))
        )
        commandCenterDao.insertState(newCc)

        // Обновляем генерала
        val curGen = general ?: GeneralState(rulerName = rulerName)
        val newGen = curGen.copy(
            level      = max(1, parseInt(binding.etGenLevel, curGen.level)),
            attack     = max(0, parseInt(binding.etGenAttack, curGen.attack)),
            defense    = max(0, parseInt(binding.etGenDefense, curGen.defense)),
            leadership = max(0, parseInt(binding.etGenLeadership, curGen.leadership)),
            experience = max(0L, parseLong(binding.etGenExperience, curGen.experience)),
            battles    = max(0, parseInt(binding.etGenBattles, curGen.battles)),
            wins       = max(0, parseInt(binding.etGenWins, curGen.wins))
        )
        generalDao.insert(newGen)

        Toast.makeText(requireContext(), "Данные игрока сохранены", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RULER_NAME = "ruler_name"
        private const val ARG_REG_TIME = "reg_time"
        private const val ARG_LAST_LOGIN = "last_login"
        private const val ARG_IS_ADMIN = "is_admin"

        /**
         * Создаёт новый фрагмент редактирования игрока. В аргументах
         * передаются логин (rulerName), время регистрации, время
         * последнего входа и флаг администратора. Даты и флаг
         * используются только для отображения и не отправляются на сервер.
         */
        fun newInstance(
            rulerName: String,
            registrationTime: Long = 0L,
            lastLoginTime: Long = 0L,
            isAdmin: Boolean = false
        ): AdminPlayerDetailsFragment {
            val f = AdminPlayerDetailsFragment()
            f.arguments = Bundle().apply {
                putString(ARG_RULER_NAME, rulerName)
                putLong(ARG_REG_TIME, registrationTime)
                putLong(ARG_LAST_LOGIN, lastLoginTime)
                putBoolean(ARG_IS_ADMIN, isAdmin)
            }
            return f
        }
    }
}