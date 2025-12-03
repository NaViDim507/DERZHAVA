package com.example.derzhava

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ChatDao
import com.example.derzhava.data.ChatMessageEntity
import com.example.derzhava.data.CountryDao
import com.example.derzhava.data.UserRepository
import com.example.derzhava.databinding.FragmentAssemblyBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AlertDialog

/**
 * Ассамблея — современный чат, но с логикой der1 (zah, погоны).
 */
class AssemblyFragment : Fragment() {

    private var _binding: FragmentAssemblyBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private lateinit var db: AppDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var countryDao: CountryDao

    private val timeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    override fun onAttach(context: Context) {
        super.onAttach(context)
        userRepository = UserRepository(context)
        db = AppDatabase.getInstance(context)
        chatDao = db.chatDao()
        countryDao = db.countryDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssemblyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        loadChat()
    }

    private fun setupUi() = with(binding) {
        tvHint.visibility = View.GONE

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnRefreshChat.setOnClickListener { loadChat() }

        btnPrivates.setOnClickListener {
            Toast.makeText(requireContext(), "Приваты пока в разработке", Toast.LENGTH_SHORT).show()
        }
// НОВОЕ: смайлики
        btnSmiles.setOnClickListener { showSmilesDialog() }
        btnSend.setOnClickListener { sendMessage() }
    }

    /** Подсказка с диапазонами zah (как в chat.php) */
    private fun buildRanksHint(): String = """
        Ассамблея — общий чат держав.

        ZAH = количество стран, которые держава захватила.
        Звания по zah:
        0–14   → za1
        15–29  → za2
        30–44  → za3
        45–59  → za4
        60–74  → za5
        75–89  → za6
        90–114 → za7
        115–129→ za8
        130–144→ za9
        145–159→ za10
        160–174→ za11
        ≥175   → za12
    """.trimIndent()

    private fun loadChat() {
        val user = userRepository.getUser()
        if (user == null) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Профиль не найден. Зайдите в игру заново."
            binding.chatContainer.removeAllViews()
            return
        }

        val messages = chatDao.getLastMessages(100)
        val container = binding.chatContainer
        container.removeAllViews()

        if (messages.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "Пока нет сообщений в Ассамблее."
                setTextColor(resources.getColor(R.color.derzhava_text_dark, null))
                textSize = 14f
            }
            container.addView(empty)
        } else {
            val ordered = messages.asReversed()
            val inflater = LayoutInflater.from(requireContext())

            ordered.forEach { msg ->
                val itemView = inflater.inflate(R.layout.item_chat_message, container, false)
                val tvMeta = itemView.findViewById<TextView>(R.id.tvMeta)
                val tvText = itemView.findViewById<TextView>(R.id.tvText)

                val time = timeFormat.format(Date(msg.timestampMillis))
                val rankShort = getMedalShort(msg.medalPath)
                val isMe = msg.rulerName == user.rulerName

                tvMeta.text = "$time • $rankShort${msg.countryName}"
                tvText.text = Smiles.applyTo(msg.text)

                val rootParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                    bottomMargin = dp(2)
                    gravity = if (isMe) Gravity.END else Gravity.START
                }
                itemView.layoutParams = rootParams

                // разные “пузыри” для своих и чужих
                if (isMe) {
                    tvText.setBackgroundResource(R.drawable.bg_chat_bubble_me)
                    tvText.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                } else {
                    tvText.setBackgroundResource(R.drawable.bg_chat_bubble_other)
                    tvText.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }

                container.addView(itemView)
            }
        }

        binding.tvError.visibility = View.GONE

        // Пролистываем в самый низ, чтобы были видны последние сообщения
        binding.scrollChat.post {
            binding.scrollChat.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendMessage() {
        val user = userRepository.getUser()
        if (user == null) {
            Toast.makeText(requireContext(), "Профиль не найден", Toast.LENGTH_SHORT).show()
            return
        }

        val rawText = binding.etMessage.text.toString().trim()
        if (rawText.isEmpty()) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Нельзя отправить пустое сообщение."
            return
        }

        val now = System.currentTimeMillis()

        // страна нужна ради zah
        val country = countryDao.getCountryByRuler(user.rulerName)
        val zah = country?.zah ?: 0
        val medalPath = computeMedalPath(zah)

        val message = ChatMessageEntity(
            rulerName = user.rulerName,
            countryName = user.countryName,
            text = rawText,
            timestampMillis = now,
            isPrivate = false,
            targetRulerName = null,
            isSystem = false,
            medalPath = medalPath
        )

        chatDao.insert(message)

        binding.etMessage.setText("")
        binding.tvError.visibility = View.GONE
        loadChat()
    }

    /** Логика pogons/zaX.jpg из chat.php */
    private fun computeMedalPath(zah: Int): String {
        val code = when {
            zah < 15 -> "za1"
            zah < 30 -> "za2"
            zah < 45 -> "za3"
            zah < 60 -> "za4"
            zah < 75 -> "za5"
            zah < 90 -> "za6"
            zah < 115 -> "za7"
            zah < 130 -> "za8"
            zah < 145 -> "za9"
            zah < 160 -> "za10"
            zah < 175 -> "za11"
            else -> "za12"
        }
        return "/pogons/$code.jpg"
    }

    /** Короткая метка звания для UI: [I], [II] ... */
    private fun getMedalShort(medalPath: String?): String {
        if (medalPath.isNullOrBlank()) return ""
        val fileName = medalPath.substringAfterLast('/').substringBeforeLast('.')
        val roman = when (fileName) {
            "za1" -> "I"
            "za2" -> "II"
            "za3" -> "III"
            "za4" -> "IV"
            "za5" -> "V"
            "za6" -> "VI"
            "za7" -> "VII"
            "za8" -> "VIII"
            "za9" -> "IX"
            "za10" -> "X"
            "za11" -> "XI"
            "za12" -> "XII"
            else -> "?"
        }
        return "[$roman] "
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
    /** Панель смайлов, как отдельное окно */
    private fun showSmilesDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val perRow = 4
        val edit = binding.etMessage

        // Разбиваем смайлы на строки по 4 штуки
        Smiles.all.chunked(perRow).forEach { rowSmiles ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            rowSmiles.forEach { smile ->
                val tv = TextView(ctx).apply {
                    text = smile.emoji
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(4), dp(8), dp(4))

                    setOnClickListener {
                        val emoji = smile.emoji
                        val editable = edit.text
                        val cursor = edit.selectionStart
                        if (editable != null && cursor >= 0 && cursor <= editable.length) {
                            editable.insert(cursor, emoji)
                        } else {
                            edit.append(emoji)
                        }
                    }
                }

                val lp = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                row.addView(tv, lp)
            }

            layout.addView(row)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Смайлики")
            .setView(layout)
            .setNegativeButton("Закрыть", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AssemblyFragment()
    }
}
