package com.example.derzhava

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.WarDao
import com.example.derzhava.data.WarLogDao
import com.example.derzhava.data.WarMoveDao
import com.example.derzhava.databinding.FragmentAdminNpcBinding
import com.example.derzhava.AdminNpcDetailsFragment
import kotlin.math.max

class AdminNpcFragment : Fragment() {

    private lateinit var warDao: WarDao
    private lateinit var warMoveDao: WarMoveDao
    private lateinit var warLogDao: WarLogDao

    private var _binding: FragmentAdminNpcBinding? = null
    private val binding get() = _binding!!

    private lateinit var countryDao: CountryDao
    private lateinit var armyDao: ArmyDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminNpcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getInstance(requireContext())
        countryDao = db.countryDao()
        armyDao = db.armyDao()
        warDao = db.warDao()
        warMoveDao = db.warMoveDao()
        warLogDao = db.warLogDao()

        binding.btnAddNpc.setOnClickListener { showCreateNpcDialog() }
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // Новый пункт: открыть список всех игроков (не NPC)
        binding.btnPlayers.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdminPlayersFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }

        renderList()
    }

    override fun onResume() {
        super.onResume()
        // Всегда обновляем список NPC‑стран при возвращении на экран.
        // Без этого при создании или удалении NPC список не пересчитывался,
        // и пользователь видел только заглушку «NPC‑стран ещё нет». Теперь
        // мы повторно загружаем данные в onResume, чтобы отражать все
        // изменения, сделанные на экранах деталей.
        if (_binding != null) {
            renderList()
        }
    }

    private fun renderList() {
        val ctx = requireContext()
        val npcs = countryDao.getNpcCountries()   // список всех NPC-стран

        binding.listContainer.removeAllViews()

        if (npcs.isEmpty()) {
            val tv = TextView(ctx).apply {
                text = "NPC-стран ещё нет. Нажми «Создать NPC-страну»."
                setTextColor(resources.getColor(R.color.derzhava_button_text))
                textSize = 14f
            }
            binding.listContainer.addView(tv)
            return
        }

        for (npc in npcs) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 10, 16, 10)
            }

            val tvName = TextView(ctx).apply {
                text = npc.countryName
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.derzhava_button_text))
            }

            val tvRuler = TextView(ctx).apply {
                text = "Правитель: ${npc.rulerName}"
                textSize = 13f
                setTextColor(resources.getColor(R.color.derzhava_button_text))
            }

            val tvStats = TextView(ctx).apply {
                text = "Деньги: ${npc.money}, Рабочие: ${npc.workers}, Земля: ${npc.land}"
                textSize = 13f
                setTextColor(resources.getColor(R.color.derzhava_button_text))
            }

            row.addView(tvName)
            row.addView(tvRuler)
            row.addView(tvStats)

            // Теперь вместо диалога открываем отдельную страницу НПС
            row.setOnClickListener {
                openNpcDetails(npc.rulerName)
            }

            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }

            binding.listContainer.addView(row, lp)
        }
    }

    private fun showCreateNpcDialog() {
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        fun row(label: String): EditText {
            val tv = TextView(ctx).apply { text = label }
            val et = EditText(ctx).apply { inputType = InputType.TYPE_CLASS_TEXT }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etCountry = row("Название страны")
        val etRuler   = row("Имя правителя")
        val etNote    = row("Примечание (опционально)")

        AlertDialog.Builder(ctx)
            .setTitle("Создать NPC-страну")
            .setView(layout)
            .setPositiveButton("Создать") { _, _ ->
                val countryName = etCountry.text.toString().trim()
                val rulerName = etRuler.text.toString().trim()
                val note = etNote.text.toString().trim().ifEmpty { null }

                if (countryName.isEmpty() || rulerName.isEmpty()) {
                    Toast.makeText(ctx, "Заполни название и правителя.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val npc = CountryEntity(
                    countryName = countryName,
                    rulerName = rulerName,
                    money = 0,
                    workers = 0,
                    food = 0,
                    wood = 0,
                    metal = 0,
                    land = 0,
                    isNpc = true,
                    npcNote = note
                )
                countryDao.insertCountry(npc)

                armyDao.insert(
                    ArmyState(
                        rulerName = rulerName,
                        infantry = 0,
                        cossacks = 0,
                        guards = 0,
                        catapults = 0
                    )
                )

                // Сразу открываем отдельную страницу редактирования НПС
                openNpcDetails(rulerName)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditNpcDialog(npc: CountryEntity) {
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        fun intRow(label: String, value: Int): EditText {
            val tv = TextView(ctx).apply { text = label }
            val et = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etMoney   = intRow("Деньги", npc.money)
        val etWorkers = intRow("Рабочие", npc.workers)
        val etLand    = intRow("Земля", npc.land)
        val etFood    = intRow("Еда", npc.food)
        val etWood    = intRow("Дерево", npc.wood)
        val etMineral = intRow("Минералы", npc.mineral)

        val etDom1 = intRow("Комбинат (domik1)", npc.domik1)
        val etDom2 = intRow("Городок (domik2)", npc.domik2)
        val etDom3 = intRow("Командный центр (domik3)", npc.domik3)
        val etDom4 = intRow("Военная база (domik4)", npc.domik4)
        val etDom5 = intRow("Периметр (domik5)", npc.domik5)
        val etDom6 = intRow("Биржа (domik6)", npc.domik6)
        val etDom7 = intRow("Сторожевая башня (domik7)", npc.domik7)

        val army = armyDao.getByRuler(npc.rulerName) ?: ArmyState(rulerName = npc.rulerName)
        val etInf = intRow("Пехота", army.infantry)
        val etKaz = intRow("Казаки", army.cossacks)
        val etGva = intRow("Гвардия", army.guards)
        val etKat = intRow("Катапульты", army.catapults)

        fun parseInt(et: EditText, def: Int) = et.text.toString().toIntOrNull() ?: def

        AlertDialog.Builder(ctx)
            .setTitle("Редактировать ${npc.countryName}")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val newNpc = npc.copy(
                    money   = parseInt(etMoney, npc.money),
                    workers = parseInt(etWorkers, npc.workers),
                    land    = parseInt(etLand, npc.land),
                    food    = parseInt(etFood, npc.food),
                    wood    = parseInt(etWood, npc.wood),
                    mineral = parseInt(etMineral, npc.mineral),
                    domik1 = parseInt(etDom1, npc.domik1).coerceAtLeast(0),
                    domik2 = parseInt(etDom2, npc.domik2).coerceAtLeast(0),
                    domik3 = parseInt(etDom3, npc.domik3).coerceAtLeast(0),
                    domik4 = parseInt(etDom4, npc.domik4).coerceAtLeast(0),
                    domik5 = parseInt(etDom5, npc.domik5).coerceAtLeast(0),
                    domik6 = parseInt(etDom6, npc.domik6).coerceAtLeast(0),
                    domik7 = parseInt(etDom7, npc.domik7).coerceAtLeast(0)
                )
                countryDao.insertCountry(newNpc)

                val newArmy = army.copy(
                    infantry = parseInt(etInf, army.infantry).coerceAtLeast(0),
                    cossacks = parseInt(etKaz, army.cossacks).coerceAtLeast(0),
                    guards   = parseInt(etGva, army.guards).coerceAtLeast(0),
                    catapults = parseInt(etKat, army.catapults).coerceAtLeast(0)
                )
                armyDao.insert(newArmy)

                renderList()
            }
            .setNeutralButton("Удалить страну") { _, _ ->
                deleteNpcCountry(npc)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteNpcCountry(npc: CountryEntity) {
        val ctx = requireContext()

        AlertDialog.Builder(ctx)
            .setTitle("Удалить ${npc.countryName}?")
            .setMessage("Будут удалены страна, её армия и все войны с её участием.")
            .setPositiveButton("Удалить") { _, _ ->
                val wars = warDao.getWarsForRuler(npc.rulerName)
                if (wars.isNotEmpty()) {
                    val warIds = wars.map { it.id }
                    warMoveDao.deleteByWarIds(warIds)
                    warLogDao.deleteByWarIds(warIds)
                    warDao.deleteWarsOfRuler(npc.rulerName)
                }

                armyDao.deleteByRuler(npc.rulerName)
                countryDao.deleteCountry(npc)

                Toast.makeText(ctx, "NPC-страна удалена.", Toast.LENGTH_SHORT).show()
                renderList()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    private fun openNpcDetails(rulerName: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AdminNpcDetailsFragment.newInstance(rulerName))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AdminNpcFragment()
    }
}
