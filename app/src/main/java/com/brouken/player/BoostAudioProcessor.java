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

    /**
     * Where the centre sits in an interleaved frame, or -1 when this many channels carry no discrete
     * centre at all. Media3 delivers samples in the ascending bit order of the channel mask, and
     * FRONT_CENTER has only FRONT_LEFT and FRONT_RIGHT below it — so wherever a centre exists it is
     * the third sample of the frame. Four channels is quad (FL, FR, BL, BR) and has none; so do mono
     * and stereo, where the speech is in every channel and lifting every channel is the volume control.
     *
     * <p>A mask can in principle carry six channels without a centre, and then the third sample is a
     * surround. Nothing this side of the sink is told the mask — AudioProcessor.AudioFormat carries
     * only the count — so that case is left to the clip counter and the log line below to expose.
     */
    static int centreChannelIndex(int channelCount) {
        switch (channelCount) {
            case 3:
            case 5:
            case 6:
            case 7:
            case 8:
            case 10:
            case 12:
                return 2;
            default:
                return -1;
        }
    }

    // Written on the app thread at every volume change, read on the playback thread.
    private volatile float gain = 1f;
    // Whether the viewer asked for the lift at all. The amount is not a setting: it is decided per
    // configuration, since it depends on what the route will do with the channels.
    private volatile boolean centreBoost;
    // The amount in force, written by onConfigure and read on the playback thread. 1 = nothing to do.
    private volatile float centreGain = 1f;
    // What the route out of this device will take, so the lift can tell a discrete centre from one the
    // platform is about to fold into stereo. 0 = not known, treated as discrete.
    private volatile int routeChannelCount;
    // Said once, when a lifted centre first runs out of headroom: the effect is inaudible in a log and
    // invisible in a screenshot, and clipped dialogue is exactly what this setting can cause.
    private boolean clipReported;

    void setGain(float gain) {
        this.gain = gain;
    }

    /**
     * Arms the lift, or turns it off with 1. The amount is decided per configuration from the route:
     * see {@link #centreGainFor}.
     */
    void setCentreBoost(boolean on, int routeChannelCount) {
        this.centreBoost = on;
        this.routeChannelCount = routeChannelCount;
        if (!on) {
            centreGain = 1f;
        }
    }

    /**
     * How much to lift the centre of a track this wide on a route that wide.
     *
     * <p>Two cases, because they have different headroom. When the route carries the channels, the
     * centre reaches the speakers as itself and a lift is spent entirely on it: three decibels, which is
     * audible on a buried line and still leaves room over an ordinary peak.
     *
     * <p>When the route is narrower, the platform folds the track into stereo and the centre arrives
     * attenuated by the downmix — about three decibels of it — mixed under everything else. Six
     * decibels here is three after the fold, the same distance as the other case. The reference player
     * goes much further, setting its downmix centre coefficient to 3.0 against a default of 0.707, but
     * that value lives inside a matrix ffmpeg normalises afterwards, so it buys emphasis rather than
     * level. Ours is applied to the samples themselves, before a fold this player does not control, and
     * copying the number literally would clip the dialogue instead of clarifying it.
     */
    static float centreGainFor(int channelCount, int routeChannelCount) {
        if (centreChannelIndex(channelCount) < 0) {
            return 1f;
        }
        return routeChannelCount > 0 && routeChannelCount < channelCount ? 2f : 1.413f;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        // Said once per configuration, because the effect itself cannot be seen from a log or a
        // screenshot: this is the line that shows the lift was actually armed, on how many channels, and
        // which of its two amounts the route earned.
        if (centreBoost) {
            final float armed = centreGainFor(inputAudioFormat.channelCount, routeChannelCount);
            centreGain = armed;
            clipReported = false;
            Utils.log("centre boost: " + inputAudioFormat.channelCount + " channels, route "
                    + (routeChannelCount > 0 ? String.valueOf(routeChannelCount) : "unknown ")
                    + (armed == 1f ? ", no centre to lift"
                            : ", lifting by " + Math.round(20 * Math.log10(armed)) + " dB"));
        }
        // NOT_SET keeps this processor out of the chain: passthrough bitstreams and any other encoding
        // are passed on untouched.
        return inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
                ? inputAudioFormat : AudioFormat.NOT_SET;
    }

    /** Rounds to a sample and holds it inside the range, reporting the first time it has to. */
    private short clip(float scaled) {
        final int sample = Math.round(scaled);
        if (sample > Short.MAX_VALUE || sample < Short.MIN_VALUE) {
            if (!clipReported) {
                clipReported = true;
                Utils.log("centre boost: clipping on this mix, the lift has no headroom here");
            }
            return sample > 0 ? Short.MAX_VALUE : Short.MIN_VALUE;
        }
        return (short) sample;
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
        final int channelCount = inputAudioFormat.channelCount;
        final float centreGain = this.centreGain;
        final int centre = centreGain != 1f ? centreChannelIndex(channelCount) : -1;
        final ByteBuffer outputBuffer = replaceOutputBuffer(inputBuffer.remaining());
        if (gain == 1f && centre < 0) {
            outputBuffer.put(inputBuffer);
        } else if (gain == 1f && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            // The usual case with the lift on: the volume is where the viewer left it and five channels
            // out of six need no arithmetic at all. Copy the block, then rewrite every centre sample in
            // place — cheaper than a per-sample loop on a box that has to keep up in software.
            final int start = outputBuffer.position();
            final int frames = inputBuffer.remaining() / (2 * channelCount);
            outputBuffer.put(inputBuffer);
            for (int frame = 0; frame < frames; frame++) {
                final int at = start + (frame * channelCount + centre) * 2;
                outputBuffer.putShort(at, clip(outputBuffer.getShort(at) * centreGain));
            }
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            // Media3 hands whole frames, so the channel counter starts at the first channel of a frame
            // every time and needs nothing carried between buffers.
            int channel = 0;
            while (inputBuffer.hasRemaining()) {
                final float scale = channel == centre ? gain * centreGain : gain;
                outputBuffer.putShort(clip(inputBuffer.getShort() * scale));
                if (++channel == channelCount) {
                    channel = 0;
                }
            }
        } else {
            // Float in, float out. Unreachable while the sink puts its own conversion to 16-bit ahead of
            // this processor, which it has always done; kept so that a build where it does not is quiet
            // rather than wrong.
            int channel = 0;
            while (inputBuffer.hasRemaining()) {
                final float scale = channel == centre ? gain * centreGain : gain;
                outputBuffer.putFloat(Math.max(-1f, Math.min(1f, inputBuffer.getFloat() * scale)));
                if (++channel == channelCount) {
                    channel = 0;
                }
            }
        }
        outputBuffer.flip();
    }
}
