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
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentTerritoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerritoryFragment : Fragment() {

    private var _binding: FragmentTerritoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var userRepository: UserRepository

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        userRepository = UserRepository(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTerritoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Авторизуйтесь", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }

        lifecycleScope.launch {
            val country = withContext(Dispatchers.IO) {
                db.countryDao().getCountryByRuler(user.rulerName)
            }
            if (country == null) {
                Toast.makeText(requireContext(), "Страна не найдена", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // --------- ЛОГИКА, КАК В game.php case 'zem' ---------
            val total = country.land + country.lesa + country.shah + country.rudn + country.pole

            binding.tvTitle.text = "Территория"

            binding.tvTotal.text = "Общая территория ($total)"
            binding.tvLand.text = "Свободная земля (${country.land})"
            binding.tvLesa.text = "Леса (${country.lesa})"
            binding.tvShah.text = "Шахты (${country.shah})"
            binding.tvRudn.text = "Рудники (${country.rudn})"
            binding.tvPole.text = "Поля (${country.pole})"

            binding.btnBack.setOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TerritoryFragment()
    }
}