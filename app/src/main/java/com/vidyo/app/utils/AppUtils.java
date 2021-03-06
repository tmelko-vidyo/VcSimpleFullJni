package com.vidyo.app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Surface;

import androidx.core.content.FileProvider;

import com.vidyo.BuildConfig;
import com.vidyo.R;
import com.vidyo.app.ApplicationJni;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AppUtils {

    private static final String TAG = AppUtils.class.getCanonicalName();

    private static final String LOG_FILE_PREFIX = "VidyoMobile_";

    private static final int ORIENTATION_UP = 0;
    private static final int ORIENTATION_DOWN = 1;
    private static final int ORIENTATION_LEFT = 2;
    private static final int ORIENTATION_RIGHT = 3;

    public static String writeCaCertificates(Context context) {
        try {
            InputStream caCertStream = context.getResources().openRawResource(R.raw.ca_certificates);
            File caCertDirectory;

            String pathDir = getAndroidInternalMemDir(context);
            if (pathDir == null) return null;

            caCertDirectory = new File(pathDir);

            File caFile = new File(caCertDirectory, "ca-certificates.crt");
            FileOutputStream caCertFile = new FileOutputStream(caFile);

            byte buf[] = new byte[1024];
            int len;

            while ((len = caCertStream.read(buf)) != -1) {
                caCertFile.write(buf, 0, len);
            }

            caCertStream.close();
            caCertFile.close();
            return caFile.getPath();
        } catch (Exception e) {
            return null;
        }
    }

    public static String getAndroidInternalMemDir(Context context) {
        File fileDir = context.getFilesDir();
        return fileDir != null ? fileDir.toString() + File.separator : null;
    }

    public static String getAndroidCacheDir(Context context) {
        File cacheDir = context.getCacheDir();
        return cacheDir != null ? cacheDir.toString() + File.separator : null;
    }

    @SuppressLint("HardwareIds")
    public static String getUniqueID(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permissions granted for Telephony manager");
            return "";
        }

        String machineID = null;

        TelephonyManager tManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tManager != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                machineID = tManager.getImei();
            } else {
                machineID = tManager.getDeviceId();
            }
        }

        if (machineID == null) {
            machineID = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }

        if (machineID == null) return "";

        return machineID;
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;

        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static void SetDeviceOrientation(int newRotation, ApplicationJni applicationJni) {
        int orientation = rotation2Orientation(newRotation);
        applicationJni.LmiAndroidJniSetOrientation(orientation);
    }

    private static int rotation2Orientation(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
                return ORIENTATION_UP;
            case Surface.ROTATION_90:
                return ORIENTATION_RIGHT;
            case Surface.ROTATION_180:
                return ORIENTATION_DOWN;
            case Surface.ROTATION_270:
                return ORIENTATION_LEFT;
            default:
                return ORIENTATION_UP;
        }
    }


    /**
     * Send email with log file
     */
    public static void sendLogs(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("message/rfc822");

        intent.putExtra(Intent.EXTRA_SUBJECT, "VidyoWorks Sample Logs");
        intent.putExtra(Intent.EXTRA_TEXT, "Logs attached..." + additionalInfo());

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, logFileUri(context));

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(Intent.createChooser(intent, "Choose sender..."));
        } catch (Exception sendReportEx) {
            sendReportEx.printStackTrace();
        }
    }

    /**
     * Expose logs file URIs for sharing.
     *
     * @param context {@link Context}
     * @return log file uri list.
     */
    private static ArrayList<Uri> logFileUri(Context context) {
        File cacheDir = context.getCacheDir();
        File[] cachedFiles;
        if (cacheDir == null || (cachedFiles = cacheDir.listFiles()) == null) return null;

        Log.d(TAG, "Going through cached files: " + cachedFiles.length);

        List<File> logFiles = new LinkedList<>();

        for (File file : cachedFiles) {
            if (file != null && !file.isDirectory() && file.getName().startsWith(LOG_FILE_PREFIX)) {
                logFiles.add(file);
            }
        }

        if (logFiles.isEmpty()) {
            Log.d(TAG, "Log files not detected.");
            return null;
        }

        Log.d(TAG, "Preparing log files: " + logFiles.size());

        logFiles.add(fetchConsoleLogs(context));

        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : logFiles) {
            uris.add(FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".file.provider", file));
        }
        return uris;
    }

    private static File fetchConsoleLogs(Context context) {
        File consoleLogs = new File(context.getCacheDir() + File.separator + "Console.log");

        try {
            // noinspection ResultOfMethodCallIgnored
            consoleLogs.delete();

            if (!consoleLogs.exists()) {
                // noinspection ResultOfMethodCallIgnored
                consoleLogs.getParentFile().mkdirs();
                // noinspection ResultOfMethodCallIgnored
                consoleLogs.createNewFile();
            }

            writeConsoleLogs(consoleLogs);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return consoleLogs;
    }

    private static String additionalInfo() {
        return "\n\nModel: " + Build.MODEL +
                "\n" + "Manufactured: " + Build.MANUFACTURER +
                "\n" + "Brand: " + Build.BRAND +
                "\n" + "Android OS version: " + Build.VERSION.RELEASE +
                "\n" + "Hardware : " + Build.HARDWARE +
                "\n" + "SDK Version : " + Build.VERSION.SDK_INT;
    }

    private static void writeConsoleLogs(File file) throws IOException {
        List<String> sCommand = new ArrayList<>();
        sCommand.add("logcat");
        sCommand.add("-d");

        Process process = new ProcessBuilder().command(sCommand).start();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        StringBuilder log = new StringBuilder();
        String line;

        while ((line = bufferedReader.readLine()) != null) {
            log.append(line);
            log.append("\n");
        }

        FileOutputStream fOut = new FileOutputStream(file, false);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fOut);
        outputStreamWriter.write(log.toString());

        outputStreamWriter.close();
        fOut.flush();
        fOut.close();

        try {
            new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Not able to clear the logs.");
        }
    }
}