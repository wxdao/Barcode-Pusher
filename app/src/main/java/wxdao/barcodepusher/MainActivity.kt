package wxdao.barcodepusher

import android.app.ProgressDialog
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.google.gson.Gson
import com.google.zxing.integration.android.IntentIntegrator
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class MainActivity : AppCompatActivity() {
    var remoteEdit: EditText? = null
    var sharedPref: SharedPreferences? = null
    var listView: ListView? = null
    var gson: Gson? = null
    var clipboard: ClipboardManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        gson = Gson()

        sharedPref = getPreferences(Context.MODE_PRIVATE)

        listView = findViewById(R.id.listView) as ListView

        remoteEdit = findViewById(R.id.remoteEdit) as EditText
        remoteEdit?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val editor = sharedPref!!.edit()
                editor.putString("remote", s.toString())
                editor.commit()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        (findViewById(R.id.button) as Button).setOnClickListener {
            view ->
            val integrator = IntentIntegrator(this)
            integrator.captureActivity = PortraitCaptureActivity::class.java
            integrator.initiateScan()
        }

        (findViewById(R.id.manual) as Button).setOnClickListener {
            view ->
            val input = EditText(this)
            input.inputType = (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)

            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setCancelable(false)
            dialogBuilder.setView(input)
            dialogBuilder.setPositiveButton("OK", { dialogInterface: DialogInterface, i: Int ->
                pushData(remoteEdit!!.text.toString(), input.text.toString(), (findViewById(R.id.checkBox) as CheckBox).isChecked)
                dialogInterface.dismiss()
            })
            dialogBuilder.setNegativeButton("Cancel", { dialogInterface: DialogInterface, i: Int ->
                dialogInterface.dismiss()
            })

            dialogBuilder.create().show()
        }

        (findViewById(R.id.textView3) as TextView).setOnClickListener {
            view ->
            AlertDialog.Builder(this).setPositiveButton("Yes", { dialogInterface: DialogInterface, i: Int ->
                val editor = sharedPref!!.edit()
                editor.putString("history", "")
                editor.commit()
                updateHistory()
            }).setNegativeButton("No", null).setMessage("Clear history?").show()
        }

        listView?.setOnItemClickListener { adapterView: AdapterView<*>, view1: View, i: Int, l: Long ->
            clipboard?.primaryClip = ClipData.newPlainText("Captured content", (view1.findViewById(R.id.item_contentTextView) as TextView).text.toString())
            Toast.makeText(this, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
        }

        listView?.setOnItemLongClickListener { adapterView, view, i, l ->
            AlertDialog.Builder(this).setMessage("Push/Re-push it?").setPositiveButton("Yes", { dialogInterface: DialogInterface, i: Int ->
                pushData(remoteEdit!!.text.toString(), (view.findViewById(R.id.item_contentTextView) as TextView).text.toString(), true)
            }).setNegativeButton("No", null).show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        remoteEdit?.setText(sharedPref!!.getString("remote", ""))

        updateHistory()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        intentResult?.let {
            it.contents?.let {
                pushData(remoteEdit!!.text.toString(), it, (findViewById(R.id.checkBox) as CheckBox).isChecked)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 0, 0, "Additional Info")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 0 && item.groupId == 0) {
            val input = EditText(this)
            input.inputType = (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            input.setText(sharedPref!!.getString("additional", ""))

            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setCancelable(false)
            dialogBuilder.setView(input)
            dialogBuilder.setPositiveButton("OK", { dialogInterface: DialogInterface, i: Int ->
                val editor = sharedPref!!.edit()
                editor.putString("additional", input.text.toString())
                editor.commit()
                dialogInterface.dismiss()
            })
            dialogBuilder.setNegativeButton("Cancel", { dialogInterface: DialogInterface, i: Int ->
                dialogInterface.dismiss()
            })

            dialogBuilder.create().show()
        }
        return super.onOptionsItemSelected(item)
    }

    fun pushData(remote: String, content: String, toPush: Boolean) {
        val handler = Handler()
        val dialog = ProgressDialog(this)
        dialog.setTitle("Network")
        dialog.setMessage("Pushing")
        dialog.setCancelable(true)
        if (toPush) {
            Thread({
                handler.post {
                    dialog.show()
                }
                try {
                    val client = OkHttpClient()
                    val body = FormBody.Builder()
                            .add("content", content)
                            .add("additional", sharedPref!!.getString("additional", ""))
                            .build()
                    val request = Request.Builder().url(remote).post(body).build()
                    val response = client.newCall(request).execute()
                    response.body().close()
                    handler.post {
                        pushItem(content, remote + " : " + response.code().toString())
                    }
                } catch (e: Exception) {
                    Log.e("", "", e)
                    handler.post {
                        pushItem(content, remote + " : ERROR")
                    }
                }
                handler.post {
                    dialog.dismiss()
                }
            }).start()
        } else {
            pushItem(content, "Not pushed")
        }
    }

    @Synchronized
    fun pushItem(content: String, remoteInfo: String) {
        val history = gson!!.fromJson(sharedPref!!.getString("history", null), HistoryObject::class.java) ?: HistoryObject()
        history.item.add(HistoryItem(remoteInfo, content))
        val editor = sharedPref!!.edit()
        editor.putString("history", gson!!.toJson(history))
        editor.commit()
        updateHistory()
    }

    @Synchronized
    fun updateHistory() {
        val list = LinkedList<HashMap<String, String>>()
        val history = gson!!.fromJson(sharedPref!!.getString("history", null), HistoryObject::class.java)
        for (i in (history ?: HistoryObject()).item) {
            val map = HashMap<String, String>()
            map.put("remote", i.remote)
            map.put("content", i.content)
            list.addFirst(map)
        }
        val adapter = SimpleAdapter(this, list, R.layout.history_list_item, listOf<String>("remote", "content").toTypedArray(), listOf<Int>(R.id.item_remoteTextView, R.id.item_contentTextView).toIntArray())
        listView?.adapter = adapter
    }
}
