package com.example.jsmart

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.cloudinary.Cloudinary
import com.example.jsmart.databinding.ActivityProfileScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.activity.OnBackPressedCallback
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.Query
import com.yalantis.ucrop.UCrop
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts



class ProfileScreen : AppCompatActivity() {
    private var binding: ActivityProfileScreenBinding? = null
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var imageUri: Uri? = null
    private var userListener: ListenerRegistration? = null
    private var totalStars: Int = 0
    private var hasCustomPhoto = false


    private lateinit var uCropLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityProfileScreenBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        auth = FirebaseAuth.getInstance()
        setupUCropCallback()

        binding?.btnChangePhoto?.setOnClickListener {
            val options = if (hasCustomPhoto) {
                arrayOf("Upload new profile picture", "Delete profile picture")
            } else {
                arrayOf("Upload new profile picture")
            }

            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Profile Picture")
            builder.setItems(options) { dialog, which ->
                when (options[which]) {
                    "Upload new profile picture" -> {
                        pickImageFromGallery() }
                    "Delete profile picture" -> {
                        binding?.ivProfilePicture?.setImageResource(R.drawable.profile)
                        saveProfilePhotoUrl("")
                        hasCustomPhoto = false
                    }
                }
            }
            builder.show()
        }



        binding?.btnBack?.setOnClickListener {
            val intent = Intent(this, DefaultScreen::class.java)
            startActivity(intent)
            finish()

        }
        binding?.btnEdit?.setOnClickListener {
            val editIntent = Intent(this,EditProfileScreen::class.java)
            startActivity(editIntent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@ProfileScreen, DefaultScreen::class.java)
                startActivity(intent)
                finish()
            }
        })

        loadUserData()
        saveTotalStars()
        loadTotalStars()
        checkCompletionStatus()

    }
    private fun setupUCropCallback() {
        uCropLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val resultUri = UCrop.getOutput(result.data!!)
                resultUri?.let {
                    binding?.ivProfilePicture?.setImageURI(it)
                    uploadProfilePicture(it)
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Toast.makeText(this, "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val sourceUri = data?.data ?: return
            val destinationUri = Uri.fromFile(
                File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            )

            val uCrop = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withOptions(UCrop.Options().apply {
                    setCircleDimmedLayer(true)
                    setCompressionFormat(Bitmap.CompressFormat.PNG)
                    setCompressionQuality(100)
                })

            uCropLauncher.launch(uCrop.getIntent(this))
        }
    }


    private fun uploadProfilePicture(uri: Uri) {
        val cloudinary = Cloudinary(BuildConfig.CLOUDINARY_URL)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val uploadResult = cloudinary.uploader().upload(inputStream, HashMap<String, Any>())
                val imageUrl = uploadResult["secure_url"] as String

                withContext(Dispatchers.Main) {
                    saveProfilePhotoUrl(imageUrl)
                    hasCustomPhoto = true
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileScreen, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveProfilePhotoUrl(photoUrl: String) {
        val user = auth.currentUser
        user?.let {
            val userRef = db.collection("users").document(it.uid)
            userRef.update("profilePhoto", photoUrl)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update profile picture.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        user?.let {
            val userRef = db.collection("users").document(it.uid)

            userListener = userRef.addSnapshotListener { document, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load user data.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    binding?.tvName?.text = document.getString("name") ?: "User"
                    val profilePhoto = document.getString("profilePhoto")

                    if (!profilePhoto.isNullOrEmpty()) {
                        hasCustomPhoto = true
                        Picasso.get()
                            .load(profilePhoto)
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile)
                            .into(binding?.ivProfilePicture)
                    } else {
                        hasCustomPhoto = false
                        binding?.ivProfilePicture?.setImageResource(R.drawable.profile)
                    }
                }
            }
        }
    }

    private fun saveTotalStars() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)
        val starCollectionRef = userRef.collection("starCollection")

        val quizStarsRef = starCollectionRef.document("quizStars")
        val exerciseStarsRef = starCollectionRef.document("exerciseStars")

        quizStarsRef.get().addOnSuccessListener { quizSnapshot ->
            var totalStars = 0

            quizSnapshot?.data?.forEach { (_, value) ->
                if (value is Map<*, *>) {
                    totalStars += (value["stars"] as? Long)?.toInt() ?: 0
                }
            }

            exerciseStarsRef.get().addOnSuccessListener { exerciseSnapshot ->
                exerciseSnapshot?.data?.forEach { (_, value) ->
                    if (value is Map<*, *>) {
                        totalStars += (value["stars"] as? Long)?.toInt() ?: 0
                    }
                }

                val totalStarsRef = starCollectionRef.document("totalStars")
                val data = hashMapOf("total" to totalStars)
                totalStarsRef.set(data)
            }
        }
    }

    private fun loadTotalStars() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val totalStarsRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("starCollection")
            .document("totalStars")

        totalStarsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to load total stars.", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            totalStars = snapshot?.getLong("total")?.toInt() ?: 0
            binding?.tvTotalStar?.text = totalStars.toString()

            loadLevel()
        }
    }



    private fun loadLevel() {
        val maxStars = 150
        val percentage = (totalStars.toDouble() / maxStars) * 100

        when {
            percentage <= 10 -> {
                binding?.tvLevel?.text = "Sprout"
                binding?.ivLevelIcon?.setImageResource(R.drawable.sprout)
            }
            percentage <= 25 -> {
                binding?.tvLevel?.text = "Seedling"
                binding?.ivLevelIcon?.setImageResource(R.drawable.seed)
            }
            percentage <= 45 -> {
                binding?.tvLevel?.text = "Bud"
                binding?.ivLevelIcon?.setImageResource(R.drawable.bud)
            }
            percentage <= 65 -> {
                binding?.tvLevel?.text = "Voyager"
                binding?.ivLevelIcon?.setImageResource(R.drawable.voyager)
            }
            percentage <= 85 -> {
                binding?.tvLevel?.text = "Luminary"
                binding?.ivLevelIcon?.setImageResource(R.drawable.luminary)
            }
            percentage <= 100 -> {
                binding?.tvLevel?.text = "Champion"
                binding?.ivLevelIcon?.setImageResource(R.drawable.champion)
            }
            else -> {
                binding?.tvLevel?.text = "Legendary"
                binding?.ivLevelIcon?.setImageResource(R.drawable.medal)
            }
        }
    }
    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        stream.close()
        return bytes
    }

    private fun generateCertificatePDF(userName: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()


        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val completionDate = document.getString("completionDate")
                    ?: SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())

                val certsRef = firestore.collection("certificates")

                certsRef.orderBy("certNumber", Query.Direction.DESCENDING).limit(1).get()
                    .addOnSuccessListener { querySnapshot ->
                        val lastCertNum = if (!querySnapshot.isEmpty) {
                            querySnapshot.documents[0].getLong("certNumber") ?: 0
                        } else 0


                        val newCertNum = lastCertNum + 1
                        val certId = "CERT-${String.format("%03d", newCertNum)}"


                        firestore.collection("users").document(uid)
                            .update("certId", certId)
                            .addOnSuccessListener {

                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val file = File(downloadsDir, "Certificate_of_Completion_$userName.pdf")

                                    val documentPDF = Document(PageSize.A4.rotate())
                                    val outputStream = FileOutputStream(file)
                                    val writer = PdfWriter.getInstance(documentPDF, outputStream)
                                    documentPDF.open()

                                    val bgStream = assets.open("cert.png")
                                    val bgBitmap = BitmapFactory.decodeStream(bgStream)
                                    val bgBytes = bitmapToPngBytes(bgBitmap)
                                    val bgImage = Image.getInstance(bgBytes)
                                    bgImage.scaleAbsolute(PageSize.A4.rotate().width, PageSize.A4.rotate().height)
                                    bgImage.setAbsolutePosition(0f, 0f)
                                    documentPDF.add(bgImage)

                                    val titleFont = Font(Font.FontFamily.HELVETICA, 40f, Font.BOLD, BaseColor(0, 51, 102))
                                    val title = Paragraph("CERTIFICATE OF \nCOMPLETION", titleFont)
                                    title.alignment = Element.ALIGN_LEFT
                                    title.spacingBefore = 80f
                                    title.indentationLeft = 250f
                                    title.setLeading(5f, 1f)
                                    documentPDF.add(title)

                                    val subtitleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.NORMAL, BaseColor.BLACK)
                                    val subtitle = Paragraph("This certificate is proudly presented to", subtitleFont)
                                    subtitle.alignment = Element.ALIGN_LEFT
                                    subtitle.spacingBefore = 10f
                                    subtitle.indentationLeft = 250f
                                    documentPDF.add(subtitle)

                                    val nameFont = Font(Font.FontFamily.HELVETICA, 28f, Font.BOLD, BaseColor(0, 51, 102))
                                    val name = Paragraph(userName, nameFont)
                                    name.alignment = Element.ALIGN_LEFT
                                    name.spacingBefore = 8f
                                    name.indentationLeft = 250f
                                    documentPDF.add(name)

                                    val lineFont = Font(Font.FontFamily.HELVETICA, 5f, Font.NORMAL, BaseColor.BLACK)
                                    val line = Paragraph("____________________________________________________________________________________________________________________________", lineFont)
                                    line.alignment = Element.ALIGN_LEFT
                                    line.spacingBefore = -2f
                                    line.indentationLeft = 250f
                                    documentPDF.add(line)

                                    val descFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK)
                                    val description = Paragraph(
                                        "for successfully completing all Lessons, Quizzes, and Laboratory Exercises in " +
                                                "JSmart: JavaScript Learning Android Mobile Application with Laboratory Exercises and Assessments.\n\n" +
                                                "Your dedication and effort in learning JavaScript fundamentals through the JSmart application " +
                                                "demonstrate your commitment to developing your programming skills and achieving excellence in the field of technology.\n\n" +
                                                "Congratulations on your achievement!",
                                        descFont
                                    )
                                    description.alignment = Element.ALIGN_LEFT
                                    description.spacingBefore = 13f
                                    description.indentationLeft = 250f
                                    description.indentationRight = 30f
                                    documentPDF.add(description)

                                    val footerFont = Font(Font.FontFamily.HELVETICA, 11f, Font.NORMAL, BaseColor.DARK_GRAY)
                                    val footer = Paragraph("Date of Completion: $completionDate\nCertificate ID: $certId", footerFont)
                                    footer.alignment = Element.ALIGN_LEFT
                                    footer.spacingBefore = 2f
                                    footer.indentationLeft = 530f
                                    documentPDF.add(footer)

                                    documentPDF.close()
                                    outputStream.close()

                                    Toast.makeText(this, "Certificate saved to Downloads!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to upload cert ID.", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error fetching cert number.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching completion date.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun checkCompletionStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        val totalModules = 13

        binding?.ibDownloadCert?.apply {
            alpha = 0.5f
            isEnabled = false
            visibility = View.VISIBLE
        }


        firestore.collection("users").document(uid)
            .addSnapshotListener { userDoc, error ->
                if (error != null || userDoc == null || !userDoc.exists()) return@addSnapshotListener

                val firstName = userDoc.getString("name") ?: ""
                val lastName = userDoc.getString("lastName") ?: ""
                val userName = "$firstName $lastName".trim()

                var completedQuizzes = 0
                var completedExercises = 0
                var completedLessons = 0


                firestore.collection("users").document(uid)
                    .collection("quizResults")
                    .addSnapshotListener { quizDocs, _ ->
                        completedQuizzes = quizDocs?.count { it.getLong("progress") == 1L } ?: 0


                        firestore.collection("users").document(uid)
                            .collection("exercise_feedback")
                            .addSnapshotListener { exDocs, _ ->
                                completedExercises = exDocs?.count { it.getLong("progress") == 1L } ?: 0


                                firestore.collection("users").document(uid)
                                    .collection("lessons")
                                    .addSnapshotListener { lessonDocs, _ ->
                                        completedLessons = lessonDocs?.count { it.getLong("progress") == 1L } ?: 0

                                        if (completedQuizzes >= totalModules &&
                                            completedExercises >= totalModules &&
                                            completedLessons >= totalModules
                                        ) {
                                            val completionDate = SimpleDateFormat(
                                                "MMMM dd, yyyy",
                                                Locale.getDefault()
                                            ).format(Date())

                                            firestore.collection("users").document(uid)
                                                .update("completionDate", completionDate)
                                                .addOnSuccessListener {
                                                    binding?.ibDownloadCert?.apply {
                                                        alpha = 1f
                                                        isEnabled = true
                                                        setOnClickListener {
                                                            generateCertificatePDF(userName)
                                                        }
                                                    }
                                                    binding?.tvCertMessage?.text =
                                                        "You can now download your certificate"
                                                }
                                        }
                                    }
                            }
                    }
            }
    }




    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

}