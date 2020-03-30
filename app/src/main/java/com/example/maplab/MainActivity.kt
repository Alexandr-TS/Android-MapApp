package com.example.maplab

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri;
import android.os.Bundle
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider;
import com.google.android.gms.maps.model.LatLng
import io.realm.Realm
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    private var currentPhotoPathKey: String = "CURRENT_PHOTO_PATH_KEY"
    private val requestTakePhoto = 1
    private var position: LatLng = LatLng(0.0, 0.0)
    private lateinit var realm: Realm
    private lateinit var linearLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        linearLayout = findViewById(R.id.linearLayout)
        scrollView = findViewById(R.id.scrollView)

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        position = intent.getParcelableExtra("marker_position")

        Realm.init(this)
        realm = Realm.getDefaultInstance()
        uploadImages()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(currentPhotoPathKey, currentPhotoPath)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentPhotoPath = savedInstanceState.getString(currentPhotoPathKey)
    }

    private fun uploadImages() {
        val mapMarker = realm.where(EntMarker::class.java)
            .equalTo("lat", position.latitude)
            .equalTo("lon", position.longitude)
            .findFirst()
        for (path in mapMarker!!.getPaths()) {
            setPic(path);
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    fun dispatchTakePictureIntent(view: View) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.maplab.android.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, requestTakePhoto)
                }
            }
        }
    }

    private fun setPic(path: String) {
        val imageView = ImageView(linearLayout.getContext())
        linearLayout.addView(imageView)

        val display = getWindowManager().getDefaultDisplay()
        imageView.setAdjustViewBounds(true)
        imageView.setVisibility(View.VISIBLE)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        params.setMargins(10, 5, 10, 5)
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER)
        imageView.setLayoutParams(params)

        // Get the dimensions of the View
        val targetW: Int = display.width
        val targetH: Int = display.height

        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, bmOptions)

        // Get the dimensions of the bitmap
        val photoW: Int = bmOptions.outWidth
        val photoH: Int = bmOptions.outHeight

        // Determine how much to scale down the image
        val scaleFactor: Int = Math.min(photoW / targetW, photoH / targetH)

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor
        bmOptions.inPurgeable = true
        BitmapFactory.decodeFile(path, bmOptions)?.also { bitmap ->
            imageView.setImageBitmap(bitmap)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == requestTakePhoto && resultCode == RESULT_OK) {
            setPic(currentPhotoPath!!)

            val mapMarker = realm.where(EntMarker::class.java)
                .equalTo("lat", position.latitude)
                .equalTo("lon", position.longitude)
                .findFirst()
            realm.beginTransaction()
            mapMarker?.addPath(currentPhotoPath!!)
            realm.commitTransaction()

            Toast.makeText(this, "Saved on $currentPhotoPath", Toast.LENGTH_LONG).show()
        }
    }
}
