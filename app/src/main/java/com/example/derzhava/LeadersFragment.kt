package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.GeneralState
import com.example.derzhava.databinding.FragmentLeadersBinding
import com.example.derzhava.net.ApiClient
import com.example.derzhava.net.toEntity
import com.example.derzhava.net.toState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран лидеров игры. Загружает все страны и генералов,
 * вычисляет топ‑5 правителей в различных категориях (богатейшие,
 * самые большие территории, леса, шахты, рудники, поля, население,
 * сильнейший генерал, самый опытный генерал) и отображает их в списке.
 */
class LeadersFragment : Fragment() {

    private var _binding: FragmentLeadersBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeadersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        // Настраиваем свайп‑обновление. При жесте «потяни вниз» заново загружаем лидеров.
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    loadLeaders()
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Не удалось обновить список лидеров", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
        // Загружаем лидеров при первом показе, отображая индикатор загрузки
        lifecycleScope.launch {
            try {
                loadLeaders()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось загрузить лидеров", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        }
    }

    private suspend fun loadLeaders() {
        // Показываем индикатор загрузки
        showLoading(true)
        val usersResp = withContext(Dispatchers.IO) { ApiClient.apiService.getUsers() }
        val users = usersResp.users ?: emptyList()
        val playerUsers = users.filter { (it.isAdmin ?: 0) == 0 }
        val candidates = withContext(Dispatchers.IO) {
            playerUsers.map { u ->
                async {
                    try {
                        val countryResp = ApiClient.apiService.getCountry(u.rulerName)
                        val country = countryResp.country?.toEntity()
                        val generalResp = ApiClient.apiService.getGeneral(u.rulerName)
                        val general = generalResp.general?.toState()
                        if (country != null) LeaderCandidate(u.rulerName, country, general) else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        // Все метрики возвращают Int, поэтому используем CategoryDefinition<Int>
        val categories: List<CategoryDefinition<Int>> = listOf(
            CategoryDefinition("Богатейшие государства") { c: LeaderCandidate -> c.country.money },
            CategoryDefinition("Самые большие территории") { c: LeaderCandidate -> c.country.land + c.country.lesa + c.country.shah + c.country.rudn + c.country.pole },
            CategoryDefinition("Больше всего лесов") { c: LeaderCandidate -> c.country.lesa },
            CategoryDefinition("Больше всего шахт") { c: LeaderCandidate -> c.country.shah },
            CategoryDefinition("Больше всего рудников") { c: LeaderCandidate -> c.country.rudn },
            CategoryDefinition("Больше всего полей") { c: LeaderCandidate -> c.country.pole },
            CategoryDefinition("Самое большое население") { c: LeaderCandidate -> c.country.workers + c.country.bots },
            CategoryDefinition("Сильнейший генерал") { c: LeaderCandidate -> (c.general?.attack ?: 0) + (c.general?.defense ?: 0) + (c.general?.leadership ?: 0) },
            CategoryDefinition("Самый опытный генерал") { c: LeaderCandidate -> c.general?.level ?: 0 }
        )
        val container = binding.leaderListContainer
        container.removeAllViews()
        val ctx = requireContext()
        for (cat in categories) {
            val tvTitle = TextView(ctx).apply {
                text = cat.title
                textSize = 16f
                setTextColor(resources.getColor(R.color.derzhava_text_light, null))
                setPadding(0, 8, 0, 4)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            container.addView(tvTitle)
            // Берём только 3 лучших по категории
            val sorted = candidates.sortedByDescending { cat.metric(it) }.take(3)
            if (sorted.isEmpty()) {
                val tvEmpty = TextView(ctx).apply {
                    text = "Нет данных"
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.derzhava_text_light, null))
                }
                container.addView(tvEmpty)
            } else {
                for ((index, cand) in sorted.withIndex()) {
                    val value = cat.metric(cand)
                    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                    val tvRank = TextView(ctx).apply {
                        // Показываем порядковый номер без лишнего символа '$'
                        text = "${index + 1}."
                        textSize = 14f
                        setTextColor(resources.getColor(R.color.derzhava_text_light, null))
                        setPadding(0, 0, 8, 0)
                    }
                    val tvName = TextView(ctx).apply {
                        text = cand.ruler
                        textSize = 14f
                        setTextColor(resources.getColor(R.color.derzhava_text_light, null))
                        setPadding(0, 0, 8, 0)
                    }
                    val tvValue = TextView(ctx).apply {
                        text = value.toString()
                        textSize = 14f
                        setTextColor(resources.getColor(R.color.derzhava_text_light, null))
                    }
                    row.addView(tvRank)
                    row.addView(tvName)
                    row.addView(tvValue)
                    container.addView(row)
                }
            }
            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(resources.getColor(R.color.derzhava_text_light, null))
            }
            container.addView(divider)
        }
        // Скрываем индикатор загрузки
        showLoading(false)
    }

    /**
     * Управляет показом индикатора загрузки и основного содержимого. Пока
     * идёт загрузка, скрываем список лидеров и показываем ProgressBar.
     */
    private fun showLoading(show: Boolean) {
        if (show) {
            binding.progressBar.visibility = View.VISIBLE
            binding.leaderListContainer.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.leaderListContainer.visibility = View.VISIBLE
        }
    }

    private data class LeaderCandidate(val ruler: String, val country: CountryEntity, val general: GeneralState?)
    private class CategoryDefinition<T : Comparable<T>>(val title: String, val metric: (LeaderCandidate) -> T)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}