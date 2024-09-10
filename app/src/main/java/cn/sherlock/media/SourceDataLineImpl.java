package cn.sherlock.media;

import android.media.AudioManager;
import android.media.AudioTrack;
import cn.sherlock.javax.sound.sampled.AudioFormat;
import cn.sherlock.javax.sound.sampled.LineUnavailableException;
import cn.sherlock.javax.sound.sampled.SourceDataLine;

public class SourceDataLineImpl implements SourceDataLine {

	private AudioTrack audioTrack;
	private int bufferSize;
	private AudioFormat format = new AudioFormat(44100, 16, 2, true, false);;

	public SourceDataLineImpl() {

	}

	public SourceDataLineImpl(AudioFormat format) {
		this.format = format;
	}

	@Override
	public void drain() {
		this.flush();
	}

	@Override
	public void flush() {
		if (audioTrack != null) {
			audioTrack.flush();
		}
	}

	@Override
	public void start() {
		if (audioTrack != null) {
			audioTrack.play();
		}
	}

	@Override
	public void stop() {
		if (audioTrack != null) {
			audioTrack.stop();
		}
	}

	@Override
	public void close() {
		if (audioTrack != null) {
			audioTrack.stop();
			audioTrack.release();
			audioTrack = null;
		}
	}

	@Override
	public boolean isOpen() {
		return audioTrack != null;
	}

	@Override
	public boolean isRunning() {
		if (audioTrack != null) {
			return audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
		}
		return false;
	}

	@Override
	public boolean isActive() {
		return isRunning();
	}

	@Override
	public AudioFormat getFormat() {
		return format;
	}

	@Override
	public int getBufferSize() {
		if (audioTrack != null) {
			return bufferSize;
		}
		return 0;
	}

	@Override
	public int available() {
		return 0;
	}

	@Override
	public int getFramePosition() {
		if (audioTrack != null) {
			return audioTrack.getPlaybackHeadPosition();
		}
		return 0;
	}

	@Override
	public long getLongFramePosition() {
		if (audioTrack != null) {
			return audioTrack.getPlaybackHeadPosition();
		}
		return 0;
	}

	@Deprecated
	@Override
	public long getMicrosecondPosition() {
		if (audioTrack != null) {
			return audioTrack.getPlaybackHeadPosition() * 1000;
		}
		return 0;
	}

	@Deprecated
	@Override
	public float getLevel() {
		if (audioTrack != null) {
			// FIXME
			return 0;
		}
		return 0;
	}

	@Deprecated
	@Override
	public Info getLineInfo() {
		return null;
	}

	@Override
	public void open() throws LineUnavailableException {
		// Get the smallest buffer to minimize latency.
		int sampleRateInHz = (int) format.getSampleRate();
		// int sampleSizeInBit = format.getSampleSizeInBits();
		int channelConfig;
		if (format.getChannels() == 1) {
			channelConfig = android.media.AudioFormat.CHANNEL_OUT_MONO;
		} else if (format.getChannels() == 2) {
			channelConfig = android.media.AudioFormat.CHANNEL_OUT_STEREO;
		} else {
			throw new IllegalArgumentException(
					"format.getChannels() must in (1,2)");
		}

		int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz,
				channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT);
		bufferSize = bufferSizeInBytes;
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz,
				channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT,
				bufferSizeInBytes, AudioTrack.MODE_STREAM);
	}

	@Override
	public void open(AudioFormat format, int bufferSize)
			throws LineUnavailableException {
		// Get the smallest buffer to minimize latency.
		this.format = format;
		this.bufferSize = bufferSize;
		int sampleRateInHz = (int) format.getSampleRate();
		// int sampleSizeInBit = format.getSampleSizeInBits();
		int channelConfig;
		if (format.getChannels() == 1) {
			channelConfig = android.media.AudioFormat.CHANNEL_OUT_MONO;
		} else if (format.getChannels() == 2) {
			channelConfig = android.media.AudioFormat.CHANNEL_OUT_STEREO;
		} else {
			throw new IllegalArgumentException(
					"format.getChannels() must in (1,2)");
		}
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz,
				channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT,
				bufferSize, AudioTrack.MODE_STREAM);
	}

	@Override
	public void open(AudioFormat format) throws LineUnavailableException {
		// Get the smallest buffer to minimize latency.
		this.format = format;
		int sampleRateInHz = (int) format.getSampleRate();
		// int sampleSizeInBit = format.getSampleSizeInBits();
		int channelConfig;
		if (format.getChannels() == 1) {
			channelConfig = android.media.AudioFormat.CHANNEL_OUT_MONO;
		} else if (format.getChannels() == 2) {
			channelConfig = android.media.AudioFormat.CHANNEL_OUT_STEREO;
		} else {
			throw new IllegalArgumentException(
					"format.getChannels() must in (1,2)");
		}
		int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz,
				channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT);
		this.bufferSize = bufferSizeInBytes;
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz,
				channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT,
				bufferSizeInBytes, AudioTrack.MODE_STREAM);
	}

	@Override
	public int write(byte[] b, int off, int len) {
		if(audioTrack != null){
			return audioTrack.write(b, off, len);
		}
		return 0;
	}

}
