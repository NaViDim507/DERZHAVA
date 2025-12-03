package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentPerimetrBinding

class PerimetrFragment : Fragment() {

    private var _binding: FragmentPerimetrBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository

    private var country: CountryEntity? = null

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
        _binding = FragmentPerimetrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCountry()
        setupButtons()
        binding.etLevels.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTotalCostPreview()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun loadCountry() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден, вход...", Toast.LENGTH_SHORT)
                .show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        val c = db.countryDao().getCountryByRuler(user.rulerName)
        if (c == null) {
            Toast.makeText(requireContext(), "Страна не найдена", Toast.LENGTH_SHORT).show()
            return
        }

        country = c

        // Как в perimetr.php: если domik5 < 1 — выхода на экран нет
        if (c.domik5 < 1) {
            binding.tvDefenseInfo.text =
                "Здание «Периметр» ещё не построено. Построй его через раздел «Стройка»."
            binding.groupControls.visibility = View.GONE
            return
        } else {
            binding.groupControls.visibility = View.VISIBLE
        }

        bindCountryToUi(c)
    }

    private fun bindCountryToUi(c: CountryEntity) = with(binding) {
        // Уровень защиты в стиле старой дер1
        tvDefenseInfo.text = "Уровень защиты: >${c.defenseLevel}<"

        // Ресурсы без лишнего "Ресурсы:"
        tvResources.text =
            "Камень (минерал): ${c.mineral}\nДерево: ${c.wood}"

        // Стоимость — фиксированным текстом
        tvCost.text = "Стоимость 1 уровня: Камень 300, Дерево 400"
        tvTotalCost.text = ""

        etLevels.setText("")
        tvResult.text = ""

    }

    private fun setupButtons() = with(binding) {
        btnApply.setOnClickListener {
            val c = country
            if (c == null) {
                Toast.makeText(requireContext(), "Страна не загружена", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val text = etLevels.text.toString().trim()

            if (text.isEmpty()) {
                tvResult.text = "Введите число уровней"
                return@setOnClickListener
            }

            val levels = text.toIntOrNull()
            if (levels == null || levels <= 0) {
                tvResult.text = "Неверное число"
                return@setOnClickListener
            }

            val costMineral = 300 * levels
            val costWood = 400 * levels

            val newMineral = c.mineral - costMineral
            val newWood = c.wood - costWood

            if (newMineral < 0 || newWood < 0) {
                tvResult.text = "Недостаточно ресурсов!!!"
                return@setOnClickListener
            }

            val updated = c.copy(
                mineral = newMineral,
                wood = newWood,
                defenseLevel = c.defenseLevel + levels
            )

            db.countryDao().insertCountry(updated)
            country = updated
            bindCountryToUi(updated)

            tvResult.text = "Уровень защиты повышен + $levels"
        }
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    private fun updateTotalCostPreview() = with(binding) {
        val text = etLevels.text.toString().trim()
        val levels = text.toIntOrNull()
        if (levels == null || levels <= 0) {
            tvTotalCost.text = ""
            return
        }
        val costMineral = 300 * levels
        val costWood = 400 * levels
        tvTotalCost.text = "Итого за $levels ур.: Камень $costMineral, Дерево $costWood"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PerimetrFragment()
    }
}
