package com.example.derzhava

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.BuildTaskDao
import com.example.derzhava.data.BuildTaskEntity
import com.example.derzhava.data.Buildings
import com.example.derzhava.data.CountryEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentWorkBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuildWorkFragment : Fragment() {

    private var _binding: FragmentWorkBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var buildTaskDao: BuildTaskDao
    private lateinit var userRepository: UserRepository

    private var taskId: Long = 0L
    private var currentTask: BuildTaskEntity? = null
    private var currentCountry: CountryEntity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = AppDatabase.getInstance(context)
        buildTaskDao = db.buildTaskDao()
        userRepository = UserRepository(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskId = requireArguments().getLong(ARG_TASK_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupClicks()
    }

    private fun loadData() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден, вход...", Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.openLoginScreen()
            return
        }
        lifecycleScope.launch {
            // Загружаем задачу и страну на IO-потоке. Если страна отсутствует,
            // создаём запись с именем правителя и названием страны, чтобы
            // дальнейшие операции на экране стройки не приводили к выходу
            // на главный экран. Это может произойти, если игрок сразу
            // переходит в стройку, минуя экран строительства, и локальная
            // таблица countries ещё не содержит записи.
            val (task, country) = withContext(Dispatchers.IO) {
                val t = buildTaskDao.getTaskById(taskId)
                var c = db.countryDao().getCountryByRuler(user.rulerName)
                if (c == null && t != null) {
                    // Создаём новую страну по данным профиля и сохраняем её
                    c = CountryEntity(rulerName = user.rulerName, countryName = user.countryName)
                    db.countryDao().insertCountry(c!!)
                }
                Pair(t, c)
            }

            // Если задачи нет, то отображать нечего — закрываем экран
            if (task == null) {
                Toast.makeText(requireContext(), "Стройка не найдена", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                return@launch
            }
            // Country может быть null только в теории, если профиля нет — но
            // check выше гарантирует user != null, поэтому c не должен быть null
            if (country == null) {
                Toast.makeText(requireContext(), "Профиль страны не найден", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                return@launch
            }
            currentTask = task
            currentCountry = country
            bind(task, country)
        }
    }

    private fun bind(task: BuildTaskEntity, country: CountryEntity) = with(binding) {
        tvTitle.text = "Строится: ${Buildings.name(task.buildingType)}"

        val now = System.currentTimeMillis()
        val remaining = (task.endTimeMillis - now).coerceAtLeast(0L)
        tvRemainingTime.text = "До завершения: ${formatDurationFull(remaining)}"

        tvWorkersOnTask.text = "На работе: ${task.workers} рабочих"
        tvFreeWorkers.text = "Свободные рабочие: ${country.workers}"
    }

    private fun setupClicks() = with(binding) {
        btnAddWorkers.setOnClickListener {
            showWorkersDialog(isAdd = true)
        }

        btnRemoveWorkers.setOnClickListener {
            showWorkersDialog(isAdd = false)
        }

        btnCancelBuild.setOnClickListener {
            cancelBuild()
        }
    }

    private fun showWorkersDialog(isAdd: Boolean) {
        val task = currentTask ?: return
        val country = currentCountry ?: return

        val edit = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = if (isAdd) "Сколько рабочих добавить?" else "Сколько рабочих отозвать?"
        }

        val title = if (isAdd) "Добавить рабочих" else "Отозвать рабочих"

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(edit)
            .setPositiveButton("ОК") { _, _ ->
                val value = edit.text.toString().toIntOrNull() ?: 0
                if (value <= 0) {
                    Toast.makeText(requireContext(), "Укажи положительное число", Toast.LENGTH_SHORT).show()
                } else {
                    if (isAdd) {
                        changeWorkers(+value)
                    } else {
                        changeWorkers(-value)
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * delta > 0  -> добавить рабочих
     * delta < 0  -> отозвать рабочих
     */
    private fun changeWorkers(delta: Int) {
        val user = userRepository.getUser() ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val task = buildTaskDao.getTaskById(taskId) ?: return@withContext null
                var country = db.countryDao().getCountryByRuler(user.rulerName) ?: return@withContext null

                val oldWorkers = task.workers
                val newWorkers = oldWorkers + delta

                // Проверка условий
                if (newWorkers <= 0) return@withContext Pair("Должен работать хотя бы 1 рабочий", null)
                if (delta > 0 && country.workers < delta) return@withContext Pair("Недостаточно свободных рабочих", null)

                // Пересчёт времени стройки: оставшаяся работа сохраняется, меняется скорость
                val now = System.currentTimeMillis()
                val remainingOld = (task.endTimeMillis - now).coerceAtLeast(1L)
                val remainingNew = (remainingOld * oldWorkers / (newWorkers)).coerceAtLeast(1_000L)

                val updatedTask = task.copy(
                    workers = newWorkers,
                    startTimeMillis = now,
                    endTimeMillis = now + remainingNew
                )

                // Сначала обновляем задачу на сервере. Сервер сам вернёт рабочих
                // старой стройки и спишет рабочих для новой. Это гарантирует,
                // что списание произойдёт один раз. Только после успешной
                // отправки изменяем локальное состояние страны.
                buildTaskDao.update(updatedTask)

                // Обновляем количество свободных рабочих локально. Это значение
                // соответствует количеству рабочих на сервере после операции
                val newFreeWorkers = country.workers - delta
                val updatedCountry = country.copy(workers = newFreeWorkers)
                country = updatedCountry
                db.countryDao().insertCountry(updatedCountry)

                Triple(updatedTask, updatedCountry, newWorkers)
            }
            when (result) {
                null -> return@launch
                is Pair<*, *> -> {
                    val message = result.first as String
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                is Triple<*, *, *> -> {
                    val updatedTask = result.first as BuildTaskEntity
                    val updatedCountry = result.second as CountryEntity
                    val newWorkers = result.third as Int
                    currentTask = updatedTask
                    currentCountry = updatedCountry
                    bind(updatedTask, updatedCountry)
                    Toast.makeText(requireContext(),
                        "Рабочие обновлены: теперь $newWorkers на объекте",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cancelBuild() {
        val user = userRepository.getUser() ?: return
        lifecycleScope.launch {
            val pair = withContext(Dispatchers.IO) {
                val task = buildTaskDao.getTaskById(taskId) ?: return@withContext null
                val country = db.countryDao().getCountryByRuler(user.rulerName) ?: return@withContext null
                Pair(task, country)
            }
            val task = pair?.first
            val country = pair?.second
            if (task == null || country == null) {
                Toast.makeText(requireContext(), "Стройка не найдена", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val buildingName = Buildings.name(task.buildingType)
            AlertDialog.Builder(requireContext())
                .setTitle("Прервать стройку?")
                .setMessage(
                    "Стройка «$buildingName» будет отменена, " +
                            "все ${task.workers} рабочих и ресурсы вернутся в резерв."
                )
                .setPositiveButton("Прервать") { _, _ ->
                    // При нажатии кнопки подтверждения выполняем отмену стройки и
                    // возврат ресурсов в фоновом потоке. Мы не меняем переменную
                    // country напрямую, чтобы избежать перезаписи val. Вместо
                    // этого возвращаем обновлённый объект и присваиваем его
                    // currentCountry после завершения операции.
                    lifecycleScope.launch {
                        val updatedCountry = withContext(Dispatchers.IO) {
                            // Стоимость этого здания
                            val cost = Buildings.cost(task.buildingType)
                            // Сначала удаляем задачу на сервере. Сервер сам вернёт
                            // ресурсы и рабочих в страну.
                            buildTaskDao.deleteById(task.id)
                            // Теперь обновляем локальное состояние страны, добавляя
                            // рабочих и ресурсы. Порядок важен: если сначала
                            // сделать saveCountry(), а затем deleteBuildTask(),
                            // ресурсы будут возвращены дважды.
                            val c = country!!
                            val updated = c.copy(
                                workers = c.workers + task.workers,
                                money   = c.money  + cost.money,
                                food    = c.food   + cost.food,
                                wood    = c.wood   + cost.wood,
                                metal   = c.metal  + cost.metal
                            )
                            db.countryDao().insertCountry(updated)
                            updated
                        }
                        // Обновляем локальную ссылку и UI
                        currentCountry = updatedCountry
                        Toast.makeText(
                            requireContext(),
                            "Стройка «$buildingName» прервана, рабочие и ресурсы возвращены",
                            Toast.LENGTH_SHORT
                        ).show()
                        parentFragmentManager.popBackStack()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }


    private fun formatDurationFull(millis: Long): String {
        var seconds = millis / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        val sec = seconds % 60

        return when {
            hours > 0 -> String.format("%d ч %02d мин %02d сек", hours, minutes, sec)
            minutes > 0 -> String.format("%d мин %02d сек", minutes, sec)
            else -> String.format("%d сек", sec)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TASK_ID = "task_id"

        fun newInstance(taskId: Long): BuildWorkFragment {
            val f = BuildWorkFragment()
            f.arguments = Bundle().apply {
                putLong(ARG_TASK_ID, taskId)
            }
            return f
        }
    }
}
