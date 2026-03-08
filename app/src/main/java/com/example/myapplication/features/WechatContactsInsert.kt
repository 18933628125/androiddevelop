package com.example.myapplication.features

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.utils.HttpUtils

class WechatContactsInsert : AppCompatActivity() {

    private lateinit var contactsContainer: LinearLayout
    private lateinit var btnAddContact: ImageButton
    private lateinit var btnSubmit: Button
    private var contactCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wechat_contacts)

        initViews()
        // 默认添加3个联系人
        for (i in 1..3) {
            addContactView()
        }
    }

    private fun initViews() {
        contactsContainer = findViewById(R.id.contactsContainer)
        btnAddContact = findViewById(R.id.btnAddContact)
        btnSubmit = findViewById(R.id.btnSubmit)

        btnAddContact.setOnClickListener {
            addContactView()
        }

        btnSubmit.setOnClickListener {
            submitContacts()
        }
    }

    private fun addContactView() {
        contactCount++
        val contactView = LayoutInflater.from(this).inflate(R.layout.item_contact_input, contactsContainer, false)

        val tvContactLabel = contactView.findViewById<TextView>(R.id.tvContactLabel)
        tvContactLabel.text = "联系人$contactCount："

        contactsContainer.addView(contactView)
    }

    private fun submitContacts() {
        val contacts = mutableListOf<Pair<String, String>>()

        for (i in 0 until contactsContainer.childCount) {
            val contactView = contactsContainer.getChildAt(i)
            val etNickname = contactView.findViewById<EditText>(R.id.etNickname)
            val etRealname = contactView.findViewById<EditText>(R.id.etRealname)

            val nickname = etNickname.text.toString().trim()
            val realname = etRealname.text.toString().trim()

            // 只添加有内容的联系人
            if (nickname.isNotEmpty() || realname.isNotEmpty()) {
                if (nickname.isEmpty() || realname.isEmpty()) {
                    showToast("联系人${i + 1}的信息不完整，请填写完整")
                    return
                }
                contacts.add(Pair(nickname, realname))
            }
        }

        if (contacts.isEmpty()) {
            showToast("请至少填写一个联系人信息")
            return
        }

        // 显示加载状态
        btnSubmit.isEnabled = false
        btnSubmit.text = "提交中..."

        HttpUtils.submitWechatContacts(contacts) { isSuccess, response, errorMsg ->
            mainHandler.post {
                btnSubmit.isEnabled = true
                btnSubmit.text = "提交"

                if (isSuccess) {
                    showToast("提交成功")
                    Log.d("WechatContactsActivity", "提交成功：$response")
                    finish()
                } else {
                    showToast("提交失败：$errorMsg")
                    Log.e("WechatContactsActivity", "提交失败：$errorMsg")
                }
            }
        }
    }

    private fun showToast(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            mainHandler.post {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
