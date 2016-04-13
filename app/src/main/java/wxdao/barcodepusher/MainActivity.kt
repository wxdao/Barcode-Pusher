package wxdao.barcodepusher

import android.Manifest
import android.app.ProgressDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.google.gson.Gson
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.integration.android.IntentIntegrator
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

class MainActivity : AppCompatActivity() {
    var remoteEdit: EditText? = null
    var sharedPref: SharedPreferences? = null
    var listView: ListView? = null
    var gson: Gson? = null
    var clipboard: ClipboardManager? = null

    val FILE_CHOOSE_REQUEST_CODE = 0xf001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics);

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

        (findViewById(R.id.fromFile) as Button).setOnClickListener {
            view ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, FILE_CHOOSE_REQUEST_CODE)
        }

        (findViewById(R.id.manual) as Button).setOnClickListener {
            view ->
            val input = EditText(this)
            input.inputType = (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)

            val dialogBuilder = AlertDialog.Builder(this).setTitle("Manual Input")
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
            }).setNegativeButton("No", null).setMessage("Clear history?").setTitle("Confirm").show()
        }

        listView?.setOnItemClickListener { adapterView: AdapterView<*>, view1: View, i: Int, l: Long ->
            clipboard?.primaryClip = ClipData.newPlainText("Captured content", (view1.findViewById(R.id.item_contentTextView) as TextView).text.toString())
            Toast.makeText(this, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
        }

        listView?.setOnItemLongClickListener { adapterView, view, i, l ->
            AlertDialog.Builder(this).setTitle("Actions").setItems(arrayOf("Push / Re-push", "Delete", "Share"), object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    when (which) {
                        0 -> {
                            pushData(remoteEdit!!.text.toString(), (view.findViewById(R.id.item_contentTextView) as TextView).text.toString(), true)
                        }
                        1 -> {
                            AlertDialog.Builder(this@MainActivity).setMessage("Push?").setPositiveButton("Yes", DialogInterface.OnClickListener { dialogInterface, i ->
                                deleteData((view.findViewById(R.id.item_uuidTextView) as TextView).text.toString(), remoteEdit!!.text.toString(), true)
                            }).setNegativeButton("No", DialogInterface.OnClickListener { dialogInterface, i ->
                                deleteData((view.findViewById(R.id.item_uuidTextView) as TextView).text.toString(), remoteEdit!!.text.toString(), false)
                            }).show()
                        }
                        2 -> {
                            val shareView = layoutInflater.inflate(R.layout.share_layout, null)
                            val imageView = shareView.findViewById(R.id.shareImageView) as ImageView
                            val textView = shareView.findViewById(R.id.shareContentDisplayTextView) as TextView
                            val spinnerView = shareView.findViewById(R.id.shareCodeTypeSpinner) as Spinner

                            val content = (view.findViewById(R.id.item_contentTextView) as TextView).text.toString()
                            textView.text = content
                            imageView.contentDescription = content
                            imageView.isLongClickable = true
                            imageView.setOnLongClickListener { view ->
                                if (view is ImageView) {
                                    try {
                                        verifyStoragePermissions()
                                        val bitmap = (view.drawable as BitmapDrawable).bitmap
                                        val root = File(Environment.getExternalStorageDirectory(), "barcode_share")
                                        root.mkdirs()
                                        val file = File(root, (System.currentTimeMillis()).toString() + ".png")
                                        val stream = FileOutputStream(file)
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                        stream.flush()
                                        stream.close()
                                        Toast.makeText(this@MainActivity, "Image saved to " + file.absolutePath, Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(this@MainActivity, "ERROR", Toast.LENGTH_SHORT).show()
                                        Log.e("", "", e)
                                    }
                                }
                                true
                            }

                            val codeTypes = listOf("QR Code", "Aztec", "Data Matrix", "PDF 417", "Code 128")
                            val adapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, codeTypes)
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerView.adapter = adapter
                            spinnerView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                override fun onNothingSelected(parent: AdapterView<*>?) {
                                    spinnerView.setSelection(0, true)
                                }

                                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                    try {
                                        val writer = MultiFormatWriter()
                                        var width = Math.min(metrics.heightPixels, metrics.widthPixels) * 7 / 8
                                        var height = Math.min(metrics.heightPixels, metrics.widthPixels) * 7 / 8
                                        val result = writer.encode(content,
                                                when (position) {
                                                    0 -> {
                                                        BarcodeFormat.QR_CODE
                                                    }
                                                    1 -> {
                                                        BarcodeFormat.AZTEC
                                                    }
                                                    2 -> {
                                                        BarcodeFormat.DATA_MATRIX
                                                    }
                                                    3 -> {
                                                        BarcodeFormat.PDF_417
                                                    }
                                                    4 -> {
                                                        height = width * 2 / 6
                                                        BarcodeFormat.CODE_128
                                                    }
                                                    else -> {
                                                        BarcodeFormat.QR_CODE
                                                    }
                                                }, width, height)
                                        val w = result.width
                                        val h = result.height
                                        val pixels = IntArray(w * h)
                                        for (y in 0..(h - 1)) {
                                            val offset = y * w
                                            for (x in 0..(w - 1)) {
                                                pixels[offset + x] = if (result.get(x, y)) Color.BLACK else Color.WHITE
                                            }
                                        }
                                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
                                        val dstBitmap =
                                                if ((position == 2 || position == 3) && w != h) {
                                                    Bitmap.createScaledBitmap(bitmap, width, (width.toFloat() * (h.toFloat() / w.toFloat())).toInt(), false)
                                                } else {
                                                    Bitmap.createScaledBitmap(bitmap, width, height, false)
                                                }
                                        imageView.setImageBitmap(dstBitmap)
                                    } catch (e: Exception) {
                                        Toast.makeText(this@MainActivity, "ERROR", Toast.LENGTH_SHORT).show()
                                        Log.e("", "", e)
                                    }
                                }

                            }
                            AlertDialog.Builder(this@MainActivity).setView(shareView).setTitle("Share").show()
                            spinnerView.setSelection(0, true)
                        }
                    }
                }
            }).show()
            true
        }

    }

    override fun onResume() {
        super.onResume()
        remoteEdit!!.setText(sharedPref!!.getString("remote", ""))
        remoteEdit!!.setSelection(remoteEdit!!.text.length)

        updateHistory()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        val intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        intentResult?.let {
            it.contents?.let {
                pushData(remoteEdit!!.text.toString(), it, (findViewById(R.id.checkBox) as CheckBox).isChecked)
            }
        }
        when (requestCode) {
            FILE_CHOOSE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val uri = data!!.data
                    val bmp = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                    val decoder = MultiFormatReader()
                    val pixels = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height);
                    try {
                        val result = decoder.decodeWithState(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, pixels))))
                        val text = result.text
                        pushData(remoteEdit!!.text.toString(), text, (findViewById(R.id.checkBox) as CheckBox).isChecked)
                    } catch (e: Exception) {
                        AlertDialog.Builder(this).setMessage("Content not found").show()
                    }
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
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
            input.setSelection(input.text.length)

            val dialogBuilder = AlertDialog.Builder(this).setTitle("Additional Information")
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

    @Synchronized
    fun pushData(remote: String, content: String, toPush: Boolean) {
        val handler = Handler()
        val dialog = ProgressDialog(this)
        dialog.setTitle("Network")
        dialog.setMessage("Pushing")
        dialog.setCancelable(true)
        val uuid = UUID.randomUUID().toString()
        val timestamp = (System.currentTimeMillis() / 1000L).toLong()
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
                            .add("timestamp", timestamp.toString())
                            .add("uuid", uuid)
                            .build()
                    val request = Request.Builder().url(remote).post(body).build()
                    val response = client.newCall(request).execute()
                    response.body().close()
                    handler.post {
                        pushItem(content, remote + " : " + response.code().toString(), uuid, timestamp)
                    }
                } catch (e: Exception) {
                    Log.e("", "", e)
                    handler.post {
                        pushItem(content, remote + " : ERROR", uuid, timestamp)
                    }
                }
                handler.post {
                    dialog.dismiss()
                }
            }).start()
        } else {
            pushItem(content, "Not pushed", uuid, timestamp)
        }
    }

    @Synchronized
    fun deleteData(uuid: String, remote: String, toPush: Boolean) {
        val handler = Handler()
        val deleteLocal = fun() {
            val history = gson!!.fromJson(sharedPref!!.getString("history", null), HistoryObject::class.java) ?: HistoryObject()
            history.item.removeAll { ho: HistoryItem ->
                if (ho.uuid == uuid) {
                    true
                } else {
                    false
                }
            }
            val editor = sharedPref!!.edit()
            editor.putString("history", gson!!.toJson(history))
            editor.commit()
            updateHistory()
        }
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
                    val request = Request.Builder().url(remote).delete(RequestBody.create(MediaType.parse("text/plain"), uuid)).build()
                    val response = client.newCall(request).execute()
                    if (response.code() == 200) {
                        handler.post {
                            deleteLocal()
                        }
                    } else {
                        handler.post {
                            AlertDialog.Builder(this).setTitle("Network").setMessage("Rejected by remote").show()
                        }
                    }
                    response.body().close()
                } catch (e: Exception) {
                    Log.e("", "", e)
                    handler.post {
                        AlertDialog.Builder(this).setTitle("Network").setMessage("Failed to push").show()
                    }
                }
                handler.post {
                    dialog.dismiss()
                }
            }).start()
        } else {
            deleteLocal()
        }
    }

    @Synchronized
    fun pushItem(content: String, remoteInfo: String, uuid: String, timestamp: Long) {
        val history = gson!!.fromJson(sharedPref!!.getString("history", null), HistoryObject::class.java) ?: HistoryObject()
        history.item.add(HistoryItem(remoteInfo, content, uuid, timestamp))
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
            map.put("date", Date(i.timestamp * 1000L).toString())
            map.put("uuid", i.uuid)
            list.addFirst(map)
        }
        val adapter = SimpleAdapter(this, list, R.layout.history_list_item, listOf<String>("remote", "content", "date", "uuid").toTypedArray(), listOf<Int>(R.id.item_remoteTextView, R.id.item_contentTextView, R.id.item_dateTextView, R.id.item_uuidTextView).toIntArray())
        listView?.adapter = adapter
    }

    fun verifyStoragePermissions() {
        val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }
}
