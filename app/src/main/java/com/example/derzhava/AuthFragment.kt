package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.User
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentAuthBinding
import com.example.derzhava.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.derzhava.net.OnlineCountrySync

/**
 * Экран регистрации (аналог reg0.php), но теперь через VPS.
 */
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
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
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Настройки визуала под "Регистрацию"
        binding.tvTitle.text = "Регистрация"
        binding.btnAction.text = "Зарегистрироваться"
        binding.etCountryName.visibility = View.VISIBLE
        binding.tvSwitchMode.text = "Уже есть аккаунт? Войти"

        // Переключение на экран логина
        binding.tvSwitchMode.setOnClickListener {
            (activity as? MainActivity)?.openLoginScreen()
        }

        // Кнопка "Зарегистрироваться"
        binding.btnAction.setOnClickListener {
            handleRegister()
        }
    }

    private fun handleRegister() {
        val login = binding.etRulerName.text.toString().trim()      // логин
        val country = binding.etCountryName.text.toString().trim()  // название страны
        val password = binding.etPassword.text.toString().trim()

        if (login.isEmpty() || country.isEmpty() || password.isEmpty()) {
            toast("Заполните все поля")
            return
        }

        if (login.length < 3) {
            toast("Логин слишком короткий")
            return
        }

        if (password.length < 4) {
            toast("Пароль слишком короткий")
            return
        }

        // Дальше – уже онлайн-регистрация через VPS
        lifecycleScope.launch {
            binding.btnAction.isEnabled = false

            try {
                // запрос к login.php с mode=register
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.register(
                        rulerName = login,
                        countryName = country,
                        password = password
                    )
                }

                if (response.success && response.user != null) {
                    val apiUser = response.user

                    // 1) сохраняем пользователя локально, включая флаг администратора
                    val user = User(
                        rulerName = apiUser.rulerName,
                        countryName = apiUser.countryName,
                        password = password,
                        isAdmin = (apiUser.isAdmin ?: 0) == 1
                    )
                    userRepository.registerUser(user)

                    // 2) синхронизируем стартовое состояние с сервером
                    withContext(Dispatchers.IO) {
                        try {
                            OnlineCountrySync.syncDownOrCreate(db, apiUser.rulerName, apiUser.countryName)
                        } catch (e: Exception) {
                            // Если нет сети – убедимся, что локальная запись создана
                            val existing = db.countryDao().getCountryByRuler(apiUser.rulerName)
                            if (existing == null) {
                                val countryEntity = CountryEntity(
                                    rulerName = apiUser.rulerName,
                                    countryName = apiUser.countryName
                                )
                                db.countryDao().insertCountry(countryEntity)
                            }
                        }
                    }

                    toast(response.message ?: "Регистрация успешна, добро пожаловать в Державу!")
                    (activity as? MainActivity)?.openGameScreen()
                } else {
                    toast(response.message ?: "Ошибка регистрации")
                }
            } catch (e: Exception) {
                // При ошибке сети не регистрируем пользователя оффлайн — сообщаем о недоступности сервера
                toast("Сервер недоступен. Попробуйте позже")
            } finally {
                binding.btnAction.isEnabled = true
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AuthFragment()
    }
}
