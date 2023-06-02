package linc.com.example;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.masoudss.lib.WaveformSeekBar;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import linc.com.amplituda.Amplituda;
import linc.com.amplituda.AmplitudaProgressListener;
import linc.com.amplituda.AmplitudaResult;
import linc.com.amplituda.Cache;
import linc.com.amplituda.Compress;
import linc.com.amplituda.InputAudio;
import linc.com.amplituda.ProgressOperation;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private SimpleExoPlayer exoPlayer;
    //    private String sampleUrl = "/storage/emulated/0/Music/Samsung/Over_the_Horizon.mp3";
    private String sampleUrl = "/storage/emulated/0/Music/Samsung/test.mp3";
    private PlayerView exoPlayerView;


    private Handler mHandler = new Handler();
    private Runnable updateSeekRunnable = this::updateSeek;
    WaveformSeekBar waveformSeekBar;

    float maxProgress = 100f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        exoPlayerView = findViewById(R.id.music_player);
        // ExoPlayer 인스턴스를 생성하고 소스를 플레이에 할당하여 비디오 플레이어 초기화
        exoPlayer = new SimpleExoPlayer.Builder(this).build();
        exoPlayerView.setPlayer(exoPlayer);

        MediaSource mediaSource = buildMediaSource();
        if (mediaSource != null) {
            exoPlayer.prepare(mediaSource);
        }

        waveformSeekBar = findViewById(R.id.wave_form_seekbar);
        waveformSeekBar.setSampleFrom(new File(sampleUrl));

        waveformSeekBar.setOnProgressChanged((waveSeekBar, currentProgress, isUser) -> {
            // do your stuff here
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                int[] sample = waveSeekBar.getSample();
                if (sample != null) {
                    float currentWaveIndex = sample.length * (currentProgress / maxProgress);
                    if (currentWaveIndex < 0) currentWaveIndex = 0;
                    else if (currentWaveIndex >= sample.length) currentWaveIndex = sample.length - 1;
                    Log.e(TAG, "currentAmplitude: " + sample[(int) currentWaveIndex]);

                }
            }
        });

        Button updateSeekBtn = findViewById(R.id.update_seek_btn);
        updateSeekBtn.setOnClickListener(view -> {
            exoPlayer.setPlayWhenReady(true);
            int[] sample = waveformSeekBar.getSample();
            if (sample != null) {
                maxProgress = sample.length - 1;
                waveformSeekBar.setMaxProgress(maxProgress);

//                HashMap<Float, String> map = new HashMap<>();
//                map.put(waveformSeekBar.getMaxProgress() / 2f, "The middle");
//                waveformSeekBar.setMarker(map);

                Log.d(TAG, "sample.length : " + sample.length + "   sampleData : " + Arrays.toString(sample));
                updateSeek();
            }
        });

        Amplituda amplituda = new Amplituda(this);
        amplituda.setLogConfig(Log.ERROR, true);


        amplituda.processAudio(sampleUrl,
                Compress.withParams(Compress.AVERAGE, 0),
                Cache.withParams(Cache.REFRESH),
                new AmplitudaProgressListener() {
                    @Override
                    public void onStartProgress() {
                        super.onStartProgress();
                        Log.e(TAG, "Start Progress");
                    }

                    @Override
                    public void onStopProgress() {
                        super.onStopProgress();
                        Log.e(TAG, "Stop Progress");
                    }

                    @Override
                    public void onProgress(ProgressOperation operation, int progress) {
                        String currentOperation = "";
                        switch (operation) {
                            case PROCESSING:
                                currentOperation = "Process audio";
                                break;
                            case DECODING:
                                currentOperation = "Decode audio";
                                break;
                            case DOWNLOADING:
                                currentOperation = "Download audio from url";
                                break;
                        }
                        Log.e(TAG, String.format("%s: %d%% %n", currentOperation, progress));
                    }
                }).get(this::printResult, exception -> {
            Log.e(TAG, "exception:" + exception);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exoPlayer.release();
        exoPlayer = null;
        mHandler.removeCallbacks(updateSeekRunnable);
    }

    private void updateSeek() {
        if (exoPlayer == null) {
            return;
        }
        long duration = exoPlayer.getDuration() >= 0 ? exoPlayer.getDuration() : 0; // 전체 음악 길이
        long position = exoPlayer.getCurrentPosition();
//        Log.e(TAG, "updateSeek: positon => " + position + " duration => " + duration);

//        int curtime = (int) (position / 1000);
//        int maxtime = (int) (duration / 1000);

        float curProgress = (float) position / duration * maxProgress;

        if (position <= duration) {
            waveformSeekBar.setProgress(curProgress);
        }
        int state = exoPlayer.getPlaybackState();
        mHandler.removeCallbacks(updateSeekRunnable);

        if (state != Player.STATE_IDLE && state != Player.STATE_ENDED && exoPlayer.isPlaying()) {
            mHandler.postDelayed(updateSeekRunnable, 60);
        }
    }

    // MediaSource: 영상에 출력할 미디어 정보를 가져오는 클래스
    private MediaSource buildMediaSource() {
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(this, "sample");
        return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(Uri.parse(sampleUrl)));
    }

    @Override
    protected void onStop() {
        super.onStop();
        exoPlayer.setPlayWhenReady(false);
        releasePlayer();
    }

    private void releasePlayer() {
        exoPlayer.release();
    }

    public static double getAmplitude(byte[] audioBuffer) {
        double sum = 0;

        for (byte sample : audioBuffer) {
            sum += sample * sample;
        }

        return Math.sqrt(sum / audioBuffer.length);
    }

    private void printResult(AmplitudaResult<?> result) {
        Log.e(TAG, String.format(Locale.US, "Audio info:\n" + "millis = %d\n" + "seconds = %d\n\n" + "source = %s\n" + "source type = %s\n\n" + "Amplitudes:\n" + "size: = %d\n" + "list: = %s\n" + "amplitudes for second 1: = %s\n" + "json: = %s\n" + "single line sequence = %s\n"
//                        + "new line sequence = %s\n"
                        + "custom delimiter sequence = %s\n"
                , result.getAudioDuration(AmplitudaResult.DurationUnit.MILLIS), result.getAudioDuration(AmplitudaResult.DurationUnit.SECONDS), result.getInputAudioType() == InputAudio.Type.FILE ? ((File) result.getAudioSource()).getAbsolutePath() : result.getAudioSource(), result.getInputAudioType().name(), result.amplitudesAsList().size(), Arrays.toString(result.amplitudesAsList().toArray()), Arrays.toString(result.amplitudesForSecond(1).toArray()), result.amplitudesAsJson(), result.amplitudesAsSequence(AmplitudaResult.SequenceFormat.SINGLE_LINE),
//                result.amplitudesAsSequence(AmplitudaResult.SequenceFormat.NEW_LINE),
                result.amplitudesAsSequence(AmplitudaResult.SequenceFormat.SINGLE_LINE, " * ")));
//        Log.e(TAG, "printResult: "+result );
    }

}