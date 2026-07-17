package com.example.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.service.AccessibilityHelperService
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var userNameInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var personalityGroup: RadioGroup
    private lateinit var accessibilityStatus: TextView
    private lateinit var primeContactsRecycler: RecyclerView
    private lateinit var addPrimeBtn: Button
    private lateinit var saveBtn: Button

    private val primeContactsList = mutableListOf<Pair<String, String>>()
    private lateinit var contactAdapter: PrimeContactAdapter

    private val models = arrayOf(
        "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "models/gemini-2.0-flash-live-001",
        "models/gemini-2.5-flash-preview-native-audio-dialog"
    )

    private val voices = arrayOf(
        "Aoede", "Charon", "Kore", "Fenrir", "Puck", "Leda", "Orus", "Zephyr"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadPreferences()
        setupListeners()
    }

    private fun initViews() {
        apiKeyInput = findViewById(R.id.apiKeyInput)
        userNameInput = findViewById(R.id.userNameInput)
        modelSpinner = findViewById(R.id.modelSpinner)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        personalityGroup = findViewById(R.id.personalityGroup)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        primeContactsRecycler = findViewById(R.id.primeContactsRecycler)
        addPrimeBtn = findViewById(R.id.addPrimeBtn)
        saveBtn = findViewById(R.id.saveBtn)

        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)

        contactAdapter = PrimeContactAdapter(primeContactsList) { pos ->
            primeContactsList.removeAt(pos)
            contactAdapter.notifyItemRemoved(pos)
        }
        primeContactsRecycler.layoutManager = LinearLayoutManager(this)
        primeContactsRecycler.adapter = contactAdapter
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = AccessibilityHelperService.isEnabled(this)
        if (enabled) {
            accessibilityStatus.text = "Accessibility Status: ✅ Enabled"
            accessibilityStatus.setTextColor(getColor(R.color.success_green))
        } else {
            accessibilityStatus.text = "Accessibility Status: ❌ Disabled (Tap to enable)"
            accessibilityStatus.setTextColor(getColor(R.color.primary_red))
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("MyraPrefs", Context.MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("api_key", ""))
        userNameInput.setText(prefs.getString("user_name", "User"))

        val selectedModel = prefs.getString("gemini_model", models[0])
        val modelIdx = models.indexOf(selectedModel).coerceAtLeast(0)
        modelSpinner.setSelection(modelIdx)

        val selectedVoice = prefs.getString("gemini_voice", voices[0])
        val voiceIdx = voices.indexOf(selectedVoice).coerceAtLeast(0)
        voiceSpinner.setSelection(voiceIdx)

        val mode = prefs.getString("personality_mode", "GF")
        when (mode) {
            "GF" -> personalityGroup.check(R.id.radioGF)
            "Professional" -> personalityGroup.check(R.id.radioProf)
            "Assistant" -> personalityGroup.check(R.id.radioAssistant)
        }

        primeContactsList.clear()
        val jsonStr = prefs.getString("prime_contacts_json", null)
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    primeContactsList.add(Pair(obj.getString("name"), obj.getString("number")))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val legName = prefs.getString("prime_name", null)
            val legNum = prefs.getString("prime_number", null)
            if (legName != null && legNum != null) {
                primeContactsList.add(Pair(legName, legNum))
            }
        }
        contactAdapter.notifyDataSetChanged()
    }

    private fun setupListeners() {
        accessibilityStatus.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        addPrimeBtn.setOnClickListener {
            showAddPrimeContactDialog()
        }

        saveBtn.setOnClickListener {
            savePreferences()
        }
    }

    private fun showAddPrimeContactDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_prime_contact, null)
        val nameInput = view.findViewById<EditText>(R.id.dialogNameInput)
        val numberInput = view.findViewById<EditText>(R.id.dialogNumberInput)

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val num = numberInput.text.toString().trim()
                if (name.isNotEmpty() && num.isNotEmpty()) {
                    primeContactsList.add(Pair(name, num))
                    contactAdapter.notifyItemInserted(primeContactsList.size - 1)
                } else {
                    Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("MyraPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString("api_key", apiKeyInput.text.toString().trim())
        editor.putString("user_name", userNameInput.text.toString().trim())
        editor.putString("gemini_model", modelSpinner.selectedItem.toString())
        editor.putString("gemini_voice", voiceSpinner.selectedItem.toString())

        val mode = when (personalityGroup.checkedRadioButtonId) {
            R.id.radioGF -> "GF"
            R.id.radioProf -> "Professional"
            R.id.radioAssistant -> "Assistant"
            else -> "GF"
        }
        editor.putString("personality_mode", mode)

        val array = JSONArray()
        for (item in primeContactsList) {
            val obj = JSONObject()
            obj.put("name", item.first)
            obj.put("number", item.second)
            array.put(obj)
        }
        editor.putString("prime_contacts_json", array.toString())

        editor.apply()
        Toast.makeText(this, "Settings saved! Restart app to apply changes", Toast.LENGTH_LONG).show()
        finish()
    }
}

class PrimeContactAdapter(
    private val contacts: List<Pair<String, String>>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PrimeContactAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prime_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.nameTv.text = contact.first
        holder.numberTv.text = contact.second
        holder.deleteBtn.setOnClickListener {
            onDelete(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = contacts.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTv: TextView = view.findViewById(R.id.primeItemName)
        val numberTv: TextView = view.findViewById(R.id.primeItemNumber)
        val deleteBtn: ImageButton = view.findViewById(R.id.primeItemDelete)
    }
}
