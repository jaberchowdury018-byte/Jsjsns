package com.example.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val numBars = 20
    private val barHeights = FloatArray(numBars)
    private val targetHeights = FloatArray(numBars)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.FILL
    }
    
    private var isAnimating = false
    private var amplitude = 0f

    fun startAnimation() {
        isAnimating = true
        postInvalidateOnAnimation()
    }

    fun stopAnimation() {
        isAnimating = false
        for (i in 0 until numBars) {
            targetHeights[i] = 0f
        }
        invalidate()
    }

    fun setAmplitude(amp: Float) {
        this.amplitude = amp.coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val spacing = 6f
        val totalSpacing = spacing * (numBars - 1)
        val barWidth = (w - totalSpacing) / numBars

        if (isAnimating) {
            for (i in 0 until numBars) {
                val rand = (0.3f + 0.7f * Math.random().toFloat())
                targetHeights[i] = (amplitude * h * rand).coerceIn(4f, h)
            }
        }

        for (i in 0 until numBars) {
            barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f
            
            val left = i * (barWidth + spacing)
            val right = left + barWidth
            val top = (h - barHeights[i]) / 2f
            val bottom = top + barHeights[i]

            val progress = (barHeights[i] / h).coerceIn(0f, 1f)
            paint.alpha = (150 + (105 * progress)).toInt()
            canvas.drawRect(left, top, right, bottom, paint)
        }

        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }
}

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_MYRA = 2
    }

    fun addMessage(message: ChatMessage) {
        if (!message.isUser && messages.isNotEmpty()) {
            val last = messages.last()
            if (!last.isUser && last.text == message.text) {
                return
            }
        }
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun lastMyraText(): String? {
        return messages.lastOrNull { !it.isUser }?.text
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_MYRA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_myra, parent, false)
            MyraViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) {
            holder.bind(message)
        } else if (holder is MyraViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userText: TextView = itemView.findViewById(R.id.chatUserText)
        fun bind(msg: ChatMessage) {
            userText.text = msg.text
        }
    }

    class MyraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val myraText: TextView = itemView.findViewById(R.id.chatMyraText)
        fun bind(msg: ChatMessage) {
            myraText.text = msg.text
        }
    }
}
