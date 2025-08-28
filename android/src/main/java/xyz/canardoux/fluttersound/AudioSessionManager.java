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
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.AutomaticGainControl;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

public class AudioSessionManager {
    private static final String TAG = "AudioSessionManager";
    private static AudioSessionManager instance;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private AtomicInteger activeSessionCount = new AtomicInteger(0);
    private boolean audioFocusGranted = false;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl agc;
    private int currentAudioSessionId = -1;

    private AudioSessionManager(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public static synchronized AudioSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioSessionManager(context);
        }
        return instance;
    }

    public synchronized boolean requestAudioFocus(AudioManager.OnAudioFocusChangeListener focusChangeListener, 
                                                 int streamType, int focusGain) {
        if (audioFocusGranted && activeSessionCount.get() > 0) {
            activeSessionCount.incrementAndGet();
            return true;
        }

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(focusGain)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();

            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(focusChangeListener, streamType, focusGain);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusGranted = true;
            activeSessionCount.incrementAndGet();
            Log.d(TAG, "Audio focus granted. Active sessions: " + activeSessionCount.get());
            return true;
        } else {
            Log.w(TAG, "Audio focus request failed");
            return false;
        }
    }

    public synchronized void releaseAudioFocus(AudioManager.OnAudioFocusChangeListener focusChangeListener) {
        int currentCount = activeSessionCount.decrementAndGet();
        
        if (currentCount <= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
                audioFocusRequest = null;
            } else {
                audioManager.abandonAudioFocus(focusChangeListener);
            }
            
            audioFocusGranted = false;
            activeSessionCount.set(0);
            Log.d(TAG, "Audio focus released");
        } else {
            Log.d(TAG, "Audio focus maintained. Active sessions: " + currentCount);
        }
    }

    public synchronized void configureForCommunication() {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true); // Enable speakerphone for better echo cancellation
            Log.d(TAG, "Audio session configured for communication mode with speakerphone");
        }
    }

    public synchronized void configureForMedia() {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            Log.d(TAG, "Audio session configured for media mode");
        }
    }

    public synchronized void enableEchoCancellation(int audioSessionId) {
        if (currentAudioSessionId != audioSessionId) {
            releaseAudioEffects();
            currentAudioSessionId = audioSessionId;
        }
        
        configureForCommunication();
        setupAudioEffects(audioSessionId);
    }

    public synchronized void disableEchoCancellation() {
        configureForMedia();
        releaseAudioEffects();
    }
    
    private void setupAudioEffects(int audioSessionId) {
        try {
            // Setup Acoustic Echo Canceler
            if (AcousticEchoCanceler.isAvailable() && aec == null) {
                aec = AcousticEchoCanceler.create(audioSessionId);
                if (aec != null) {
                    aec.setEnabled(true);
                    Log.d(TAG, "AcousticEchoCanceler enabled: " + aec.getEnabled());
                } else {
                    Log.w(TAG, "Failed to create AcousticEchoCanceler");
                }
            }
            
            // Setup Noise Suppressor
            if (NoiseSuppressor.isAvailable() && noiseSuppressor == null) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                    Log.d(TAG, "NoiseSuppressor enabled: " + noiseSuppressor.getEnabled());
                } else {
                    Log.w(TAG, "Failed to create NoiseSuppressor");
                }
            }
            
            // Setup Automatic Gain Control
            if (AutomaticGainControl.isAvailable() && agc == null) {
                agc = AutomaticGainControl.create(audioSessionId);
                if (agc != null) {
                    agc.setEnabled(true);
                    Log.d(TAG, "AutomaticGainControl enabled: " + agc.getEnabled());
                } else {
                    Log.w(TAG, "Failed to create AutomaticGainControl");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up audio effects", e);
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
        
        currentAudioSessionId = -1;
    }

    public boolean isAudioFocusGranted() {
        return audioFocusGranted;
    }

    public int getActiveSessionCount() {
        return activeSessionCount.get();
    }
}