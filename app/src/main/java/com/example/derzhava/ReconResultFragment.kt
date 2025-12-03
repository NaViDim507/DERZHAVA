package com.example.derzhava

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.derzhava.databinding.FragmentReconResultBinding

/**
 * Экран отображения результатов разведки. Вместо всплывающего диалога
 * результаты показываются на отдельной странице с возможностью вернуться
 * назад. На экран передаётся название страны, примерное количество
 * ресурсов и список построек. Пользователь может нажать кнопку «Назад»
 * для возврата к предыдущему экрану.
 */
class ReconResultFragment : Fragment() {

    private var _binding: FragmentReconResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReconResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        val countryName = args.getString(ARG_COUNTRY_NAME) ?: ""
        val gold = args.getInt(ARG_GOLD)
        val food = args.getInt(ARG_FOOD)
        val metal = args.getInt(ARG_METAL)
        val wood = args.getInt(ARG_WOOD)
        val buildings = args.getStringArrayList(ARG_BUILDINGS) ?: arrayListOf()

        binding.tvTitle.text = "Разведка: $countryName"
        binding.tvGold.text = "Золото: $gold"
        binding.tvFood.text = "Пища: $food"
        binding.tvMetalWood.text = "Металл / Дерево: $metal / $wood"

        // Заполняем список построек
        binding.buildingsContainer.removeAllViews()
        if (buildings.isEmpty()) {
            val tv = android.widget.TextView(requireContext()).apply {
                text = "Построек не обнаружено."
                setTextColor(resources.getColor(R.color.derzhava_text_dark))
                textSize = 14f
            }
            binding.buildingsContainer.addView(tv)
        } else {
            buildings.forEach { line ->
                val tv = android.widget.TextView(requireContext()).apply {
                    text = line
                    setTextColor(resources.getColor(R.color.derzhava_text_dark))
                    textSize = 14f
                }
                binding.buildingsContainer.addView(tv)
            }
        }

        // Назад – возвращаемся к предыдущему фрагменту
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_COUNTRY_NAME = "country_name"
        private const val ARG_GOLD = "gold"
        private const val ARG_FOOD = "food"
        private const val ARG_METAL = "metal"
        private const val ARG_WOOD = "wood"
        private const val ARG_BUILDINGS = "buildings"

        /**
         * Создаёт новый экземпляр фрагмента и упаковывает данные разведки в Bundle.
         */
        fun newInstance(
            countryName: String,
            gold: Int,
            food: Int,
            metal: Int,
            wood: Int,
            buildings: ArrayList<String>
        ): ReconResultFragment {
            val f = ReconResultFragment()
            f.arguments = Bundle().apply {
                putString(ARG_COUNTRY_NAME, countryName)
                putInt(ARG_GOLD, gold)
                putInt(ARG_FOOD, food)
                putInt(ARG_METAL, metal)
                putInt(ARG_WOOD, wood)
                putStringArrayList(ARG_BUILDINGS, buildings)
            }
            return f
        }
    }
}