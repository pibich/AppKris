package com.example.appkris

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var profilePicture: ImageView
    private lateinit var profileName: TextView
    private lateinit var logoutButton: Button

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storageRef = FirebaseStorage.getInstance().reference

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Intent>
    private lateinit var photoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profilePicture = findViewById(R.id.profilePicture)
        profileName = findViewById(R.id.profileName)
        logoutButton = findViewById(R.id.logoutButton)

        loadUserProfile()

        profilePicture.setOnClickListener {
            showChangeProfilePictureDialog()
        }

        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uploadProfilePicture(it) }
        }

        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                uploadProfilePicture(photoUri)
            }
        }
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid
        userId?.let {
            firestore.collection("users").document(it).get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name")
                    profileName.text = "Hi, ${name ?: "Pengguna"}!"

                    val profilePicUrl = document.getString("profilePicture")
                    if (profilePicUrl != null) {
                        Glide.with(this)
                            .load(profilePicUrl)
                            .into(profilePicture)
                    } else {
                        profilePicture.setImageResource(R.drawable.baseline_account_circle_24)
                    }
                } else {
                    val userData = hashMapOf("name" to "Pengguna", "profilePicture" to null)
                    firestore.collection("users").document(it).set(userData).addOnSuccessListener {
                        profileName.text = "Hi, Pengguna!"
                        profilePicture.setImageResource(R.drawable.baseline_account_circle_24)
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal membuat profil pengguna: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    private fun showChangeProfilePictureDialog() {
        val options = arrayOf("Ambil Foto", "Pilih dari Galeri", "Hapus Foto Profil", "Batal")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Pilih Opsi")
        builder.setItems(options) { dialog, which ->
            when (options[which]) {
                "Ambil Foto" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
                    } else {
                        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        val photoFile = createImageFile()
                        photoUri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                        takePhotoLauncher.launch(takePictureIntent)
                    }
                }
                "Pilih dari Galeri" -> {
                    pickImageLauncher.launch("image/*")
                }
                "Hapus Foto Profil" -> {
                    deleteProfilePicture()
                }
                "Batal" -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }

    private fun uploadProfilePicture(uri: Uri) {
        profilePicture.setImageURI(uri)

        val userId = auth.currentUser?.uid
        val profilePicRef = storageRef.child("profile_pictures/$userId.jpg")
        val uploadTask = profilePicRef.putFile(uri)

        uploadTask.addOnSuccessListener {
            profilePicRef.downloadUrl.addOnSuccessListener { downloadUri ->
                firestore.collection("users").document(userId!!)
                    .update("profilePicture", downloadUri.toString())
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            loadUserProfile()
                        } else {
                            Toast.makeText(this, "Gagal memperbarui gambar profil", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private fun deleteProfilePicture() {
        val userId = auth.currentUser?.uid
        val profilePicRef = storageRef.child("profile_pictures/$userId.jpg")
        profilePicRef.delete().addOnSuccessListener {
            firestore.collection("users").document(userId!!)
                .update("profilePicture", null)
            profilePicture.setImageResource(R.drawable.baseline_account_circle_24)
        }
    }
}
