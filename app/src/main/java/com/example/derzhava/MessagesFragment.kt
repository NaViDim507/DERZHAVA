package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AllianceDao
import com.example.derzhava.data.AllianceEntity
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.MessageDao
import com.example.derzhava.data.MessageEntity
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentMessagesBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Корутинные утилиты для фоновых операций
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesFragment : Fragment() {

    // Полная дата + время: 30.11.2025 13:47
    private val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var allianceDao: AllianceDao
    private lateinit var countryDao: CountryDao

    override fun onAttach(context: Context) {
        super.onAttach(context)
        userRepository = UserRepository(context)
        db = AppDatabase.getInstance(context)
        messageDao = db.messageDao()
        allianceDao = db.allianceDao()
        countryDao = db.countryDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Кнопка «Назад» возвращает к предыдущему экрану
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val user = userRepository.getUser()
        if (user == null) {
            binding.tvMessagesEmpty.visibility = View.VISIBLE
            binding.tvMessagesEmpty.text = "Профиль не найден"
            return
        }

        val myRuler = user.rulerName
        val messages = messageDao.getMessagesForRuler(myRuler)

        val container = binding.messagesContainer
        container.removeAllViews()

        if (messages.isEmpty()) {
            binding.tvMessagesEmpty.visibility = View.VISIBLE
        } else {
            binding.tvMessagesEmpty.visibility = View.GONE

            val inflater = LayoutInflater.from(requireContext())
            val now = System.currentTimeMillis()

            for (msg in messages) {
                val itemView = inflater.inflate(
                    R.layout.item_message,
                    container,
                    false
                )

                val tvText =
                    itemView.findViewById<TextView>(R.id.tvMessageText)
                val tvTime =
                    itemView.findViewById<TextView>(R.id.tvMessageTime)
                val layoutAlliance =
                    itemView.findViewById<LinearLayout>(R.id.layoutAllianceActions)
                val btnAccept =
                    itemView.findViewById<Button>(R.id.btnAllianceAccept)
                val btnDecline =
                    itemView.findViewById<Button>(R.id.btnAllianceDecline)

                // текст
                tvText.text = msg.text

                // дата + время
                tvTime.text = timeFormat.format(Date(msg.timestampMillis))

                // по умолчанию кнопки скрыты
                layoutAlliance.visibility = View.GONE

                // Показываем кнопки только для ещё актуального приглашения в союз
                if (msg.type == "alliance_invite" && msg.payloadRuler != null) {
                    val initiatorRuler = msg.payloadRuler

                    val (a, b) = normalizePair(myRuler, initiatorRuler)
                    val alliance = allianceDao.getAlliance(a, b)

                    if (alliance != null &&
                        alliance.status == ALLIANCE_STATUS_PENDING &&
                        alliance.expiresAt > now
                    ) {
                        layoutAlliance.visibility = View.VISIBLE

                        btnAccept.setOnClickListener {
                            handleAllianceDecision(
                                alliance = alliance,
                                myRuler = myRuler,
                                initiatorRuler = initiatorRuler,
                                accept = true,
                                buttonContainer = layoutAlliance
                            )
                        }

                        btnDecline.setOnClickListener {
                            handleAllianceDecision(
                                alliance = alliance,
                                myRuler = myRuler,
                                initiatorRuler = initiatorRuler,
                                accept = false,
                                buttonContainer = layoutAlliance
                            )
                        }
                    }
                }

                container.addView(itemView)
            }
        }

        // помечаем все как прочитанные на фоне, чтобы не блокировать UI
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                messageDao.markAllAsRead(myRuler)
            }
        }

        // Обновляем метку времени последнего чтения сообщений. Благодаря этому
        // индикатор непрочитанных сообщений в GameFragment будет корректно
        // обнуляться после просмотра. Значение сохраняем сразу, не ожидая
        // завершения markAllAsRead, так как сервер всё равно не поддерживает
        // пометку сообщений.
        userRepository.setLastMessagesReadTimestamp(System.currentTimeMillis())
    }

    private fun normalizePair(r1: String, r2: String): Pair<String, String> =
        if (r1 <= r2) r1 to r2 else r2 to r1

    private fun handleAllianceDecision(
        alliance: AllianceEntity,
        myRuler: String,
        initiatorRuler: String,
        accept: Boolean,
        buttonContainer: View
    ) {
        val now = System.currentTimeMillis()

        val myCountryName =
            countryDao.getCountryByRuler(myRuler)?.countryName ?: "ваше государство"
        val initiatorCountryName =
            countryDao.getCountryByRuler(initiatorRuler)?.countryName ?: "другое государство"

        val newStatus = if (accept) ALLIANCE_STATUS_ACTIVE else ALLIANCE_STATUS_REJECTED

        allianceDao.update(
            alliance.copy(
                status = newStatus,
                respondedAt = now
            )
        )

        if (accept) {
            // себе
            messageDao.insert(
                MessageEntity(
                    rulerName = myRuler,
                    text = "Вы согласились на союз с государством $initiatorCountryName.",
                    timestampMillis = now,
                    isRead = false
                )
            )
            // инициатору
            messageDao.insert(
                MessageEntity(
                    rulerName = initiatorRuler,
                    text = "$myCountryName приняло ваше предложение союза.",
                    timestampMillis = now,
                    isRead = false
                )
            )
            Toast.makeText(
                requireContext(),
                "Союз заключён.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // себе
            messageDao.insert(
                MessageEntity(
                    rulerName = myRuler,
                    text = "Вы отклонили предложение союза от государства $initiatorCountryName.",
                    timestampMillis = now,
                    isRead = false
                )
            )
            // инициатору
            messageDao.insert(
                MessageEntity(
                    rulerName = initiatorRuler,
                    text = "$myCountryName отклонило ваше предложение союза.",
                    timestampMillis = now,
                    isRead = false
                )
            )
            Toast.makeText(
                requireContext(),
                "Предложение союза отклонено.",
                Toast.LENGTH_SHORT
            ).show()
        }

        // прячем кнопки у исходного сообщения
        buttonContainer.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ALLIANCE_STATUS_PENDING = 0
        private const val ALLIANCE_STATUS_ACTIVE = 1
        private const val ALLIANCE_STATUS_REJECTED = 2
        private const val ALLIANCE_STATUS_BROKEN = 3
        private const val ALLIANCE_STATUS_EXPIRED = 4

        fun newInstance() = MessagesFragment()
    }
}
