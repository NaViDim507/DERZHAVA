package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentProfileBinding
import com.example.derzhava.net.OnlineCountrySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран профиля игрока. Здесь отображаются данные текущего
 * пользователя и доступны действия по смене пароля и выходу.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
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
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }
        binding.tvProfileRulerName.text = "Логин: ${user.rulerName}"
        binding.tvProfileCountryName.text = "Держава: ${user.countryName}"

        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                val u = userRepository.getUser()
                if (u != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            OnlineCountrySync.syncUp(db, u.rulerName)
                        }
                    } catch (_: Exception) {
                    }
                }
                userRepository.clearUser()
                (activity as? MainActivity)?.openLoginScreen()
            }
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun showChangePasswordDialog() {
        val ctx = requireContext()
        val etPassword = EditText(ctx)
        etPassword.hint = "Новый пароль"
        AlertDialog.Builder(ctx)
            .setTitle("Сменить пароль")
            .setView(etPassword)
            .setPositiveButton("Сохранить") { _, _ ->
                val newPass = etPassword.text.toString().trim()
                if (newPass.isEmpty()) {
                    Toast.makeText(ctx, "Пароль не может быть пустым", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val user = userRepository.getUser()
                if (user != null) {
                    userRepository.updatePassword(newPass)
                    Toast.makeText(ctx, "Пароль обновлён", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}