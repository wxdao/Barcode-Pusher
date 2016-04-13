package wxdao.barcodepusher

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.ImageView

class LargeImageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_large_image)

    }

    override fun onResume() {
        super.onResume()

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics);

        val maxHeight = metrics.heightPixels * 90 / 100
        val maxWidth = metrics.widthPixels * 98 / 100

        val buffer = intent.getByteArrayExtra("image")
        val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size)
        val height = Math.min(maxHeight.toFloat(), bitmap.height * (maxWidth.toFloat() / bitmap.width.toFloat()))
        val width = bitmap.width.toFloat() * (height.toFloat() / bitmap.height.toFloat())
        (findViewById(R.id.largeImageView) as ImageView).setImageBitmap(Bitmap.createScaledBitmap(bitmap, width.toInt(), height.toInt(), false))
    }
}
