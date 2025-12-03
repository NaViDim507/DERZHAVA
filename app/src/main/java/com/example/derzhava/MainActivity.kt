package com.example.derzhava

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Разрешаем сетевые операции на главном потоке. В идеале все сетевые вызовы
        // должны выполняться в корутинах с Dispatchers.IO, но для совместимости и
        // предотвращения NetworkOnMainThreadException разрешаем их здесь.
        try {
            val policy = android.os.StrictMode.ThreadPolicy.Builder().permitAll().build()
            android.os.StrictMode.setThreadPolicy(policy)
        } catch (_: Exception) {
            // На случай отсутствия StrictMode в рантайме
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)

        if (savedInstanceState == null) {
            // Всегда отправляем пользователя на экран логина при запуске приложения.
            // Ранее здесь проверялось наличие профиля в SharedPreferences и при наличии
            // автоматически открывался GameFragment, что позволяло играть даже без
            // соединения с сервером. Поскольку оффлайн‑режима быть не должно, мы
            // всегда заставляем пользователя пройти процедуру входа или регистрации.
            openLoginScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        // Если пользователь не авторизован, а текущий фрагмент не экран входа/регистрации —
        // отправляем его на экран логина. Это защищает доступ к другим экранам при отсутствии профиля.
        val currentUserMissing = !userRepository.hasUser()
        if (currentUserMissing) {
            val currentFragment = supportFragmentManager.findFragmentById(binding.fragmentContainer.id)
            if (currentFragment !is LoginFragment && currentFragment !is AuthFragment) {
                openLoginScreen()
            }
        }
    }

    fun openLoginScreen() {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, LoginFragment.newInstance())
            .commit()
    }

    fun openRegisterScreen() {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, AuthFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    fun openGameScreen() {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, GameFragment.newInstance())
            .commit()
    }
}
