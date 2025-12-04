package com.example.derzhava

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.databinding.FragmentAdminNpcDetailsBinding
import kotlin.math.max

class AdminNpcDetailsFragment : Fragment() {

    private var _binding: FragmentAdminNpcDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var countryDao: CountryDao
    private lateinit var armyDao: ArmyDao

    private lateinit var rulerName: String
    private lateinit var npc: CountryEntity
    private var army: ArmyState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rulerName = requireArguments().getString(ARG_RULER_NAME)
            ?: throw IllegalArgumentException("rulerName is required")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminNpcDetailsBinding.inflate(inflater, container, false)

        val db = AppDatabase.getInstance(requireContext())
        countryDao = db.countryDao()
        armyDao = db.armyDao()

        npc = countryDao.getNpcByRuler(rulerName)
            ?: countryDao.getCountryByRuler(rulerName)
                    ?: throw IllegalStateException("NPC-страна для ruler=$rulerName не найдена")

        army = armyDao.getByRuler(rulerName)

        bindData()

        binding.btnSave.setOnClickListener { saveNpc() }
        binding.btnNpcMarket.setOnClickListener {
            val rulerName = npc.rulerName   // npc: CountryEntity, он у тебя уже есть
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdminNpcMarketFragment.newInstance(rulerName))
                .addToBackStack(null)
                .commit()
        }
        binding.btnBackToAdmin.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return binding.root
    }

    private fun bindData() = with(binding) {
        tvTitle.text = "НПС: ${npc.countryName}"

        etCountryName.setText(npc.countryName)
        etRulerName.setText(npc.rulerName)
        etNpcNote.setText(npc.npcNote ?: "")

        etMoney.setText(npc.money.toString())
        etWorkers.setText(npc.workers.toString())
        etLand.setText(npc.land.toString())
        etFood.setText(npc.food.toString())
        etWood.setText(npc.wood.toString())
        etMineral.setText(npc.mineral.toString())

        etDom1.setText(npc.domik1.toString())
        etDom2.setText(npc.domik2.toString())
        etDom3.setText(npc.domik3.toString())
        etDom4.setText(npc.domik4.toString())
        etDom5.setText(npc.domik5.toString())
        etDom6.setText(npc.domik6.toString())
        etDom7.setText(npc.domik7.toString())

        val a = army ?: ArmyState(rulerName = npc.rulerName)
        etInfantry.setText(a.infantry.toString())
        etCossacks.setText(a.cossacks.toString())
        etGuards.setText(a.guards.toString())
        etCatapults.setText(a.catapults.toString())
    }

    private fun parseInt(et: EditText, def: Int): Int {
        val text = et.text.toString().trim()
        return text.toIntOrNull() ?: def
    }

    private fun saveNpc() {
        val oldRuler = npc.rulerName

        val newCountryName = binding.etCountryName.text.toString().trim()
            .ifEmpty { npc.countryName }
        val newRulerName = binding.etRulerName.text.toString().trim()
            .ifEmpty { npc.rulerName }
        val noteText = binding.etNpcNote.text.toString().trim()
            .ifEmpty { null }

        val updatedNpc = npc.copy(
            countryName = newCountryName,
            rulerName = newRulerName,
            npcNote = noteText,
            isNpc = true, // на всякий случай фиксируем, что это именно НПС
            money   = max(0, parseInt(binding.etMoney, npc.money)),
            workers = max(0, parseInt(binding.etWorkers, npc.workers)),
            land    = max(0, parseInt(binding.etLand, npc.land)),
            food    = max(0, parseInt(binding.etFood, npc.food)),
            wood    = max(0, parseInt(binding.etWood, npc.wood)),
            mineral = max(0, parseInt(binding.etMineral, npc.mineral)),
            domik1  = max(0, parseInt(binding.etDom1, npc.domik1)),
            domik2  = max(0, parseInt(binding.etDom2, npc.domik2)),
            domik3  = max(0, parseInt(binding.etDom3, npc.domik3)),
            domik4  = max(0, parseInt(binding.etDom4, npc.domik4)),
            domik5  = max(0, parseInt(binding.etDom5, npc.domik5)),
            domik6  = max(0, parseInt(binding.etDom6, npc.domik6)),
            domik7  = max(0, parseInt(binding.etDom7, npc.domik7))
        )
        countryDao.insertCountry(updatedNpc)

        val currentArmy = army ?: ArmyState(rulerName = oldRuler)
        val updatedArmy = currentArmy.copy(
            rulerName = newRulerName,
            infantry  = max(0, parseInt(binding.etInfantry, currentArmy.infantry)),
            cossacks  = max(0, parseInt(binding.etCossacks, currentArmy.cossacks)),
            guards    = max(0, parseInt(binding.etGuards, currentArmy.guards)),
            catapults = max(0, parseInt(binding.etCatapults, currentArmy.catapults))
        )
        armyDao.insert(updatedArmy)

        // Если поменяли имя правителя НПС – подчистим старую запись армии
        if (oldRuler != newRulerName) {
            armyDao.deleteByRuler(oldRuler)
        }

        Toast.makeText(requireContext(), "NPC-страна сохранена", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RULER_NAME = "ruler_name"

        fun newInstance(rulerName: String): AdminNpcDetailsFragment {
            val f = AdminNpcDetailsFragment()
            f.arguments = Bundle().apply {
                putString(ARG_RULER_NAME, rulerName)
            }
            return f
        }
    }
}
