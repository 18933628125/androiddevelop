package com.example.myapplication.features

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.model.Contact
import com.example.myapplication.utils.HttpUtils
import org.json.JSONObject

class WechatContactsDelete : AppCompatActivity() {

    private var selectedRelation: String? = null
    private lateinit var contacts: List<Contact>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_contact)

        // 获取联系人列表
        fetchContacts()

        // 提交按钮点击事件
        val btnSubmit = findViewById<Button>(R.id.btnSubmitDelete)
        btnSubmit.setOnClickListener {
            submitDelete()
        }
    }

    private fun fetchContacts() {
        HttpUtils.getContacts { isSuccess, response, errorMsg ->
            if (isSuccess && response != null) {
                try {
                    val jsonObject = JSONObject(response)
                    val contactsJsonArray = jsonObject.getJSONArray("contacts")
                    contacts = mutableListOf()
                    for (i in 0 until contactsJsonArray.length()) {
                        val contactJson = contactsJsonArray.getJSONObject(i)
                        val relation = contactJson.getString("relation")
                        val nickname = contactJson.getString("nickname")
                        (contacts as MutableList<Contact>).add(Contact(relation, nickname))
                    }
                    // 渲染联系人列表
                    renderContacts()
                } catch (e: Exception) {
                    Log.e("WechatContactsDelete", "解析联系人失败：${e.message}", e)
                    Toast.makeText(this, "解析联系人失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("WechatContactsDelete", "获取联系人失败：$errorMsg")
                Toast.makeText(this, "获取联系人失败：$errorMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderContacts() {
        val contactsContainer = findViewById<LinearLayout>(R.id.contactsContainer)
        contactsContainer.removeAllViews()

        for (contact in contacts) {
            val contactView = layoutInflater.inflate(R.layout.contact_item, contactsContainer, false)
            val checkBox = contactView.findViewById<CheckBox>(R.id.checkBox)
            val tvRelation = contactView.findViewById<TextView>(R.id.tvRelation)
            val tvNickname = contactView.findViewById<TextView>(R.id.tvNickname)

            tvRelation.text = contact.relation
            tvNickname.text = contact.nickname

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedRelation = contact.relation
                    // 取消其他选择
                    for (i in 0 until contactsContainer.childCount) {
                        val childView = contactsContainer.getChildAt(i)
                        val otherCheckBox = childView.findViewById<CheckBox>(R.id.checkBox)
                        if (otherCheckBox != checkBox) {
                            otherCheckBox.isChecked = false
                        }
                    }
                } else {
                    selectedRelation = null
                }
            }

            contactsContainer.addView(contactView)
        }
    }

    private fun submitDelete() {
        val selectedRelation = selectedRelation ?: run {
            Toast.makeText(this, "请选择要删除的联系人", Toast.LENGTH_SHORT).show()
            return
        }

        // 调用删除接口
        HttpUtils.deleteContact(selectedRelation) { isSuccess, response, errorMsg ->
            if (isSuccess && response != null) {
                try {
                    val jsonObject = JSONObject(response)
                    val status = jsonObject.getString("status")
                    val message = jsonObject.getString("message")
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    finish() // 返回主界面
                } catch (e: Exception) {
                    Log.e("WechatContactsDelete", "解析响应失败：${e.message}", e)
                    Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                Log.e("WechatContactsDelete", "删除失败：$errorMsg")
                Toast.makeText(this, "删除失败：$errorMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }
}