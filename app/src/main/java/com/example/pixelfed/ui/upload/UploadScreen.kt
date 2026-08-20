package com.example.pixelfed.ui.upload

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.pixelfed.data.repository.PixelfedRepository
import com.example.pixelfed.utils.ImageMetadata
import com.example.pixelfed.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    repository: PixelfedRepository,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var captionState by remember { mutableStateOf(TextFieldValue("")) }
    var resizeTo8Mb by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var topTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingTags by remember { mutableStateOf(false) }

    var originalMetadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var resizedMetadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var isCalculatingResized by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun fetchTags(forceRefresh: Boolean = false) {
        scope.launch {
            isLoadingTags = true
            val result = repository.getUserTopTags(forceRefresh = forceRefresh)
            result.fold(
                onSuccess = { tags ->
                    Log.d("UploadScreen", "fetchTags success: loaded ${tags.size} tags: $tags")
                    topTags = tags
                },
                onFailure = { ex ->
                    Log.e("UploadScreen", "fetchTags failure: ${ex.message}", ex)
                }
            )
            isLoadingTags = false
        }
    }

    LaunchedEffect(Unit) {
        fetchTags(forceRefresh = false)
    }

    fun insertTagAtCursor(tag: String) {
        val tagToInsert = "#$tag "
        val currentText = captionState.text
        val selection = captionState.selection
        val start = selection.min.coerceAtLeast(0)
        val end = selection.max.coerceAtLeast(0)

        val newText = currentText.substring(0, start) + tagToInsert + currentText.substring(end)
        val newCursorPos = start + tagToInsert.length

        captionState = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    }

    LaunchedEffect(selectedImageUri) {
        val uri = selectedImageUri
        if (uri != null) {
            originalMetadata = withContext(Dispatchers.IO) {
                ImageUtils.getImageMetadata(context, uri)
            }
        } else {
            originalMetadata = null
            resizedMetadata = null
        }
    }

    LaunchedEffect(selectedImageUri, resizeTo8Mb, originalMetadata) {
        val uri = selectedImageUri
        val meta = originalMetadata
        if (uri != null && resizeTo8Mb && meta != null && meta.sizeBytes > ImageUtils.MAX_BYTES_8MB) {
            isCalculatingResized = true
            val resizedFile = withContext(Dispatchers.IO) {
                ImageUtils.resizeImageDownToMaxBytes(context, uri, ImageUtils.MAX_BYTES_8MB)
            }
            resizedMetadata = if (resizedFile != null) {
                ImageUtils.getFileMetadata(resizedFile)
            } else {
                null
            }
            isCalculatingResized = false
        } else {
            resizedMetadata = null
            isCalculatingResized = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            statusMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload to Pixelfed") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(selectedImageUri),
                        contentDescription = "Selected Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "No image selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (selectedImageUri != null && originalMetadata != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val meta = originalMetadata!!
                        if (resizeTo8Mb && meta.sizeBytes > ImageUtils.MAX_BYTES_8MB) {
                            Text(
                                text = "Original: ${meta.formatFileSize()} (${meta.formatDimensions()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isCalculatingResized) {
                                Text(
                                    text = "Resized: Calculating...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (resizedMetadata != null) {
                                val rMeta = resizedMetadata!!
                                Text(
                                    text = "Resized: ${rMeta.formatFileSize()} (${rMeta.formatDimensions()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                text = "Size: ${meta.formatFileSize()} (${meta.formatDimensions()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { resizeTo8Mb = !resizeTo8Mb }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = resizeTo8Mb,
                    onCheckedChange = { resizeTo8Mb = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Resize down to 8MB",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedImageUri == null) "Select Photo from Gallery" else "Change Photo")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = captionState,
                onValueChange = { captionState = it },
                label = { Text("Write a caption...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top Tags",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { fetchTags(forceRefresh = true) },
                        enabled = !isLoadingTags
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh tags"
                        )
                    }
                }

                if (isLoadingTags) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else if (topTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        topTags.forEach { tag ->
                            SuggestionChip(
                                onClick = { insertTagAtCursor(tag) },
                                label = { Text("#$tag") }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No tags found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (selectedImageUri == null) {
                        statusMessage = "Please select an image first"
                        isError = true
                        return@Button
                    }

                    isUploading = true
                    statusMessage = null
                    isError = false

                    scope.launch {
                        val result = repository.uploadPhotoAndCreateStatus(
                            imageUri = selectedImageUri!!,
                            caption = captionState.text,
                            resizeTo8Mb = resizeTo8Mb
                        )
                        isUploading = false
                        result.fold(
                            onSuccess = {
                                statusMessage = "Successfully uploaded photo to Pixelfed!"
                                isError = false
                                selectedImageUri = null
                                captionState = TextFieldValue("")
                            },
                            onFailure = { ex ->
                                statusMessage = "Upload failed: ${ex.localizedMessage ?: "Unknown error"}"
                                isError = true
                            }
                        )
                    }
                },
                enabled = !isUploading && selectedImageUri != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Upload Photo")
                }
            }
        }
    }
}
