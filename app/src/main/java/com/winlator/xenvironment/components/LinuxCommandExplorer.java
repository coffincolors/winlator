package com.winlator.xenvironment.components;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LinuxCommandExplorer {

    private static final String TAG = "LinuxCommandExplorer";

    private Context context = null;

    public LinuxCommandExplorer(Context context) {
        this.context = context;
    }

    public LinuxCommandExplorer() {

    }

    // Function to execute a Linux command and log the output
    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "Command Output: " + line);
            }
            process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Error executing command: " + command, e);
        }
    }

    // Function to execute multiple commands and log the results
    public void runCommandTests() {
        String[] commands = {
                "uname -a",          // Check the kernel version
                "lsmod",             // List loaded kernel modules
                "modprobe snd-aloop", // Try to load the ALSA loopback module
                "pactl list",        // List PulseAudio modules
                "which pactl",       // Check if PulseAudio control utility is available
                "which alsamixer",   // Check if ALSA Mixer is available
                "cat /proc/asound/cards",  // List sound cards
                "aplay -l",          // List available playback devices
                "arecord -l",        // List available capture devices
                "lspci",             // Check PCI devices (unlikely on Android, but testing)
                "dmesg",             // Kernel log messages
                "df -h",             // Check disk space
                "top -n 1",          // Check running processes
                // Add any other Linux commands you want to test
        };

        for (String command : commands) {
            Log.d(TAG, "Executing: " + command);
            executeCommand(command);
        }
    }
}
