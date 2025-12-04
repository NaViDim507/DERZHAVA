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
import com.example.derzhava.databinding.FragmentPopulationBinding
import com.example.derzhava.net.OnlineArmySync
import com.example.derzhava.net.OnlineCountrySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PopulationFragment : Fragment() {

    private var _binding: FragmentPopulationBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPopulationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Авторизуйтесь", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val country = withContext(Dispatchers.IO) {
                db.countryDao().getCountryByRuler(user.rulerName)
            }
            if (country == null) {
                Toast.makeText(requireContext(), "Страна не найдена", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val freeWorkers = country.workers
            val productionWorkers = country.metallWorkers + country.mineWorkers +
                    country.woodWorkers + country.industryWorkers
            val scientists = country.bots

            // Армию берём из ArmyState, поскольку значения peh/kaz/gva в CountryEntity хранятся только для совместимости.
            val army = withContext(Dispatchers.IO) { db.armyDao().getByRuler(user.rulerName) }
            val infantry = army?.infantry ?: country.peh
            val cossacks = army?.cossacks ?: country.kaz
            val guards = army?.guards ?: country.gva

            val totalPopulation = freeWorkers + productionWorkers + scientists + infantry + cossacks + guards

            binding.tvTitle.text = "Население"
            binding.tvTotalPopulation.text = "Общее население: $totalPopulation"

            binding.tvWorkersFree.text = freeWorkers.toString()
            binding.tvWorkersProduction.text = productionWorkers.toString()
            binding.tvScientists.text = scientists.toString()
            binding.tvInfantry.text = infantry.toString()
            binding.tvCossacks.text = cossacks.toString()
            binding.tvGuards.text = guards.toString()

            binding.btnBack.setOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val user = userRepository.getUser() ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    OnlineCountrySync.syncDownOrCreate(db, user.rulerName, user.countryName)
                    OnlineArmySync.syncDown(db, user.rulerName)
                }
            } catch (_: Exception) {
                // игнорируем сетевые ошибки
            }

            val country = withContext(Dispatchers.IO) { db.countryDao().getCountryByRuler(user.rulerName) }
            if (country == null) return@launch
            val army = withContext(Dispatchers.IO) { db.armyDao().getByRuler(user.rulerName) }
            val freeWorkers = country.workers
            val productionWorkers = country.metallWorkers + country.mineWorkers +
                    country.woodWorkers + country.industryWorkers
            val scientists = country.bots
            val infantry = army?.infantry ?: country.peh
            val cossacks = army?.cossacks ?: country.kaz
            val guards = army?.guards ?: country.gva

            val totalPopulation =
                freeWorkers + productionWorkers + scientists + infantry + cossacks + guards

            binding.tvTotalPopulation.text = "Общее население: $totalPopulation"
            binding.tvWorkersFree.text = freeWorkers.toString()
            binding.tvWorkersProduction.text = productionWorkers.toString()
            binding.tvScientists.text = scientists.toString()
            binding.tvInfantry.text = infantry.toString()
            binding.tvCossacks.text = cossacks.toString()
            binding.tvGuards.text = guards.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PopulationFragment()
    }
}
