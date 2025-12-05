package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.GeneralDao
import com.example.derzhava.data.GeneralState
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentGeneralBinding
import com.example.derzhava.net.OnlineCountrySync
import com.example.derzhava.net.OnlineGeneralSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Экран генерала. Показывает текущие характеристики генерала и
 * позволяет тренировать атаку, защиту и лидерство. Стоимость
 * тренировки растёт квадратично от текущего значения навыка.
 */
class GeneralFragment : Fragment() {

    private var _binding: FragmentGeneralBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private lateinit var db: AppDatabase
    private lateinit var countryDao: CountryDao
    private lateinit var generalDao: GeneralDao

    private var country: CountryEntity? = null
    private var general: GeneralState? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        userRepository = UserRepository(context)
        db = AppDatabase.getInstance(context)
        countryDao = db.countryDao()
        generalDao = db.generalDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        lifecycleScope.launch {
            val user = userRepository.getUser()
            if (user == null) {
                Toast.makeText(requireContext(), "Профиль не найден", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                return@launch
            }
            withContext(Dispatchers.IO) {
                country = countryDao.getCountryByRuler(user.rulerName)
                if (country == null) {
                    val newCountry = CountryEntity(rulerName = user.rulerName, countryName = user.countryName)
                    countryDao.insertCountry(newCountry)
                    country = newCountry
                }
                general = generalDao.getByRuler(user.rulerName)
                if (general == null) {
                    val newGeneral = GeneralState(rulerName = user.rulerName)
                    generalDao.insert(newGeneral)
                    general = newGeneral
                }
            }
            refreshUi()
        }
        binding.btnTrainAttack.setOnClickListener { trainStat("attack") }
        binding.btnTrainDefense.setOnClickListener { trainStat("defense") }
        binding.btnTrainLeadership.setOnClickListener { trainStat("leadership") }
    }

    private fun refreshUi() {
        val g = general ?: return
        val level = max(1, (g.attack + g.defense + g.leadership) / 3)
        binding.tvGeneralLevel.text = "Уровень: $level"
        binding.tvGeneralAttack.text = "Атака: ${g.attack}"
        binding.tvGeneralDefense.text = "Защита: ${g.defense}"
        binding.tvGeneralLeadership.text = "Лидерство: ${g.leadership}"
        binding.tvGeneralExperience.text = "Опыт: ${g.experience}"
    }

    private fun trainStat(stat: String) {
        val g = general ?: return
        val c = country ?: return
        val currentValue = when (stat) {
            "attack" -> g.attack
            "defense" -> g.defense
            "leadership" -> g.leadership
            else -> 0
        }
        val cost = 100 + currentValue * currentValue * 20
        if (c.money < cost) {
            Toast.makeText(requireContext(), "Недостаточно денег", Toast.LENGTH_SHORT).show()
            return
        }
        val newGeneral = when (stat) {
            "attack" -> g.copy(attack = g.attack + 1)
            "defense" -> g.copy(defense = g.defense + 1)
            "leadership" -> g.copy(leadership = g.leadership + 1)
            else -> g
        }
        val newLevel = max(1, (newGeneral.attack + newGeneral.defense + newGeneral.leadership) / 3)
        val updatedGeneral = newGeneral.copy(level = newLevel)
        val updatedCountry = c.copy(money = c.money - cost)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                generalDao.insert(updatedGeneral)
                countryDao.insertCountry(updatedCountry)
            }
            country = updatedCountry
            general = updatedGeneral
            refreshUi()
            Toast.makeText(requireContext(), "Тренировка завершена. Стоимость: $cost", Toast.LENGTH_SHORT).show()
            launch(Dispatchers.IO) {
                try {
                    OnlineGeneralSync.syncUp(db, updatedGeneral.rulerName)
                    OnlineCountrySync.syncUp(db, updatedCountry.rulerName)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}