package com.brouken.player;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * Volume boost applied to the PCM stream itself, used where the LoudnessEnhancer effect is not
 * available: some devices (Lenovo/MediaTek among them) gate third-party audio effects, handing out an
 * effect that never reaches AudioFlinger — it stays uninitialized and silently does nothing. See
 * Utils.applyBoost, which routes the boost to whichever of the two actually works.
 * <p>
 * Media3 ships a GainProcessor, but it casts the scaled sample straight to short: an overflowing peak
 * wraps around into noise instead of clipping, which is exactly what boosting produces.
 */
class BoostAudioProcessor extends BaseAudioProcessor {

    // Written on the app thread at every volume change, read on the playback thread.
    private volatile float gain = 1f;

    void setGain(float gain) {
        this.gain = gain;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        // NOT_SET keeps this processor out of the chain: passthrough bitstreams and any other encoding
        // are passed on untouched.
        return inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
                ? inputAudioFormat : AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) {
            // The pipeline drains itself with the shared empty buffer, which replaceOutputBuffer(0)
            // would hand straight back — copying a buffer onto itself throws.
            return;
        }
        // Read once: the gesture can change it while a buffer is being scaled.
        final float gain = this.gain;
        final ByteBuffer outputBuffer = replaceOutputBuffer(inputBuffer.remaining());
        if (gain == 1f) {
            outputBuffer.put(inputBuffer);
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.hasRemaining()) {
                final int sample = Math.round(inputBuffer.getShort() * gain);
                outputBuffer.putShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample)));
            }
        } else {
            while (inputBuffer.hasRemaining()) {
                outputBuffer.putFloat(Math.max(-1f, Math.min(1f, inputBuffer.getFloat() * gain)));
            }
        }
        outputBuffer.flip();
    }
}
