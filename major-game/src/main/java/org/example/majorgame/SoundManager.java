package org.example.majorgame;

import javafx.scene.media.Media;
import javafx.scene.media.AudioClip;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;


public class SoundManager {
    private static final String SOUND_PATH = "/org/example/majorgame/sounds/";

    private final Map<String, AudioClip> effects = new HashMap<>();
    private Media activeMedia;
    private MediaPlayer musicPlayer;
    private AudioClip activeLongTrack;

    private double musicVolume = 0.35;
    private double effectsVolume = 0.65;

    public void playMusic(String fileName) {
        Runnable playTask = () -> {
            stopMusic();

            String resourcePath = SOUND_PATH + fileName;
            URL resource = getClass().getResource(SOUND_PATH + fileName);
            if (resource == null) {
                System.err.println("Missing music file: " + fileName);
                System.err.println("Path:" + resourcePath);
                return;
            }

            try {
                //ich peitsch den java garbage collector hab so ne halbe stunde rumfiddeln müssen deswegen
                activeMedia = new Media(resource.toExternalForm());
                musicPlayer = new MediaPlayer(activeMedia);

                musicPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                musicPlayer.setVolume(musicVolume);


                musicPlayer.setOnReady(() -> System.out.println("Audio successfully loaded and playing: " + fileName));
                musicPlayer.setOnError(() -> System.err.println("Player error: " + musicPlayer.getError().getMessage()));

                musicPlayer.play();
            } catch (Exception e) {
                System.err.println("Error initializing player for: " + fileName);
                e.printStackTrace();
            }
        };

        if (javafx.application.Platform.isFxApplicationThread()) {
            playTask.run();
        } else {
            try {
                javafx.application.Platform.runLater(playTask);
            } catch (IllegalStateException e) {
                playTask.run();
            }
        }
    }



    public void stopMusic() {
        if (musicPlayer != null) {
            musicPlayer.stop();
            musicPlayer.dispose();
            musicPlayer = null;
            activeMedia = null;
        }
        stopLongWavTrack();
    }

    public void playEffect(String fileName) {
        AudioClip clip = effects.computeIfAbsent(fileName, this::loadEffect);
        if (clip != null) {
            clip.setVolume(effectsVolume);
            clip.play();
        }
    }

    private AudioClip loadEffect(String fileName) {
        URL resource = getClass().getResource(SOUND_PATH + fileName);
        if (resource == null) {
            System.err.println("Missing sound effect: " + fileName);
            return null;
        }
        return new AudioClip(resource.toExternalForm());
    }

    public void setMusicVolume(double musicVolume) {
        this.musicVolume = clamp(musicVolume);
        if (musicPlayer != null) {
            musicPlayer.setVolume(this.musicVolume);
        }
    }

    public void setEffectsVolume(double effectsVolume) {
        this.effectsVolume = clamp(effectsVolume);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public void playLongWavTrack(String fileName) {
        stopLongWavTrack();
        URL resource = getClass().getResource(SOUND_PATH + fileName);
        if (resource == null) {
            System.err.println("Missing file: " + fileName);
            return;
        }

        // AudioClip bypasses MediaPlayer's native compression issues
        activeLongTrack = new AudioClip(resource.toExternalForm());
        activeLongTrack.setCycleCount(AudioClip.INDEFINITE); // Loop forever
        activeLongTrack.setVolume(musicVolume);
        activeLongTrack.play();
    }

    public void stopLongWavTrack() {
        if (activeLongTrack != null) {
            activeLongTrack.stop();
            activeLongTrack = null;
        }
    }

}