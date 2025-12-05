package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentStatsBinding
import com.example.derzhava.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран общей статистики. Подгружает список всех правителей и NPC,
 * подсчитывает количество уникальных войн и выводит сводную
 * информацию. Некоторые показатели (кланы, альянсы, строения)
 * пока не доступны, поэтому помечены как "в разработке".
 */
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
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
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        // Инициализируем поля заглушками
        binding.tvTotalPlayers.text = "Правителей: ..."
        binding.tvTotalNpcs.text = "NPC стран: ..."
        binding.tvTotalWars.text = "Всего войн: ..."

        // Настраиваем жест обновления: при потягивании вниз перезагружаем статистику
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                loadStats()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Загружаем статистику при первом показе экрана
        lifecycleScope.launch {
            loadStats()
        }
    }

    /**
     * Загружает статистические данные и обновляет UI. Показывает
     * индикатор загрузки, пока выполняются сетевые вызовы и вычисления.
     */
    private suspend fun loadStats() {
        showLoading(true)
        try {
            val stats = withContext(Dispatchers.IO) {
                val usersResp = ApiClient.apiService.getUsers()
                val users = usersResp.users ?: emptyList()
                val playerUsers = users.filter { (it.isAdmin ?: 0) == 0 }
                val totalPlayers = playerUsers.size

                val npcsResp = ApiClient.apiService.getNpcList()
                val totalNpcs = npcsResp.countries?.size ?: 0

                // Считаем количество уникальных войн. Для каждого правителя
                // запрашиваем список его войн и добавляем id в множество.
                val warIdSet = mutableSetOf<Long>()
                for (u in playerUsers) {
                    try {
                        val warResp = ApiClient.apiService.getWarsForRuler(u.rulerName)
                        val wars = warResp.wars ?: emptyList()
                        for (w in wars) warIdSet.add(w.id)
                    } catch (_: Exception) {
                        // Игнорируем ошибки для отдельных пользователей
                    }
                }
                val totalWars = warIdSet.size
                Triple(totalPlayers, totalNpcs, totalWars)
            }
            val (totalPlayers, totalNpcs, totalWars) = stats
            binding.tvTotalPlayers.text = "Правителей: $totalPlayers"
            binding.tvTotalNpcs.text = "NPC стран: $totalNpcs"
            binding.tvTotalWars.text = "Всего войн: $totalWars"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось загрузить статистику", Toast.LENGTH_SHORT).show()
        } finally {
            showLoading(false)
        }
    }

    /**
     * Показывает или скрывает индикатор загрузки и данные статистики.
     */
    private fun showLoading(show: Boolean) {
        if (show) {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvTotalPlayers.visibility = View.INVISIBLE
            binding.tvTotalNpcs.visibility = View.INVISIBLE
            binding.tvTotalWars.visibility = View.INVISIBLE
            binding.tvClanCount.visibility = View.INVISIBLE
            binding.tvAllianceCount.visibility = View.INVISIBLE
            binding.tvBuildCount.visibility = View.INVISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.tvTotalPlayers.visibility = View.VISIBLE
            binding.tvTotalNpcs.visibility = View.VISIBLE
            binding.tvTotalWars.visibility = View.VISIBLE
            binding.tvClanCount.visibility = View.VISIBLE
            binding.tvAllianceCount.visibility = View.VISIBLE
            binding.tvBuildCount.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}