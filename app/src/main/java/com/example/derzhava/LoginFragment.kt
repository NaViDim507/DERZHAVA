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
import com.example.derzhava.databinding.FragmentLoginBinding
import com.example.derzhava.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.derzhava.net.OnlineCountrySync

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
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
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Кнопка "Войти"
        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        // Переход к регистрации
        binding.tvGoToRegister.setOnClickListener {
            (activity as? MainActivity)?.openRegisterScreen()
        }
    }

    private fun handleLogin() {
        val login = binding.etLogin.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (login.isEmpty() || password.isEmpty()) {
            toast("Заполните логин и пароль")
            return
        }

        lifecycleScope.launch {
            binding.btnLogin.isEnabled = false

            try {
                // запрос к login.php с mode=login
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.login(
                        rulerName = login,
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

                    // 2) синхронизируем страну с сервером
                    //    получаем актуальное состояние или создаём дефолтную на VPS.
                    withContext(Dispatchers.IO) {
                        try {
                            OnlineCountrySync.syncDownOrCreate(db, apiUser.rulerName, apiUser.countryName)
                        } catch (e: Exception) {
                            // Если нет сети – создадим локальную запись, если её ещё нет
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

                    toast(response.message ?: "Успешный вход!")
                    (activity as? MainActivity)?.openGameScreen()
                } else {
                    toast(response.message ?: "Неверный логин или пароль")
                }
            } catch (e: Exception) {
                // Если возникла ошибка сети, просто сообщаем пользователю, что сервер недоступен
                toast("Сервер недоступен. Попробуйте позже")
            } finally {
                binding.btnLogin.isEnabled = true
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
        fun newInstance() = LoginFragment()
    }
}
