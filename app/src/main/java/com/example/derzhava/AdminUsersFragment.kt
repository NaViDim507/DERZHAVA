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
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryDao
import com.example.derzhava.databinding.FragmentAdminUsersBinding
import com.example.derzhava.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран администрирования, доступный из главного меню. Показывает список
 * администраторов – пользователей с флагом is_admin = 1. Для каждого
 * администратора выводится логин, название державы и статус (онлайн/офлайн).
 */
class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!

    private lateinit var countryDao: CountryDao
    private lateinit var db: AppDatabase

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        countryDao = db.countryDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadAdmins()
    }

    /**
     * Загружает список администраторов через API и заполняет список на экране.
     */
    private fun loadAdmins() {
        lifecycleScope.launch {
            binding.listContainer.removeAllViews()
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.apiService.getUsers() }
                val users = resp.users ?: emptyList()
                val admins = users.filter { (it.isAdmin ?: 0) == 1 }
                if (admins.isEmpty()) {
                    val tv = TextView(requireContext()).apply {
                        text = "Администраторы отсутствуют"
                        setTextColor(resources.getColor(R.color.derzhava_button_text))
                        textSize = 14f
                    }
                    binding.listContainer.addView(tv)
                    return@launch
                }
                val now = System.currentTimeMillis()
                for (u in admins) {
                    val countryName = u.countryName
                    // Считаем онлайн, если последний вход менее 5 минут назад
                    val lastLogin = u.lastLoginTime ?: 0L
                    val isOnline = now - lastLogin < 5 * 60 * 1000
                    val status = if (isOnline) "[Online]" else "[Offline]"

                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 12, 16, 12)
                        setBackgroundResource(R.drawable.bg_derzhava_button_base)
                    }
                    val tvName = TextView(requireContext()).apply {
                        text = "${u.rulerName} / $countryName"
                        textSize = 16f
                        setTextColor(resources.getColor(R.color.derzhava_button_text))
                    }
                    val tvStatus = TextView(requireContext()).apply {
                        text = "$status {Администратор}"
                        textSize = 13f
                        setTextColor(resources.getColor(R.color.derzhava_button_text))
                    }
                    row.addView(tvName)
                    row.addView(tvStatus)
                    binding.listContainer.addView(row)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось загрузить администраторов", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AdminUsersFragment()
    }
}