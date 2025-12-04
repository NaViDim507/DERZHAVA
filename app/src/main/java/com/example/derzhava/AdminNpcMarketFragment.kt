package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.derzhava.data.*
import com.example.derzhava.databinding.FragmentAdminNpcMarketBinding

class AdminNpcMarketFragment : Fragment() {

    private var _binding: FragmentAdminNpcMarketBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var countryDao: CountryDao
    private lateinit var marketDao: MarketDao

    private var npc: CountryEntity? = null
    private var rulerName: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        countryDao = db.countryDao()
        marketDao = db.marketDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rulerName = arguments?.getString(ARG_RULER_NAME)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminNpcMarketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadNpc()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        bindAllResources()
    }

    private fun loadNpc() {
        val rn = rulerName
        if (rn == null) {
            Toast.makeText(requireContext(), "Не задан правитель NPC", Toast.LENGTH_SHORT).show()
            return
        }

        val c = countryDao.getNpcByRuler(rn) ?: countryDao.getCountryByRuler(rn)
        if (c == null) {
            Toast.makeText(requireContext(), "NPC не найден", Toast.LENGTH_SHORT).show()
            return
        }

        npc = c

        binding.tvTitle.text = "Биржа NPC"
        binding.tvCountryName.text = c.countryName
        binding.tvRulerName.text = "Правитель NPC: ${c.rulerName}"
        binding.tvMoney.text = "Деньги: ${c.money}"
    }

    private data class ResourceConfig(
        val type: Int,
        val title: String,
        val titleRod: String,
        val minPrice: Int,
        val maxPrice: Int
    )

    private fun getResourceConfig(type: Int): ResourceConfig = when (type) {
        1 -> ResourceConfig(1, "Металл", "железа", 15, 20)
        2 -> ResourceConfig(2, "Камень", "камня", 8, 13)
        3 -> ResourceConfig(3, "Дерево", "древесины", 3, 7)
        4 -> ResourceConfig(4, "Зерно", "зерна", 1, 2)
        5 -> ResourceConfig(5, "Рабочие", "рабочих", 25, 50)
        6 -> ResourceConfig(6, "Учёные", "учёных", 40, 80)
        else -> ResourceConfig(type, "Ресурс", "ресурса", 1, 999999)
    }

    private fun getResourceAmount(c: CountryEntity, type: Int): Int = when (type) {
        1 -> c.metal
        2 -> c.mineral
        3 -> c.wood
        4 -> c.food
        5 -> c.workers
        6 -> c.bots
        else -> 0
    }

    private fun setResourceAmount(c: CountryEntity, type: Int, newValue: Int): CountryEntity =
        when (type) {
            1 -> c.copy(metal = newValue)
            2 -> c.copy(mineral = newValue)
            3 -> c.copy(wood = newValue)
            4 -> c.copy(food = newValue)
            5 -> c.copy(workers = newValue)
            6 -> c.copy(bots = newValue)
            else -> c
        }

    private fun bindAllResources() {
        val c = npc ?: return

        bindResource(
            type = 1,
            tv = binding.tvMetalInfo,
            button = binding.btnMetalEdit
        )
        bindResource(
            type = 2,
            tv = binding.tvMineralInfo,
            button = binding.btnMineralEdit
        )
        bindResource(
            type = 3,
            tv = binding.tvWoodInfo,
            button = binding.btnWoodEdit
        )
        bindResource(
            type = 4,
            tv = binding.tvFoodInfo,
            button = binding.btnFoodEdit
        )
        bindResource(
            type = 5,
            tv = binding.tvWorkersInfo,
            button = binding.btnWorkersEdit
        )
        bindResource(
            type = 6,
            tv = binding.tvBotsInfo,
            button = binding.btnBotsEdit
        )
    }

    private fun bindResource(type: Int, tv: TextView, button: View) {
        val c = npc ?: return
        val cfg = getResourceConfig(type)
        val owned = getResourceAmount(c, type)
        val offer = marketDao.getOfferForRulerAndResource(c.rulerName, type)

        val onSale = offer?.amount ?: 0
        val priceText = offer?.pricePerUnit?.toString() ?: "—"

        tv.text = "${cfg.title}: в запасе $owned, на продаже $onSale (цена: $priceText)"

        button.setOnClickListener {
            showEditOfferDialog(cfg, c, offer)
        }
    }

    private fun showEditOfferDialog(
        cfg: ResourceConfig,
        currentNpc: CountryEntity,
        currentOffer: MarketOfferEntity?
    ) {
        val owned = getResourceAmount(currentNpc, cfg.type)
        val onSale = currentOffer?.amount ?: 0
        val maxTotal = owned + onSale

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val tvInfo = TextView(requireContext()).apply {
            text = "Всего ${cfg.titleRod}: $maxTotal (в запасе + уже на продаже)."
        }
        layout.addView(tvInfo)

        val etAmount = EditText(requireContext()).apply {
            hint = "Количество на продаже (0..$maxTotal)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(onSale.takeIf { it > 0 }?.toString() ?: "")
        }
        layout.addView(etAmount)

        val etPrice = EditText(requireContext()).apply {
            hint = "Цена за 1 ед. (${cfg.minPrice}..${cfg.maxPrice})"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentOffer?.pricePerUnit?.toString() ?: "")
        }
        layout.addView(etPrice)

        AlertDialog.Builder(requireContext())
            .setTitle("Лот NPC: ${cfg.title}")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val amount = etAmount.text.toString().toIntOrNull() ?: 0
                val price = etPrice.text.toString().toIntOrNull() ?: 0

                if (amount < 0 || amount > maxTotal) {
                    Toast.makeText(requireContext(), "Неверное количество (0..$maxTotal)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (amount > 0 && (price < cfg.minPrice || price > cfg.maxPrice)) {
                    Toast.makeText(
                        requireContext(),
                        "Цена должна быть от ${cfg.minPrice} до ${cfg.maxPrice}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val newOnSale = amount
                val newOwned = maxTotal - newOnSale

                var updatedNpc = setResourceAmount(currentNpc, cfg.type, newOwned)
                countryDao.insertCountry(updatedNpc)
                npc = updatedNpc

                if (newOnSale == 0) {
                    if (currentOffer != null) {
                        marketDao.delete(currentOffer)
                    }
                } else {
                    val updatedOffer = (currentOffer ?: MarketOfferEntity(
                        rulerName = currentNpc.rulerName,
                        resourceType = cfg.type,
                        amount = newOnSale,
                        pricePerUnit = price
                    )).copy(
                        amount = newOnSale,
                        pricePerUnit = price
                    )
                    marketDao.insert(updatedOffer)
                }

                loadNpc()
                bindAllResources()

                Toast.makeText(requireContext(), "Лот NPC обновлён", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RULER_NAME = "ruler_name"

        fun newInstance(rulerName: String): AdminNpcMarketFragment {
            val f = AdminNpcMarketFragment()
            f.arguments = Bundle().apply {
                putString(ARG_RULER_NAME, rulerName)
            }
            return f
        }
    }
}
