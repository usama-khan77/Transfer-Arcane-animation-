package com.usama.aianimationstudio;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 100;
    private static final String DEFAULT_PROMPT = "Transform this photo into a polished painterly animated-film illustration. Preserve the same subject, facial identity and features, hairstyle, clothing, pose, and composition. Use expressive clean linework, detailed painterly cel-shading, textured surfaces, cinematic warm and cool lighting, dramatic shadows, rich colors, and a softly painted background.";

    private final ArrayList<Uri> selectedImages = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private EditText promptInput;
    private Button downloadButton;
    private Button transformButton;
    private ProgressBar progressBar;
    private ImageView preview;
    private long sessionId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 30, 28, 28);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(layout);

        TextView title = new TextView(this);
        title.setText("AI Animation Studio");
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText("Offline photo-to-image transformation. The model downloads once; generation then runs on this device.");
        description.setTextSize(15);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 14, 0, 20);
        layout.addView(description);

        downloadButton = new Button(this);
        downloadButton.setText("Download AI Model (about 1.66 GB)");
        layout.addView(downloadButton);

        Button chooseButton = new Button(this);
        chooseButton.setText("Choose Photos");
        layout.addView(chooseButton);

        status = new TextView(this);
        status.setText("Model not downloaded. Choose Download AI Model first.");
        status.setTextSize(14);
        status.setPadding(0, 14, 0, 14);
        layout.addView(status);

        promptInput = new EditText(this);
        promptInput.setHint("Describe the transformation");
        promptInput.setText(DEFAULT_PROMPT);
        promptInput.setMinLines(4);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        layout.addView(promptInput);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        layout.addView(progressBar, new LinearLayout.LayoutParams(-1, 12));

        transformButton = new Button(this);
        transformButton.setText("Transform Selected Photos");
        transformButton.setEnabled(false);
        layout.addView(transformButton);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 700);
        imageParams.topMargin = 20;
        layout.addView(preview, imageParams);

        downloadButton.setOnClickListener(v -> startModelDownload());
        chooseButton.setOnClickListener(v -> chooseImages());
        transformButton.setOnClickListener(v -> startTransform());

        if (SanaModelManager.isModelReady(this)) {
            status.setText("AI model is ready. Choose photos to begin.");
            transformButton.setEnabled(true);
            downloadButton.setText("Model Downloaded");
        }
        setContentView(scroll);
    }

    private void chooseImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        selectedImages.clear();
        try {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    keepReadPermission(uri, data);
                    selectedImages.add(uri);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                keepReadPermission(uri, data);
                selectedImages.add(uri);
            }
        } catch (Exception e) {
            showStatus("Could not access selected photos: " + e.getMessage());
            return;
        }
        showStatus(selectedImages.size() + " photo(s) selected");
        transformButton.setEnabled(SanaModelManager.isModelReady(this) && !selectedImages.isEmpty());
    }

    private void keepReadPermission(Uri uri, Intent data) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some document providers do not offer persistable grants; the URI is copied immediately during processing.
        }
    }

    private void startModelDownload() {
        downloadButton.setEnabled(false);
        transformButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        showStatus("Starting model download. Keep the app open and use a stable Wi-Fi connection.");
        worker.execute(() -> {
            try {
                SanaModelManager.download(this, message -> runOnUiThread(() -> showStatus(message)));
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    downloadButton.setText("Model Downloaded");
                    downloadButton.setEnabled(false);
                    transformButton.setEnabled(!selectedImages.isEmpty());
                    showStatus("Model ready. You can now transform photos offline.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                    transformButton.setEnabled(SanaModelManager.isModelReady(this) && !selectedImages.isEmpty());
                    showStatus("Model download stopped: " + e.getMessage() + " Tap download again to resume.");
                });
            }
        });
    }

    private void startTransform() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Choose at least one photo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SanaModelManager.isModelReady(this)) {
            Toast.makeText(this, "Download the AI model first", Toast.LENGTH_SHORT).show();
            return;
        }
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) prompt = DEFAULT_PROMPT;
        final String finalPrompt = prompt;
        final ArrayList<Uri> images = new ArrayList<>(selectedImages);
        transformButton.setEnabled(false);
        downloadButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        showStatus("Preparing on-device AI engine…");
        worker.execute(() -> processImages(images, finalPrompt));
    }

    private void processImages(ArrayList<Uri> images, String prompt) {
        File modelDir = SanaModelManager.getModelDirectory(this);
        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AIAnimationStudio");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            finishProcessing("Cannot create output folder");
            return;
        }
        try {
            if (sessionId == 0) {
                String config = "{\"diffusion_memory_mode\":\"2\",\"backend_type\":\"cpu\",\"image_width\":512,\"image_height\":512,\"grid_size\":1}";
                sessionId = SanaEngine.initNative(modelDir.getAbsolutePath(), config);
                if (sessionId == 0) throw new Exception("Could not initialize Sana engine");
            }
            for (int i = 0; i < images.size(); i++) {
                final int number = i + 1;
                Uri uri = images.get(i);
                runOnUiThread(() -> showStatus("Preparing photo " + number + "/" + images.size()));
                File input = copyUriToFile(uri, number);
                File output = new File(outputDir, "AI_Animation_" + System.currentTimeMillis() + "_" + number + ".png");
                HashMap<String, Object> result = SanaEngine.generateNative(
                        sessionId, prompt, input.getAbsolutePath(), output.getAbsolutePath(),
                        10, 42 + i, false, 1.0f,
                        progress -> {
                            runOnUiThread(() -> showStatus("Transforming photo " + number + "/" + images.size() + " — " + progress + "%"));
                            return true;
                        });
                Object success = result == null ? null : result.get("success");
                if (!(success instanceof Boolean) || !((Boolean) success) || !output.isFile() || output.length() == 0) {
                    Object error = result == null ? "No result returned" : result.get("message");
                    throw new Exception("Photo " + number + " failed: " + error);
                }
                saveToGallery(output);
                final File finished = output;
                runOnUiThread(() -> {
                    preview.setImageBitmap(BitmapFactory.decodeFile(finished.getAbsolutePath()));
                    showStatus("Finished photo " + number + "/" + images.size() + ". Saved to Pictures/AI Animation Studio where supported.");
                });
            }
            finishProcessing("All selected photos were processed.");
        } catch (Exception e) {
            finishProcessing("Generation stopped: " + e.getMessage());
        }
    }

    private File copyUriToFile(Uri uri, int index) throws Exception {
        File file = new File(getCacheDir(), "sana_input_" + index + ".img");
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(file)) {
            if (input == null) throw new Exception("Cannot open selected photo");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return file;
    }

    private void saveToGallery(File source) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, source.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Animation Studio");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create gallery image");
            try (InputStream input = new java.io.FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new Exception("Could not write gallery image");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
        }
    }

    private void finishProcessing(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            transformButton.setEnabled(SanaModelManager.isModelReady(this) && !selectedImages.isEmpty());
            downloadButton.setEnabled(!SanaModelManager.isModelReady(this));
            showStatus(message);
        });
    }

    private void showStatus(String message) {
        if (status != null) status.setText(message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.execute(() -> {
            if (sessionId != 0) {
                SanaEngine.releaseNative(sessionId);
                sessionId = 0;
            }
        });
        worker.shutdown();
    }
}
