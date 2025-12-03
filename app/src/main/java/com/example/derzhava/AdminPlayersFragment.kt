package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.databinding.FragmentAdminPlayersBinding
import com.example.derzhava.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Список всех игроков (не NPC) для администратора. Отображает дату
 * регистрации, дату последнего захода, логин и название страны. При клике
 * открывает экран редактирования параметров игрока.
 */
class AdminPlayersFragment : Fragment() {

    private var _binding: FragmentAdminPlayersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminPlayersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadPlayers()
    }

    private fun loadPlayers() {
        lifecycleScope.launch {
            binding.listContainer.removeAllViews()
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.apiService.getUsers() }
                val users = resp.users ?: emptyList()
                val players = users.filter { (it.isAdmin ?: 0) == 0 }
                if (players.isEmpty()) {
                    val tv = TextView(requireContext()).apply {
                        text = "Игроки отсутствуют"
                        setTextColor(resources.getColor(R.color.derzhava_button_text))
                        textSize = 14f
                    }
                    binding.listContainer.addView(tv)
                    return@launch
                }
                val dateFormatReg = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val dateFormatLogin = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                for (u in players) {
                    val regTime = u.registrationTime ?: 0L
                    val loginTime = u.lastLoginTime ?: 0L
                    val regStr = dateFormatReg.format(Date(regTime))
                    val loginStr = dateFormatLogin.format(Date(loginTime))
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 12, 16, 12)
                        setBackgroundResource(R.drawable.bg_derzhava_button_base)
                    }
                    // Первая строка: даты
                    val tvDates = TextView(requireContext()).apply {
                        text = "$regStr / $loginStr"
                        textSize = 13f
                        setTextColor(resources.getColor(R.color.derzhava_button_text))
                    }
                    // Вторая строка: логин и страна
                    val tvUser = TextView(requireContext()).apply {
                        text = "${u.rulerName} / ${u.countryName}"
                        textSize = 16f
                        setTextColor(resources.getColor(R.color.derzhava_button_text))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    row.addView(tvDates)
                    row.addView(tvUser)
                    // При нажатии открываем экран редактирования игрока
                    row.setOnClickListener {
                        // Передаём также дату регистрации, дату последнего входа и флаг администратора
                        val reg = u.registrationTime ?: 0L
                        val last = u.lastLoginTime ?: 0L
                        val isAdmin = (u.isAdmin ?: 0) == 1
                        parentFragmentManager.beginTransaction()
                            .replace(
                                R.id.fragment_container,
                                AdminPlayerDetailsFragment.newInstance(
                                    u.rulerName,
                                    registrationTime = reg,
                                    lastLoginTime = last,
                                    isAdmin = isAdmin
                                )
                            )
                            .addToBackStack(null)
                            .commit()
                    }
                    binding.listContainer.addView(row)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось загрузить список игроков", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AdminPlayersFragment()
    }
}