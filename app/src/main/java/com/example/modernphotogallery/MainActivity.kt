
package com.example.modernphotogallery

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.modernphotogallery.ui.theme.ModernPhotoGalleryTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete

private val GalleryBlue = Color(0xFF2196F3)
private val GalleryPurple = Color(0xFF9575CD)
private val GalleryBackground = Color(0xFF10131C)
private val GalleryCard = Color(0xFF1D2535)
private val GalleryText = Color(0xFFF5F7FF)
private val GalleryMutedText = Color(0xFFB8C2D9)

data class GalleryPhoto(
    val title: String,
    val imageUrl: String
)

private val samplePhotos = listOf(
    GalleryPhoto(
        "Mountain View",
        "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=600"
    ),
    GalleryPhoto(
        "Ocean",
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600"
    ),
    GalleryPhoto(
        "Flowers",
        "https://images.unsplash.com/photo-1490750967868-88aa4486c946?w=600"
    ),
    GalleryPhoto(
        "Forest",
        "https://images.unsplash.com/photo-1448375240586-882707db888b?w=600"
    ),
    GalleryPhoto(
        "Sunset",
        "https://images.unsplash.com/photo-1472120435266-818b7c3b3f8a?w=600"
    ),
    GalleryPhoto(
        "City",
        "https://images.unsplash.com/photo-1519608487953-e999c86e7455?w=600"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ModernPhotoGalleryTheme {
                GalleryScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val selectedPhotos = remember { mutableStateListOf<Uri>() }
    var showCamera by remember { mutableStateOf(false) }
    var photoToDelete by remember { mutableStateOf<Uri?>(null) }

    // Load photos previously saved by this app.
    LaunchedEffect(Unit) {
        selectedPhotos.clear()
        selectedPhotos.addAll(loadSavedPhotos(context))
    }

    // Open Android's visual photo picker.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { pickedUris ->
        val combinedPhotos = (selectedPhotos.toList() + pickedUris)
            .distinctBy { it.toString() }

        selectedPhotos.clear()
        selectedPhotos.addAll(combinedPhotos)

        if (pickedUris.isNotEmpty()) {
            Toast.makeText(
                context,
                "${pickedUris.size} photo(s) selected",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Request camera permission when needed.
    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            Toast.makeText(
                context,
                "Camera permission is needed to take photos.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Ask Android to confirm deletion when a photo needs system approval.
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            photoToDelete?.let { uri ->
                selectedPhotos.removeAll { it == uri }
            }
            photoToDelete = null

            // Refresh the gallery after Android confirms deletion.
            selectedPhotos.clear()
            selectedPhotos.addAll(loadSavedPhotos(context))

            Toast.makeText(
                context,
                "Photo deleted",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            photoToDelete = null
            Toast.makeText(
                context,
                "Photo was not deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    BackHandler(enabled = showCamera) {
        showCamera = false
    }

    if (photoToDelete != null) {
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Delete photo?") },
            text = {
                Text(
                    "Are you sure you want to delete this photo? " +
                            "You may be asked to approve the deletion."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = photoToDelete ?: return@TextButton

                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val request = MediaStore.createDeleteRequest(
                                    context.contentResolver,
                                    listOf(uri)
                                )
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(
                                        request.intentSender
                                    ).build()
                                )
                            } else {
                                val deleted = context.contentResolver.delete(
                                    uri,
                                    null,
                                    null
                                )

                                if (deleted > 0) {
                                    selectedPhotos.removeAll { it == uri }
                                    Toast.makeText(
                                        context,
                                        "Photo deleted",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Android could not delete this photo.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                photoToDelete = null
                            }
                        } catch (exception: Exception) {
                            photoToDelete = null
                            Toast.makeText(
                                context,
                                "Android could not delete this photo. " +
                                        "It may require additional permission.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF6B81))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { photoToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = GalleryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "My Photo Gallery",
                            color = GalleryBlue,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your favorite moments, all in one place",
                            color = GalleryMutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = GalleryBackground
                )
            )
        }
    ) { innerPadding ->
        if (showCamera) {
            CameraScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onPhotoSaved = { uri ->
                    selectedPhotos.add(0, uri)
                    showCamera = false
                    Toast.makeText(
                        context,
                        "Photo saved to your gallery!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onClose = { showCamera = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GalleryBackground)
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GalleryBlue
                    )
                ) {
                    Text("Choose My Photos", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            showCamera = true
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GalleryPurple
                    )
                ) {
                    Text("Take a Photo", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (selectedPhotos.isEmpty()) {
                        "Explore the gallery"
                    } else {
                        "${selectedPhotos.size} photos selected or saved"
                    },
                    color = GalleryMutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(
                        start = 2.dp,
                        bottom = 8.dp
                    )
                )

                if (selectedPhotos.isEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(samplePhotos) { photo ->
                            SamplePhotoCard(photo = photo)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = selectedPhotos.toList(),
                            key = { it.toString() }
                        ) { uri ->
                            PersonalPhotoCard(
                                uri = uri,
                                onDelete = { photoToDelete = uri }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplePhotoCard(photo: GalleryPhoto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = GalleryCard
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        AsyncImage(
            model = photo.imageUrl,
            contentDescription = photo.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .padding(4.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )

        Text(
            text = photo.title,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 9.dp
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = GalleryText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PersonalPhotoCard(
    uri: Uri,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = GalleryCard
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Box {
            AsyncImage(
                model = uri,
                contentDescription = "Personal photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(36.dp)
                    .background(
                        color = Color(0xDD10131C),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete photo",
                    tint = Color(0xFFFF7B8B)
                )
            }
        }

        Text(
            text = "My Photo",
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 9.dp
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = GalleryText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CameraScreen(
    modifier: Modifier = Modifier,
    onPhotoSaved: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var savingPhoto by remember { mutableStateOf(false) }
    val cameraExecutor: ExecutorService = remember {
        Executors.newSingleThreadExecutor()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GalleryBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { viewContext ->
                val previewView = PreviewView(viewContext)

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(viewContext)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            )
                            .build()

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )

                        imageCapture = capture
                        cameraReady = true
                    } catch (exception: Exception) {
                        Toast.makeText(
                            viewContext,
                            "Could not start the camera.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }, ContextCompat.getMainExecutor(viewContext))

                previewView
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GalleryCard
                )
            ) {
                Text("Back to Gallery", color = GalleryText)
            }

            Button(
                onClick = {
                    val capture = imageCapture

                    if (capture == null || !cameraReady || savingPhoto) {
                        return@Button
                    }

                    savingPhoto = true

                    val timestamp = SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date())

                    val contentValues = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "ModernGallery_$timestamp.jpg"
                        )
                        put(
                            MediaStore.Images.Media.MIME_TYPE,
                            "image/jpeg"
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/ModernPhotoGallery"
                            )
                        }
                    }

                    val outputOptions =
                        ImageCapture.OutputFileOptions.Builder(
                            context.contentResolver,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        ).build()

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(
                                outputFileResults:
                                ImageCapture.OutputFileResults
                            ) {
                                savingPhoto = false
                                val savedUri = outputFileResults.savedUri

                                if (savedUri != null) {
                                    onPhotoSaved(savedUri)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Photo saved, but its location could not be found.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                            override fun onError(
                                exception: ImageCaptureException
                            ) {
                                savingPhoto = false
                                Toast.makeText(
                                    context,
                                    "Could not save the photo.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = cameraReady && !savingPhoto,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GalleryBlue
                )
            ) {
                Text(if (savingPhoto) "Saving..." else "Capture")
            }
        }
    }
}

private fun loadSavedPhotos(context: Context): List<Uri> {
    val photos = mutableListOf<Uri>()

    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.RELATIVE_PATH
    )

    val selection: String
    val selectionArgs: Array<String>

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection =
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        selectionArgs = arrayOf("Pictures/ModernPhotoGallery/")
    } else {
        // Older Android versions may not expose RELATIVE_PATH.
        selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        selectionArgs = arrayOf("%/Pictures/ModernPhotoGallery/%")
    }

    try {
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media._ID
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val photoUri = Uri.withAppendedPath(
                    collection,
                    id.toString()
                )
                photos.add(photoUri)
            }
        }
    } catch (_: Exception) {
        // The gallery can still open if Android cannot return saved photos.
    }

    return photos
}
