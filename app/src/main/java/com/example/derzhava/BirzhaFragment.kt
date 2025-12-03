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
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.net.OnlineCountrySync
import com.example.derzhava.databinding.FragmentBirzhaBinding

class BirzhaFragment : Fragment() {

    private var _binding: FragmentBirzhaBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var countryDao: CountryDao
    private lateinit var userRepository: UserRepository

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        countryDao = db.countryDao()
        userRepository = UserRepository(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBirzhaBinding.inflate(inflater, container, false)
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

        // Синхронизируем страну перед отображением
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                OnlineCountrySync.syncDownOrCreate(db, user.rulerName, user.countryName)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка обновления страны: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }
            // Получаем страну из БД в фоне
            val country = withContext(Dispatchers.IO) {
                countryDao.getCountryByRuler(user.rulerName)
            }
            if (country == null) {
                Toast.makeText(requireContext(), "Страна не найдена", Toast.LENGTH_SHORT).show()
                return@launch
            }
            render(country)

            binding.btnMetal.setOnClickListener { openResourceScreen(1) }
            binding.btnMineral.setOnClickListener { openResourceScreen(2) }
            binding.btnWood.setOnClickListener { openResourceScreen(3) }
            binding.btnFood.setOnClickListener { openResourceScreen(4) }
            binding.btnWorkers.setOnClickListener { openResourceScreen(5) }
            binding.btnBots.setOnClickListener { openResourceScreen(6) }
        }
    }

    private fun render(c: CountryEntity) = with(binding) {
        tvTitle.text = "БИРЖА"
        tvCountryName.text = c.countryName
        tvRulerName.text = "Правитель: ${c.rulerName}"

        tvMoney.text = "Деньги: ${c.money}"
        tvMetal.text = "Металл: ${c.metal}"
        tvMineral.text = "Камень: ${c.mineral}"
        tvWood.text = "Дерево: ${c.wood}"
        tvFood.text = "Зерно: ${c.food}"
        tvWorkers.text = "Рабочие: ${c.workers}"
        tvBots.text = "Учёные: ${c.bots}"

        btnMetal.text = "Железо [${c.metal}]"
        btnMineral.text = "Камень [${c.mineral}]"
        btnWood.text = "Дерево [${c.wood}]"
        btnFood.text = "Зерно [${c.food}]"
        btnWorkers.text = "Рабочие [${c.workers}]"
        btnBots.text = "Учёные [${c.bots}]"
    }

    private fun openResourceScreen(resourceType: Int) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BirzhaResourceFragment.newInstance(resourceType))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = BirzhaFragment()
    }
}
