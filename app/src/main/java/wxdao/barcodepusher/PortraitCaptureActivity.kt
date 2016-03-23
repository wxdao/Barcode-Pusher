package wxdao.barcodepusher

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.CompoundBarcodeView

class PortraitCaptureActivity : Activity(), CompoundBarcodeView.TorchListener {
    var scannerView: CompoundBarcodeView? = null
    var torchButton: Button? = null
    var capture: CaptureManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.capture_layout)

        scannerView = findViewById(R.id.zxing_barcode_scanner) as CompoundBarcodeView
        scannerView!!.setTorchListener(this)
        torchButton = findViewById(R.id.torchButton) as Button
        if (!hasTorch()) {
            torchButton!!.isEnabled = false
        }
        torchButton!!.setOnClickListener({ view ->
            if (torchButton!!.text == "Turn On Torch") {
                scannerView!!.setTorchOn()
            } else {
                scannerView!!.setTorchOff()
            }
        })

        capture = CaptureManager(this, scannerView)
        capture!!.initializeFromIntent(intent, savedInstanceState)
        capture!!.decode()
    }

    override fun onResume() {
        super.onResume()
        capture!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture!!.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture!!.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        capture!!.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return scannerView!!.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onTorchOn() {
        torchButton!!.text = "Turn Off Torch"
    }

    override fun onTorchOff() {
        torchButton!!.text = "Turn On Torch"
    }

    fun hasTorch(): Boolean {
        return applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
}