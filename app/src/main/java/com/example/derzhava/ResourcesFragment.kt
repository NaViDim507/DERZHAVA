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
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentResourcesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ResourcesFragment : Fragment() {

    private var _binding: FragmentResourcesBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private lateinit var db: AppDatabase

    override fun onAttach(context: Context) {
        super.onAttach(context)
        userRepository = UserRepository(context)
        db = AppDatabase.getInstance(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResourcesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден, вход...", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        lifecycleScope.launch {
            val country = withContext(Dispatchers.IO) {
                db.countryDao().getCountryByRuler(user.rulerName)
            }

            if (country == null) {
                Toast.makeText(requireContext(), "Страна не найдена", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // --- Если нет Комбината — никакого производства ресурсов ---
            if (country.domik1 == 0) {
                val metal = country.metal
                val mineral = country.mineral
                val wood = country.wood
                val food = country.food
                val total = metal + mineral + wood + food

                binding.tvTotalResources.text = "Всего ресурсов: $total"
                binding.tvMetal.text = "$metal (+0 в час)"
                binding.tvMineral.text = "$mineral (+0 в час)"
                binding.tvWood.text = "$wood (+0 в час)"
                binding.tvFood.text = "$food (+0 в час)"

                // Можно при желании подсказать:
                // Toast.makeText(requireContext(), "Нет Комбината — ресурсы не производятся", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // --- Текущее количество ресурсов (аналог stg4..stg7) ---
            val metal = country.metal
            val mineral = country.mineral
            val wood = country.wood
            val food = country.food

            val total = metal + mineral + wood + food
            binding.tvTotalResources.text = "Всего ресурсов: $total"

            // --- Производство ресурсов в час (dm/di/dd/dp из der1) ---
            var incomeMetal = ((country.metallWorkers * country.scienceMetal) / 100.0).roundToInt()
            var incomeMineral = ((country.mineWorkers * country.scienceStone) / 100.0).roundToInt()
            var incomeWood = ((country.woodWorkers * country.scienceWood) / 100.0).roundToInt()
            var incomeFood = ((country.industryWorkers * country.scienceFood) / 100.0).roundToInt()

            // Проверка, хватает ли шахт/рудников/лесов/полей (как в game.php case 'ress')
            val x1 = (country.shah - country.metallWorkers / 10.0).roundToInt()
            val x2 = (country.rudn - country.mineWorkers / 10.0).roundToInt()
            val x3 = (country.lesa - country.woodWorkers / 10.0).roundToInt()
            val x4 = (country.pole - country.industryWorkers / 10.0).roundToInt()

            if (x1 <= 0) incomeMetal = 0
            if (x2 <= 0) incomeMineral = 0
            if (x3 <= 0) incomeWood = 0
            if (x4 <= 0) incomeFood = 0

            // --- Вывод "как в der1: Ресурсы -> значение(+прирост в час)" ---
            binding.tvMetal.text = "$metal (+$incomeMetal в час)"
            binding.tvMineral.text = "$mineral (+$incomeMineral в час)"
            binding.tvWood.text = "$wood (+$incomeWood в час)"
            binding.tvFood.text = "$food (+$incomeFood в час)"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ResourcesFragment()
    }
}
