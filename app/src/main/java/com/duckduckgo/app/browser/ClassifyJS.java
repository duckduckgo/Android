/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser;


import android.content.Context;
import android.util.Log;
import android.util.Pair;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ai.onnxruntime.OnnxSequence;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import ai.onnxruntime.OrtSession.Result;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class ClassifyJS {

    // The ORT environment used to create the sessions for the classification models.
    private final OrtEnvironment env;
    private OrtSession sessionClassification;
    private OrtSession sessionClassificationTfidf;

    private final File file;

    String message = null;
    private static final String CACHE_FILE = "blocks.txt";
    private static final HashMap<String, String> blocks = new HashMap<>();

    private static  Locale locale = Locale.getDefault();


    // A set of keywords used to extract features from scripts for classification.
    private final Set<String> classificationKws;
    // A vector of features used to classify scripts.
    private List<String> classificationFeatures;

    private static final String CLASSIFICATION_FEATURES_JSON = "classification_features";

    // A map containing the names of the categories that scripts can be classified into.
    private static final Map<Integer, String> CLASSIFICATION_LABELS = new HashMap<Integer, String>() {{
        put(0, "marketing");
        put(1, "cdn");
        put(2, "tag-manager");
        put(3, "video");
        put(4, "customer-success");
        put(5, "utility");
        put(6, "ads");
        put(7, "analytics");
        put(8, "hosting");
        put(9, "content");
        put(10, "social");
        put(11, "other");
    }};


    public ClassifyJS(Context context ) {
        // Create an OrtEnvironment instance
        env = OrtEnvironment.getEnvironment();

        // Load the classification and classification_tfidf model files from raw resources
        try (InputStream classificationInputStream = context.getResources().openRawResource(R.raw.classification);
             InputStream classificationTfidfInputStream = context.getResources().openRawResource(R.raw.classification_tfidf)) {

            byte[] classificationBytes = new byte[classificationInputStream.available()];
            classificationInputStream.read(classificationBytes);
            sessionClassification = env.createSession(classificationBytes);

            byte[] classificationTfidfBytes = new byte[classificationTfidfInputStream.available()];
            classificationTfidfInputStream.read(classificationTfidfBytes);
            sessionClassificationTfidf = env.createSession(classificationTfidfBytes);

        } catch (IOException | OrtException e) {
            e.printStackTrace();
        }

        try {
            classificationFeatures = loadJsonFile(context, CLASSIFICATION_FEATURES_JSON).get("features");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Get the keywords from the features and store them in classificationKws variable
        classificationKws = getKwsFromFeatures(classificationFeatures);

        file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/" + CACHE_FILE);
        // read the file contents to a hashmap if it exists. The file contains key and value pairs separated by a comma in each line
        if (file.exists()) {
            try{
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {

                    String[] parts = line.split(",");
                    blocks.put(parts[0], parts[1]+","+parts[2]);
                }
                br.close();
            } catch (IOException e) {
                Log.e("browser", "Failed to read blocks.txt", e);
            }
            // log the contents of the hashmap and also the count of entries
            Log.d("Blocks file", "File exists and Loaded " + blocks.size() + " entries");
        }else {

            try {
                Log.d("Blocks file", "Creating blocks.txt");
                file.createNewFile();
                downloadFile("https://raw.githubusercontent.com/sashanksilwal/Capstone/main/Phase5/predictions.csv", file, context);

            } catch (IOException e) {
                Log.e("browser", "Failed to create blocks.txt", e);
            }
        }
    }

    // Load a JSON file from raw resources and parse it into a Map<String, List<String>> object
    private Map<String, List<String>> loadJsonFile(Context context, String jsonFilename) throws IOException {
        // Open the JSON file from raw resources
        int resourceId = context.getResources().getIdentifier(jsonFilename, "raw", context.getPackageName());
        try (InputStream inputStream = context.getResources().openRawResource(resourceId)) {
            // Read the contents of the file into a byte array
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);

            // Convert the byte array into a UTF-8 encoded string
            String json = new String(buffer, StandardCharsets.UTF_8);

            // Create a Gson instance to parse the JSON string
            Gson gson = new Gson();

            // Define a TypeToken to specify the target type of the JSON parsing
            TypeToken<Map<String, List<String>>> token = new TypeToken<Map<String, List<String>>>() {};

            // Parse the JSON string into a Map<String, List<String>> object using Gson
            return gson.fromJson(json, token.getType());
        }
    }

    private Set<String> getKwsFromFeatures(List<String> features) {
        Set<String> classification_kws = new HashSet<>();

        // Iterate through each input string
        for (String element : features) {
            // Split the input string by the "|" character
            String[] tokens = element.split("\\|");

            // Iterate through the tokens and process each one
            for (String token : tokens) {
                // Remove any spaces from the keyword
                String trimmedToken = token.trim();

                // Add the keyword to the classification_kws set
                classification_kws.add(trimmedToken);
            }
        }
        return classification_kws;
    }


    public Pair<String, Float> predict(String url) throws OrtException {

        // check if url in blocks hashmap
        if (blocks.containsKey(url)) {
            String[] parts = Objects.requireNonNull(blocks.get(url)).split(",");
            return new Pair<>(parts[0], Float.parseFloat(parts[1]));
        }

        // Use the URL to download JavaScript code
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                message = stringBuilder.toString();
                bufferedReader.close();
            }
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create a map to store the prediction results
        Map<Integer, Float> result = new HashMap<>();

        // Get the reduced script for classification
        String reducedScript = getScriptsClassificationFeatures(message, classificationKws, classificationFeatures);
        String reducedScriptCopy = reducedScript;

        // Create an array to hold the input data for the first classification model
        String[] floatInputArray = new String[1];

        // Create an input tensor from the reducedScriptCopy for the first classification model
        long[] inputShape_tfidf = new long[]{1, 1};
        floatInputArray[0] = reducedScriptCopy;
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, floatInputArray, inputShape_tfidf);

        // Run the first classification model and get the output tensor
        Result output_tensor = sessionClassificationTfidf.run(Collections.singletonMap("float_input", inputTensor));

        // Create an array to hold the input data for the second classification model
        long[] inputShape = new long[]{1, 499};
        float[] input_tensor_values = new float[499];

        // Get the float values from the output tensor of the first classification model
        OnnxTensor outputTensor = (OnnxTensor) output_tensor.get(0);
        float[] floatArr = outputTensor.getFloatBuffer().array();

        // Copy non-zero float values to the input tensor array for the second classification model
        for (int i = 0; i < 499; i++) {
            if (floatArr[i] != 0) {
                input_tensor_values[i] = floatArr[i];
            }
        }

        // Create an input tensor from the input_tensor_values for the second classification model
        OnnxTensor inputTensor2 = OnnxTensor.createTensor(env, FloatBuffer.wrap(input_tensor_values), inputShape);

        // Run the second classification model and get the output tensor
        Result output_tensor2 = sessionClassification.run(Collections.singletonMap("float_input", inputTensor2));

        OnnxSequence outputTensor2 = (OnnxSequence) output_tensor2.get(1);

        List<OnnxValue> onnxValueList = (List<OnnxValue>) outputTensor2.getValue();

        float index = 0;

        // Loop through onnxValueList and get the values
        for (OnnxValue onnxValue : onnxValueList) {
            // Get the value of onnxValue
            Map<String, Float> mapValue = (Map<String, Float>) onnxValue.getValue();

            // Loop through mapValue and get the values
            for (Map.Entry<String, Float> entry : mapValue.entrySet()) {
                // Get the value of entry
                Float value = entry.getValue();
                result.put((int) index, value);
                index++;
            }
        }
        // call method maxKey to get the key with the highest value
        int maxKey = getHighestValue(result);
        // add the url as key and the classification label and probability as value separated by comma to the blocks map
        blocks.put(url, CLASSIFICATION_LABELS.get(maxKey) + "," + result.get(maxKey));

        // add to the file the url and the classification label and probability separated by comma
        try {
            // create the file in the Download folder
            FileWriter writer = new FileWriter(file, true);
            // write the value of floatInputArray to the file in each in new line
            writer.append(url).append(",").append(CLASSIFICATION_LABELS.get(maxKey)).append(",").append(result.get(maxKey).toString()).append("\n");
            writer.flush();
            writer.close();

        } catch (IOException e) {

            // log the exception
            Log.e("Exception", "File write failed: " + e.toString());
        }

        return new Pair<>(CLASSIFICATION_LABELS.get(maxKey), result.get(maxKey));
    }

    public static String getScriptsClassificationFeatures(String data, Set<String> classificationKws, List<String> classificationFeatures) {
        List<String> features = getScriptsFeatures(data, classificationKws, classificationFeatures);
        StringBuilder resultantFeatures = new StringBuilder();
        for (String ft : features) {
            resultantFeatures.append(ft).append(" ");
        }
        return resultantFeatures.toString();
    }

    public static List<String> getScriptsFeatures(String data, Set<String> kws, List<String> features) {
        List<String> resultantFeatures = new ArrayList<>();
        List<String> scriptsKws = new ArrayList<>();

        for (String kw : kws) {
            String kwNoSpaces = kw.replace(" ", ""); // remove spaces from kw
            String kw1 = "." + kwNoSpaces + "(";

            if (data != null) {
                int pos = data.indexOf(kw1); // Find the first occurrence of kw1 in data

                if (pos != -1) { // If kw1 is found in data
                    int count = 0;
                    do {
                        count++; // Count the number of occurrences of kw1 in data
                        pos = data.indexOf(kw1, pos + 1);
                    } while (pos != -1);

                    for (int i = 0; i < count; i++) { // Add kw to scripts_kws for each occurrence of kw1 in data
                        scriptsKws.add(kwNoSpaces);
                    }
                }
            }
        }

        for (String ft : features) {
            ft = ft.replace(" ", ""); // remove spaces from ft

            if (!ft.contains("|")) {
                int count = 0;
                for (String kw : scriptsKws) {
                    if (kw.equals(ft)) {
                        count++;
                    }
                }
                for (int i = 0; i < count; i++) {
                    resultantFeatures.add(ft);
                }
            } else {
                List<String> singularKws = Arrays.asList(ft.split("\\|")); // Use Arrays.asList() for efficiency

                int count = 0;
                for (String kw : singularKws) {
                    if (scriptsKws.contains(kw)) {
                        count++;
                    }
                }
                if (count == singularKws.size()) {
                    resultantFeatures.add(ft);
                }
            }
        }

        return resultantFeatures;
    }



    // function that takes Map<Integer, Float> result and returns the key with the highest value
    private int getHighestValue(Map<Integer, Float> result) {
        int maxKey = 0;
        float maxValue = 0;
        for (Map.Entry<Integer, Float> entry : result.entrySet()) {
            if (entry.getValue() > maxValue) {
                maxValue = entry.getValue();
                maxKey = entry.getKey();
            }
        }
        return maxKey;
    }

    // method that takes file and then downlods the content from the url and saves it to the file
    public static void downloadFile(String url, File file, Context context) {
        Thread thread = new Thread(() -> {
            try {
                URL blockUrl = new URL(url);
                Log.d("browser", "Download AdBlock hosts");
                URLConnection connection = blockUrl.openConnection();
                connection.setReadTimeout(5000);
                connection.setConnectTimeout(10000);
                InputStream is = connection.getInputStream();
                BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                FileOutputStream outStream = new FileOutputStream(file);
                byte[] buff = new byte[5 * 1024];

                int len;
                while ((len = inStream.read(buff)) != -1) {
                    outStream.write(buff, 0, len);
                }

                outStream.flush();
                outStream.close();
                inStream.close();
                loadBlockClassifications(context);
            } catch (IOException i) {
                Log.w("browser", "Error downloading AdBlock hosts", i);

            }
        });
        thread.start();
    }

    private static void loadBlockClassifications(final Context context) {
        Thread thread = new Thread(() -> {
            try{
                File file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/" + CACHE_FILE);
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {

                    String[] parts = line.split(",");
                    blocks.put(parts[0], parts[1]+","+parts[2]);
                }
                Log.d("Blocks file", "File downloaded and Loaded " + blocks.size() + " entries");
                br.close();

            } catch (IOException e) {
                Log.e("browser", "Failed to read blocks.txt", e);
            }
        });
        thread.start();
    }
}