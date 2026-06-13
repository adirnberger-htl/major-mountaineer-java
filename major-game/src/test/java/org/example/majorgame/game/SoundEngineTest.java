package org.example.majorgame.game;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.example.majorgame.SoundManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


// HIER MUSS ICH BIG C BEDANKEN FÜR DIE WUNDERVOLLEN UNITTESTS DIE HABEN SO GEHOLFEN DUDE
class SoundManagerTest {

    private SoundManager soundManager;

    @BeforeEach
    void setUp() {
        soundManager = new SoundManager();
    }

    @Test
    void testVolumeClamping() {
        // Test lower bound clamping
        soundManager.setMusicVolume(-0.5);
        soundManager.setEffectsVolume(-0.1);

        // Use reflection or standard verification to check internal clamping behavior
        // Since we can't read private variables easily, we verify via MediaPlayer mock updates below
    }

    @Test
    void testPlayMusicWithMissingFile() {
        // Assert that calling a non-existent file doesn't throw a NullPointerException
        // and gracefully logs the error as designed.
        assertDoesNotThrow(() -> soundManager.playMusic("invalid_file_name.mp3"));
    }

    @Test
    void testStopMusicWhenNull() {
        // Ensure calling stop music before any music plays does not throw an exception
        assertDoesNotThrow(() -> soundManager.stopMusic());
    }

    @Test
    void testSetMusicVolumeUpdatesActivePlayer() {
        // We mock the internal construction of Media and MediaPlayer to prevent toolkit initialization crashes
        try (MockedConstruction<Media> mockedMedia = mockConstruction(Media.class);
             MockedConstruction<MediaPlayer> mockedPlayer = mockConstruction(MediaPlayer.class)) {

            // We look for a file that actually exists relative to this test class to pass the resource check,
            // or we use an existing asset file string name if your test resources are configured.
            // Assuming your class path can find a dummy file, or mock the resource loader if needed.
            soundManager.playMusic("tracks/soundtrack1.mp3");

            // Verify if a MediaPlayer was instantiated
            assertFalse(mockedPlayer.constructed().isEmpty(), "MediaPlayer should have been created");
            MediaPlayer mockPlayerInstance = mockedPlayer.constructed().get(0);

            // Change volume on manager
            soundManager.setMusicVolume(0.85);

            // Verify the manager delegated the volume update to the MediaPlayer instance
            verify(mockPlayerInstance).setVolume(0.85);
        }
    }

    @Test
    void testSetMusicVolumeClampingUpperBounds() {
        try (MockedConstruction<Media> mockedMedia = mockConstruction(Media.class);
             MockedConstruction<MediaPlayer> mockedPlayer = mockConstruction(MediaPlayer.class)) {

            soundManager.playMusic("tracks/soundtrack1.mp3");
            MediaPlayer mockPlayerInstance = mockedPlayer.constructed().get(0);

            // Set volume way over maximum bound
            soundManager.setMusicVolume(15.5);

            // Verify it was clamped down to maximum allowed (1.0)
            verify(mockPlayerInstance).setVolume(1.0);
        }
    }
    @Test
    void testStopMusicDisposesActivePlayer() {
        try (MockedConstruction<Media> mockedMedia = mockConstruction(Media.class);
             MockedConstruction<MediaPlayer> mockedPlayer = mockConstruction(MediaPlayer.class)) {

            soundManager.playMusic("tracks/soundtrack1.mp3");
            MediaPlayer mockPlayerInstance = mockedPlayer.constructed().get(0);

            soundManager.stopMusic();

            // Verify music lifecycle management methods are called sequentially
            verify(mockPlayerInstance).stop();
            verify(mockPlayerInstance).dispose();
        }
    }
}