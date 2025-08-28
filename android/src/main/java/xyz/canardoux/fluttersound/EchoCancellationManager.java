/*
 * Copyright 2018, 2019, 2020, 2021 Canardoux.
 *
 * This file is part of Flutter-Sound.
 *
 * Flutter-Sound is free software: you can redistribute it and/or modify
 * it under the terms of the Mozilla Public License version 2 (MPL2.0),
 * as published by the Mozilla organization.
 *
 * Flutter-Sound is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * MPL General Public License for more details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.canardoux.fluttersound;

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.AutomaticGainControl;
import android.util.Log;

/**
 * Enhanced Echo Cancellation Manager specifically designed for Android 15
 * Based on proven implementation from successful voice assistant projects
 */
public class EchoCancellationManager {
    private static final String TAG = "EchoCancellationManager";
    private static EchoCancellationManager instance;
    
    private AudioManager audioManager;
    private Context context;
    private boolean isEchoCancellationActive = false;
    private int originalAudioMode;
    private boolean originalSpeakerphoneState;
    
    // Audio effects that can work with any audio session ID
    private AcousticEchoCanceler aec;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl agc;
    
    private EchoCancellationManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    
    public static synchronized EchoCancellationManager getInstance(Context context) {
        if (instance == null) {
            instance = new EchoCancellationManager(context);
        }
        return instance;
    }
    
    /**
     * Enable echo cancellation using the approach that works on Android 15
     */
    public synchronized boolean enableEchoCancellation() {
        if (isEchoCancellationActive) {
            Log.d(TAG, "Echo cancellation already active");
            return true;
        }
        
        try {
            // Store original audio settings
            originalAudioMode = audioManager.getMode();
            originalSpeakerphoneState = audioManager.isSpeakerphoneOn();
            
            // Configure audio manager for voice communication
            // This is the key setting that enables hardware echo cancellation
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
            
            Log.d(TAG, "Audio configured for communication mode");
            Log.d(TAG, "Original mode: " + originalAudioMode + ", Speaker: " + originalSpeakerphoneState);
            
            // Try to setup software audio effects as backup
            // These will be created when an audio session becomes available
            setupAudioEffects();
            
            isEchoCancellationActive = true;
            Log.i(TAG, "Echo cancellation enabled successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable echo cancellation", e);
            return false;
        }
    }
    
    /**
     * Disable echo cancellation and restore original audio settings
     */
    public synchronized boolean disableEchoCancellation() {
        if (!isEchoCancellationActive) {
            Log.d(TAG, "Echo cancellation not active");
            return true;
        }
        
        try {
            // Release audio effects
            releaseAudioEffects();
            
            // Restore original audio settings
            audioManager.setMode(originalAudioMode);
            audioManager.setSpeakerphoneOn(originalSpeakerphoneState);
            
            isEchoCancellationActive = false;
            Log.i(TAG, "Echo cancellation disabled, audio settings restored");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable echo cancellation", e);
            return false;
        }
    }
    
    /**
     * Setup audio effects when an audio session ID becomes available
     */
    public synchronized void attachToAudioSession(int audioSessionId) {
        if (!isEchoCancellationActive || audioSessionId <= 0) {
            return;
        }
        
        try {
            // Release any existing effects first
            releaseAudioEffects();
            
            // Setup new effects with the provided session ID
            setupAudioEffectsForSession(audioSessionId);
            
            Log.d(TAG, "Audio effects attached to session ID: " + audioSessionId);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to attach audio effects to session: " + audioSessionId, e);
        }
    }
    
    private void setupAudioEffects() {
        // This method sets up effects that don't require a specific session ID
        // The actual attachment happens when attachToAudioSession is called
        Log.d(TAG, "Preparing audio effects for echo cancellation");
    }
    
    private void setupAudioEffectsForSession(int audioSessionId) {
        try {
            // Setup Acoustic Echo Canceler
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(audioSessionId);
                if (aec != null) {
                    aec.setEnabled(true);
                    Log.d(TAG, "AcousticEchoCanceler created and enabled for session " + audioSessionId);
                } else {
                    Log.w(TAG, "Failed to create AcousticEchoCanceler for session " + audioSessionId);
                }
            } else {
                Log.w(TAG, "AcousticEchoCanceler not available on this device");
            }
            
            // Setup Noise Suppressor
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                    Log.d(TAG, "NoiseSuppressor created and enabled for session " + audioSessionId);
                } else {
                    Log.w(TAG, "Failed to create NoiseSuppressor for session " + audioSessionId);
                }
            } else {
                Log.w(TAG, "NoiseSuppressor not available on this device");
            }
            
            // Setup Automatic Gain Control
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(audioSessionId);
                if (agc != null) {
                    agc.setEnabled(true);
                    Log.d(TAG, "AutomaticGainControl created and enabled for session " + audioSessionId);
                } else {
                    Log.w(TAG, "Failed to create AutomaticGainControl for session " + audioSessionId);
                }
            } else {
                Log.w(TAG, "AutomaticGainControl not available on this device");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up audio effects for session " + audioSessionId, e);
        }
    }
    
    private void releaseAudioEffects() {
        try {
            if (aec != null) {
                aec.setEnabled(false);
                aec.release();
                aec = null;
                Log.d(TAG, "AcousticEchoCanceler released");
            }
            
            if (noiseSuppressor != null) {
                noiseSuppressor.setEnabled(false);
                noiseSuppressor.release();
                noiseSuppressor = null;
                Log.d(TAG, "NoiseSuppressor released");
            }
            
            if (agc != null) {
                agc.setEnabled(false);
                agc.release();
                agc = null;
                Log.d(TAG, "AutomaticGainControl released");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error releasing audio effects", e);
        }
    }
    
    /**
     * Check if echo cancellation features are available on this device
     */
    public static boolean isEchoCancellationSupported() {
        return AcousticEchoCanceler.isAvailable();
    }
    
    /**
     * Get detailed support information for debugging
     */
    public static String getEchoCancellationSupportInfo() {
        StringBuilder info = new StringBuilder();
        info.append("AcousticEchoCanceler: ").append(AcousticEchoCanceler.isAvailable()).append("\n");
        info.append("NoiseSuppressor: ").append(NoiseSuppressor.isAvailable()).append("\n");
        info.append("AutomaticGainControl: ").append(AutomaticGainControl.isAvailable()).append("\n");
        return info.toString();
    }
    
    public boolean isActive() {
        return isEchoCancellationActive;
    }
}