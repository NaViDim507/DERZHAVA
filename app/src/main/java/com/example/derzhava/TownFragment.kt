package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.data.WarDao
import com.example.derzhava.databinding.FragmentTownBinding
import com.example.derzhava.data.ProductionManager

/**
 * Городок (domm/town/index.php).
 * Сообщения и формулы максимально близки к PHP.
 */
class TownFragment : Fragment() {

    private var _binding: FragmentTownBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository
    private lateinit var armyDao: ArmyDao
    private lateinit var warDao: WarDao

    private var country: CountryEntity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)
        armyDao = db.armyDao()
        warDao = db.warDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAndRender()
        setupButtons()
    }

    /**
     * Грузим страну и ОДИН РАЗ инициализируем время последнего сбора налогов,
     * если оно ещё нулевое (аналог datanal в der1).
     */
    fun loadAndRender() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден, вход...", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        val c0 = db.countryDao().getCountryByRuler(user.rulerName)
        if (c0 == null) {
            Toast.makeText(requireContext(), "Страна не найдена", Toast.LENGTH_SHORT).show()
            return
        }

        // Инициализация времени налогов, если ещё не было (как раньше)
        val withTaxInit =
            if (c0.lastTaxTime == 0L) {
                val now = System.currentTimeMillis()
                val updated = c0.copy(lastTaxTime = now)
                db.countryDao().insertCountry(updated)
                updated
            } else {
                c0
            }

        // ---- Оффлайн-производство ресурсов и прирост населения (через ProductionManager) ----
        val now = System.currentTimeMillis()
        val withProduction = ProductionManager.applyHourlyProductionAndGrowth(withTaxInit, now)
        if (withProduction != withTaxInit) {
            db.countryDao().insertCountry(withProduction)
        }

        country = withProduction
        renderTown(withProduction)
    }

    private fun renderTown(c: CountryEntity) {
        binding.tvTitle.text = "Городок"
        binding.tvLevel.text = "Уровень: 1"

        // ----- Население и армия -----

        // Все рабочие (свободные + распределённые по отраслям)
        val totalWorkersBlock =
            c.workers + c.metallWorkers + c.mineWorkers + c.woodWorkers + c.industryWorkers

        val armyState: ArmyState =
            armyDao.getByRuler(c.rulerName) ?: ArmyState(rulerName = c.rulerName)

        // Войны нужны только для потребления зерна
        val wars = warDao.getWarsForRuler(c.rulerName)
            .filter { it.attackerRuler == c.rulerName && !it.isResolved }

        val warInfantry = wars.sumOf { it.peh }
        val warCossacks = wars.sumOf { it.kaz }
        val warGuards = wars.sumOf { it.gva }

        // Армия в бункере
        val bunkerInfantry = c.bunkerInfantry
        val bunkerCossacks = c.bunkerCossacks
        val bunkerGuards = c.bunkerGuards

        // Общее население — ТОЛЬКО рабочие и войска в бункере (по твоему требованию)
        val totalPopulation =
            totalWorkersBlock + bunkerInfantry + bunkerCossacks + bunkerGuards

        // Прирост из der1: $rab=round((($ww1/2)/100)*$www);
        // ww1 = все рабочие + люди на рынке (5/6). Рынок пока не реализован, поэтому берём только рабочих.
        val ww1 = totalWorkersBlock
        val baseRabGrowth = (((ww1 / 2.0) / 100.0) * c.scienceGrowthBonus).roundToInt()

        // Прирост возможен ТОЛЬКО если есть и Комбинат, и Городок
        val canGrowPopulation = (c.domik1 == 1 && c.domik2 == 1)

        val rabGrowth =
            if (canGrowPopulation && c.populationGrowthEnabled) baseRabGrowth else 0

        val growthStatus = when {
            !canGrowPopulation ->
                "недоступен (нет Комбината или Городка)"
            c.populationGrowthEnabled ->
                "включен"
            else ->
                "выключен"
        }

        // Потребление зерна zz, формула как в PHP (z1 = rab+bot+rab*...; дальше армия + бункер + война)
        val z1 = c.workers + c.bots +
                c.metallWorkers + c.mineWorkers + c.woodWorkers + c.industryWorkers

        val zz = z1 * 2 +
                (armyState.infantry + warInfantry) * 5 +
                (armyState.cossacks + warCossacks) * 10 +
                (armyState.guards + warGuards) * 15 +
                bunkerInfantry * 5 +
                bunkerCossacks * 10 +
                bunkerGuards * 15

        // Тексты в стиле gorodok.php
        binding.tvPopulation.text = "Общее население(${totalPopulation})"
        binding.tvWorkers.text = "Свободные рабочие[${c.workers}]"

        val totalArmyAll =
            armyState.infantry + armyState.cossacks + armyState.guards +
                    warInfantry + warCossacks + warGuards +
                    bunkerInfantry + bunkerCossacks + bunkerGuards
        binding.tvArmyPeople.text = "Военные: $totalArmyAll"

        binding.tvGrowthInfo.text =
            "Прирост(+${rabGrowth})[$growthStatus]"

        // кнопка — только если оба здания есть
        binding.btnToggleGrowth.isEnabled = canGrowPopulation
        binding.btnToggleGrowth.text =
            if (c.populationGrowthEnabled) "выключить" else "включить"

        binding.tvGrainInfo.text = "Общее потребление зерна[${zz}]"

        // Жильё (пока просто равно количеству людей блока рабочих)
        binding.tvHousing.text = "Дома: ${totalWorkersBlock} / ${totalWorkersBlock} мест"

        // ----- Налоги -----
        val now = System.currentTimeMillis()
        val diffSec = ((now - c.lastTaxTime) / 1000).coerceAtLeast(0)
        val hours = diffSec / 3600
        val minutes = (diffSec % 3600) / 60
        val seconds = diffSec % 60
        val taxAmount = ((diffSec / 3600.0) * 1000.0).roundToInt()

        binding.tvTaxesInfo.text =
            "С последнего сбора налогов прошло\n" +
                    "${hours} час.${minutes} мин.${seconds} сек.\n" +
                    "Накоплено ($taxAmount) денег"

        binding.tvStashInfo.text = "В тайнике (${c.stashMoney}) денег"

        binding.tvBunkerInfo.text =
            "В бункере: пехота-(${bunkerInfantry}) " +
                    "казаки-(${bunkerCossacks}) гвардия-(${bunkerGuards})"
    }

    private fun setupButtons() = with(binding) {
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnToggleGrowth.setOnClickListener {
            val c = country ?: return@setOnClickListener
            togglePopulationGrowth(c)
        }

        btnCollectTaxes.setOnClickListener {
            val c = country ?: return@setOnClickListener
            showCollectTaxesDialog(c)
        }

        btnStashToggle.setOnClickListener {
            val c = country ?: return@setOnClickListener
            toggleStash(c)
        }

        btnBunkerSend.setOnClickListener {
            val c = country ?: return@setOnClickListener
            sendArmyToBunker(c)
        }

        btnBunkerReturn.setOnClickListener {
            val c = country ?: return@setOnClickListener
            returnArmyFromBunker(c)
        }

        btnKickWorkers.setOnClickListener {
            val c = country ?: return@setOnClickListener
            showKickWorkersDialog(c)
        }
    }

    // ---------- Прирост населения (gor=r1) ----------

    private fun togglePopulationGrowth(c: CountryEntity) {
        // Жёсткое правило: без Комбината или Городка — прирост недоступен
        if (c.domik1 == 0 || c.domik2 == 0) {
            Toast.makeText(
                requireContext(),
                "Нет Комбината или Городка — прирост населения недоступен.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val updated = c.copy(populationGrowthEnabled = !c.populationGrowthEnabled)
        db.countryDao().insertCountry(updated)
        country = updated

        val status = if (updated.populationGrowthEnabled) "включен" else "выключен"
        Toast.makeText(requireContext(), "Прирост $status.", Toast.LENGTH_SHORT).show()
        renderTown(updated)
    }

    // ---------- Налоги (gor=2,4) ----------

    private fun showCollectTaxesDialog(c: CountryEntity) {
        val ctx = requireContext()
        val now = System.currentTimeMillis()
        val diffSec = ((now - c.lastTaxTime) / 1000).coerceAtLeast(0)
        val hours = diffSec / 3600
        val minutes = (diffSec % 3600) / 60
        val seconds = diffSec % 60
        val taxAmount = ((diffSec / 3600.0) * 1000.0).roundToInt()

        if (taxAmount <= 0) {
            Toast.makeText(ctx, "Пока нечего собирать.", Toast.LENGTH_SHORT).show()
            return
        }

        val msg = "С последнего сбора налогов прошло\n" +
                "${hours} час.${minutes} мин.${seconds} сек.\n" +
                "Накоплено ($taxAmount) денег"

        AlertDialog.Builder(ctx)
            .setTitle("Сбор налогов")
            .setMessage(msg)
            .setPositiveButton("Собрать налог") { _, _ ->
                collectTaxes(c)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun collectTaxes(c: CountryEntity) {
        val now = System.currentTimeMillis()
        val diffSec = ((now - c.lastTaxTime) / 1000).coerceAtLeast(0)
        val taxAmount = ((diffSec / 3600.0) * 1000.0).roundToInt()

        if (taxAmount <= 0) {
            Toast.makeText(requireContext(), "Пока нечего собирать.", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = c.copy(
            money = c.money + taxAmount,
            lastTaxTime = now
        )
        db.countryDao().insertCountry(updated)
        country = updated

        Toast.makeText(requireContext(), "Вы сняли $taxAmount денег", Toast.LENGTH_SHORT).show()
        renderTown(updated)
    }

    // ---------- Тайник (gor=zan,8,9) ----------

    private fun toggleStash(c: CountryEntity) {
        val ctx = requireContext()

        if (c.stashMoney == 0) {
            val toStash = (c.money / 4.0).roundToInt()
            if (toStash <= 0) {
                Toast.makeText(ctx, "Недостаточно денег для тайника.", Toast.LENGTH_SHORT).show()
                return
            }

            AlertDialog.Builder(ctx)
                .setTitle("Тайник")
                .setMessage("Добавить $toStash денег в тайник (1/4 от ${c.money})?")
                .setPositiveButton("Добавить") { _, _ ->
                    val updated = c.copy(
                        money = c.money - toStash,
                        stashMoney = c.stashMoney + toStash
                    )
                    db.countryDao().insertCountry(updated)
                    country = updated
                    Toast.makeText(ctx, "Вы добавили $toStash в тайник.", Toast.LENGTH_SHORT).show()
                    renderTown(updated)
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            AlertDialog.Builder(ctx)
                .setTitle("Тайник")
                .setMessage("В тайнике (${c.stashMoney}) денег. Забрать всё?")
                .setPositiveButton("Забрать") { _, _ ->
                    val updated = c.copy(
                        money = c.money + c.stashMoney,
                        stashMoney = 0
                    )
                    db.countryDao().insertCountry(updated)
                    country = updated
                    Toast.makeText(ctx, "Вы забрали ${c.stashMoney} из тайника.", Toast.LENGTH_SHORT).show()
                    renderTown(updated)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    // ---------- Бункер (gor=3,5,7) ----------

    private fun sendArmyToBunker(c: CountryEntity) {
        val ctx = requireContext()
        val army = armyDao.getByRuler(c.rulerName) ?: ArmyState(rulerName = c.rulerName)

        val total = army.infantry + army.cossacks + army.guards
        if (total <= 0) {
            Toast.makeText(ctx, "Нет войск для размещения в бункере.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(ctx)
            .setTitle("Бункер для армии")
            .setMessage(
                "Отправить всю пехоту, казаков и гвардию в бункер?\n" +
                        "Сейчас: пехота ${army.infantry}, казаки ${army.cossacks}, гвардия ${army.guards}."
            )
            .setPositiveButton("Добавить") { _, _ ->
                val updatedArmy = army.copy(
                    infantry = 0,
                    cossacks = 0,
                    guards = 0
                )
                val updatedCountry = c.copy(
                    bunkerInfantry = c.bunkerInfantry + army.infantry,
                    bunkerCossacks = c.bunkerCossacks + army.cossacks,
                    bunkerGuards = c.bunkerGuards + army.guards
                )

                armyDao.insert(updatedArmy)
                db.countryDao().insertCountry(updatedCountry)
                country = updatedCountry
                Toast.makeText(ctx, "Войска укрыты в бункере.", Toast.LENGTH_SHORT).show()
                renderTown(updatedCountry)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun returnArmyFromBunker(c: CountryEntity) {
        val ctx = requireContext()
        if (c.bunkerInfantry + c.bunkerCossacks + c.bunkerGuards <= 0) {
            Toast.makeText(ctx, "В бункере нет войск.", Toast.LENGTH_SHORT).show()
            return
        }

        val army = armyDao.getByRuler(c.rulerName) ?: ArmyState(rulerName = c.rulerName)

        AlertDialog.Builder(ctx)
            .setTitle("Бункер для армии")
            .setMessage(
                "Забрать войска из бункера?\n" +
                        "В бункере: пехота ${c.bunkerInfantry}, казаки ${c.bunkerCossacks}, гвардия ${c.bunkerGuards}."
            )
            .setPositiveButton("Забрать") { _, _ ->
                val updatedArmy = army.copy(
                    infantry = army.infantry + c.bunkerInfantry,
                    cossacks = army.cossacks + c.bunkerCossacks,
                    guards = army.guards + c.bunkerGuards
                )
                val updatedCountry = c.copy(
                    bunkerInfantry = 0,
                    bunkerCossacks = 0,
                    bunkerGuards = 0
                )

                armyDao.insert(updatedArmy)
                db.countryDao().insertCountry(updatedCountry)
                country = updatedCountry
                Toast.makeText(ctx, "Войска выведены из бункера.", Toast.LENGTH_SHORT).show()
                renderTown(updatedCountry)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- Выгнать рабочих (gor=6) ----------

    private fun showKickWorkersDialog(c: CountryEntity) {
        val ctx = requireContext()
        val user = userRepository.getUser() ?: return

        val wars = warDao.getWarsForRuler(user.rulerName)
        val hasInvasion =
            wars.any { it.defenderRuler == user.rulerName && it.state == "active" && !it.isResolved }
        if (hasInvasion) {
            Toast.makeText(ctx, "Вы не можете выгнать рабочих при вторжении", Toast.LENGTH_SHORT).show()
            return
        }

        if (c.workers <= 0) {
            Toast.makeText(ctx, "Свободных рабочих нет.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Сколько выгнать?"
        }

        AlertDialog.Builder(ctx)
            .setTitle("Выгнать рабочих")
            .setMessage("Свободные рабочие: ${c.workers}. Сколько выгнать?")
            .setView(input)
            .setPositiveButton("Выгнать") { _, _ ->
                val value = input.text.toString().trim()
                val count = value.toIntOrNull() ?: 0

                if (count <= 0 || count > c.workers) {
                    Toast.makeText(ctx, "У вас нет столько рабочих", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val updated = c.copy(
                    workers = c.workers - count
                )

                db.countryDao().insertCountry(updated)
                country = updated
                Toast.makeText(ctx, "Вы выгнали $count рабочих!", Toast.LENGTH_SHORT).show()
                renderTown(updated)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TownFragment()
    }
}
