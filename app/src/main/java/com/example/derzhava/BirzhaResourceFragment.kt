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
import com.example.derzhava.databinding.FragmentBirzhaResourceBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.derzhava.data.MessageDao
import com.example.derzhava.data.MessageEntity
import com.example.derzhava.net.OnlineCountrySync
import com.example.derzhava.net.OnlineMarketSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class BirzhaResourceFragment : Fragment() {

    private var _binding: FragmentBirzhaResourceBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var countryDao: CountryDao
    private lateinit var marketDao: MarketDao
    private lateinit var warDao: WarDao
    private lateinit var messageDao: MessageDao
    private lateinit var userRepository: UserRepository

    private var country: CountryEntity? = null
    private var resourceType: Int = 1

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        countryDao = db.countryDao()
        marketDao = db.marketDao()
        warDao = db.warDao()
        messageDao = db.messageDao()
        userRepository = UserRepository(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resourceType = arguments?.getInt(ARG_RESOURCE_TYPE) ?: 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBirzhaResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSell.setOnClickListener { showSellDialog() }
        binding.btnRefresh.setOnClickListener { loadData() }

        loadData()
    }

    // ---------- Resource config ----------

    private data class ResourceConfig(
        val type: Int,
        val title: String,
        val titleRod: String,   // родительный падеж
        val minPrice: Int,
        val maxPrice: Int
    )

    private fun getResourceConfig(type: Int): ResourceConfig = when (type) {
        1 -> ResourceConfig(1, "железо", "железа", 15, 20)
        2 -> ResourceConfig(2, "камень", "камня", 8, 13)
        3 -> ResourceConfig(3, "древесина", "древесины", 3, 7)
        4 -> ResourceConfig(4, "зерно", "зерна", 1, 2)
        5 -> ResourceConfig(5, "рабочие", "рабочих", 25, 50)
        6 -> ResourceConfig(6, "учёные", "учёных", 40, 80)
        else -> ResourceConfig(type, "ресурс", "ресурса", 1, 999999)
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

    // ---------- Основная загрузка ----------

    private fun loadData() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден, вход...", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }
        // Синхронизируем страну и лоты перед отображением
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                OnlineCountrySync.syncDownOrCreate(db, user.rulerName, user.countryName)
                // Загружаем только чужие лоты по данному ресурсу
                OnlineMarketSync.syncDown(db, resourceType, user.rulerName)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка обновления биржи: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }
            // Получаем данные из базы в фоновом потоке
            val data = withContext(Dispatchers.IO) {
                val c = countryDao.getCountryByRuler(user.rulerName) ?: return@withContext null
                val invasionsCount = warDao.countActiveAsDefender(c.rulerName)
                val offers = marketDao.getOffersForResource(resourceType, c.rulerName)
                // Предзагружаем страны продавцов
                val sellers = offers.associate { off -> off.rulerName to countryDao.getCountryByRuler(off.rulerName) }
                Triple(c, invasionsCount, Pair(offers, sellers))
            }
            if (data == null) {
                Toast.makeText(requireContext(), "Страна не найдена", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val c = data.first
            val invasionsCount = data.second
            val offers = data.third.first
            val sellers = data.third.second
            country = c
            val cfg = getResourceConfig(resourceType)
            binding.tvTitle.text = "Биржа: ${cfg.title.replaceFirstChar { it.uppercase() }}"
            binding.tvCountryName.text = c.countryName
            binding.tvRulerName.text = "Правитель: ${c.rulerName}"
            binding.tvMyMoney.text = "Деньги: ${c.money}"
            binding.tvMyResource.text = "У вас ${cfg.titleRod}: ${getResourceAmount(c, resourceType)}"
            binding.tvPriceRange.text =
                "Диапазон цен: от ${cfg.minPrice} до ${cfg.maxPrice} за 1 ед."
            // --- Логика войны ---
            val hasInvasion = invasionsCount > 0
            val canSell = !hasInvasion
            val canBuy = true
            if (hasInvasion) {
                binding.tvWarWarning.visibility = View.VISIBLE
                binding.tvWarWarning.text =
                    "На территорию вашего государства идёт вторжение. " +
                            "Вы не можете выставлять ресурсы на продажу, но покупки на бирже разрешены."
            } else {
                binding.tvWarWarning.visibility = View.GONE
            }
            binding.btnSell.isEnabled = canSell
            // Список лотов
            val container = binding.offersContainer
            container.removeAllViews()
            if (offers.isEmpty()) {
                binding.tvEmptyOffers.visibility = View.VISIBLE
                return@launch
            } else {
                binding.tvEmptyOffers.visibility = View.GONE
            }
            for (offer in offers) {
                val sellerCountry = sellers[offer.rulerName]
                val itemView = layoutInflater.inflate(
                    R.layout.item_market_offer,
                    container,
                    false
                )
                val tvSeller = itemView.findViewById<TextView>(R.id.tvSeller)
                val tvDetails = itemView.findViewById<TextView>(R.id.tvDetails)
                val btnBuy = itemView.findViewById<View>(R.id.btnBuy)
                val totalCost = offer.amount * offer.pricePerUnit
                val sellerTitle = if (sellerCountry != null) {
                    val prefix = if (sellerCountry.isNpc) "NPC" else "Гос."
                    "$prefix ${sellerCountry.countryName} (${sellerCountry.rulerName})"
                } else {
                    "Неизвестное государство (${offer.rulerName})"
                }
                tvSeller.text = sellerTitle
                tvDetails.text =
                    "Кол-во: ${offer.amount} ед.\nЦена: ${offer.pricePerUnit} за ед. (всего $totalCost)"
                btnBuy.isEnabled = canBuy
                btnBuy.setOnClickListener {
                    if (!canBuy) {
                        Toast.makeText(
                            requireContext(),
                            "Сейчас покупка на бирже недоступна.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (sellerCountry != null && country != null) {
                        showBuyDialog(
                            getResourceConfig(resourceType),
                            country!!,
                            sellerCountry,
                            offer
                        )
                    }
                }
                container.addView(itemView)
            }
        }
    }



    // ---------- Продажа (создание / изменение лота) ----------

    private fun showSellDialog() {
        val user = userRepository.getUser() ?: return
        val c = country ?: return
        val cfg = getResourceConfig(resourceType)

        val currentOffer = marketDao.getOfferForRulerAndResource(user.rulerName, resourceType)
        val ownedNow = getResourceAmount(c, resourceType)
        val onSale = currentOffer?.amount ?: 0
        val maxCanSell = ownedNow + onSale

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val tvInfo = TextView(requireContext()).apply {
            text = "Всего ${cfg.titleRod}: $maxCanSell (с учётом уже выставленного)."
        }
        layout.addView(tvInfo)

        val etAmount = EditText(requireContext()).apply {
            hint = "Количество на продаже (0..$maxCanSell)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (currentOffer != null) currentOffer.amount.toString() else "")
        }
        layout.addView(etAmount)

        val etPrice = EditText(requireContext()).apply {
            hint = "Цена за 1 ед. (${cfg.minPrice}..${cfg.maxPrice})"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (currentOffer != null) currentOffer.pricePerUnit.toString() else "")
        }
        layout.addView(etPrice)

        AlertDialog.Builder(requireContext())
            .setTitle("Продажа: ${cfg.title}")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val amount = etAmount.text.toString().toIntOrNull() ?: 0
                val price = etPrice.text.toString().toIntOrNull() ?: 0

                if (amount < 0 || amount > maxCanSell) {
                    Toast.makeText(requireContext(), "Неверное количество (0..$maxCanSell)", Toast.LENGTH_SHORT).show()
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
                val newOwned = maxCanSell - newOnSale

                var newCountry = setResourceAmount(c, resourceType, newOwned)
                // Сохраняем лот на сервере и страну. Используем корутину
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        countryDao.insertCountry(newCountry)
                        country = newCountry
                    }
                    try {
                        if (newOnSale == 0) {
                            // Удаляем лот, если он был
                            if (currentOffer != null) {
                                OnlineMarketSync.deleteOffer(db, currentOffer.id, user.rulerName)
                            }
                            Toast.makeText(requireContext(), "Лот снят с продажи", Toast.LENGTH_SHORT).show()
                        } else {
                            val updatedOffer = (currentOffer ?: MarketOfferEntity(
                                rulerName = user.rulerName,
                                resourceType = resourceType,
                                amount = newOnSale,
                                pricePerUnit = price
                            )).copy(
                                amount = newOnSale,
                                pricePerUnit = price
                            )
                            OnlineMarketSync.addOffer(db, updatedOffer)
                            Toast.makeText(requireContext(), "Лот обновлён: $newOnSale ед. по $price", Toast.LENGTH_SHORT).show()
                        }
                        // синхронизируем страну после продажи
                        OnlineCountrySync.syncUp(db, user.rulerName)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка сохранения лота: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
                    }
                    loadData()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    private fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }
    // ---------- Покупка ----------

    private fun showBuyDialog(
        cfg: ResourceConfig,
        buyerCountry: CountryEntity,
        sellerCountry: CountryEntity,
        offer: MarketOfferEntity
    ) {
        val maxAmount = offer.amount

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val tvInfo = TextView(requireContext()).apply {
            text = "Доступно: ${offer.amount} ед. по цене ${offer.pricePerUnit} за ед. (всего ${offer.amount * offer.pricePerUnit})"
        }
        layout.addView(tvInfo)

        val etAmount = EditText(requireContext()).apply {
            hint = "Сколько купить (1..$maxAmount)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(etAmount)

        AlertDialog.Builder(requireContext())
            .setTitle("Покупка ${cfg.title}")
            .setView(layout)
            .setPositiveButton("Купить") { _, _ ->
                val amount = etAmount.text.toString().toIntOrNull() ?: 0
                if (amount <= 0 || amount > maxAmount) {
                    Toast.makeText(requireContext(), "Неверное количество", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val cost = amount * offer.pricePerUnit
                if (cost > buyerCountry.money) {
                    Toast.makeText(requireContext(), "Недостаточно денег", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newBuyerMoney = buyerCountry.money - cost
                val newSellerMoney = sellerCountry.money + cost

                val newBuyerRes = getResourceAmount(buyerCountry, cfg.type) + amount
                val updatedBuyer = setResourceAmount(buyerCountry, cfg.type, newBuyerRes)
                    .copy(money = newBuyerMoney)
                val updatedSeller = sellerCountry.copy(money = newSellerMoney)

                // Обновляем локальные записи и выполняем сетевые операции в корутине
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        countryDao.insertCountry(updatedBuyer)
                        countryDao.insertCountry(updatedSeller)
                        country = updatedBuyer
                    }
                    try {
                        val leftOnSale = offer.amount - amount
                        if (leftOnSale > 0) {
                            // Снимаем старый лот и создаём новый на остаток
                            OnlineMarketSync.deleteOffer(db, offer.id, offer.rulerName)
                            // Новый лот принадлежит продавцу
                            val newOffer = offer.copy(amount = leftOnSale)
                            OnlineMarketSync.addOffer(db, newOffer)
                        } else {
                            OnlineMarketSync.deleteOffer(db, offer.id, offer.rulerName)
                        }
                        // Сохраняем обновлённые страны
                        OnlineCountrySync.syncUp(db, buyerCountry.rulerName)
                        OnlineCountrySync.syncUp(db, sellerCountry.rulerName)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка синхронизации сделки: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
                    }
                    // Сообщения локально для истории
                    val now = System.currentTimeMillis()
                    val buyerText =
                        "Вы купили у ${sellerCountry.rulerName} ${cfg.titleRod} $amount ед. за $cost денег (биржа)"
                    val sellerText =
                        "Государство ${buyerCountry.countryName} купило у вас ${cfg.titleRod} $amount ед. за $cost денег (биржа)"
                    messageDao.insert(
                        MessageEntity(
                            rulerName = buyerCountry.rulerName,
                            text = buyerText,
                            timestampMillis = now,
                            isRead = false
                        )
                    )
                    messageDao.insert(
                        MessageEntity(
                            rulerName = sellerCountry.rulerName,
                            text = sellerText,
                            timestampMillis = now,
                            isRead = false
                        )
                    )
                    Toast.makeText(requireContext(), "Сделка завершена: $amount ед. за $cost", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RESOURCE_TYPE = "resource_type"

        fun newInstance(resourceType: Int): BirzhaResourceFragment {
            val f = BirzhaResourceFragment()
            f.arguments = Bundle().apply {
                putInt(ARG_RESOURCE_TYPE, resourceType)
            }
            return f
        }
    }
}
